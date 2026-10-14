package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Query

internal data class DateCountRow(
    val localDate: String,
    val count: Int,
)

/** One immutable plan snapshot joined with its word book name and article-generated flag. */
internal data class PlanRow(
    val planId: String,
    val profileId: String,
    val localDate: String,
    val zoneId: String,
    val activeWordBookId: String,
    val displayName: String?,
    val newTarget: Int,
    val dueTarget: Int,
    val ruleVersion: String,
    val generatedAtEpochMillis: Long,
    val articleGenerated: Boolean,
)

/** Completed-card counts per day and task kind ('NEW'/'DUE'); fast path pinned against UnlockPolicy. */
internal data class TaskKindDoneRow(
    val localDate: String,
    val taskKind: String,
    val done: Int,
)

internal data class PlanTaskRow(
    val planId: String,
    val cardId: String,
    val taskKind: String,
)

internal data class CompletedCardRow(
    val planId: String,
    val cardId: String,
)

@Dao
internal interface InternalLearningStatsDao {
    @Query("SELECT COUNT(DISTINCT cardId) FROM learning_events WHERE profileId = :profileId AND occurredAtEpochMillis >= :fromEpochMillis AND occurredAtEpochMillis < :toExclusiveEpochMillis")
    suspend fun countReviewedCards(profileId: String, fromEpochMillis: Long, toExclusiveEpochMillis: Long): Int

    @Query("SELECT localDate, COUNT(*) AS count FROM reading_completions WHERE profileId = :profileId AND localDate >= :fromDate AND localDate <= :toDate GROUP BY localDate")
    suspend fun countReadings(profileId: String, fromDate: String, toDate: String): List<DateCountRow>

    @Query("SELECT p.planId, p.profileId, p.localDate, p.zoneId, p.activeWordBookId, w.displayName, " +
        "p.newTarget, p.dueTarget, p.ruleVersion, p.generatedAtEpochMillis, " +
        "(SELECT COUNT(*) FROM articles a WHERE a.profileId = p.profileId AND a.localDate = p.localDate " +
        "AND a.activeWordBookId = p.activeWordBookId) > 0 AS articleGenerated " +
        "FROM today_plans p LEFT JOIN word_books w ON w.id = p.activeWordBookId " +
        "WHERE p.profileId = :profileId AND p.localDate >= :fromDate AND p.localDate <= :toDate " +
        "ORDER BY p.localDate")
    suspend fun planRows(profileId: String, fromDate: String, toDate: String): List<PlanRow>

    @Query("SELECT p.localDate, t.taskKind, COUNT(DISTINCT t.cardId) AS done FROM today_plan_tasks t " +
        "JOIN learning_events e ON e.planId = t.planId AND e.cardId = t.cardId " +
        "JOIN today_plans p ON p.planId = t.planId " +
        "WHERE e.profileId = :profileId AND p.localDate >= :fromDate AND p.localDate <= :toDate " +
        "GROUP BY p.localDate, t.taskKind")
    suspend fun dailyDoneByTaskKind(profileId: String, fromDate: String, toDate: String): List<TaskKindDoneRow>

    @Query("SELECT t.planId, t.cardId, t.taskKind FROM today_plan_tasks t " +
        "JOIN today_plans p ON p.planId = t.planId " +
        "WHERE p.profileId = :profileId AND p.localDate >= :fromDate AND p.localDate <= :toDate")
    suspend fun planTaskRowsForRange(profileId: String, fromDate: String, toDate: String): List<PlanTaskRow>

    @Query("SELECT DISTINCT e.planId, e.cardId FROM learning_events e " +
        "JOIN today_plans p ON p.planId = e.planId " +
        "WHERE e.profileId = :profileId AND p.localDate >= :fromDate AND p.localDate <= :toDate")
    suspend fun completedCardsForRange(profileId: String, fromDate: String, toDate: String): List<CompletedCardRow>

    @Query("SELECT COUNT(DISTINCT cardId) FROM learning_events WHERE profileId = :profileId")
    suspend fun totalLearnedCards(profileId: String): Int

    @Query("SELECT COUNT(*) FROM learning_events e JOIN today_plan_tasks t ON t.planId = e.planId " +
        "AND t.cardId = e.cardId AND t.taskKind = 'DUE' WHERE e.profileId = :profileId")
    suspend fun totalReviewEvents(profileId: String): Int
}
