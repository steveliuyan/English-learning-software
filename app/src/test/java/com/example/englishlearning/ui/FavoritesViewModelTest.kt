package com.example.englishlearning.ui

import com.example.englishlearning.learning.FavoriteWord
import com.example.englishlearning.learning.FavoriteWordRepository
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.WordCard
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {
    @Test
    fun `load exposes favorites for the screen`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeFavoritesRepository(words = mutableListOf(favorite("cet4:cat"), favorite("cet4:dog")))
        val viewModel = FavoritesViewModel(repository, FakeCards(card()), Dispatchers.Unconfined)

        viewModel.load("default")
        advanceUntilIdle()

        val state = viewModel.uiState.value as FavoritesUiState.Ready
        assertEquals(listOf("cet4:cat", "cet4:dog"), state.words.map { it.cardId })
        Dispatchers.resetMain()
    }

    @Test
    fun `load failure degrades the whole page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = FavoritesViewModel(
            FakeFavoritesRepository(listFailure = true),
            FakeCards(card()),
            Dispatchers.Unconfined,
        )

        viewModel.load("default")
        advanceUntilIdle()

        assertEquals(FavoritesUiState.Unavailable, viewModel.uiState.value)
        Dispatchers.resetMain()
    }

    @Test
    fun `toggle on a favorited card removes it and reloads the list`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeFavoritesRepository(words = mutableListOf(favorite("cet4:cat")))
        val viewModel = FavoritesViewModel(repository, FakeCards(card()), Dispatchers.Unconfined)
        viewModel.load("default")
        advanceUntilIdle()

        viewModel.toggle("cet4:cat", "cet4", "cat")
        advanceUntilIdle()

        assertEquals(listOf("cet4:cat"), repository.toggles.map { it.first })
        val state = viewModel.uiState.value as FavoritesUiState.Ready
        assertNull(state.words.find { it.cardId == "cet4:cat" })
        Dispatchers.resetMain()
    }

    @Test
    fun `toggle failure degrades to unavailable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeFavoritesRepository(
            words = mutableListOf(favorite("cet4:cat")),
            toggleFailure = true,
        )
        val viewModel = FavoritesViewModel(repository, FakeCards(card()), Dispatchers.Unconfined)
        viewModel.load("default")
        advanceUntilIdle()

        viewModel.toggle("cet4:cat", "cet4", "cat")
        advanceUntilIdle()

        assertEquals(FavoritesUiState.Unavailable, viewModel.uiState.value)
        Dispatchers.resetMain()
    }

    @Test
    fun `open loads the full card once and consume clears it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeFavoritesRepository(words = mutableListOf(favorite("cet4:cat")))
        val viewModel = FavoritesViewModel(repository, FakeCards(card()), Dispatchers.Unconfined)
        viewModel.load("default")
        advanceUntilIdle()

        viewModel.open(favorite("cet4:cat"))
        advanceUntilIdle()

        assertEquals("cet4:cat", (viewModel.uiState.value as FavoritesUiState.Ready).words.single().cardId)
        assertNotNull(viewModel.openedCard.value)
        assertEquals("cat", viewModel.openedCard.value!!.lemma)
        viewModel.consumeOpenedCard()
        assertNull(viewModel.openedCard.value)
        Dispatchers.resetMain()
    }

    @Test
    fun `open a favorite whose content was removed surfaces an honest notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeFavoritesRepository(words = mutableListOf(favorite("cet4:ghost")))
        val viewModel = FavoritesViewModel(repository, FakeCards(null), Dispatchers.Unconfined)
        viewModel.load("default")
        advanceUntilIdle()

        viewModel.open(favorite("cet4:ghost"))
        advanceUntilIdle()

        assertNull(viewModel.openedCard.value)
        assertNotNull((viewModel.uiState.value as FavoritesUiState.Ready).notice)
        Dispatchers.resetMain()
    }

    private fun favorite(cardId: String) =
        FavoriteWord("default", cardId, "cet4", cardId.substringAfterLast(':'), Instant.ofEpochMilli(100L))

    private fun card() = WordCard(
        cardId = "cet4:cat",
        wordBookId = "cet4",
        lemma = "cat",
        ipa = "/kæt/",
        partOfSpeech = "n.",
        meaningZh = "猫",
    )

    private class FakeFavoritesRepository(
        val words: MutableList<FavoriteWord> = mutableListOf(),
        private val listFailure: Boolean = false,
        private val toggleFailure: Boolean = false,
    ) : FavoriteWordRepository {
        val toggles = mutableListOf<Pair<String, String>>()

        override suspend fun toggle(profileId: String, cardId: String, wordBookId: String, lemma: String): Result<Unit> {
            if (toggleFailure) return Result.failure(IllegalStateException("storage"))
            toggles += cardId to wordBookId
            if (!words.removeIf { it.cardId == cardId }) {
                words += FavoriteWord(profileId, cardId, wordBookId, lemma, Instant.ofEpochMilli(100L))
            }
            return Result.success(Unit)
        }

        override suspend fun isFavorite(profileId: String, cardId: String): Result<Boolean> =
            Result.success(words.any { it.cardId == cardId })

        override suspend fun listFavorites(profileId: String): Result<List<FavoriteWord>> =
            if (listFailure) Result.failure(IllegalStateException("storage")) else Result.success(words.toList())
    }

    private class FakeCards(private val card: WordCard?) : WordCardSource {
        override suspend fun cardIds(wordBookId: String): List<String> = emptyList()

        override suspend fun cards(cardIds: List<String>): List<WordCard> =
            listOfNotNull(card?.takeIf { it.cardId in cardIds })
    }
}
