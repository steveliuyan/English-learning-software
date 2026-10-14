package com.example.englishlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.CardReviewStateEntity
import com.example.englishlearning.core.storage.entity.FavoriteWordEntity
import com.example.englishlearning.core.storage.entity.LearningEventEntity
import com.example.englishlearning.core.storage.entity.ReadingCompletionEntity
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity
import com.example.englishlearning.core.storage.entity.WordBookProgressMigrationAuditEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F4-01 决策 §9「删除全部学习数据」真库口径（androidTest）：
 * 删五类学习进度事实与派生态（事件/调度态/计划+任务/阅读完成/迁移审计），
 * 文章、收藏、搜索历史与别档案行零触碰。card_review_states 无 profileId 列，
 * 本机单档案下整表清——这条把「整表清」钉成契约，防止有人悄悄改成 JOIN 词书。
 */
@RunWith(AndroidJUnit4::class)
class RoomLearningDataDeletionTest {
    @Test
    fun wipeRemovesFiveLearningTablesScopedToProfileAndKeepsContentRows(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "learning-wipe-${System.nanoTime()}.db"
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
        try {
            val events = database.internalLearningEventDao()
            events.insertEventIfAbsent(event("e1", "p1"))
            events.insertEventIfAbsent(event("e2", "p1"))
            events.insertEventIfAbsent(event("e3", "p2"))
            events.upsertCardState(reviewState("book:cat"))
            events.upsertCardState(reviewState("book:run"))

            val plans = database.internalTodayPlanDao()
            plans.insertPlan(plan("plan-p1", "p1"))
            plans.insertTasks(listOf(TodayPlanTaskEntity("plan-p1", "book:cat", "NEW", 0)))
            plans.insertPlan(plan("plan-p2", "p2"))
            plans.insertTasks(listOf(TodayPlanTaskEntity("plan-p2", "book:dog", "NEW", 0)))

            val completions = database.internalReadingCompletionDao()
            completions.insert(ReadingCompletionEntity("a1", profileId = "p1", localDate = "2026-09-22", completedAtEpochMillis = 1L))
            completions.insert(ReadingCompletionEntity("b1", profileId = "p2", localDate = "2026-09-23", completedAtEpochMillis = 2L))

            database.internalWordBookProgressMigrationAuditDao().insertIfAbsent(
                WordBookProgressMigrationAuditEntity("p1", "old-book", "book", "old-book:cat", "book:cat", 1L),
            )

            // 保留面走正门：文章经仓储、收藏经 DAO（都是既有生产写路径）。
            val articles = com.example.englishlearning.reading.RoomArticleRepository(database, Dispatchers.Unconfined)
            articles.saveNewVersion(article("a1", "p1")).getOrThrow()
            articles.saveNewVersion(article("b1", "p2")).getOrThrow()
            database.internalFavoriteWordDao().insertIgnore(FavoriteWordEntity("p1", "book:cat", "book", "cat", 1L))

            val repository = RoomLearningDataDeletionRepository(database, Dispatchers.Unconfined)
            val impact = repository.deletionImpact("p1").getOrThrow()
            assertEquals(2, impact.eventCount)
            assertEquals("调度态整表数（单档案设备=当前档案）", 2, impact.reviewStateCount)
            assertEquals(1, impact.planCount)
            assertEquals(1, impact.completionCount)
            assertEquals(1, impact.migrationAuditCount)
            assertEquals(1, impact.keptArticleCount)
            assertEquals(1, impact.keptFavoriteCount)

            val deleted = repository.deleteAllLearningData("p1").getOrThrow()
            assertEquals("回执必须来自同一批真数，不能拿预览凑数", impact, deleted)

            // 删净面：p1 五类全部归零。
            assertEquals(null, events.findEvent("e1"))
            assertEquals(0, countRows(database, "today_plan_tasks WHERE planId = 'plan-p1'"))
            assertEquals(0, countRows(database, "card_review_states"))
            assertEquals(0, countRows(database, "reading_completions WHERE profileId = 'p1'"))
            assertEquals(0, countRows(database, "word_book_progress_migration_audits WHERE profileId = 'p1'"))

            // 保留面：p1 文章与收藏原样；p2 一行都不许被牵连。
            assertEquals(1, countRows(database, "articles WHERE profileId = 'p1'"))
            assertEquals(1, countRows(database, "favorite_words WHERE profileId = 'p1'"))
            assertEquals("别档案的事件原样", 1, countRows(database, "learning_events WHERE profileId = 'p2'"))
            assertEquals(1, countRows(database, "today_plans WHERE profileId = 'p2'"))
            assertEquals(1, countRows(database, "today_plan_tasks WHERE planId = 'plan-p2'"))
            assertEquals(1, countRows(database, "reading_completions WHERE profileId = 'p2'"))
            assertEquals(1, countRows(database, "articles WHERE profileId = 'p2'"))
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(name)
        }
    }

    private fun countRows(database: AppDatabase, from: String): Int =
        database.openHelper.writableDatabase
            .query("SELECT COUNT(*) FROM $from")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun event(eventId: String, profileId: String) = LearningEventEntity(
        eventId = eventId, profileId = profileId, planId = "plan-$profileId",
        cardId = "book:cat", wordBookId = "book", feedback = "GOOD",
        occurredAtEpochMillis = 1L, algorithmVersion = "fsrs-1", paramsVersion = "p-1",
        dueBeforeEpochMillis = null, nextReviewAtEpochMillis = 2L,
    )

    private fun reviewState(cardId: String) = CardReviewStateEntity(
        cardId = cardId, wordBookId = "book", lastFeedback = "GOOD",
        lastReviewedAtEpochMillis = 1L, nextReviewAtEpochMillis = 2L,
    )

    private fun plan(planId: String, profileId: String) = TodayPlanEntity(
        planId = planId, profileId = profileId, localDate = if (profileId == "p1") "2026-09-22" else "2026-09-23",
        zoneId = "UTC", activeWordBookId = "book", newTarget = 1, dueTarget = 0,
        ruleVersion = "r-1", generatedAtEpochMillis = 1L,
    )

    private fun article(articleId: String, profileId: String) =
        com.example.englishlearning.reading.domain.Article(
            articleId = articleId, profileId = profileId, localDate = "2026-09-22", activeWordBookId = "book",
            articleType = com.example.englishlearning.reading.domain.ArticleType.STORY,
            lengthTier = com.example.englishlearning.reading.domain.ArticleLengthTier.STANDARD,
            version = 1, title = "A local story", englishText = "text", chineseText = "译文",
            generatedAtEpochMillis = 1L, coveredLemmas = emptyList(),
            source = com.example.englishlearning.reading.domain.ArticleSource.AiGenerated(modelName = "", parameterSummary = ""),
        )
}
