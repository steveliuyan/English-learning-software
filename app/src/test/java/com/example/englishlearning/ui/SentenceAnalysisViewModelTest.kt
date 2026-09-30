package com.example.englishlearning.ui

import com.example.englishlearning.ai.AiFailure
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
import com.example.englishlearning.sentence.SentenceAnalysisNotConfiguredReason
import com.example.englishlearning.sentence.SentenceAnalysisUseCase
import com.example.englishlearning.sentence.SentenceRole
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 长难句分析状态机：Idle → Analyzing → Analyzed/NeedsOutboundConfirmation/Failed/NotConfigured。
 * 确认重放沿用同一句；重置清掉旧结果，避免上一句的分析显示在下一句的屏幕上。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SentenceAnalysisViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val profile = AiProfile(
        profileId = "p1",
        displayName = "测试",
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "gpt-x",
        capabilities = setOf(AiCapability.Text),
        secretReference = SecretReference("ai-profile-p1"),
    )

    private class FakeSecretStore : SecretStore {
        override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
        override fun read(reference: SecretReference) = Result.success("k3y".toCharArray())
        override fun delete(reference: SecretReference) = Result.success(Unit)
        override fun has(reference: SecretReference) = Result.success(true)
    }

    private class RecordingTransport : AiHttpTransport {
        var sends = 0
        var result: AiHttpResult = AiHttpResult.Responded(
            AiHttpResponse(200, """{"choices":[{"message":{"content":"主句\tBirds fly.\t鸟会飞。"}}]}"""),
        )

        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            sends += 1
            return result
        }
    }

    private fun viewModel(
        transport: RecordingTransport = RecordingTransport(),
        resolver: DefaultTextProfileResult = DefaultTextProfileResult.Selected(profile),
    ): Pair<SentenceAnalysisViewModel, RecordingTransport> {
        val useCase = SentenceAnalysisUseCase(
            defaultTextProfile = DefaultTextProfileResolver { resolver },
            secrets = AiProfileSecretUseCase(FakeSecretStore()),
            transport = transport,
        )
        return SentenceAnalysisViewModel(analyze = useCase) to transport
    }

    @Test
    fun analyzeFlowEndsInParsedSegments() = runTest(dispatcher) {
        val (viewModel, _) = viewModel()

        viewModel.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()
        viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as SentenceAnalysisUiState.Analyzed
        assertEquals(1, state.segments.size)
        assertEquals(SentenceRole.Main, state.segments.single().role)
    }

    @Test
    fun firstAnalyzeRequiresOutboundConfirmationBeforeTheNetwork() = runTest(dispatcher) {
        val (viewModel, transport) = viewModel()

        viewModel.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()

        assertEquals(SentenceAnalysisUiState.NeedsOutboundConfirmation("api.example.com"), viewModel.uiState.value)
        assertEquals(0, transport.sends)
    }

    @Test
    fun confirmingReplaysTheSameRequest() = runTest(dispatcher) {
        val (viewModel, transport) = viewModel()

        viewModel.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()
        viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SentenceAnalysisUiState.Analyzed)
        assertEquals(1, transport.sends)
    }

    @Test
    fun decliningConfirmationReturnsToIdleWithoutSending() = runTest(dispatcher) {
        val (viewModel, transport) = viewModel()

        viewModel.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()
        viewModel.confirmOutbound(confirmed = false)
        advanceUntilIdle()

        assertEquals(SentenceAnalysisUiState.Idle, viewModel.uiState.value)
        assertEquals(0, transport.sends)
    }

    @Test
    fun notConfiguredSurfacesTheReason() = runTest(dispatcher) {
        val (viewModel, _) = viewModel(resolver = DefaultTextProfileResult.NoSelection)

        viewModel.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()

        assertEquals(
            SentenceAnalysisUiState.NotConfigured(SentenceAnalysisNotConfiguredReason.NoDefaultProfile),
            viewModel.uiState.value,
        )
    }

    @Test
    fun failedSurfacesTheFailure() = runTest(dispatcher) {
        val transport = RecordingTransport().apply { result = AiHttpResult.Responded(AiHttpResponse(401, "{}")) }
        val (viewModel, _) = viewModel(transport = transport)

        viewModel.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()
        viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()

        assertEquals(SentenceAnalysisUiState.Failed(AiFailure.Unauthorized), viewModel.uiState.value)
    }

    @Test
    fun resetClearsTheStaleSegments() = runTest(dispatcher) {
        val (viewModel, _) = viewModel()

        viewModel.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()
        viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()
        viewModel.reset()
        advanceUntilIdle()

        assertEquals(SentenceAnalysisUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun blankSentenceEndsIdleWithoutSending() = runTest(dispatcher) {
        val (viewModel, transport) = viewModel()

        viewModel.analyze("   ", profileId = "default")
        advanceUntilIdle()

        assertEquals(SentenceAnalysisUiState.Idle, viewModel.uiState.value)
        assertEquals(0, transport.sends)
    }
}
