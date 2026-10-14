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

/** F4-01 决策⑤派生行：某 profile 下「最近一次学习事件反馈为 Again」的一张卡。 */
internal data class WrongWordRow(
    val cardId: String,
    val wordBookId: String,
    val lastFeedbackAtEpochMillis: Long,
    val nextReviewAtEpochMillis: Long?,
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

    /**
     * F4-01 决策⑤：错词 = 该 profile 下最近一次学习事件反馈为 Again 的卡（card_review_states 无
     * profileId，不能承载按档案口径，故从只追加的 learning_events 派生）。
     * 排序 COALESCE(card_review_states.nextReviewAtEpochMillis, 事件时间)：越急复习越靠前；
     * GROUP BY 兜住同一毫秒双事件的退化（同卡同行，取一即可）。
     *
     * ORDER BY 必须重写子查询而不是引用别名：SQLite 只在「ORDER BY 裸名」时优先匹配结果别名，
     * 嵌进 COALESCE 表达式就按源表列解析——`learning_events` 恰好有同名调度列，
     * 真库 `RoomWrongWordsDerivationTest` 实测踩过（dog/big 顺序倒挂）。
     */
    @Query("SELECT e.cardId, e.wordBookId, e.occurredAtEpochMillis AS lastFeedbackAtEpochMillis, " +
        "(SELECT MIN(s.nextReviewAtEpochMillis) FROM card_review_states s WHERE s.cardId = e.cardId) AS nextReviewAtEpochMillis " +
        "FROM learning_events e " +
        "WHERE e.profileId = :profileId AND e.feedback = 'Again' " +
        "AND e.occurredAtEpochMillis = (SELECT MAX(l.occurredAtEpochMillis) FROM learning_events l " +
        "WHERE l.profileId = e.profileId AND l.cardId = e.cardId) " +
        "GROUP BY e.cardId " +
        "ORDER BY COALESCE((SELECT MIN(s2.nextReviewAtEpochMillis) FROM card_review_states s2 WHERE s2.cardId = e.cardId), " +
        "e.occurredAtEpochMillis) ASC")
    suspend fun wrongWordRows(profileId: String): List<WrongWordRow>

    /**
     * F4-01 Task10b 删除词书确认框的级联计数。三个口径各自对齐一条生产写路径：
     * 词条按册（跨 profile）与 `deleteForWordBook` 同范围；学习事件与文章按 profile+册
     * 数的是**保留面**——决策 §6 明文它们零触碰，条数进文案是为了让用户看见「删了什么、留了什么」。
     */
    @Query("SELECT COUNT(*) FROM vocabulary_entries WHERE wordBookId = :wordBookId")
    suspend fun entriesForWordBook(wordBookId: String): Int

    @Query("SELECT COUNT(*) FROM learning_events WHERE profileId = :profileId AND wordBookId = :wordBookId")
    suspend fun eventsForWordBook(profileId: String, wordBookId: String): Int

    @Query("SELECT COUNT(*) FROM articles WHERE profileId = :profileId AND activeWordBookId = :wordBookId")
    suspend fun articlesForWordBook(profileId: String, wordBookId: String): Int
}
