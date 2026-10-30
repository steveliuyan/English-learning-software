package com.example.englishlearning.ui

import com.example.englishlearning.learning.LearningProfile
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.learning.WordBookProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TodayPlanViewModelTest {
    @Test
    fun `load exposes ready snapshot counts and date`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeLearningProfileRepository()
        var invocations = 0
        val viewModel = TodayPlanViewModel({ invocations++; TodayPlanResult.Ready(plan()) }, repository, FakeLearningEventRepository(), bundled)
        viewModel.load("profile-1")
        viewModel.load("profile-1")
        advanceUntilIdle()
        assertEquals(2, invocations)
        assertEquals(
            TodayPlanUiState.Ready(
                "小学", "2026-09-19", 0, 5, 5,
                newDone = 0, dueDone = 0, isUnlocked = false,
                unlockReason = "完成新词与复习后解锁文章",
                planId = "p1",
                activeWordBookId = "primary-school",
            ),
            viewModel.uiState.value,
        )
        Dispatchers.resetMain()
    }

    @Test
    fun `two argument constructor keeps legacy ready semantics`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeLearningProfileRepository()
        var invocations = 0
        val viewModel = TodayPlanViewModel({ invocations++; TodayPlanResult.Ready(plan()) }, repository)
        viewModel.load("profile-1")
        viewModel.load("profile-1")
        advanceUntilIdle()
        assertEquals(2, invocations)
        assertEquals(
            TodayPlanUiState.Ready(
                "小学", "2026-09-19", 0, 5, 5,
                newDone = 0, dueDone = 0, isUnlocked = false,
                unlockReason = "完成新词与复习后解锁文章",
                planId = "p1",
                activeWordBookId = "primary-school",
            ),
            viewModel.uiState.value,
        )
        Dispatchers.resetMain()
    }

    @Test
    fun `non ready result never triggers the event repository`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val events = SpyLearningEventRepository()
        val viewModel = TodayPlanViewModel({ TodayPlanResult.MissingLearningSetup }, FakeLearningProfileRepository(), events, bundled)
        viewModel.load("profile-1")
        advanceUntilIdle()
        assertEquals(TodayPlanUiState.MissingSetup, viewModel.uiState.value)
        assertEquals(0, events.completedCardIdsCalls)
        Dispatchers.resetMain()
    }

    @Test
    fun `strict default stays locked until both new and due groups complete`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val events = FakeLearningEventRepository(listOf("n1", "n2", "d1"))
        val viewModel = TodayPlanViewModel({ TodayPlanResult.Ready(planWith(newTarget = 2, dueTarget = 2)) }, FakeLearningProfileRepository(), events, bundled)
        viewModel.load("profile-1")
        advanceUntilIdle()
        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(2, state.newDone)
        assertEquals(1, state.dueDone)
        assertEquals(false, state.isUnlocked)
        assertEquals("完成新词与复习后解锁文章", state.unlockReason)
        Dispatchers.resetMain()
    }

    @Test
    fun `strict unlocks only when both groups done`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val events = FakeLearningEventRepository(listOf("n1", "n2", "d1", "d2"))
        val viewModel = TodayPlanViewModel({ TodayPlanResult.Ready(planWith(newTarget = 2, dueTarget = 2)) }, FakeLearningProfileRepository(), events, bundled)
        viewModel.load("profile-1")
        advanceUntilIdle()
        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(true, state.isUnlocked)
        assertEquals("今日计划已完成", state.unlockReason)
        Dispatchers.resetMain()
    }

    @Test
    fun `future relaxed ruleVersion unlocks on new cards alone`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val events = FakeLearningEventRepository(listOf("n1", "n2"))
        val viewModel = TodayPlanViewModel({ TodayPlanResult.Ready(planWith(newTarget = 2, dueTarget = 3, ruleVersion = "f1-v1-relaxed")) }, FakeLearningProfileRepository(), events, bundled)
        viewModel.load("profile-1")
        advanceUntilIdle()
        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(true, state.isUnlocked)
        assertEquals("今日计划已完成", state.unlockReason)
        Dispatchers.resetMain()
    }

    @Test
    fun `relaxed ruleVersion still requires new cards`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val events = FakeLearningEventRepository(emptyList())
        val viewModel = TodayPlanViewModel({ TodayPlanResult.Ready(planWith(newTarget = 2, dueTarget = 3, ruleVersion = "f1-v1-relaxed")) }, FakeLearningProfileRepository(), events, bundled)
        viewModel.load("profile-1")
        advanceUntilIdle()
        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(false, state.isUnlocked)
        assertEquals("完成新词后解锁文章", state.unlockReason)
        Dispatchers.resetMain()
    }

    @Test
    fun `completion counts only snapshot cards and ignores extras`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val events = FakeLearningEventRepository(listOf("n1", "n2", "d1", "outside", "late"))
        val viewModel = TodayPlanViewModel({ TodayPlanResult.Ready(planWith(newTarget = 2, dueTarget = 2)) }, FakeLearningProfileRepository(), events, bundled)
        viewModel.load("profile-1")
        advanceUntilIdle()
        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(2, state.newDone)
        assertEquals(1, state.dueDone)
        Dispatchers.resetMain()
    }

    /**
     * 首页词书卡的整册进度：分母取词书声明的 [WordBook.totalWords]（10），分子是
     * 只落在本册卡片里的已复习 id。`outside` 不在本册，必须被丢掉——否则别的册
     * 学过的词会凭空抬高这一册的进度。
     */
    @Test
    fun `load exposes whole book progress from declared totals`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = TodayPlanViewModel(
            { TodayPlanResult.Ready(plan()) },
            FakeLearningProfileRepository(totalWords = 10),
            ReviewedEventRepository(listOf("w1", "w2", "outside")),
            bundledIds = bundled,
            cards = FakeWordCardSource(listOf("w1", "w2", "w3", "w4", "w5", "w6", "w7", "w8", "w9", "w10")),
        )
        viewModel.load("profile-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(WordBookProgress(learned = 2, total = 10), state.bookProgress)
        Dispatchers.resetMain()
    }

    /** 词书没声明词数时进度是「未知」而不是「0%」：界面据此隐藏整行，而不是画一根空条。 */
    @Test
    fun `load leaves book progress unknown when the book declares no words`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = TodayPlanViewModel(
            { TodayPlanResult.Ready(plan()) },
            FakeLearningProfileRepository(totalWords = 0),
            ReviewedEventRepository(listOf("w1")),
            bundledIds = bundled,
            cards = FakeWordCardSource(emptyList()),
        )
        viewModel.load("profile-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(null, state.bookProgress)
        Dispatchers.resetMain()
    }

    @Test
    fun `active book outside the visible catalog falls back to setup`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        // 活动词书是 `primary-school`，但可见清单里只有 cet4：旧占位册/被删导入包的真实情形。
        val viewModel = TodayPlanViewModel(
            { TodayPlanResult.Ready(plan()) },
            FakeLearningProfileRepository(),
            FakeLearningEventRepository(),
            com.example.englishlearning.learning.BundledWordBookIdSource { setOf("cet4") },
        )
        viewModel.load("profile-1")
        advanceUntilIdle()

        assertEquals(TodayPlanUiState.MissingSetup, viewModel.uiState.value)
        Dispatchers.resetMain()
    }

    @Test
    fun `bundled active book keeps the plan ready`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = TodayPlanViewModel(
            { TodayPlanResult.Ready(plan()) },
            FakeLearningProfileRepository(),
            FakeLearningEventRepository(),
            bundled,
        )
        viewModel.load("profile-1")
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value is TodayPlanUiState.Ready)
        Dispatchers.resetMain()
    }

    /**
     * 批 1 Task 16：「今日词单」屏（原型 `sLearnQueue`，`app.js:357-390`）的数据面。
     *
     * 计划快照本来就有 `newCardIds` / `dueCardIds`，但只读计数的 `Ready` 画不出词单——
     * 这一条钉的是**快照口径**：行序 = 快照里的 id 序（复习那节就是 DAO 的
     * `ORDER BY nextReviewAtEpochMillis ASC`，屏侧那句「按遗忘曲线排序」靠它才是真话），
     * 今日已完成的 id 单独给出，供行尾标「已学 / 待学 / 到期」。
     */
    @Test
    fun `load exposes today's queue words in snapshot order`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = TodayPlanViewModel(
            { TodayPlanResult.Ready(planWith(newTarget = 2, dueTarget = 2)) },
            FakeLearningProfileRepository(),
            FakeLearningEventRepository(listOf("n1", "d1")),
            bundledIds = bundled,
            cards = ContentWordCardSource(
                listOf(
                    queueCard("d1", "obscure", "adj.", "晦涩的"),
                    queueCard("n1", "vivid", "adj.", "生动的"),
                    queueCard("n2", "prevail", "v.", "盛行"),
                    queueCard("d2", "tedious", "adj.", "冗长乏味的"),
                ),
            ),
        )
        viewModel.load("profile-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(
            listOf("n1", "n2"),
            state.newQueue.map { it.cardId },
            "新学节按快照 newCardIds 的原序，不按内容源那册的字典序（快照是不可变权威）",
        )
        assertEquals(
            listOf("d1", "d2"),
            state.dueQueue.map { it.cardId },
            "复习节按快照 dueCardIds 的原序（DAO 已按 nextReviewAt 升序排好，屏侧不再排）",
        )
        assertEquals(setOf("n1", "d1"), state.doneCardIds, "今日已完成集合要逐 id 给：行尾状态靠它，不靠计数")
        Dispatchers.resetMain()
    }

    /** 内容源缺某张卡（词书包被换掉 / 那册不再可见）时只丢那一行，不得把整节或整屏带走。 */
    @Test
    fun `queue drops ids the content source has no card for`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val viewModel = TodayPlanViewModel(
            { TodayPlanResult.Ready(planWith(newTarget = 2, dueTarget = 1)) },
            FakeLearningProfileRepository(),
            FakeLearningEventRepository(),
            bundledIds = bundled,
            cards = ContentWordCardSource(listOf(queueCard("n1", "vivid", "adj.", "生动的"))),
        )
        viewModel.load("profile-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(listOf("n1"), state.newQueue.map { it.cardId }, "读不到内容的 n2 只丢自己那一行")
        assertEquals(
            emptyList<com.example.englishlearning.learning.domain.WordCard>(),
            state.dueQueue,
            "整节都读不到时那一节是空的，不是拿 id 当词头凑一行",
        )
        Dispatchers.resetMain()
    }

    private fun queueCard(cardId: String, lemma: String, pos: String, meaning: String) =
        com.example.englishlearning.learning.domain.WordCard(
            cardId = cardId,
            wordBookId = "primary-school",
            lemma = lemma,
            ipa = "",
            partOfSpeech = pos,
            meaningZh = meaning,
        )

    /**
     * 带内容的卡片来源。
     *
     * 与 [FakeWordCardSource] 分开写：那枚的 `cards()` 永远返回空是**刻意的**（它的 KDoc 写明「若这里也返回
     * 内容，就会掩盖分母取自声明值」那条取舍）。本屏要读词头与释义，必须让内容真流到界面，
     * 所以另起一枚，不动那位的口径。
     */
    private class ContentWordCardSource(private val contents: List<com.example.englishlearning.learning.domain.WordCard>) :
        com.example.englishlearning.learning.WordCardSource {
        override suspend fun cardIds(wordBookId: String) = contents.map { it.cardId }

        /** 端口契约：按**请求序**返回，没有内容的 id 直接丢（`WordCardSource.kt` 的 KDoc 逐字）。 */
        override suspend fun cards(cardIds: List<String>) =
            cardIds.mapNotNull { id -> contents.firstOrNull { it.cardId == id } }
    }

    private fun plan() = TodayPlan("p1", "profile-1", LocalDate.of(2026, 9, 19), "Asia/Shanghai", "primary-school", 0, 5, emptyList(), listOf("d1", "d2", "d3", "d4", "d5"), "f1-v1", Instant.EPOCH)

    /** 测试里的活动词书是 `primary-school`，显式声明为内置册，否则会被可见性判定拦下。 */
    private val bundled = com.example.englishlearning.learning.BundledWordBookIdSource { setOf("primary-school") }

    private fun planWith(newTarget: Int = 0, dueTarget: Int = 0, ruleVersion: String = "f1-v1") = TodayPlan(
        "p1", "profile-1", LocalDate.of(2026, 9, 19), "Asia/Shanghai", "primary-school",
        newTarget, dueTarget,
        (1..newTarget).map { "n$it" },
        (1..dueTarget).map { "d$it" },
        ruleVersion, Instant.EPOCH,
    )

    private class FakeLearningEventRepository(private val completed: List<String> = emptyList()) : com.example.englishlearning.learning.LearningEventRepository {
        override suspend fun append(event: com.example.englishlearning.learning.domain.LearningEvent, nextState: com.example.englishlearning.learning.domain.CardReviewState) = com.example.englishlearning.learning.AppendEventResult.Appended(false)
        override suspend fun findEvent(eventId: String) = RepositoryResult.Success<com.example.englishlearning.learning.domain.LearningEvent?>(null)
        override suspend fun findCardState(cardId: String) = RepositoryResult.Success<com.example.englishlearning.learning.domain.CardReviewState?>(null)
        override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)
        override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(completed)
        override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())
        override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
    }

    private class FakeLearningProfileRepository(private val totalWords: Int = 0) : LearningProfileRepository {
        override suspend fun current(profileId: String) = RepositoryResult.Success<LearningProfile?>(null)
        override suspend fun save(profile: LearningProfile) = RepositoryResult.Success(Unit)
        override suspend fun listWordBooks() = RepositoryResult.Success(emptyList<WordBook>())
        override suspend fun findWordBook(id: String) = RepositoryResult.Success(WordBook(id, "小学", "Primary", totalWords, "v1", "fixture"))
        override suspend fun upsertWordBook(wordBook: WordBook) = RepositoryResult.Success(Unit)
    }

    /**
     * 只回答「这本词书有哪些卡」的假来源。
     *
     * [cards] 永远返回空列表是刻意的：被测逻辑只用到 [cardIds] 与词书声明的 totalWords，
     * 若这里也返回内容，就会掩盖「分母取自声明值而不是卡片数」这一关键取舍。
     */
    private class FakeWordCardSource(private val ids: List<String>) : com.example.englishlearning.learning.WordCardSource {
        override suspend fun cardIds(wordBookId: String) = ids

        override suspend fun cards(cardIds: List<String>) = emptyList<com.example.englishlearning.learning.domain.WordCard>()
    }

    /** 复习状态可配置的事件仓储：`reviewedCardIds` 是整册进度的分子来源。 */
    private class ReviewedEventRepository(private val reviewed: List<String>) : com.example.englishlearning.learning.LearningEventRepository {
        override suspend fun append(event: com.example.englishlearning.learning.domain.LearningEvent, nextState: com.example.englishlearning.learning.domain.CardReviewState) = com.example.englishlearning.learning.AppendEventResult.Appended(false)
        override suspend fun findEvent(eventId: String) = RepositoryResult.Success<com.example.englishlearning.learning.domain.LearningEvent?>(null)
        override suspend fun findCardState(cardId: String) = RepositoryResult.Success<com.example.englishlearning.learning.domain.CardReviewState?>(null)
        override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)
        override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(emptyList<String>())
        override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(reviewed)
        override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
    }

    private class SpyLearningEventRepository : com.example.englishlearning.learning.LearningEventRepository {
        var completedCardIdsCalls = 0
        override suspend fun append(event: com.example.englishlearning.learning.domain.LearningEvent, nextState: com.example.englishlearning.learning.domain.CardReviewState) = com.example.englishlearning.learning.AppendEventResult.Appended(false)
        override suspend fun findEvent(eventId: String) = RepositoryResult.Success<com.example.englishlearning.learning.domain.LearningEvent?>(null)
        override suspend fun findCardState(cardId: String) = RepositoryResult.Success<com.example.englishlearning.learning.domain.CardReviewState?>(null)
        override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)
        override suspend fun completedCardIds(planId: String): RepositoryResult<List<String>> {
            completedCardIdsCalls++
            return RepositoryResult.Success(emptyList())
        }
        override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())
        override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
    }
}
