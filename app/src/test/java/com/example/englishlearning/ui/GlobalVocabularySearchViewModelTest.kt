package com.example.englishlearning.ui

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.OpenVocabularySearchResultUseCase
import com.example.englishlearning.learning.VocabularySearchIndexEntry
import com.example.englishlearning.learning.VocabularySearchIndexRepository
import com.example.englishlearning.learning.LearningProfile
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.LearningProfileRepositoryError
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SearchRepresentative
import com.example.englishlearning.learning.SearchVocabularyResult
import com.example.englishlearning.learning.SearchVocabularyUseCase
import com.example.englishlearning.learning.VocabularySearchHistory
import com.example.englishlearning.learning.VocabularySearchHistoryRepository
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.PhraseEntry
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.learning.domain.WordSense
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlinx.coroutines.Dispatchers

class GlobalVocabularySearchViewModelTest {
    @BeforeEach
    fun setMain() { Dispatchers.setMain(dispatcher) }

    @AfterEach
    fun resetMain() { Dispatchers.resetMain() }
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-10-04T09:00:00Z")

    @Test
    fun blank_query_loads_only_current_profile_history() = runTest(dispatcher) {
        val history = ObservableHistory(
            entries = listOf(history("p1", "ability", 3), history("p2", "other", 8)),
        )
        val vm = viewModel(history = history)
        vm.setProfile("p1")
        vm.updateQuery("")
        vm.search()
        advanceUntilIdle()

        val state = assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value)
        assertEquals(listOf("ability"), state.history.map { it.history.normalizedQuery })
        assertTrue(history.listProfiles == listOf("p1"))
        assertEquals(0, history.recordCalls)
    }

    @Test
    fun query_exposes_loading_then_ready_result_with_word_book_and_count() = runTest(dispatcher) {
        val history = ObservableHistory(entries = listOf(history("p1", "ability", 4)))
        val vm = viewModel(history = history)
        vm.setProfile("p1")
        vm.updateQuery(" ability ")
        vm.search()
        assertEquals(GlobalVocabularySearchUiState.Loading, vm.uiState.value)
        advanceUntilIdle()

        val state = assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value)
        assertEquals(listOf("ability"), state.results.map { it.result.lemma })
        assertEquals("四级词书", state.results.single().result.wordBookName)
        assertEquals(4, state.results.single().searchCount)
    }

    @Test
    fun search_failure_can_be_retried_and_empty_results_are_distinct() = runTest(dispatcher) {
        val history = ObservableHistory(entries = emptyList())
        val useCase = FailingOnceUseCase(history)
        val vm = GlobalVocabularySearchViewModel(useCase, history, OpenVocabularySearchResultUseCase(RecordingCardSource(emptyMap())), dispatcher)
        vm.setProfile("p1")
        vm.updateQuery("ability")
        vm.search()
        advanceUntilIdle()
        assertEquals(GlobalVocabularySearchUiState.Failure, vm.uiState.value)
        vm.retry()
        advanceUntilIdle()
        assertEquals(GlobalVocabularySearchUiState.Empty, vm.uiState.value)
    }

    @Test
    fun newer_query_cannot_be_overwritten_by_old_request() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val history = ObservableHistory(entries = emptyList())
        val index = DelayedIndex(gate)
        val vm = viewModel(history = history, index = index)
        vm.setProfile("p1")
        vm.updateQuery("old")
        vm.search()
        advanceUntilIdle()
        vm.updateQuery("new")
        vm.search()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        val state = assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value)
        assertEquals(listOf("new"), state.results.map { it.result.lemma })
    }

    @Test
    fun profile_switch_clears_old_state_and_history_isolated() = runTest(dispatcher) {
        val history = ObservableHistory(entries = listOf(history("p1", "old", 2)))
        val vm = viewModel(history = history)
        vm.setProfile("p1")
        vm.search()
        advanceUntilIdle()
        vm.setProfile("p2")
        assertEquals(GlobalVocabularySearchUiState.Idle, vm.uiState.value)
        history.entries = listOf(history("p2", "new", 1))
        vm.search()
        advanceUntilIdle()
        val state = assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value)
        assertEquals(listOf("new"), state.history.map { it.history.normalizedQuery })
    }

    @Test
    fun clear_history_failure_keeps_history_and_success_clears_only_current_profile() = runTest(dispatcher) {
        val history = ObservableHistory(entries = listOf(history("p1", "one", 1), history("p2", "two", 1)))
        val vm = viewModel(history = history)
        vm.setProfile("p1")
        vm.search()
        advanceUntilIdle()
        history.clearFailure = true
        vm.clearHistory()
        advanceUntilIdle()
        assertEquals(GlobalVocabularySearchUiState.Failure, vm.uiState.value)
        val failedState = assertIs<GlobalVocabularySearchUiState.Failure>(vm.uiState.value)
        assertEquals(listOf("one"), history.remainingFor("p1").map { it.normalizedQuery })

        history.clearFailure = false
        vm.clearHistory()
        advanceUntilIdle()
        assertEquals(GlobalVocabularySearchUiState.Empty, vm.uiState.value)
        assertEquals(listOf("p1", "p1"), history.clearProfiles)
        assertEquals(listOf("two"), history.remainingFor("p2").map { it.normalizedQuery })
    }

    @Test
    fun show_count_false_hides_count_without_deleting_or_resetting_history() = runTest(dispatcher) {
        val history = ObservableHistory(entries = listOf(history("p1", "ability", 7)))
        val vm = viewModel(history = history)
        vm.setProfile("p1")
        vm.setShowCount(false)
        vm.updateQuery("ability")
        vm.search()
        advanceUntilIdle()
        val state = assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value)
        assertEquals(null, state.history.single().searchCountForDisplay)
        assertEquals(null, state.results.single().searchCountForDisplay)
        assertEquals(7, history.entries.single().searchCount)
    }

    // ---- 点开结果：索引只给摘要，完整词卡由这里回源补全 ----

    /**
     * 点开一条结果必须拿到**完整词卡**（释义分组、例句、短语、配图都在），
     * 并且消费一次之后要能再次点开同一条。
     *
     * 后半句是必须的：`openedCard` 是一个「一次性事件」。若不置空，
     * 第二次点同一条时值没变化，界面上的 effect 不会重跑——用户看到的就是「点了没反应」。
     */
    @Test
    fun open_result_delivers_the_full_card_and_can_be_consumed_then_opened_again() = runTest(dispatcher) {
        val history = ObservableHistory(entries = emptyList())
        val cards = RecordingCardSource(mapOf("book:ability" to fullCard("book:ability")))
        val vm = viewModel(history = history, cards = cards)
        vm.setProfile("p1")
        vm.updateQuery("ability")
        vm.search()
        advanceUntilIdle()
        val item = assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value).results.single()

        assertNull(vm.openedCard.value)
        vm.open(item.result)
        advanceUntilIdle()

        assertEquals(fullCard("book:ability"), vm.openedCard.value)
        assertEquals(listOf(listOf("book:ability")), cards.requested)

        vm.consumeOpenedCard()
        assertNull(vm.openedCard.value)

        vm.open(item.result)
        advanceUntilIdle()
        assertEquals(fullCard("book:ability"), vm.openedCard.value)
    }

    /**
     * 索引行可能短暂残留一本已删除词书（索引是派生数据，下次刷新才收敛）。
     * 读不到词卡时必须**保留已有结果**并挂起一个可见的失败标记，而不是把整页变成失败态，
     * 更不能产出一张空壳卡让详情页显示成「这个词没有释义」。
     */
    @Test
    fun open_failure_keeps_the_result_list_and_flags_it() = runTest(dispatcher) {
        val vm = viewModel(history = ObservableHistory(entries = emptyList()), cards = RecordingCardSource(emptyMap()))
        vm.setProfile("p1")
        vm.updateQuery("ability")
        vm.search()
        advanceUntilIdle()
        val item = assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value).results.single()

        vm.open(item.result)
        advanceUntilIdle()

        val state = assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value)
        assertTrue(state.openFailed, "读不到词卡时必须给出可见的失败标记")
        assertEquals(listOf("book:ability"), state.results.map { it.result.cardId })
        assertNull(vm.openedCard.value)
    }

    /** 词书包损坏时源抛异常：点开一个词不该让整个搜索页崩掉，降级成同一条失败标记。 */
    @Test
    fun open_failure_from_a_throwing_source_degrades_instead_of_crashing() = runTest(dispatcher) {
        val cards = RecordingCardSource(emptyMap(), failWith = IllegalStateException("broken package"))
        val vm = viewModel(history = ObservableHistory(entries = emptyList()), cards = cards)
        vm.setProfile("p1")
        vm.updateQuery("ability")
        vm.search()
        advanceUntilIdle()
        val item = assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value).results.single()

        vm.open(item.result)
        advanceUntilIdle()

        assertTrue(assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value).openFailed)
        assertNull(vm.openedCard.value)
    }

    /**
     * 连点两条结果，且**后点的那条先返回**：慢的那条回来时不得把详情覆盖回去。
     *
     * 这个顺序是刻意的。如果两条共用一个闸门，它们总是按发起顺序依次返回，
     * 于是「先点的最后写入」永远不会发生——测试看着像在测竞态，其实测的是顺序。
     * 只有让先点的那条**最后**返回，才能真正逼出「旧请求覆盖新选择」这个缺陷。
     */
    @Test
    fun the_later_opened_card_wins_over_an_earlier_slow_one() = runTest(dispatcher) {
        val slowGate = CompletableDeferred<Unit>()
        val content = mapOf(
            "book:older" to fullCard("book:older"),
            "book:newer" to fullCard("book:newer"),
        )
        // 只有 book:older 被闸住；book:newer 立即返回。
        val cards = GatedCardSource(mapOf("book:older" to slowGate), content)
        val vm = viewModel(history = ObservableHistory(entries = emptyList()), index = TwoCardIndex(), cards = cards)
        vm.setProfile("p1")
        // 查询词必须真的同时命中两条：索引返回候选后，用例还会按匹配等级过滤，
        // 用不命中的查询会让其中一条在进入「点开」之前就被丢掉，测到的就不是竞态了。
        vm.updateQuery("er")
        vm.search()
        advanceUntilIdle()
        val results = assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value).results
        val slow = results.first { it.result.cardId == "book:older" }
        val last = results.first { it.result.cardId == "book:newer" }

        vm.open(slow.result)
        advanceUntilIdle()
        vm.open(last.result)
        advanceUntilIdle()
        assertEquals(fullCard("book:newer"), vm.openedCard.value, "后点的那条应当已经打开")

        // 慢的那条现在才回来：它必须被丢弃，而不是把详情覆盖回 book:older。
        slowGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(fullCard("book:newer"), vm.openedCard.value, "先点、后返回的词卡不得覆盖用户最后的选择")
    }

    /** 切换资料时已打开的词卡不能留在内存里——它属于上一个资料的上下文。 */
    @Test
    fun profile_switch_clears_an_already_opened_card() = runTest(dispatcher) {
        val cards = RecordingCardSource(mapOf("book:ability" to fullCard("book:ability")))
        val vm = viewModel(history = ObservableHistory(entries = emptyList()), cards = cards)
        vm.setProfile("p1")
        vm.updateQuery("ability")
        vm.search()
        advanceUntilIdle()
        vm.open(assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value).results.single().result)
        advanceUntilIdle()
        assertEquals(fullCard("book:ability"), vm.openedCard.value)

        vm.setProfile("p2")

        assertNull(vm.openedCard.value)
    }

    // ---- 查词入口收敛：主页与阅读页走同一条路径，只是初始查询词不同 ----

    /**
     * 阅读页点未知词会带着被点中的词进来，所以这里必须**既设置查询词、又立即出结果**。
     *
     * 缺任何一半，用户看到的都是「输入框是空的、只有最近搜索」——他点了一个词，
     * 却没看到那个词的意思。这两件事各自都容易漏：只 `updateQuery` 不 `search`，
     * 或者搜索了却被界面的「打开时清空查询」覆盖掉。
     */
    @Test
    fun opening_from_the_reading_page_seeds_the_query_and_searches_immediately() = runTest(dispatcher) {
        val vm = viewModel(history = ObservableHistory(entries = emptyList()))
        vm.setProfile("p1")

        vm.openSearch("ability")
        advanceUntilIdle()

        assertEquals("ability", vm.query.value)
        val state = assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value)
        assertEquals(listOf("ability"), state.results.map { it.result.lemma })
    }

    /**
     * 主页入口传空串，用同一个方法必须落在「最近搜索」：既不能停在 Idle，
     * 也不能把空串当成一次真实查询记进历史（记录只在真正搜词时发生）。
     */
    @Test
    fun opening_from_home_with_a_blank_query_shows_recent_history() = runTest(dispatcher) {
        val history = ObservableHistory(entries = listOf(history("p1", "ability", 3)))
        val vm = viewModel(history = history)
        vm.setProfile("p1")

        vm.openSearch("")
        advanceUntilIdle()

        assertEquals("", vm.query.value)
        val state = assertIs<GlobalVocabularySearchUiState.Ready>(vm.uiState.value)
        assertEquals(listOf("ability"), state.history.map { it.history.normalizedQuery })
        assertTrue(state.results.isEmpty())
        assertEquals(0, history.recordCalls)
    }

    private fun viewModel(
        history: ObservableHistory,
        index: VocabularySearchIndexRepository = FixedIndex(),
        cards: WordCardSource = RecordingCardSource(emptyMap()),
    ) = GlobalVocabularySearchViewModel(
        SearchVocabularyUseCase(
            history = history,
            index = index,
            clock = FixedClockProvider(now, ZoneOffset.UTC),
        ),
        history,
        OpenVocabularySearchResultUseCase(cards),
        dispatcher,
    )

    private fun history(profileId: String, query: String, count: Int) = VocabularySearchHistory(
        profileId, query, query, count, now, now, SearchRepresentative("book", "book:$query"),
    )

    private fun fullCard(cardId: String) = WordCard(
        cardId = cardId,
        wordBookId = cardId.substringBefore(':'),
        lemma = cardId.substringAfter(':'),
        ipa = "/${cardId.substringAfter(':')}/",
        partOfSpeech = "n.",
        meaningZh = "能力",
        example = "an example",
        senses = listOf(WordSense("n.", "能力；才能")),
        phrases = listOf(PhraseEntry("to the best of my ability", "尽我所能")),
    )

    private class FixedIndex : VocabularySearchIndexRepository {
        override suspend fun replaceWordBook(
            wordBookId: String,
            wordBookName: String,
            dataVersion: String,
            cards: List<WordCard>,
        ) = RepositoryResult.Success(Unit)

        override suspend fun deleteWordBook(wordBookId: String) = RepositoryResult.Success(Unit)

        override suspend fun search(normalizedQuery: String, limit: Int): RepositoryResult<List<VocabularySearchIndexEntry>> =
            RepositoryResult.Success(
                listOf(indexEntry("book:ability"))
                    .filter { it.normalizedTerms.any { term -> term.contains(normalizedQuery) } }
                    .take(limit),
            )

        override suspend fun indexedVersions() = RepositoryResult.Success(emptyMap<String, String>())

        override suspend fun deleteMissing(keepWordBookIds: Collection<String>) = RepositoryResult.Success(Unit)
    }

    private class DelayedIndex(private val gate: CompletableDeferred<Unit>) : VocabularySearchIndexRepository {
        override suspend fun replaceWordBook(
            wordBookId: String,
            wordBookName: String,
            dataVersion: String,
            cards: List<WordCard>,
        ) = RepositoryResult.Success(Unit)

        override suspend fun deleteWordBook(wordBookId: String) = RepositoryResult.Success(Unit)

        override suspend fun search(normalizedQuery: String, limit: Int): RepositoryResult<List<VocabularySearchIndexEntry>> {
            if (!gate.isCompleted) gate.await()
            return RepositoryResult.Success(
                listOf(indexEntry("book:old"), indexEntry("book:new"))
                    .filter { it.normalizedTerms.any { term -> term.contains(normalizedQuery) } }
                    .take(limit),
            )
        }

        override suspend fun indexedVersions() = RepositoryResult.Success(emptyMap<String, String>())

        override suspend fun deleteMissing(keepWordBookIds: Collection<String>) = RepositoryResult.Success(Unit)
    }

    private class TwoCardIndex : VocabularySearchIndexRepository {
        override suspend fun replaceWordBook(
            wordBookId: String,
            wordBookName: String,
            dataVersion: String,
            cards: List<WordCard>,
        ) = RepositoryResult.Success(Unit)

        override suspend fun deleteWordBook(wordBookId: String) = RepositoryResult.Success(Unit)

        override suspend fun search(normalizedQuery: String, limit: Int): RepositoryResult<List<VocabularySearchIndexEntry>> =
            RepositoryResult.Success(listOf(indexEntry("book:older"), indexEntry("book:newer")).take(limit))

        override suspend fun indexedVersions() = RepositoryResult.Success(emptyMap<String, String>())

        override suspend fun deleteMissing(keepWordBookIds: Collection<String>) = RepositoryResult.Success(Unit)
    }

    private class RecordingCardSource(
        private val content: Map<String, WordCard>,
        private val failWith: Exception? = null,
    ) : WordCardSource {
        val requested = CopyOnWriteArrayList<List<String>>()

        override suspend fun cardIds(wordBookId: String): List<String> =
            content.keys.filter { it.startsWith("$wordBookId:") }

        override suspend fun cards(cardIds: List<String>): List<WordCard> {
            requested += cardIds
            failWith?.let { throw it }
            return cardIds.mapNotNull(content::get)
        }
    }

    /** 按 `cardId` 单独闸住的取卡源：没配闸门的卡立即返回，用来构造「后发先至」。 */
    private class GatedCardSource(
        private val gates: Map<String, CompletableDeferred<Unit>>,
        private val content: Map<String, WordCard>,
    ) : WordCardSource {
        override suspend fun cardIds(wordBookId: String): List<String> =
            content.keys.filter { it.startsWith("$wordBookId:") }

        override suspend fun cards(cardIds: List<String>): List<WordCard> {
            cardIds.forEach { id -> gates[id]?.takeIf { !it.isCompleted }?.await() }
            return cardIds.mapNotNull(content::get)
        }
    }

    private class FailingOnceUseCase(private val history: ObservableHistory) : com.example.englishlearning.learning.SearchVocabularyOperator {
        private var failed = false
        override suspend fun search(profileId: String, query: String): RepositoryResult<List<SearchVocabularyResult>> {
            if (!failed) { failed = true; return RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable) }
            return RepositoryResult.Success(emptyList())
        }
    }

    private class ObservableHistory(var entries: List<VocabularySearchHistory>) : VocabularySearchHistoryRepository {
        val listProfiles = CopyOnWriteArrayList<String>()
        val clearProfiles = CopyOnWriteArrayList<String>()
        var clearFailure = false
        var recordCalls = 0
        override suspend fun list(profileId: String, limit: Int): RepositoryResult<List<VocabularySearchHistory>> {
            listProfiles += profileId
            return RepositoryResult.Success(entries.filter { it.profileId == profileId }.take(limit))
        }
        override suspend fun record(profileId: String, query: String, representative: SearchRepresentative?, at: Instant): RepositoryResult<Unit> { recordCalls++; return RepositoryResult.Success(Unit) }
        override suspend fun find(profileId: String, normalizedQuery: String) = RepositoryResult.Success(entries.find { it.profileId == profileId && it.normalizedQuery == normalizedQuery })
        override suspend fun clear(profileId: String): RepositoryResult<Unit> {
            clearProfiles += profileId
            if (clearFailure) return RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            entries = entries.filterNot { it.profileId == profileId }
            return RepositoryResult.Success(Unit)
        }
        fun remainingFor(profileId: String) = entries.filter { it.profileId == profileId }
    }
}

/**
 * 索引层的一条摘要夹具。
 *
 * 声明在文件级而不是测试类里：它在几个**嵌套的**假仓储内部被使用，
 * 而 Kotlin 的嵌套类访问不到外层类的实例成员。
 */
private fun indexEntry(cardId: String) = VocabularySearchIndexEntry(
    wordBookId = cardId.substringBefore(':'),
    cardId = cardId,
    wordBookName = "四级词书",
    lemma = cardId.substringAfter(':'),
    ipa = "",
    meaningZh = "能力",
    normalizedTerms = listOf(cardId.substringAfter(':')),
)
