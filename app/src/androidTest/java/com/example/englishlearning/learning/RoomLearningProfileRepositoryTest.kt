package com.example.englishlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.ArticleEntity
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.CardReviewStateEntity
import com.example.englishlearning.core.storage.entity.LearningEventEntity
import com.example.englishlearning.core.storage.entity.ReadingCompletionEntity
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity
import com.example.englishlearning.core.storage.entity.VocabularyEntryEntity
import com.example.englishlearning.core.storage.entity.WordAiNoteEntity
import com.example.englishlearning.core.time.FixedClockProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room/SQLite behaviour must be proven on a device. The local unit test source set runs on
 * the JUnit 5 platform only — no JUnit 4 runner and no Robolectric registration exist there,
 * so `ApplicationProvider` cannot resolve and the test fails with "No instrumentation
 * registered". This file therefore lives in `androidTest`, like the other Room tests.
 */
@RunWith(AndroidJUnit4::class)
class RoomLearningProfileRepositoryTest {
    @Test
    fun savedActiveWordbookAndTargetSurviveDatabaseReopen(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "learning-profile-${System.nanoTime()}.db"
        val database = openDatabase(context, databaseName)
        val repository = RoomLearningProfileRepository(database, Dispatchers.Unconfined)
        val wordBook = WordBook("primary-school", "小学", "基础", 0, "v1", "ngsl-nawl-1.2")

        assertEquals(RepositoryResult.Success(Unit), repository.upsertWordBook(wordBook))
        assertEquals(
            RepositoryResult.Success(Unit),
            repository.save(LearningProfile("default", "primary-school", 10)),
        )
        database.close()

        val reopened = openDatabase(context, databaseName)
        assertEquals(
            RepositoryResult.Success(LearningProfile("default", "primary-school", 10)),
            RoomLearningProfileRepository(reopened, Dispatchers.Unconfined).current("default"),
        )
        reopened.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun closedDatabaseReturnsStableStorageError(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "closed-learning-profile-${System.nanoTime()}.db"
        val database = openDatabase(context, databaseName)
        database.openHelper.writableDatabase // force Room to actually open the connection
        val repository = RoomLearningProfileRepository(database, Dispatchers.Unconfined)
        database.close()

        assertEquals(
            RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable),
            repository.current("default"),
        )
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase(context: Context, name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()

    /**
     * F4-01 决策⑥（docs/decisions/2026-10-14-f4-01-history-stats-and-deletion-semantics.md）：
     * 删词书 = **软删词书行（deleted=1）+ 物理删词条与审计**，学习事件、复习状态、计划、
     * 文章、阅读记录、AI 笔记、资产登记一律零触碰——「删词书连带删学习历史」是对
     * AGENTS.md「词书各自维护独立进度、切换不删除旧记录」的违反，此测试把语义反转钉死。
     *
     * 展示侧（listWordBooks）过滤软删行，详展侧（historyRange/findById）仍能读出原册名，
     * 保证历史页与统计口径不因删除而失真。
     */
    @Test
    fun deleteWordBookSoftDeletesBookAndKeepsEveryLearningHistoryRow(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "wordbook-delete-semantics-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        try {
            val profileId = "default"
            val zone = ZoneId.of("Asia/Shanghai")
            val day = LocalDate.of(2026, 10, 10)
            val planId = "plan-$day"
            database.internalWordBookDao().upsert(
                com.example.englishlearning.core.storage.entity.WordBookEntity("imported", "导入册", "基础", 1, "v1", "test"),
            )
            database.internalWordBookDao().upsert(
                com.example.englishlearning.core.storage.entity.WordBookEntity("cet4", "四级", "基础", 1, "v1", "test"),
            )
            val repository = RoomLearningProfileRepository(database, Dispatchers.Unconfined)
            assertEquals(RepositoryResult.Success(Unit), repository.save(LearningProfile(profileId, "cet4", 10)))

            database.internalVocabularyEntryDao().upsert(VocabularyEntryEntity(profileId, "imported", "imported:cat", 1L, "Known"))
            database.internalTodayPlanDao().insertIfAbsent(
                TodayPlanEntity(
                    planId = planId,
                    profileId = profileId,
                    localDate = day.toString(),
                    zoneId = zone.id,
                    activeWordBookId = "imported",
                    newTarget = 1,
                    dueTarget = 1,
                    ruleVersion = "v1",
                    generatedAtEpochMillis = day.atStartOfDay(zone).toInstant().toEpochMilli(),
                ),
                listOf(
                    TodayPlanTaskEntity(planId, "imported:cat", "NEW", 0),
                    TodayPlanTaskEntity(planId, "imported:run", "DUE", 1),
                ),
            )
            database.internalLearningEventDao().insertEventIfAbsent(
                LearningEventEntity(
                    eventId = "ev-1",
                    profileId = profileId,
                    planId = planId,
                    cardId = "imported:cat",
                    wordBookId = "imported",
                    feedback = "Known",
                    occurredAtEpochMillis = 1_759_000_000_000L,
                    algorithmVersion = "fsrs-4.5",
                    paramsVersion = "params-1",
                    dueBeforeEpochMillis = null,
                    nextReviewAtEpochMillis = 1_759_086_400_000L,
                ),
            )
            database.internalLearningEventDao().insertEventIfAbsent(
                LearningEventEntity(
                    eventId = "ev-2",
                    profileId = profileId,
                    planId = planId,
                    cardId = "imported:run",
                    wordBookId = "imported",
                    feedback = "Known",
                    occurredAtEpochMillis = 1_759_000_100_000L,
                    algorithmVersion = "fsrs-4.5",
                    paramsVersion = "params-1",
                    dueBeforeEpochMillis = null,
                    nextReviewAtEpochMillis = 1_759_086_500_000L,
                ),
            )
            database.internalLearningEventDao().upsertCardState(
                CardReviewStateEntity("imported:cat", "imported", "Known", 1_759_000_000_000L, 1_759_086_400_000L),
            )
            database.internalArticleDao().insert(
                ArticleEntity(
                    articleId = "art-1",
                    profileId = profileId,
                    localDate = day.toString(),
                    activeWordBookId = "imported",
                    articleType = "DAILY",
                    lengthTier = "SHORT",
                    version = 1,
                    title = "A day",
                    englishText = "The cat ran.",
                    chineseText = "猫跑了。",
                    generatedAtEpochMillis = day.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L,
                    coveredLemmas = "[\"cat\"]",
                    parameterSummary = "seed",
                    modelName = null,
                    sourceType = "AI_GENERATED",
                    sourceId = "",
                    sourceDisplayName = "",
                    sourceUrl = "",
                    sourceLicenseNote = "",
                    sourceAttribution = "",
                ),
            )
            database.internalReadingCompletionDao().insert(ReadingCompletionEntity("art-1", profileId, day.toString(), 1_759_000_200_000L))
            database.internalWordAiNoteDao().insert(
                WordAiNoteEntity("note-1", profileId, "imported", "imported:cat", "cat", "EXAMPLE", "The cat sat.", 1_759_000_300_000L),
            )
            database.internalAssetDao().upsert(AssetRecordEntity("imported/audio-cat.mp3", "0".repeat(64), "imported/audio-cat.mp3", 11L, 1_759_000_400_000L))

            val result = repository.deleteWordBook(profileId, "imported")

            assertEquals(RepositoryResult.Success(Unit), result)

            assertEquals("词书行必须软删保留，不得物理删除", 1, rawCount(database, "SELECT COUNT(*) FROM word_books WHERE id = 'imported'"))
            assertEquals("软删标记必须置 1", 1, rawCount(database, "SELECT deleted FROM word_books WHERE id = 'imported'"))
            val visible = (repository.listWordBooks() as RepositoryResult.Success).value.map { it.id }
            assertFalse("列表不得再出现已删词书", visible.contains("imported"))
            assertEquals(listOf("cet4"), visible)
            val found = repository.findWordBook("imported")
            assertTrue("findWordBook 读全量：历史归属仍需原册", found is RepositoryResult.Success && found.value != null)

            assertEquals("学习事件零触碰", 2, rawCount(database, "SELECT COUNT(*) FROM learning_events"))
            assertEquals("复习状态零触碰", 1, rawCount(database, "SELECT COUNT(*) FROM card_review_states"))
            assertEquals("每日计划零触碰", 1, rawCount(database, "SELECT COUNT(*) FROM today_plans"))
            assertEquals("计划任务零触碰", 2, rawCount(database, "SELECT COUNT(*) FROM today_plan_tasks"))
            assertEquals("文章零触碰", 1, rawCount(database, "SELECT COUNT(*) FROM articles"))
            assertEquals("阅读完成记录零触碰", 1, rawCount(database, "SELECT COUNT(*) FROM reading_completions"))
            assertEquals("词 AI 笔记零触碰", 1, rawCount(database, "SELECT COUNT(*) FROM word_ai_notes"))
            assertEquals("资产登记零触碰", 1, rawCount(database, "SELECT COUNT(*) FROM asset_records"))
            assertEquals("词条本体物理删除", 0, rawCount(database, "SELECT COUNT(*) FROM vocabulary_entries WHERE wordBookId = 'imported'"))

            val stats = RoomLearningStatsRepository(
                database,
                FixedClockProvider(Instant.parse("2026-10-11T02:00:00Z"), zone),
                Dispatchers.Unconfined,
            )
            val history = stats.historyRange(profileId, day, day).getOrThrow().single()
            assertEquals("删除词书后历史仍归属原册名", "导入册", history.activeWordBookName)
            assertEquals(1, history.newDone)
            assertEquals(1, history.dueDone)
            assertTrue(history.unlocked)
            assertTrue(history.articleGenerated)
            assertTrue(history.articleRead)
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(name)
        }
    }

    /** 走原生 SQL 计数，绕开 Room DAO 的过滤语义——这里要钉的正是表里真实行数。 */
    private fun rawCount(database: AppDatabase, sql: String): Int =
        database.openHelper.writableDatabase.query(sql).use { cursor ->
            assertTrue("查询必须返回一行：$sql", cursor.moveToFirst())
            cursor.getInt(0)
        }
}
