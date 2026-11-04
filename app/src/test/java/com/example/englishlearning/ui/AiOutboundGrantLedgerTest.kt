package com.example.englishlearning.ui

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
import com.example.englishlearning.sentence.SentenceAnalysisUseCase
import com.example.englishlearning.wordqa.WordAiNote
import com.example.englishlearning.wordqa.WordAiNoteRepository
import com.example.englishlearning.wordqa.WordQaKind
import com.example.englishlearning.wordqa.WordQaUseCase
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
 * 「文本出站确认按域名一次」的跨屏契约（决策 2026-11-05 D-1/D-2）。
 *
 * 这条性质**只能在一个同时握着两屏的文件里钉**：查词问答与长难句分析各自的文件里，
 * 第二屏看不见第一屏的确认是常态，写进单屏文件反而会把「共享」这件事测成「各测各的」。
 * 两屏的构造都接同一枚 [AiOutboundGrantStore]，正对应 DI 里那个 `@Singleton`
 * （由 `AiOutboundGrantStoreTest` 钉在 AppModule 源码上）。
 *
 * 逐次确认的四条腿（文章生成与「换一篇」、拍照翻译、生图出图、连接探活）不在这里，
 * 它们由 `OutboundConfirmationGranularityContractTest` 钉成「不许顺手接台账」。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiOutboundGrantLedgerTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 录制型传输：`sends` 就是「真的出站了几次」， body 由调用方按解析器实际吃的那份 JSON 给全。 */
    private class RecordingTransport(private val body: String) : AiHttpTransport {
        var sends = 0

        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            sends += 1
            return AiHttpResult.Responded(AiHttpResponse(200, body))
        }
    }

    private class FakeSecretStore : SecretStore {
        override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
        override fun read(reference: SecretReference) = Result.success("k3y".toCharArray())
        override fun delete(reference: SecretReference) = Result.success(Unit)
        override fun has(reference: SecretReference) = Result.success(true)
    }

    private class NoNotes : WordAiNoteRepository {
        override suspend fun save(note: WordAiNote): Result<Unit> = Result.success(Unit)
        override suspend fun list(profileId: String, lemma: String, wordBookId: String, cardId: String): Result<List<WordAiNote>> =
            Result.success(emptyList())
        override suspend fun delete(profileId: String, noteId: String): Result<Unit> = Result.success(Unit)
    }

    /**
     * 两屏共用一份台账 + 一份可变的「当前默认 Profile」。
     * [switchEndpointTo] 模拟用户在设置页换了服务地址： resolver 每次调用现读，
     * 所以第二次的确认目标会跟着变——这正是「换域名必须重新问」那条的现场。
     */
    private class Fixture {
        val grants = AiOutboundGrantStore()
        var currentProfile: AiProfile = aiProfile("https://api.example.com/v1")
        val sentenceTransport =
            RecordingTransport("""{"choices":[{"message":{"content":"主句\tBirds fly.\t鸟会飞。"}}]}""")
        val qaTransport = RecordingTransport("""{"choices":[{"message":{"content":"这是一个回答。"}}]}""")

        private fun resolver(): DefaultTextProfileResolver = DefaultTextProfileResolver { DefaultTextProfileResult.Selected(currentProfile) }

        val sentence: SentenceAnalysisViewModel = SentenceAnalysisViewModel(
            analyze = SentenceAnalysisUseCase(resolver(), AiProfileSecretUseCase(FakeSecretStore()), sentenceTransport),
            outboundGrants = grants,
        )
        val qa: WordAiQaViewModel = WordAiQaViewModel(
            ask = WordQaUseCase(resolver(), AiProfileSecretUseCase(FakeSecretStore()), qaTransport),
            notes = NoNotes(),
            noteIds = WordNoteIdFactory { "note-1" },
            clock = com.example.englishlearning.core.time.FixedClockProvider(Instant.ofEpochMilli(1234L), ZoneOffset.UTC),
            outboundGrants = grants,
        )

        fun switchEndpointTo(endpoint: String) {
            currentProfile = aiProfile(endpoint)
        }
    }

    @Test
    fun aHostConfirmedOnTheSentenceScreenIsNotAskedAgainOnTheWordQaScreen() = runTest(dispatcher) {
        val f = Fixture()

        f.sentence.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()
        assertIs<SentenceAnalysisUiState.NeedsOutboundConfirmation>(f.sentence.uiState.value, "同域名第一次仍要问：台账空着")
        assertEquals(0, f.sentenceTransport.sends)

        f.sentence.confirmOutbound(confirmed = true)
        advanceUntilIdle()
        assertIs<SentenceAnalysisUiState.Analyzed>(f.sentence.uiState.value)

        f.qa.ask(WordQaKind.Sentence, "apple", null, profileId = "default")
        advanceUntilIdle()

        assertIs<WordAiQaUiState.Answered>(f.qa.uiState.value, "查词问答必须复用长难句那一次确认：按域名一次是跨屏的")
        assertEquals(1, f.qaTransport.sends)
    }

    @Test
    fun aHostConfirmedOnTheWordQaScreenIsNotAskedAgainOnTheSentenceScreen() = runTest(dispatcher) {
        val f = Fixture()

        f.qa.ask(WordQaKind.Sentence, "apple", null, profileId = "default")
        advanceUntilIdle()
        assertIs<WordAiQaUiState.NeedsOutboundConfirmation>(f.qa.uiState.value)
        f.qa.confirmOutbound(confirmed = true)
        advanceUntilIdle()

        f.sentence.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()

        assertIs<SentenceAnalysisUiState.Analyzed>(f.sentence.uiState.value, "共享是双向的，不是只让某一屏占便宜")
        assertEquals(1, f.sentenceTransport.sends)
    }

    @Test
    fun theSecondRequestOnTheSameScreenGoesOutWithoutAsking() = runTest(dispatcher) {
        val f = Fixture()

        f.sentence.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()
        f.sentence.confirmOutbound(confirmed = true)
        advanceUntilIdle()

        f.sentence.analyze("Cats sleep.", profileId = "default")
        advanceUntilIdle()

        assertIs<SentenceAnalysisUiState.Analyzed>(f.sentence.uiState.value, "用户-visible 的收益就是这一条：同一域名第二次不再弹")
        assertEquals(2, f.sentenceTransport.sends)
    }

    @Test
    fun leavingAndReenteringThePageKeepsTheSessionGrant() = runTest(dispatcher) {
        // reset() 清的是「上一句的结果显示在哪」，不是「这个域名用户确认过没有」。
        // 若 reset 顺手清了账，用户每次进出功能页都要再点一次确认——那等于回到逐次确认。
        val f = Fixture()

        f.sentence.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()
        f.sentence.confirmOutbound(confirmed = true)
        advanceUntilIdle()
        f.sentence.reset()

        f.sentence.analyze("Cats sleep.", profileId = "default")
        advanceUntilIdle()

        assertIs<SentenceAnalysisUiState.Analyzed>(f.sentence.uiState.value, "reset 不许把确认台账一起清掉")
        assertEquals(2, f.sentenceTransport.sends)
    }

    @Test
    fun switchingToAnotherEndpointAsksAgainOnBothScreens() = runTest(dispatcher) {
        val f = Fixture()

        f.sentence.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()
        f.sentence.confirmOutbound(confirmed = true)
        advanceUntilIdle()
        f.switchEndpointTo("https://other.example.com/v1")

        f.sentence.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()
        f.qa.ask(WordQaKind.Sentence, "apple", null, profileId = "default")
        advanceUntilIdle()

        assertEquals(
            SentenceAnalysisUiState.NeedsOutboundConfirmation("other.example.com"),
            f.sentence.uiState.value,
            "换域名必须重新问：旧域名的确认不能顺延",
        )
        assertIs<WordAiQaUiState.NeedsOutboundConfirmation>(f.qa.uiState.value, "新域名对两屏都还没确认")
        assertEquals(1, f.sentenceTransport.sends, "未确认的新域名必须零字节出网：那一枚 1 是换域名之前发出去的")
        assertEquals(0, f.qaTransport.sends, "新域名对两屏都还没确认，查词这一腿同样不许出站")
    }

    @Test
    fun decliningConfirmationGrantsNothingToEitherScreen() = runTest(dispatcher) {
        val f = Fixture()

        f.sentence.analyze("Birds fly.", profileId = "default")
        advanceUntilIdle()
        f.sentence.confirmOutbound(confirmed = false)
        advanceUntilIdle()

        f.qa.ask(WordQaKind.Sentence, "apple", null, profileId = "default")
        advanceUntilIdle()

        assertIs<WordAiQaUiState.NeedsOutboundConfirmation>(f.qa.uiState.value, "拒绝过一次=什么都没记，另一屏照样要问")
        assertEquals(0, f.sentenceTransport.sends)
        assertEquals(0, f.qaTransport.sends)
        assertTrue(f.grants.confirmedHosts().isEmpty(), "拒绝路径不得往台账里写任何东西：${f.grants.confirmedHosts()}")
    }
}

/**
 * 顶层而不是类内成员：[AiOutboundGrantLedgerTest.Fixture] 需要它造「当前默认 Profile」，
 * 而 Kotlin 的嵌套类读不到外层实例的成员——写成成员函数的话，编译期就是一条 Unresolved reference。
 */
private fun aiProfile(endpoint: String) = AiProfile(
    profileId = "p1",
    displayName = "测试",
    websiteUrl = "https://example.com",
    endpoint = endpoint,
    model = "gpt-x",
    capabilities = setOf(AiCapability.Text),
    secretReference = SecretReference("ai-profile-p1"),
)
