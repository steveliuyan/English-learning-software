package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiFailure
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
)

@HiltViewModel
class PhotoTranslationViewModel @Inject constructor(
    private val ocr: OcrProviderChain,
    private val workspace: TemporaryImageWorkspace,
    private val translate: PhotoTranslationUseCase,
    private val modelAssets: EnglishOcrModelAssets,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PhotoTranslationUiState())
    val uiState: StateFlow<PhotoTranslationUiState> = _uiState

    private var captureJob: Job? = null
    private var translateJob: Job? = null
    private var modelJob: Job? = null

    fun onImageBytes(
        bytes: ByteArray,
    ) {
        captureJob?.cancel()
        _uiState.update {
            it.copy(
                busy = true,
                ocrText = null,
                lowConfidenceLineIndices = emptySet(),
                engine = null,
                ocrIssue = null,
                translation = null,
                translationIssue = null,
                pendingConfirmationHost = null,
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
        if (text.isEmpty() || _uiState.value.busy) return
        runTranslation(text, confirmedTextHost = null)
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
        runTranslation(text, confirmedTextHost = host)
    }

    fun reset() {
        captureJob?.cancel()
        translateJob?.cancel()
        modelJob?.cancel()
        workspace.purgeAll()
        _uiState.value = PhotoTranslationUiState()
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
        confirmedTextHost: String?,
    ) {
        translateJob?.cancel()
        translateJob =
            viewModelScope.launch {
                try {
                    when (val result = translate.translate(text, confirmedTextHost)) {
                        is PhotoTranslationResult.Translated ->
                            _uiState.update {
                                it.copy(
                                    translation = result.source to result.target,
                                    translationIssue = null,
                                    pendingConfirmationHost = null,
                                )
                            }
                        is PhotoTranslationResult.NeedsConfirmation ->
                            _uiState.update { it.copy(pendingConfirmationHost = result.host, translationIssue = null) }
                        is PhotoTranslationResult.Failed ->
                            _uiState.update {
                                it.copy(
                                    translationIssue = PhotoTranslationFailureCategory.Ai(result.failure),
                                    translation = null,
                                    pendingConfirmationHost = null,
                                )
                            }
                        is PhotoTranslationResult.NotConfigured ->
                            _uiState.update {
                                it.copy(
                                    translationIssue = PhotoTranslationFailureCategory.NotConfigured(result.reason),
                                    translation = null,
                                    pendingConfirmationHost = null,
                                )
                            }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    _uiState.update { it.copy(pendingConfirmationHost = null) }
                }
            }
    }

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
