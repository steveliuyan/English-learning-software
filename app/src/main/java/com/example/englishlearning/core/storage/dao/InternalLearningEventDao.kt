package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.englishlearning.core.storage.entity.CardReviewStateEntity
import com.example.englishlearning.core.storage.entity.LearningEventEntity

internal data class PlanCardFeedbackRow(
    val cardId: String,
    val feedback: String,
)

/**
 * Append-only access to the learning event log (spec F1-03).
 *
 * There is intentionally no update or delete method: an algorithm upgrade may
 * only recompute future `nextReviewAt`, never rewrite a recorded event.
 */
@Dao
internal interface InternalLearningEventDao {
    /**
     * Returns the new row id, or -1 when [eventId] was already present. Callers
     * treat -1 as "duplicate submission" and skip the state transition.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventIfAbsent(event: LearningEventEntity): Long

    @Query("SELECT * FROM learning_events WHERE eventId = :eventId LIMIT 1")
    suspend fun findEvent(eventId: String): LearningEventEntity?

    @Query("SELECT COUNT(*) FROM learning_events WHERE planId = :planId AND cardId = :cardId")
    suspend fun countEventsForCard(planId: String, cardId: String): Int

    /** Cards of this plan that already carry an event, i.e. plan items already complete. */
    @Query("SELECT DISTINCT cardId FROM learning_events WHERE planId = :planId")
    suspend fun completedCardIds(planId: String): List<String>

    @Query(
        "SELECT event.cardId AS cardId, event.feedback AS feedback FROM learning_events AS event " +
            "INNER JOIN (SELECT cardId, MAX(occurredAtEpochMillis) AS latestAt FROM learning_events " +
            "WHERE planId = :planId GROUP BY cardId) AS latest " +
            "ON event.cardId = latest.cardId AND event.occurredAtEpochMillis = latest.latestAt " +
            "WHERE event.planId = :planId ORDER BY event.cardId ASC",
    )
    suspend fun completedCardFeedback(planId: String): List<PlanCardFeedbackRow>

    /** Cards of [wordBookId] that have been reviewed at least once, so they are no longer new. */
    @Query("SELECT cardId FROM card_review_states WHERE wordBookId = :wordBookId")
    suspend fun reviewedCardIds(wordBookId: String): List<String>

    /** Cards of [wordBookId] whose derived schedule is due at or before [nowEpochMillis]. */
    @Query(
        "SELECT cardId FROM card_review_states " +
            "WHERE wordBookId = :wordBookId AND nextReviewAtEpochMillis <= :nowEpochMillis " +
            "ORDER BY nextReviewAtEpochMillis ASC, cardId ASC",
    )
    suspend fun dueCardIds(wordBookId: String, nowEpochMillis: Long): List<String>

    @Query("SELECT * FROM card_review_states WHERE cardId = :cardId LIMIT 1")
    suspend fun findCardState(cardId: String): CardReviewStateEntity?

    @Upsert
    suspend fun upsertCardState(state: CardReviewStateEntity)

    @Transaction
    suspend fun appendEvent(event: LearningEventEntity, next: CardReviewStateEntity): Long {
        val rowId = insertEventIfAbsent(event)
        if (rowId != -1L) {
            upsertCardState(next)
        }
        return rowId
    }
}
