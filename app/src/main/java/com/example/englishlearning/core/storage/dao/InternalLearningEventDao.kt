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

internal data class LearningRecordEventRow(
    val planId: String,
    val cardId: String,
    val feedback: String,
    val occurredAtEpochMillis: Long,
    val localDate: String,
)

internal data class ReviewStateRow(
    val cardId: String,
    val nextReviewAtEpochMillis: Long,
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

    @Query("SELECT e.planId AS planId, e.cardId AS cardId, e.feedback AS feedback, e.occurredAtEpochMillis AS occurredAtEpochMillis, p.localDate AS localDate FROM learning_events e INNER JOIN today_plans p ON p.planId = e.planId WHERE e.profileId = :profileId AND e.planId = :planId ORDER BY e.occurredAtEpochMillis ASC")
    suspend fun todayRecords(profileId: String, planId: String): List<LearningRecordEventRow>

    @Query("SELECT e.planId AS planId, e.cardId AS cardId, e.feedback AS feedback, e.occurredAtEpochMillis AS occurredAtEpochMillis, p.localDate AS localDate FROM learning_events e INNER JOIN today_plans p ON p.planId = e.planId WHERE e.profileId = :profileId ORDER BY p.localDate DESC, e.occurredAtEpochMillis ASC")
    suspend fun historyRecords(profileId: String): List<LearningRecordEventRow>

    @Query("SELECT cardId, nextReviewAtEpochMillis FROM card_review_states WHERE wordBookId = :wordBookId")
    suspend fun reviewStates(wordBookId: String): List<ReviewStateRow>

    @Query("SELECT * FROM card_review_states WHERE wordBookId = :wordBookId")
    suspend fun fullReviewStates(wordBookId: String): List<CardReviewStateEntity>

    @Query("DELETE FROM card_review_states WHERE wordBookId = :wordBookId")
    suspend fun deleteReviewStates(wordBookId: String)

    @Query("DELETE FROM learning_events WHERE wordBookId = :wordBookId")
    suspend fun deleteEvents(wordBookId: String)

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

    @Query("SELECT * FROM card_review_states WHERE cardId = :cardId LIMIT 1")
    suspend fun findCardStateByCardId(cardId: String): CardReviewStateEntity?

    @Transaction
    suspend fun appendEvent(event: LearningEventEntity, next: CardReviewStateEntity): Long {
        val rowId = insertEventIfAbsent(event)
        if (rowId != -1L) {
            upsertCardState(next)
        }
        return rowId
    }
}
