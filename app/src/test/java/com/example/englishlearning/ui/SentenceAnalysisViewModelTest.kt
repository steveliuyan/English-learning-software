package com.example.englishlearning.ui

import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiOutboundGrantStore
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
import kotlinx.coroutines.CompletableDeferred
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

    private open class RecordingTransport : AiHttpTransport {
        var sends = 0
        var result: AiHttpResult = AiHttpResult.Responded(
            AiHttpResponse(200, """{"choices":[{"message":{"content":"主句\tBirds fly.\t鸟会飞。"}}]}"""),
        )

        open override suspend fun send(request: AiHttpRequest): AiHttpResult {
            sends += 1
            return result
        }
    }

    /**
     * 挂起版传输：`send` 停在 `gate.await()` 上，直到测试自己 `complete`。
     *
     * 批 C-4 的取消判据必须有这一枚。`RecordingTransport` 立即返回，`advanceUntilIdle()` 之后
     * 协程早就跑完了——「取消一枚在飞的请求」在这种假传输上**永远是真的**，
     * 用它测取消会得到一条假绿（VM 不加 Job 句柄也照样通过）。
     *
     * 继承而不是并列：`viewModel(transport = …)` 的形参类型是 `RecordingTransport`，
     * 两枚各写一个类就得改那条构造口（连带全部既有用例的 `transport.sends` 取数）；
     * 挂起版只覆盖「什么时候才返回」这一件事，计数与响应形状沿用父类那一份。
     */
    private class HangingTransport : RecordingTransport() {
        val gate = CompletableDeferred<AiHttpResult>()

        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            sends += 1
            return gate.await()
        }
    }

    private fun viewModel(
        transport: RecordingTransport = RecordingTransport(),
        resolver: DefaultTextProfileResult = DefaultTextProfileResult.Selected(profile),
        grants: AiOutboundGrantStore = AiOutboundGrantStore(),
    ): Pair<SentenceAnalysisViewModel, RecordingTransport> {
        val useCase = SentenceAnalysisUseCase(
            defaultTextProfile = DefaultTextProfileResolver { resolver },
            secrets = AiProfileSecretUseCase(FakeSecretStore()),
            transport = transport,
        )
        return SentenceAnalysisViewModel(analyze = useCase, outboundGrants = grants) to transport
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

    /**
     * 批 C-4「可取消」的正身：取消必须做到「不再等待 + 迟到结果不回写 + 不多发一次」。
     *
     * 三条断言各挡一种假实现：
     *  - 只把界面文案换掉、VM 不留句柄 → 取消后 `gate.complete(…)` 迟到结果照样把状态推成 `Analyzed`
     *    （用户按了取消，画面却长出结果），第二条 `assertEquals(Idle, …)` 即红；
     *  - 取消被实现成「重发一次以求快」→ 第一条 `sends` 计数即红；
     *  - 取消被当成失败 → 状态会是 `Failed`，第一条即时红。
     * `grants` 预授权 `api.example.com`：本用例测的是**在飞请求**，不能让它停在出站确认框上，
     * 否则协程根本没开始，取消测的还是空档（那也是真机侧「点了取消没反应」的同一种形状）。
     */
    @Test
    fun cancelStopsWaitingAndDiscardsTheLateAnalysis() = runTest(dispatcher) {
        val transport = HangingTransport()
        val grants = AiOutboundGrantStore().apply { confirm("api.example.com") }
        val (viewModel, _) = viewModel(transport = transport, grants = grants)

        viewModel.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()
        assertEquals(SentenceAnalysisUiState.Analyzing, viewModel.uiState.value)
        assertEquals(1, transport.sends)

        viewModel.cancelAnalysis()
        advanceUntilIdle()
        assertEquals(SentenceAnalysisUiState.Idle, viewModel.uiState.value)

        transport.gate.complete(
            AiHttpResult.Responded(AiHttpResponse(200, """{"choices":[{"message":{"content":"主句\tBirds fly.\t鸟会飞。"}}]}""")),
        )
        advanceUntilIdle()

        assertEquals(SentenceAnalysisUiState.Idle, viewModel.uiState.value, "取消之后迟到的响应不许把界面推成结果")
        assertEquals(1, transport.sends, "取消不该再发一次请求")
    }

    /**
     * 取消**不回收**已经给过的域名授权（决策 2026-11-07 §5 那一条相互作用风险的判据）。
     *
     * 记账点在「点确认」那一刻，取消一次在飞请求不该把授权退回去——否则每次取消都换来
     * 一次重新弹框，用户会得到「我按了取消，所以你得再确认一遍」这种说不通的因果。
     * 判据用 `sends` 而不是 `uiState`：第二次直接出站（`Analyzing`）就是免确认的证据，
     * 若授权被回退，状态会停在 `NeedsOutboundConfirmation` 且 `sends` 仍为 1。
     */
    @Test
    fun cancelKeepsTheDomainGrantForTheNextAnalysis() = runTest(dispatcher) {
        val transport = HangingTransport()
        val grants = AiOutboundGrantStore().apply { confirm("api.example.com") }
        val (viewModel, _) = viewModel(transport = transport, grants = grants)

        viewModel.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()
        viewModel.cancelAnalysis()
        advanceUntilIdle()

        viewModel.analyze("Fish swim.", profileId = "default")
        advanceUntilIdle()

        assertEquals(2, transport.sends, "取消后重新分析应当免二次确认直接出站")
        assertTrue(
            viewModel.uiState.value is SentenceAnalysisUiState.Analyzing,
            "第二次分析停在 ${viewModel.uiState.value}：授权被回退了，取消换来的是重新弹框",
        )

        transport.gate.complete(
            AiHttpResult.Responded(AiHttpResponse(200, """{"choices":[{"message":{"content":"主句\tFish swim.\t鱼会游。"}}]}""")),
        )
        advanceUntilIdle()
    }
}
