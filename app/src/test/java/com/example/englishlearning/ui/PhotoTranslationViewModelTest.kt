package com.example.englishlearning.ui

import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpResponse
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.language.domain.EnglishOcrModelAssets
import com.example.englishlearning.language.domain.OcrChainOutcome
import com.example.englishlearning.language.domain.OcrEngineKind
import com.example.englishlearning.language.domain.OcrImage
import com.example.englishlearning.language.domain.OcrModelAssetInstallOutcome
import com.example.englishlearning.language.domain.OcrModelAssetStatus
import com.example.englishlearning.language.domain.OcrOutcome
import com.example.englishlearning.language.domain.OcrProvider
import com.example.englishlearning.language.domain.OcrProviderChain
import com.example.englishlearning.language.domain.OcrRegion
import com.example.englishlearning.language.domain.OcrResult
import com.example.englishlearning.language.domain.TemporaryImageWorkspace
import com.example.englishlearning.phototranslation.PhotoTranslationHistoryRepository
import com.example.englishlearning.phototranslation.PhotoTranslationNotConfiguredReason
import com.example.englishlearning.phototranslation.PhotoTranslationResult
import com.example.englishlearning.phototranslation.PhotoTranslationUseCase
import com.example.englishlearning.phototranslation.SavedPhotoTranslation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir

/**
 * 拍照翻译状态机：OCR 分类（不可用/失败）与翻译分类互不混同（AC3-04）；
 * 临时区任何出口零残留（AC3-05）；确认门每次触发、拒绝零字节出网、失败后保留可编辑
 * OCR 文本并可重试（AC3-06）。
 */
class PhotoTranslationViewModelTest {
    @TempDir
    lateinit var root: File

