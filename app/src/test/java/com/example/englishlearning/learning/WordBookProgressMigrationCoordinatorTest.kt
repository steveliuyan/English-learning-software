package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.ReviewFeedback
import com.example.englishlearning.learning.domain.WordCard
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class WordBookProgressMigrationCoordinatorTest {
    @Test
    fun `same book can save without migration`() = runTest {
        val events = RecordingEvents()
        val coordinator = coordinator(events)

        val result = coordinator.prepare("profile", "book", "book")

        assertEquals(MigrationPreparation.SaveWithoutMigration, result)
        assertEquals(0, events.previewCalls)
    }

    @Test
    fun `unmatched reviewed cards can save without migration`() = runTest {
        val source = card("old", "ability")
        val events = RecordingEvents(
            state = CardReviewState(source.cardId, source.wordBookId, ReviewFeedback.Good, Instant.EPOCH, Instant.EPOCH),
        )
        val coordinator = coordinator(events, source = source, target = card("new", "different"))

        val result = coordinator.prepare("profile", "old", "new")

        assertEquals(MigrationPreparation.SaveWithoutMigration, result)
    }

    @Test
    fun `matching reviewed cards require confirmation and apply only after confirmation`() = runTest {
        val source = card("old", "ability")
        val target = card("new", "ability")
        val events = RecordingEvents(
            state = CardReviewState(source.cardId, source.wordBookId, ReviewFeedback.Good, Instant.EPOCH, Instant.EPOCH),
        )
        val coordinator = coordinator(events, source = source, target = target)

        val preparation = coordinator.prepare("profile", "old", "new")

        assertEquals(1, (preparation as MigrationPreparation.RequiresConfirmation).preview.candidates.size)
        assertEquals(0, events.migrateCalls)
        coordinator.confirm("profile", "old", "new", preparation.preview)
        assertEquals(1, events.migrateCalls)
    }

    private fun coordinator(events: RecordingEvents, source: WordCard = card("old", "ability"), target: WordCard = card("new", "different")) =
        WordBookProgressMigrationCoordinator(
            WordBookProgressMigrationService(
                cards = Cards(listOf(source, target)),
                events = events,
            ),
        )

    private fun card(book: String, lemma: String) = WordCard(
        cardId = "$book:$lemma",
        wordBookId = book,
        lemma = lemma,
        ipa = "",
        partOfSpeech = "n.",
        meaningZh = "能力",
    )

    private class Cards(private val cards: List<WordCard>) : WordCardSource {
        override suspend fun cardIds(wordBookId: String) = cards.filter { it.wordBookId == wordBookId }.map { it.cardId }
        override suspend fun cards(cardIds: List<String>) = cards.filter { it.cardId in cardIds }
    }

    private class RecordingEvents(private val state: CardReviewState? = null) : LearningEventRepository {
        var previewCalls = 0
        var migrateCalls = 0
        override suspend fun append(event: com.example.englishlearning.learning.domain.LearningEvent, nextState: CardReviewState) = AppendEventResult.Appended(false)
        override suspend fun findEvent(eventId: String) = RepositoryResult.Success<com.example.englishlearning.learning.domain.LearningEvent?>(null)
        override suspend fun findCardState(cardId: String) = RepositoryResult.Success<CardReviewState?>(null)
        override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)
        override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(emptyList<String>())
        override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())
        override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
        override suspend fun reviewedStates(wordBookId: String): RepositoryResult<List<CardReviewState>> {
            previewCalls++
            return RepositoryResult.Success(listOfNotNull(state))
        }
        override suspend fun migrateReviewStates(profileId: String, sourceBookId: String, targetBookId: String, candidates: List<ProgressMigrationCandidate>): RepositoryResult<Int> {
            migrateCalls++
            return RepositoryResult.Success(candidates.size)
        }
    }
}
