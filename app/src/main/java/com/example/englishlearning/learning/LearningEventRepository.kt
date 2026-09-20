package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import java.time.Instant

sealed interface AppendEventResult {
    /** [duplicate] is true when the same event id was already stored and nothing changed. */
    data class Appended(val duplicate: Boolean) : AppendEventResult

    data object StorageUnavailable : AppendEventResult
}

/**
 * Event-log port (spec F1-03). The log is append-only: there is deliberately no
 * update or delete, so a later algorithm upgrade can only recompute future
 * review instants and never rewrite recorded history.
 */
interface LearningEventRepository {
    suspend fun append(event: LearningEvent, nextState: CardReviewState): AppendEventResult

    suspend fun findEvent(eventId: String): RepositoryResult<LearningEvent?>

    suspend fun findCardState(cardId: String): RepositoryResult<CardReviewState?>

    /** Number of recorded events for a card within a plan; 1 means the card is complete. */
    suspend fun countEventsForCard(planId: String, cardId: String): RepositoryResult<Int>

    /**
     * Cards of a plan that already carry an event, i.e. plan items already complete. Read from
     * the event log, never from a separate counter, so completion cannot drift from history.
     */
    suspend fun completedCardIds(planId: String): RepositoryResult<List<String>>

    /** Cards of [wordBookId] reviewed at least once, so they are no longer "new". */
    suspend fun reviewedCardIds(wordBookId: String): RepositoryResult<List<String>>

    /** Cards of [wordBookId] whose derived schedule is due at or before [now], earliest first. */
    suspend fun dueCardIds(wordBookId: String, now: Instant): RepositoryResult<List<String>>
}
