package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.language.domain.EnglishOcrModelAssets
import com.example.englishlearning.language.domain.OcrChainOutcome
import com.example.englishlearning.language.domain.OcrEngineKind
import com.example.englishlearning.language.domain.OcrImage
import com.example.englishlearning.language.domain.OcrModelAssetInstallOutcome
import com.example.englishlearning.language.domain.OcrModelAssetStatus
import com.example.englishlearning.language.domain.OcrProviderChain
import com.example.englishlearning.language.domain.TemporaryImageWorkspace
import com.example.englishlearning.phototranslation.PhotoTranslationNotConfiguredReason
import com.example.englishlearning.phototranslation.PhotoTranslationResult
import com.example.englishlearning.phototranslation.PhotoTranslationUseCase
import com.example.englishlearning.phototranslation.PhotoTranslationHistoryRepository
import com.example.englishlearning.phototranslation.RoomPhotoTranslationHistoryRepository
import com.example.englishlearning.phototranslation.SavedPhotoTranslation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** OCR 自身的分类（AC3-04）：与翻译失败互不混同，「不可用」不是「翻译失败」。 */
sealed interface PhotoTranslationOcrIssue {
    data object Unavailable : PhotoTranslationOcrIssue

    data class Failed(val reasons: Map<OcrEngineKind, String?>) : PhotoTranslationOcrIssue
}

sealed interface PhotoTranslationFailureCategory {
    data class Ai(val failure: AiFailure) : PhotoTranslationFailureCategory

    data class NotConfigured(val reason: PhotoTranslationNotConfiguredReason) : PhotoTranslationFailureCategory
}

data class PhotoTranslationUiState(
    val busy: Boolean = false,
    /**
     * AI 翻译是否在飞（批 C-4）。此前只有 [busy]，而 `busy` 只由识别路径写 `true`——
     * 翻译在飞时界面零回执、翻译按钮照旧可点，再点一次就是「掐掉上一发 + 再付一次费」。
     * 识别与翻译是两件事，所以是两个字段，不用一个 `busy` 混装（AC3-04 同一口径）。
     */
    val translating: Boolean = false,
    /** 识别后的可编辑文本；翻译失败后依然保留（AC3-06）。 */
    val ocrText: String? = null,
    val lowConfidenceLineIndices: Set<Int> = emptySet(),
    val engine: OcrEngineKind? = null,
    val ocrIssue: PhotoTranslationOcrIssue? = null,
    val translation: Pair<String, String>? = null,
    val translationIssue: PhotoTranslationFailureCategory? = null,
    /** 非空即弹出站确认框；每次翻译请求都要重新确认，确认前零字节出网（AC3-06）。 */
    val pendingConfirmationHost: String? = null,
    /** Tesseract 离线备用模型的安装态（下载前展示大小与许可，可删除）。 */
    val modelStatus: OcrModelAssetStatus? = null,
    val modelInstallProgress: Float? = null,
    val modelIssue: OcrModelAssetInstallOutcome? = null,
    /** 已明确保存的历史（按创建时间降序）。 */
    val history: List<SavedPhotoTranslation> = emptyList(),
    val lastSavedRecordId: String? = null,
    val saveIssue: AppError? = null,
)

