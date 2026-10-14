package com.example.englishlearning.ui

import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.WrongWord
import com.example.englishlearning.learning.WrongWordsRepository
import com.example.englishlearning.learning.domain.WordCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * F4-01 决策⑤错词列表 ViewModel：派生口径（每卡最近反馈 Again）在仓储 SQL 里，
 * 由 connected `RoomWrongWordsDerivationTest` 兜底；这里钉状态机——
 * 列表按仓储派生顺序展示、内容已随词书删除的卡诚实剔除、失败整页降级。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WrongWordsViewModelTest {
    @Test
    fun `load lists cards in derived order dropping cards whose content is gone`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeWrongWordsRepository(
            mapOf("default" to listOf(
                wrong("cet4:cat", 500L, null),
                wrong("cet4:run", 100L, 50L),
                wrong("cet4:ghost", 200L, null),
            )),
        )
        val viewModel = WrongWordsViewModel(repository, FakeCards(listOf(card("cet4:cat"), card("cet4:run"))), Dispatchers.Unconfined)

        viewModel.load("default")
        advanceUntilIdle()

        val state = viewModel.uiState.value as WrongWordsUiState.Ready
        assertEquals(
            listOf("cet4:cat", "cet4:run"),
            state.cards.map { it.cardId },
            "内容缺失的卡被剔除，其余保持派生顺序",
        )
        Dispatchers.resetMain()
    }

    @Test
    fun `load failure degrades the whole page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = WrongWordsViewModel(FakeWrongWordsRepository(failure = true), FakeCards(emptyList()), Dispatchers.Unconfined)

        viewModel.load("default")
        advanceUntilIdle()

        assertEquals(WrongWordsUiState.Unavailable, viewModel.uiState.value)
        Dispatchers.resetMain()
    }

    @Test
    fun `card source failure degrades the whole page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeWrongWordsRepository(mapOf("default" to listOf(wrong("cet4:cat", 500L, null))))
        val viewModel = WrongWordsViewModel(repository, FakeCards(failing = true), Dispatchers.Unconfined)

        viewModel.load("default")
        advanceUntilIdle()

        assertEquals(WrongWordsUiState.Unavailable, viewModel.uiState.value)
        Dispatchers.resetMain()
    }

    @Test
    fun `no wrong words is an honest empty list`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = WrongWordsViewModel(FakeWrongWordsRepository(mapOf("default" to emptyList())), FakeCards(emptyList()), Dispatchers.Unconfined)

        viewModel.load("default")
        advanceUntilIdle()

        val state = viewModel.uiState.value as WrongWordsUiState.Ready
        assertEquals(emptyList<WordCard>(), state.cards)
        Dispatchers.resetMain()
    }

    @Test
    fun `stale profile load result is discarded when a newer load starts`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeWrongWordsRepository(
            mapOf(
                "p1" to listOf(wrong("cet4:cat", 500L, null)),
                "p2" to listOf(wrong("cet4:dog", 500L, null)),
            ),
        )
        val viewModel = WrongWordsViewModel(
            repository,
            FakeCards(listOf(card("cet4:cat"), card("cet4:dog"))),
            Dispatchers.Unconfined,
        )

        viewModel.load("p1")
        viewModel.load("p2")
        advanceUntilIdle()

        val state = viewModel.uiState.value as WrongWordsUiState.Ready
        assertEquals(listOf("cet4:dog"), state.cards.map { it.cardId })
        Dispatchers.resetMain()
    }

    private fun wrong(cardId: String, at: Long, nextReview: Long?) =
        WrongWord(cardId = cardId, wordBookId = "cet4", lastFeedbackAtEpochMillis = at, nextReviewAtEpochMillis = nextReview)

    private fun card(cardId: String) = WordCard(
        cardId = cardId,
        wordBookId = "cet4",
        lemma = cardId.substringAfterLast(':'),
        ipa = "",
        partOfSpeech = "n.",
        meaningZh = "释义",
    )

    private class FakeWrongWordsRepository(
        private val byProfile: Map<String, List<WrongWord>> = emptyMap(),
        private val failure: Boolean = false,
    ) : WrongWordsRepository {
        override suspend fun wrongWords(profileId: String): Result<List<WrongWord>> =
            if (failure) Result.failure(IllegalStateException("storage")) else Result.success(byProfile[profileId].orEmpty())
    }

    private class FakeCards(
        private val available: List<WordCard> = emptyList(),
        private val failing: Boolean = false,
    ) : WordCardSource {
        override suspend fun cardIds(wordBookId: String): List<String> = emptyList()

        override suspend fun cards(cardIds: List<String>): List<WordCard> {
            if (failing) throw IllegalStateException("storage")
            return cardIds.mapNotNull { id -> available.firstOrNull { it.cardId == id } }
        }
    }
}
