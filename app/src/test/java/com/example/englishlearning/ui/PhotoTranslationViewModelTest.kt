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
import com.example.englishlearning.language.domain.OcrChainOutcome
import com.example.englishlearning.language.domain.OcrEngineKind
import com.example.englishlearning.language.domain.OcrImage
import com.example.englishlearning.language.domain.OcrOutcome
import com.example.englishlearning.language.domain.OcrProvider
import com.example.englishlearning.language.domain.OcrProviderChain
import com.example.englishlearning.language.domain.OcrRegion
import com.example.englishlearning.language.domain.OcrResult
import com.example.englishlearning.language.domain.TemporaryImageWorkspace
import com.example.englishlearning.phototranslation.PhotoTranslationNotConfiguredReason
import com.example.englishlearning.phototranslation.PhotoTranslationResult
import com.example.englishlearning.phototranslation.PhotoTranslationUseCase
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

    private fun viewModel(
        chain: OcrProviderChain,
        transport: RecordingTransport = RecordingTransport(),
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
        )
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

    private fun assertIs(
        value: Any?,
        expected: kotlin.reflect.KClass<*>,
    ) {
        assertTrue(expected.isInstance(value), "expected $expected, got $value")
    }
}
