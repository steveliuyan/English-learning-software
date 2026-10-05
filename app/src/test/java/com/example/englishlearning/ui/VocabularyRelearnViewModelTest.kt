package com.example.englishlearning.ui

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SubmitCardFeedbackUseCase
import com.example.englishlearning.learning.VocabularyEntry
import com.example.englishlearning.learning.VocabularyRepository
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.ReviewFeedback
import com.example.englishlearning.learning.domain.WordCard
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

@OptIn(ExperimentalCoroutinesApi::class)
class VocabularyRelearnViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        Events.appendResult = AppendEventResult.Appended(false)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load only requests entries from active word book`() = runTest {
        val vocabulary = RecordingVocabulary(
            entries = listOf(
                entry("cet4", "cet4:ability"),
                entry("toefl", "toefl:ability"),
            ),
        )
        val vm = viewModel(vocabulary)

        vm.load("profile-1", "cet4")
        advanceUntilIdle()

        val ready = assertIs<VocabularyRelearnUiState.Ready>(vm.state.value)
        assertEquals("cet4:ability", ready.card.cardId)
        assertEquals(listOf<Pair<String, String?>>("profile-1" to "cet4"), vocabulary.requests)
    }

    @Test
    fun `empty active word book ends without a card`() = runTest {
        val vm = viewModel(RecordingVocabulary(emptyList()))

        vm.load("profile-1", "cet4")
        advanceUntilIdle()

        assertEquals(VocabularyRelearnUiState.Done(0), vm.state.value)
    }

    @Test
    fun `known feedback removes the current card from vocabulary`() = runTest {
        val vocabulary = RecordingVocabulary(listOf(entry("cet4", "cet4:ability")))
        val vm = viewModel(vocabulary)

        vm.load("profile-1", "cet4")
        advanceUntilIdle()
        vm.submit(CardFeedback.Known)
        advanceUntilIdle()

        assertEquals(listOf("profile-1" to ("cet4" to "cet4:ability")), vocabulary.removals)
        assertEquals(VocabularyRelearnUiState.Done(1), vm.state.value)
    }

    @Test
    fun `unknown feedback keeps the current card in vocabulary`() = runTest {
        val vocabulary = RecordingVocabulary(listOf(entry("cet4", "cet4:ability")))
        val vm = viewModel(vocabulary)

        vm.load("profile-1", "cet4")
        advanceUntilIdle()
        vm.submit(CardFeedback.Unknown)
        advanceUntilIdle()

        assertEquals(emptyList(), vocabulary.removals)
        assertEquals(VocabularyRelearnUiState.Done(1), vm.state.value)
    }

    @Test
    fun `storage failure keeps current card and retry can submit again`() = runTest {
        val vocabulary = RecordingVocabulary(listOf(entry("cet4", "cet4:ability")))
        Events.appendResult = AppendEventResult.StorageUnavailable
        val vm = viewModel(vocabulary)

        vm.load("profile-1", "cet4")
        advanceUntilIdle()
        vm.submit(CardFeedback.Fuzzy)
        advanceUntilIdle()

        val failed = assertIs<VocabularyRelearnUiState.Ready>(vm.state.value)
        assertEquals("cet4:ability", failed.card.cardId)
        assertEquals(false, failed.submitting)
        assertEquals("保存失败，请重试", failed.message)

        Events.appendResult = AppendEventResult.Appended(false)
        vm.submit(CardFeedback.Fuzzy)
        advanceUntilIdle()

        assertEquals(VocabularyRelearnUiState.Done(1), vm.state.value)
    }

    private fun viewModel(vocabulary: RecordingVocabulary): VocabularyRelearnViewModel =
        VocabularyRelearnViewModel(
            vocabulary = vocabulary,
            cards = Cards,
            submitFeedback = SubmitCardFeedbackUseCase(Events, FixedClockProvider(Instant.EPOCH, java.time.ZoneOffset.UTC)),
            eventIds = EventIdFactory { "event-1" },
        )

    private fun entry(book: String, cardId: String) = VocabularyEntry(
        profileId = "profile-1", wordBookId = book, cardId = cardId,
        addedAt = Instant.EPOCH, lastFeedback = "Again",
    )

    private class RecordingVocabulary(private val entries: List<VocabularyEntry>) : VocabularyRepository {
        val requests = mutableListOf<Pair<String, String?>>()
        val removals = mutableListOf<Pair<String, Pair<String, String>>>()
        override suspend fun add(profileId: String, wordBookId: String, cardId: String, feedback: String, addedAt: Instant) = Unit
        override suspend fun remove(profileId: String, wordBookId: String, cardId: String) {
            removals += profileId to (wordBookId to cardId)
        }
        override suspend fun contains(profileId: String, wordBookId: String, cardId: String) = true
        override suspend fun list(profileId: String, wordBookId: String?): RepositoryResult<List<VocabularyEntry>> {
            requests += profileId to wordBookId
            return RepositoryResult.Success(entries.filter { wordBookId == null || it.wordBookId == wordBookId })
        }
    }

    private object Cards : WordCardSource {
        override suspend fun cardIds(wordBookId: String) = emptyList<String>()
        override suspend fun cards(cardIds: List<String>) = cardIds.map { id ->
            WordCard(id, id.substringBefore(':'), id.substringAfter(':'), "n.", "释义", "", "例句")
        }
    }

    private object Events : LearningEventRepository {
        var appendResult: AppendEventResult = AppendEventResult.Appended(false)
        override suspend fun append(event: LearningEvent, nextState: CardReviewState) = appendResult
        override suspend fun findEvent(eventId: String): RepositoryResult<LearningEvent?> = RepositoryResult.Success(null)
        override suspend fun findCardState(cardId: String): RepositoryResult<CardReviewState?> = RepositoryResult.Success(null)
        override suspend fun countEventsForCard(planId: String, cardId: String): RepositoryResult<Int> = RepositoryResult.Success(0)
        override suspend fun completedCardIds(planId: String): RepositoryResult<List<String>> = RepositoryResult.Success(emptyList())
        override suspend fun reviewedCardIds(wordBookId: String): RepositoryResult<List<String>> = RepositoryResult.Success(emptyList())
        override suspend fun dueCardIds(wordBookId: String, now: Instant): RepositoryResult<List<String>> = RepositoryResult.Success(emptyList())
    }
}
