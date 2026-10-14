package com.example.englishlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.ArticleEntity
import com.example.englishlearning.core.storage.entity.LearningEventEntity
import com.example.englishlearning.core.storage.entity.ReadingCompletionEntity
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity
import com.example.englishlearning.core.storage.entity.WordBookEntity
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.domain.DailyLearningHistory
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
 * AC4-01 closed loop against the real Room database: after two learning days the history
 * exposes both days' plan, completion counts and article state, and writing day 2 never
 * rewrites day 1's expanded row. Also pins the SQL fast path
 * ([InternalLearningStatsDao.dailyDoneByTaskKind]) against the authoritative
 * [UnlockPolicy.progress] caliber, so the two calibers cannot drift apart.
 */
@RunWith(AndroidJUnit4::class)
class DailyHistoryAcrossDaysTest {
    private val profileId = "profile-1"
    private val wordBookId = "cet4"
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun secondDayWritesNeverRewriteFirstDayHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "daily-history-across-days-${System.nanoTime()}.db"
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
        try {
            database.internalWordBookDao().upsert(
                WordBookEntity(wordBookId, "四级", "基础", 12, "v1", "ngsl-nawl-1.2"),
            )
            val day1 = LocalDate.of(2026, 9, 20)
            val day2 = LocalDate.of(2026, 9, 21)

            seedPlan(database, day1, new = listOf("cet4:cat", "cet4:dog"), due = listOf("cet4:run"), ruleVersion = "v1")
            complete(database, "plan-$day1", "cet4:cat", 1_758_300_000_000L)
            complete(database, "plan-$day1", "cet4:dog", 1_758_300_100_000L)
            complete(database, "plan-$day1", "cet4:run", 1_758_300_200_000L)
            insertArticle(database, "art-day1", day1)
            database.internalReadingCompletionDao().insert(
                ReadingCompletionEntity("art-day1", profileId, day1.toString(), 1_758_300_300_000L),
            )

            val repository = RoomLearningStatsRepository(
                database,
                FixedClockProvider(Instant.parse("2026-09-21T02:00:00Z"), zone),
                Dispatchers.Unconfined,
            )

            val day1Before = repository.historyRange(profileId, day1, day1).getOrThrow().single()
            assertEquals("四级", day1Before.activeWordBookName)
            assertEquals(2, day1Before.newDone)
            assertEquals(1, day1Before.dueDone)
            assertEquals(UnlockMode.Strict, day1Before.unlockMode)
            assertTrue(day1Before.unlocked)
            assertTrue(day1Before.articleGenerated)
            assertTrue(day1Before.articleRead)

            seedPlan(database, day2, new = listOf("cet4:one"), due = listOf("cet4:two"), ruleVersion = "v1-relaxed")
            complete(database, "plan-$day2", "cet4:one", 1_758_386_400_000L)
            insertArticle(database, "art-day2", day2)

            val all = repository.historyRange(profileId, day1, day2).getOrThrow()
            assertEquals(listOf(day1, day2), all.map { it.localDate })
            assertEquals(
                "第二天写入不得改写第一天历史（AC4-01）",
                day1Before,
                all.first { it.localDate == day1 },
            )

            val day2History = all.first { it.localDate == day2 }
            assertEquals(UnlockMode.Relaxed, day2History.unlockMode)
            assertTrue("宽松模式完成新词即解锁", day2History.unlocked)
            assertEquals(1, day2History.newDone)
            assertEquals(0, day2History.dueDone)
            assertTrue(day2History.articleGenerated)
            assertFalse(day2History.articleRead)

            assertFastPathMatchesUnlockPolicy(database, repository, day1)
            assertFastPathMatchesUnlockPolicy(database, repository, day2)

            assertEquals(2, repository.streak(profileId, day2).getOrThrow())
            val totals = repository.totals(profileId).getOrThrow()
            assertEquals(4, totals.learnedWordCount)
            assertEquals(1, totals.reviewEventCount)
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(name)
        }
    }

    /** 对每一天：SQL 快路径展示数必须等于 UnlockPolicy 权威口径，两口径在此钉死。 */
    private suspend fun assertFastPathMatchesUnlockPolicy(
        database: AppDatabase,
        repository: RoomLearningStatsRepository,
        localDate: LocalDate,
    ) {
        val planId = "plan-$localDate"
        val tasks = database.internalTodayPlanDao().findTasks(planId)
        val planEntity = database.internalTodayPlanDao().findPlan(profileId, localDate.toString())!!
        val completed = database.internalLearningEventDao().completedCardIds(planId).toSet()
        val plan = TodayPlan(
            planId = planId,
            profileId = profileId,
            localDate = localDate,
            zoneId = planEntity.zoneId,
            activeWordBookId = planEntity.activeWordBookId,
            newTarget = planEntity.newTarget,
            dueTarget = planEntity.dueTarget,
            newCardIds = tasks.filter { it.taskKind == "NEW" }.map { it.cardId },
            dueCardIds = tasks.filter { it.taskKind == "DUE" }.map { it.cardId },
            ruleVersion = planEntity.ruleVersion,
            generatedAt = Instant.ofEpochMilli(planEntity.generatedAtEpochMillis),
        )
        val progress = UnlockPolicy.progress(plan, completed)
        val history: DailyLearningHistory = repository.historyRange(profileId, localDate, localDate).getOrThrow().single()
        assertEquals(progress.newDone, history.newDone)
        assertEquals(progress.dueDone, history.dueDone)
        assertEquals(
            UnlockPolicy.isUnlocked(plan, completed, UnlockPolicy.modeForRuleVersion(plan.ruleVersion)),
            history.unlocked,
        )
    }

    private suspend fun seedPlan(
        database: AppDatabase,
        localDate: LocalDate,
        new: List<String>,
        due: List<String>,
        ruleVersion: String,
    ) {
        val planId = "plan-$localDate"
        val tasks = new.mapIndexed { ordinal, cardId -> TodayPlanTaskEntity(planId, cardId, "NEW", ordinal) } +
            due.mapIndexed { ordinal, cardId -> TodayPlanTaskEntity(planId, cardId, "DUE", ordinal) }
        database.internalTodayPlanDao().insertIfAbsent(
            TodayPlanEntity(
                planId = planId,
                profileId = profileId,
                localDate = localDate.toString(),
                zoneId = zone.id,
                activeWordBookId = wordBookId,
                newTarget = new.size,
                dueTarget = due.size,
                ruleVersion = ruleVersion,
                generatedAtEpochMillis = localDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            ),
            tasks,
        )
    }

    private suspend fun complete(database: AppDatabase, planId: String, cardId: String, occurredAtEpochMillis: Long) {
        database.internalLearningEventDao().insertEventIfAbsent(
            LearningEventEntity(
                eventId = "ev-$planId-$cardId",
                profileId = profileId,
                planId = planId,
                cardId = cardId,
                wordBookId = wordBookId,
                feedback = "Known",
                occurredAtEpochMillis = occurredAtEpochMillis,
                algorithmVersion = "fsrs-4.5",
                paramsVersion = "params-1",
                dueBeforeEpochMillis = null,
                nextReviewAtEpochMillis = occurredAtEpochMillis + 86_400_000L,
            ),
        )
    }

    private suspend fun insertArticle(database: AppDatabase, articleId: String, localDate: LocalDate) {
        database.internalArticleDao().insert(
            ArticleEntity(
                articleId = articleId,
                profileId = profileId,
                localDate = localDate.toString(),
                activeWordBookId = wordBookId,
                articleType = "DAILY",
                lengthTier = "SHORT",
                version = 1,
                title = "A day",
                englishText = "The cat ran.",
                chineseText = "猫跑了。",
                generatedAtEpochMillis = localDate.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L,
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
    }
}