    private val dispatcher = StandardTestDispatcher()

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        assertTrue(root.walkBottomUp().filter { it.isFile }.toList().isEmpty(), "capture residue: ${root.walkBottomUp().filter { it.isFile }.toList()}")
    }

    private class ScriptedOcrProvider(
        override val kind: OcrEngineKind,
        private vararg val outcomes: OcrOutcome,
    ) : OcrProvider {
        var calls = 0

        @Suppress("UNCHECKED_CAST")
        override suspend fun recognize(
            image: OcrImage,
        ): OcrOutcome = outcomes.getOrElse(calls++) { outcomes.lastOrNull() ?: error("exhausted") }
    }

    private val profile =
        AiProfile(
            profileId = "p1",
            displayName = "测试",
            websiteUrl = "https://example.com",
            endpoint = "https://api.example.com/v1",
            model = "gpt-x",
            capabilities = setOf(AiCapability.Text),
            secretReference = SecretReference("ai-profile-p1"),
        )

    private class FakeSecretStore : SecretStore {
        override fun save(
            reference: SecretReference,
            secret: CharArray,
        ) = Result.success(Unit)

        override fun read(reference: SecretReference) = Result.success("k3y".toCharArray())

        override fun delete(reference: SecretReference) = Result.success(Unit)

        override fun has(reference: SecretReference) = Result.success(true)
    }

    private class RecordingTransport : AiHttpTransport {
        val requests = mutableListOf<AiHttpRequest>()
        var result: AiHttpResult = AiHttpResult.Responded(AiHttpResponse(200, """{"choices":[{"message":{"content":"鸟儿会飞。"}}]}"""))

        override suspend fun send(
            request: AiHttpRequest,
        ): AiHttpResult {
            requests += request
            return result
        }
    }

    /** 可记录的历史仓库假件：保存/列表/删除与原图存取全部只记不真写。 */
    private class FakeHistory : PhotoTranslationHistoryRepository {
        val saved = mutableListOf<SavedPhotoTranslation>()
        val storedImages = mutableListOf<String>()
        val removedImages = mutableListOf<String>()
        var saveResult: Result<Unit> = Result.success(Unit)
        var storeResult: Result<String> = Result.success("asset-1")
        var deleteCalls = mutableListOf<Pair<String, String>>()

        override suspend fun save(record: SavedPhotoTranslation): Result<Unit> {
            saved += record
            return saveResult
        }

        override suspend fun listRecent(profileId: String): Result<List<SavedPhotoTranslation>> =
            Result.success(saved.filter { it.profileId == profileId }.sortedByDescending { it.createdAtEpochMillis })

        override suspend fun delete(profileId: String, recordId: String): Result<Unit> {
            deleteCalls += profileId to recordId
            saved.removeAll { it.recordId == recordId && it.profileId == profileId }
            return Result.success(Unit)
        }

        override suspend fun storeImage(bytes: ByteArray): Result<String> {
            return storeResult.onSuccess { storedImages += it }
        }

        override suspend fun removeImage(assetId: String): Result<Unit> {
            removedImages += assetId
            return Result.success(Unit)
        }
    }

    private fun viewModel(
        chain: OcrProviderChain,
        transport: RecordingTransport = RecordingTransport(),
        modelAssets: FakeModelAssets = FakeModelAssets(),
        history: PhotoTranslationHistoryRepository = FakeHistory(),
        ids: WordNoteIdFactory = WordNoteIdFactory { "record-1" },
        clock: ClockProvider = FixedClockProvider(java.time.Instant.ofEpochMilli(1_700_000_000_000L), java.time.ZoneOffset.UTC),
    ): PhotoTranslationViewModel {
        Dispatchers.setMain(dispatcher)
        return PhotoTranslationViewModel(
            ocr = chain,
            workspace = TemporaryImageWorkspace(root),
            translate =
                PhotoTranslationUseCase(
                    defaultTextProfile = DefaultTextProfileResolver { DefaultTextProfileResult.Selected(profile) },
                    secrets = AiProfileSecretUseCase(FakeSecretStore()),
                    transport = transport,
                ),
            modelAssets = modelAssets,
            history = history,
            recordIds = ids,
            clock = clock,
        )
    }

    /** 可控的离线备用模型资产：状态、安装进度、安装结果与删除都可脚本化。 */
    private class FakeModelAssets(
        var status: OcrModelAssetStatus = OcrModelAssetStatus.NotInstalled,
        var installOutcome: OcrModelAssetInstallOutcome =
            OcrModelAssetInstallOutcome.Installed(4_113_088L),
        var progressEmissions: List<Float> = emptyList(),
    ) : EnglishOcrModelAssets {
        var installCalls = 0
        var uninstallCalls = 0

        override suspend fun status(): OcrModelAssetStatus = status

        override suspend fun install(
            onProgress: (Float) -> Unit,
        ): OcrModelAssetInstallOutcome {
            installCalls += 1
            progressEmissions.forEach { onProgress(it) }
            status =
                when (val outcome = installOutcome) {
                    is OcrModelAssetInstallOutcome.Installed -> OcrModelAssetStatus.Installed(outcome.sizeBytes)
                    else -> OcrModelAssetStatus.NotInstalled
                }
            return installOutcome
        }

        override suspend fun uninstall() {
            uninstallCalls += 1
            status = OcrModelAssetStatus.NotInstalled
        }
    }

    private fun recognized(text: String, confidence: Float = 0.9f) =
        OcrOutcome.Success(
            OcrResult(text, listOf(OcrRegion(0, 0, 10, 10, confidence))),
            OcrEngineKind.MlKit,
        )

    @Test
    fun successfulRecognitionExposesEditableTextAndCleansTheCapture() = runTest(dispatcher) {
        val provider = ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("Birds fly."))
        val vm = viewModel(OcrProviderChain(listOf(provider)))

        vm.onImageBytes(byteArrayOf(1, 2, 3))
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Birds fly.", state.ocrText)
        assertEquals(OcrEngineKind.MlKit, state.engine)
        assertNull(state.ocrIssue)
        assertTrue(state.busy.not())
    }

    @Test
    fun lowConfidenceLinesAreFlaggedForTheEditor() = runTest(dispatcher) {
        val result =
            OcrResult(
                text = "clear line\nshaky line",
                regions =
                    listOf(
                        OcrRegion(0, 0, 10, 10, 0.95f),
                        OcrRegion(0, 12, 10, 20, 0.2f),
                    ),
            )
        val provider = ScriptedOcrProvider(OcrEngineKind.MlKit, OcrOutcome.Success(result, OcrEngineKind.MlKit))
        val vm = viewModel(OcrProviderChain(listOf(provider)))

        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()

        assertEquals(setOf(1), vm.uiState.value.lowConfidenceLineIndices)
    }

    @Test
    fun bothProvidersUnavailableIsAnOcrIssueNeverATranslationFailure() = runTest(dispatcher) {
        val chain = OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, OcrOutcome.Unavailable), ScriptedOcrProvider(OcrEngineKind.Tesseract, OcrOutcome.Unavailable)))
        val vm = viewModel(chain)

        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(PhotoTranslationOcrIssue.Unavailable, state.ocrIssue)
        assertNull(state.translationIssue)
        assertNull(state.translation)
    }

    @Test
    fun recognitionFailureKeepsItsOwnCategoryAndReasons() = runTest(dispatcher) {
        val chain =
            OcrProviderChain(
                listOf(
                    ScriptedOcrProvider(OcrEngineKind.MlKit, OcrOutcome.RecognitionFailed("blurry")),
                    ScriptedOcrProvider(OcrEngineKind.Tesseract, OcrOutcome.RecognitionFailed("engine error")),
                ),
            )
        val vm = viewModel(chain)

        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()

        val issue = assertNotNull(vm.uiState.value.ocrIssue)
        assertIs(issue, PhotoTranslationOcrIssue.Failed::class)
    }

    @Test
    fun translateFirstDemandsConfirmationAndDenialSendsNoBytes() = runTest(dispatcher) {
        val transport = RecordingTransport()
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("Birds fly.")))), transport)
        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()

        vm.onTranslateTapped()
        testScheduler.advanceUntilIdle()

        assertEquals("api.example.com", vm.uiState.value.pendingConfirmationHost)
        assertEquals(0, transport.requests.size)

        vm.onConfirmationResult(confirmed = false)
        testScheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.pendingConfirmationHost)
        assertEquals(0, transport.requests.size)
        assertEquals("Birds fly.", vm.uiState.value.ocrText)
    }

    @Test
    fun confirmedTranslationShowsSourceAndTarget() = runTest(dispatcher) {
        val transport = RecordingTransport()
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("Birds fly.")))), transport)
        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()

        vm.onTranslateTapped()
        testScheduler.advanceUntilIdle()
        vm.onConfirmationResult(confirmed = true)
        testScheduler.advanceUntilIdle()

        assertEquals("Birds fly." to "鸟儿会飞。", vm.uiState.value.translation)
        assertEquals(1, transport.requests.size)
        assertNull(vm.uiState.value.pendingConfirmationHost)
    }

    @Test
    fun editedTextIsWhatGetsTranslated() = runTest(dispatcher) {
        val transport = RecordingTransport()
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("Birds fly.")))), transport)
        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()

        vm.onOcrTextEdited("Birds swim.")
        vm.onTranslateTapped()
        testScheduler.advanceUntilIdle()
        vm.onConfirmationResult(confirmed = true)
        testScheduler.advanceUntilIdle()

        assertEquals("Birds swim." to "鸟儿会飞。", vm.uiState.value.translation)
        val body = assertNotNull(transport.requests.single().body)
        assertTrue(body.contains("Birds swim."))
        assertTrue(body.contains("Birds fly.").not())
    }

    @Test
    fun translationFailureKeepsEditableOcrTextAndAllowsRetry() = runTest(dispatcher) {
        val transport = RecordingTransport().apply { result = AiHttpResult.NetworkUnavailable }
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("Birds fly.")))), transport)
        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()

        vm.onTranslateTapped()
        testScheduler.advanceUntilIdle()
        vm.onConfirmationResult(confirmed = true)
        testScheduler.advanceUntilIdle()

        assertEquals(PhotoTranslationFailureCategory.Ai(AiFailure.NetworkUnavailable), vm.uiState.value.translationIssue)
        assertEquals("Birds fly.", vm.uiState.value.ocrText)
        assertNull(vm.uiState.value.translation)

        transport.result = AiHttpResult.Responded(AiHttpResponse(200, """{"choices":[{"message":{"content":"鸟儿会飞。"}}]}"""))
        vm.onTranslateTapped()
        testScheduler.advanceUntilIdle()
        vm.onConfirmationResult(confirmed = true)
        testScheduler.advanceUntilIdle()

        assertEquals("Birds fly." to "鸟儿会飞。", vm.uiState.value.translation)
        assertNull(vm.uiState.value.translationIssue)
    }

    @Test
    fun notConfiguredAiIsItsOwnCategory() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val vm =
            PhotoTranslationViewModel(
                ocr = OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("x")))),
                workspace = TemporaryImageWorkspace(root),
                translate =
                    PhotoTranslationUseCase(
                        defaultTextProfile = DefaultTextProfileResolver { DefaultTextProfileResult.NoSelection },
                        secrets = AiProfileSecretUseCase(FakeSecretStore()),
                        transport = RecordingTransport(),
                    ),
                modelAssets = FakeModelAssets(),
                history = FakeHistory(),
                recordIds = WordNoteIdFactory { "record-1" },
                clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC),
            )
        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()

        vm.onTranslateTapped()
        testScheduler.advanceUntilIdle()

        assertEquals(
            PhotoTranslationFailureCategory.NotConfigured(PhotoTranslationNotConfiguredReason.NoDefaultProfile),
            vm.uiState.value.translationIssue,
        )
    }

    @Test
    fun modelStatusRefreshExposesWhatIsInstalled() = runTest(dispatcher) {
        val assets = FakeModelAssets(status = OcrModelAssetStatus.Installed(4_113_088L))
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("x")))), modelAssets = assets)

        vm.refreshModelStatus()
        testScheduler.advanceUntilIdle()

        assertEquals(OcrModelAssetStatus.Installed(4_113_088L), vm.uiState.value.modelStatus)
    }

    @Test
    fun installingTheFallbackModelPublishesInstalledAndIgnoresDoubleTaps() = runTest(dispatcher) {
        val assets =
            FakeModelAssets(
                installOutcome = OcrModelAssetInstallOutcome.Installed(4_113_088L),
                progressEmissions = listOf(0.5f),
            )
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("x")))), modelAssets = assets)

        vm.installModel()
        vm.installModel()
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(OcrModelAssetStatus.Installed(4_113_088L), state.modelStatus)
        assertNull(state.modelInstallProgress)
        assertNull(state.modelIssue)
        assertEquals(1, assets.installCalls)
    }

    @Test
    fun failedInstallOrIntegrityMismatchIsReportedAndStatusStaysNotInstalled() = runTest(dispatcher) {
        val assets = FakeModelAssets(installOutcome = OcrModelAssetInstallOutcome.IntegrityMismatch)
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("x")))), modelAssets = assets)

        vm.installModel()
        testScheduler.advanceUntilIdle()

        assertEquals(OcrModelAssetInstallOutcome.IntegrityMismatch, vm.uiState.value.modelIssue)
        assertEquals(OcrModelAssetStatus.NotInstalled, vm.uiState.value.modelStatus)
    }

    @Test
    fun uninstallingTheModelReturnsToNotInstalled() = runTest(dispatcher) {
        val assets =
            FakeModelAssets(
                status = OcrModelAssetStatus.Installed(4_113_088L),
                installOutcome = OcrModelAssetInstallOutcome.Installed(4_113_088L),
            )
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("x")))), modelAssets = assets)

        vm.uninstallModel()
        testScheduler.advanceUntilIdle()

        assertEquals(OcrModelAssetStatus.NotInstalled, vm.uiState.value.modelStatus)
        assertEquals(1, assets.uninstallCalls)
    }

    @Test
    fun resetAndLeavingTheScreenPurgeEveryCapture() = runTest(dispatcher) {
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("Birds fly.")))))
        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()
        // 上一次进程崩溃的孤儿文件也要在离开/重置时清掉。
        File(root, "orphan.jpg").writeBytes(byteArrayOf(9))

        vm.reset()
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.ocrText == null)
        assertTrue(root.walkBottomUp().filter { it.isFile }.toList().isEmpty())
    }

    @Test
    fun explicitSaveStoresImageTextAndTranslation() = runTest(dispatcher) {
        val history = FakeHistory()
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("Birds fly.")))), history = history)
        vm.onImageBytes(byteArrayOf(1, 2, 3))
        testScheduler.advanceUntilIdle()
        vm.onTranslateTapped()
        testScheduler.advanceUntilIdle()
        vm.onConfirmationResult(confirmed = true)
        testScheduler.advanceUntilIdle()

        vm.saveCurrentTranslation("default")
        testScheduler.advanceUntilIdle()

        val record = history.saved.single()
        assertEquals("record-1", record.recordId)
        assertEquals("default", record.profileId)
        assertEquals("Birds fly.", record.sourceText)
        assertEquals("鸟儿会飞。", record.translatedText)
        assertEquals(OcrEngineKind.MlKit, record.engine)
        assertEquals("asset-1", record.imageAssetId)
        assertEquals(1_700_000_000_000L, record.createdAtEpochMillis)
        assertEquals(listOf("asset-1"), history.storedImages)
        assertEquals(record.recordId, vm.uiState.value.lastSavedRecordId)
    }

    @Test
    fun savingWithoutACaptureKeepsNoImage() = runTest(dispatcher) {
        val history = FakeHistory()
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("Birds fly.")))), history = history)
        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()
        vm.onTranslateTapped()
        testScheduler.advanceUntilIdle()
        vm.onConfirmationResult(confirmed = true)
        testScheduler.advanceUntilIdle()
        // 采集缓冲只服务「明确保存」；离开页面后一切清零。
        vm.reset()
        testScheduler.advanceUntilIdle()

        vm.saveCurrentTranslation("default")
        testScheduler.advanceUntilIdle()

        assertTrue(history.saved.isEmpty(), "reset 后无译文可存")
        assertTrue(history.storedImages.isEmpty())
    }

    @Test
    fun editedTextWithoutRecognitionIsStillSavableAndImageless() = runTest(dispatcher) {
        val history = FakeHistory()
        // 识别失败：没有 engine、没有可存原图缓冲之外语义，但用户手动编辑文本后翻译成功。
        val chain = OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, OcrOutcome.RecognitionFailed("blurry"))))
        val vm = viewModel(chain, history = history)
        vm.onImageBytes(byteArrayOf(7))
        testScheduler.advanceUntilIdle()

        vm.onOcrTextEdited("Birds fly.")
        vm.onTranslateTapped()
        testScheduler.advanceUntilIdle()
        vm.onConfirmationResult(confirmed = true)
        testScheduler.advanceUntilIdle()

        vm.saveCurrentTranslation("default")
        testScheduler.advanceUntilIdle()

        val record = history.saved.single()
        assertNull(record.engine)
        assertEquals("asset-1", record.imageAssetId, "识别失败的图片仍随明确保存留存")
    }

    @Test
    fun saveFailureReportsTheIssueAndKeepsStateUsable() = runTest(dispatcher) {
        val history =
            FakeHistory().apply {
                saveResult = Result.failure(com.example.englishlearning.core.storage.AppErrorException(com.example.englishlearning.core.error.AppError.StorageUnavailable))
            }
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("Birds fly.")))), history = history)
        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()
        vm.onTranslateTapped()
        testScheduler.advanceUntilIdle()
        vm.onConfirmationResult(confirmed = true)
        testScheduler.advanceUntilIdle()

        vm.saveCurrentTranslation("default")
        testScheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.lastSavedRecordId)
        assertIs(vm.uiState.value.saveIssue, com.example.englishlearning.core.error.AppError::class)
        assertEquals("Birds fly." to "鸟儿会飞。", vm.uiState.value.translation, "保存失败不得清掉已有结果")
    }

    @Test
    fun failedImageStorageAbortsTheSave() = runTest(dispatcher) {
        val history =
            FakeHistory().apply {
                storeResult = Result.failure(com.example.englishlearning.core.storage.AppErrorException(com.example.englishlearning.core.error.AppError.StorageUnavailable))
            }
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("Birds fly.")))), history = history)
        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()
        vm.onTranslateTapped()
        testScheduler.advanceUntilIdle()
        vm.onConfirmationResult(confirmed = true)
        testScheduler.advanceUntilIdle()

        vm.saveCurrentTranslation("default")
        testScheduler.advanceUntilIdle()

        assertTrue(history.saved.isEmpty(), "原图写入失败时不允许静默存成无图记录")
        assertNull(vm.uiState.value.lastSavedRecordId)
    }

    @Test
    fun saveIsNotPossibleBeforeTranslationSucceeds() = runTest(dispatcher) {
        val history = FakeHistory()
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("Birds fly.")))), history = history)
        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()

        vm.saveCurrentTranslation("default")
        testScheduler.advanceUntilIdle()

        assertTrue(history.saved.isEmpty())
        assertTrue(history.storedImages.isEmpty(), "未确认翻译前谈不上保存，更不写图")
    }

    @Test
    fun refreshHistoryListsRecordsAndDeleteRemovesOne() = runTest(dispatcher) {
        val history = FakeHistory()
        val vm = viewModel(OcrProviderChain(listOf(ScriptedOcrProvider(OcrEngineKind.MlKit, recognized("Birds fly.")))), history = history)
        vm.onImageBytes(byteArrayOf(1))
        testScheduler.advanceUntilIdle()
        vm.onTranslateTapped()
        testScheduler.advanceUntilIdle()
        vm.onConfirmationResult(confirmed = true)
        testScheduler.advanceUntilIdle()
        vm.saveCurrentTranslation("default")
        testScheduler.advanceUntilIdle()

        vm.refreshHistory("default")
        testScheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.history.size)

        vm.deleteSaved("default", "record-1")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("default" to "record-1"), history.deleteCalls)
        assertTrue(vm.uiState.value.history.isEmpty())
    }

    private fun assertIs(
        value: Any?,
        expected: kotlin.reflect.KClass<*>,
    ) {
        assertTrue(expected.isInstance(value), "expected $expected, got $value")
    }
}