@HiltViewModel
class PhotoTranslationViewModel @Inject constructor(
    private val ocr: OcrProviderChain,
    private val workspace: TemporaryImageWorkspace,
    private val translate: PhotoTranslationUseCase,
    private val modelAssets: EnglishOcrModelAssets,
    private val history: PhotoTranslationHistoryRepository,
    private val recordIds: WordNoteIdFactory,
    private val clock: ClockProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PhotoTranslationUiState())
    val uiState: StateFlow<PhotoTranslationUiState> = _uiState

    private var captureJob: Job? = null
    private var translateJob: Job? = null
    private var modelJob: Job? = null
    private var historyJob: Job? = null

    /**
     * 本次采集的原图字节只留在内存，唯一出口是用户「明确保存」（AC3-05）；
     * reset/离开页面即释放，磁盘上从头到尾只有临时区，且任何出口零残留。
     */
    private var captureBytes: ByteArray? = null

    fun onImageBytes(
        bytes: ByteArray,
    ) {
        captureJob?.cancel()
        // 换一张图 = 上一张的一切作废：文本、译文、以及**还在飞的那一发翻译**。旧写法只清了显示结果，
        // 请求照旧飞完并把上一张的译文写回新图界面（批 C-4 补这条，与 translating 同一个面）。
        translateJob?.cancel()
        translateJob = null
        captureBytes = bytes.copyOf()
        _uiState.update {
            it.copy(
                busy = true,
                translating = false,
                ocrText = null,
                lowConfidenceLineIndices = emptySet(),
                engine = null,
                ocrIssue = null,
                translation = null,
                translationIssue = null,
                pendingConfirmationHost = null,
                lastSavedRecordId = null,
                saveIssue = null,
            )
        }
        captureJob =
            viewModelScope.launch {
                try {
                    val outcome =
                        workspace.useCapture(IMAGE_EXTENSION) { handle ->
                            when (workspace.write(handle, bytes)) {
                                TemporaryImageWorkspace.WriteOutcome.RejectedTooLarge ->
                                    OcrChainOutcome.RecognitionFailed(emptyMap())
                                TemporaryImageWorkspace.WriteOutcome.Written -> ocr.recognize(OcrImage(bytes))
                            }
                        }
                    when (outcome) {
                        is OcrChainOutcome.Recognized ->
                            _uiState.update {
                                it.copy(
                                    busy = false,
                                    engine = outcome.engine,
                                    ocrText = outcome.result.text,
                                    lowConfidenceLineIndices = lowConfidenceLines(outcome),
                                    ocrIssue = null,
                                )
                            }
                        OcrChainOutcome.OcrUnavailable ->
                            _uiState.update {
                                it.copy(
                                    busy = false,
                                    ocrIssue = PhotoTranslationOcrIssue.Unavailable,
                                    translationIssue = null,
                                )
                            }
                        is OcrChainOutcome.RecognitionFailed ->
                            _uiState.update {
                                it.copy(
                                    busy = false,
                                    ocrIssue = PhotoTranslationOcrIssue.Failed(outcome.reasons),
                                    translationIssue = null,
                                )
                            }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    _uiState.update { it.copy(busy = false, ocrIssue = PhotoTranslationOcrIssue.Failed(emptyMap())) }
                }
            }
    }

    fun onOcrTextEdited(
        text: String,
    ) {
        _uiState.update { it.copy(ocrText = text, translation = null, translationIssue = null) }
    }

    fun onTranslateTapped() {
        val text = _uiState.value.ocrText?.trim().orEmpty()
        // translating 也在闸内：在飞时再点一次不是「再要一次翻译」，是 translateJob?.cancel() 后重发，
        // 用户按两下付两次钱、界面显示哪一句由竞态决定（屏侧禁用是第一道防线，这里是第二道）。
        if (text.isEmpty() || _uiState.value.busy || _uiState.value.translating) return
        runTranslation(text, confirmedTextHosts = emptySet())
    }

    fun onConfirmationResult(
        confirmed: Boolean,
    ) {
        val host = _uiState.value.pendingConfirmationHost ?: return
        if (!confirmed) {
            _uiState.update { it.copy(pendingConfirmationHost = null) }
            return
        }
        val text = _uiState.value.ocrText?.trim().orEmpty()
        if (text.isEmpty()) {
            _uiState.update { it.copy(pendingConfirmationHost = null) }
            return
        }
        // 确认框在点下去那一刻就收起：它问的是「发不发」，答案已经给出，继续压着会把下面的
        // 进行中面挡在框后面，用户看不到「正在等」这件事（批 C-4 的进行中语义要有地方画）。
        _uiState.update { it.copy(pendingConfirmationHost = null) }
        runTranslation(text, confirmedTextHosts = setOf(host))
    }

    /**
     * 取消正在等待的翻译（批 C-4）：不再等这一发，但**识别出的文本原样留着**可改可重试
     * （决策 2026-11-07 §3 决定 3 第 2 条——落点取决于用户手里已经有什么；本机识别的文本没花过钱，
     * 却是用户校对过的手上物，销毁它等于让用户重拍一张）。
     */
    fun cancelTranslate() {
        if (!_uiState.value.translating) return
        translateJob?.cancel()
        translateJob = null
        _uiState.update { it.copy(translating = false, pendingConfirmationHost = null) }
    }

    fun reset() {
        captureJob?.cancel()
        translateJob?.cancel()
        modelJob?.cancel()
        historyJob?.cancel()
        captureBytes = null
        workspace.purgeAll()
        _uiState.value = PhotoTranslationUiState()
    }

    /**
     * 用户明确保存才写原图 +  OCR 文本 + 译文（AC3-05）。没有成功译文时是空操作；
     * 原图写入失败失败关闭（不静默存成无图记录），记录落库失败则回滚已写入的资产。
     */
    fun saveCurrentTranslation(
        profileId: String,
    ) {
        val state = _uiState.value
        val translation = state.translation ?: return
        if (state.lastSavedRecordId != null) return
        historyJob?.cancel()
        historyJob =
            viewModelScope.launch {
                try {
                    val bytes = captureBytes
                    val assetId: String? =
                        if (bytes == null) {
                            null
                        } else {
                            history.storeImage(bytes).getOrElse { failure ->
                                _uiState.update { it.copy(saveIssue = storageError(failure)) }
                                return@launch
                            }
                        }
                    val record =
                        SavedPhotoTranslation(
                            recordId = recordIds.newId(),
                            profileId = profileId,
                            sourceText = translation.first,
                            translatedText = translation.second,
                            engine = state.engine,
                            imageAssetId = assetId,
                            createdAtEpochMillis = clock.instant().toEpochMilli(),
                        )
                    history.save(record)
                        .onSuccess {
                            _uiState.update {
                                it.copy(
                                    lastSavedRecordId = record.recordId,
                                    saveIssue = null,
                                    history = (listOf(record) + it.history).take(RoomPhotoTranslationHistoryRepository.LIST_LIMIT),
                                )
                            }
                        }
                        .onFailure { failure ->
                            if (assetId != null) history.removeImage(assetId)
                            _uiState.update { it.copy(saveIssue = storageError(failure)) }
                        }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    _uiState.update { it.copy(saveIssue = AppError.StorageUnavailable) }
                }
            }
    }

    fun refreshHistory(
        profileId: String,
    ) {
        historyJob?.cancel()
        historyJob =
            viewModelScope.launch {
                val records = history.listRecent(profileId).getOrNull() ?: return@launch
                _uiState.update { it.copy(history = records) }
            }
    }

    fun deleteSaved(
        profileId: String,
        recordId: String,
    ) {
        historyJob?.cancel()
        historyJob =
            viewModelScope.launch {
                history.delete(profileId, recordId)
                val records = history.listRecent(profileId).getOrNull() ?: return@launch
                _uiState.update { it.copy(history = records) }
            }
    }

    fun refreshModelStatus() {
        modelJob =
            viewModelScope.launch {
                runCatching { modelAssets.status() }
                    .onSuccess { status -> _uiState.update { it.copy(modelStatus = status) } }
                    .onFailure { _uiState.update { it.copy(modelStatus = null) } }
            }
    }

    fun installModel() {
        // 同步置进度即互斥：连点第二次进来时进度已非空，直接返回，不会并发下载。
        if (_uiState.value.modelInstallProgress != null) return
        _uiState.update { it.copy(modelInstallProgress = 0f, modelIssue = null) }
        modelJob =
            viewModelScope.launch {
                try {
                    when (val outcome = modelAssets.install { progress ->
                        _uiState.update { it.copy(modelInstallProgress = progress) }
                    }) {
                        is OcrModelAssetInstallOutcome.Installed ->
                            _uiState.update {
                                it.copy(
                                    modelStatus = OcrModelAssetStatus.Installed(outcome.sizeBytes),
                                    modelInstallProgress = null,
                                    modelIssue = null,
                                )
                            }
                        else ->
                            _uiState.update {
                                it.copy(
                                    modelStatus = OcrModelAssetStatus.NotInstalled,
                                    modelInstallProgress = null,
                                    modelIssue = outcome,
                                )
                            }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    _uiState.update {
                        it.copy(
                            modelStatus = OcrModelAssetStatus.NotInstalled,
                            modelInstallProgress = null,
                            modelIssue = OcrModelAssetInstallOutcome.DownloadFailed(failure.message),
                        )
                    }
                }
            }
    }

    fun uninstallModel() {
        modelJob?.cancel()
        modelJob =
            viewModelScope.launch {
                runCatching { modelAssets.uninstall() }
                _uiState.update {
                    it.copy(
                        modelStatus = modelAssets.status(),
                        modelInstallProgress = null,
                        modelIssue = null,
                    )
                }
            }
    }

    override fun onCleared() {
        workspace.purgeAll()
        super.onCleared()
    }

    private fun runTranslation(
        text: String,
        confirmedTextHosts: Set<String>,
    ) {
        translateJob?.cancel()
        // 同步置 translating 即互斥：第二发还没进得来（onTranslateTapped 的闸），进来了也先被上面那行顶掉。
        _uiState.update { it.copy(translating = true) }
        translateJob =
            viewModelScope.launch {
                try {
                    when (val result = translate.translate(text, confirmedTextHosts)) {
                        is PhotoTranslationResult.Translated ->
                            _uiState.update {
                                it.copy(
                                    translating = false,
                                    translation = result.source to result.target,
                                    translationIssue = null,
                                    pendingConfirmationHost = null,
                                )
                            }
                        is PhotoTranslationResult.NeedsConfirmation ->
                            _uiState.update {
                                it.copy(
                                    translating = false,
                                    pendingConfirmationHost = result.host,
                                    translationIssue = null,
                                )
                            }
                        is PhotoTranslationResult.Failed ->
                            _uiState.update {
                                it.copy(
                                    translating = false,
                                    translationIssue = PhotoTranslationFailureCategory.Ai(result.failure),
                                    translation = null,
                                    pendingConfirmationHost = null,
                                )
                            }
                        is PhotoTranslationResult.NotConfigured ->
                            _uiState.update {
                                it.copy(
                                    translating = false,
                                    translationIssue = PhotoTranslationFailureCategory.NotConfigured(result.reason),
                                    translation = null,
                                    pendingConfirmationHost = null,
                                )
                            }
                    }
                } catch (cancellation: CancellationException) {
                    // cancelTranslate() 已经把 translating 收回 false；被下一发顶掉时那一发的臂自己会写，
                    // 这里都不许再补一笔——补了就是「新请求还在飞、界面却说不飞」。
                    throw cancellation
                } catch (_: Throwable) {
                    _uiState.update { it.copy(translating = false, pendingConfirmationHost = null) }
                }
            }
    }

    private fun storageError(
        failure: Throwable,
    ): AppError = (failure as? AppErrorException)?.appError ?: AppError.StorageUnavailable

    private fun lowConfidenceLines(
        outcome: OcrChainOutcome.Recognized,
    ): Set<Int> =
        outcome.result.regions.mapIndexedNotNull { index, region ->
            index.takeIf { region.confidence.isNaN() || region.confidence < LOW_CONFIDENCE_THRESHOLD }
        }.toSet()

    private companion object {
        const val IMAGE_EXTENSION = "img"
        const val LOW_CONFIDENCE_THRESHOLD = 0.5f
    }
}
