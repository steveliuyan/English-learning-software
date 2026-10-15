package com.example.englishlearning.perf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.backup.device.RestoreEntityDecoder
import com.example.englishlearning.backup.device.RoomRestoreDataWriter
import com.example.englishlearning.backup.domain.BackupPackageValidator
import com.example.englishlearning.backup.domain.PackageValidationResult
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.RoomLearningEventRepository
import com.example.englishlearning.learning.RoomTodayPlanRepository
import com.example.englishlearning.learning.RoomVocabularySearchIndexRepository
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.ReviewFeedback
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SP-B 计划 Task 4：五项可自动化性能基线（第六项冷启动由 scripts/perf-baseline.sh 测）。
 * 每项走生产入口（仓储/验证器/恢复写入器），5 轮取中位数，打印 `PERF|<metric>|<medianMillis>|<runs>`。
 * 断言只设生存线（防挂死、防量纲级回归）；验收阈值按决策「实测中位数×2」在首轮数据出来后
 * 回填 `docs/verification/perf-baseline-2026-10.md`，不在这里预先拍脑袋。
 */
@RunWith(AndroidJUnit4::class)
class PerfSixMetricsInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun planReadMedian() {
        val name = SeedData.newDatabaseName("plan-read")
        val database = SeedData.newDatabase(context, name)
        try {
            runBlocking {
                SeedData.seedPlanWithTasks(database, taskCount = 400, localDate = LOCAL_DATE)
                val repository = RoomTodayPlanRepository(database, Dispatchers.IO)
                val median = SeedData.medianMillis {
                    val result = repository.find(SeedData.PROFILE_ID, LocalDate.parse(LOCAL_DATE))
                    assertTrue("计划读取应 Ready：$result", result is TodayPlanResult.Ready)
                    assertEquals(400, (result as TodayPlanResult.Ready).plan.newCardIds.size + result.plan.dueCardIds.size)
                }
                SeedData.report("plan_read", median, RUNS)
                assertWithinBaseline("plan_read", median)
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun cardPagingDueBatchMedian() {
        val name = SeedData.newDatabaseName("card-paging")
        val database = SeedData.newDatabase(context, name)
        try {
            runBlocking {
                val now = Instant.ofEpochMilli(1_700_000_000_000L)
                SeedData.seedReviewStates(database, stateCount = 1000, nowEpochMillis = now.toEpochMilli())
                val repository = RoomLearningEventRepository(database, Dispatchers.IO)
                val median = SeedData.medianMillis {
                    val result = repository.dueCardIds(SeedData.WORD_BOOK_ID, now)
                    val due = (result as RepositoryResult.Success).value
                    assertEquals("1000 个调度态中一半到期", 500, due.size)
                }
                SeedData.report("card_paging", median, RUNS)
                assertWithinBaseline("card_paging", median)
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun wordLookupMedian() {
        val name = SeedData.newDatabaseName("word-lookup")
        val database = SeedData.newDatabase(context, name)
        try {
            runBlocking {
                val repository = RoomVocabularySearchIndexRepository(database, Dispatchers.IO)
                val seed = repository.replaceWordBook(
                    SeedData.WORD_BOOK_ID, "Perf Book", "1", SeedData.wordCards(500),
                )
                assertTrue("索引构建应成功：$seed", seed is RepositoryResult.Success)
                val median = SeedData.medianMillis {
                    val result = repository.search("capword", limit = 30)
                    val hits = (result as RepositoryResult.Success).value
                    assertEquals("前缀命中应打满 limit", 30, hits.size)
                }
                SeedData.report("word_lookup", median, RUNS)
                assertWithinBaseline("word_lookup", median)
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun eventAppend100Median() {
        val name = SeedData.newDatabaseName("event-append")
        val database = SeedData.newDatabase(context, name)
        try {
            runBlocking {
                val repository = RoomLearningEventRepository(database, Dispatchers.IO)
                var ordinal = 0L
                val median = SeedData.medianMillis {
                    repeat(100) {
                        ordinal++
                        val event = learningEvent(ordinal)
                        val result = repository.append(event, reviewState(event))
                        assertTrue("反馈提交必须落库：$result", result is com.example.englishlearning.learning.AppendEventResult.Appended)
                    }
                }
                SeedData.report("event_append_100", median, RUNS)
                assertWithinBaseline("event_append_100", median)
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun backupValidate500Median() {
        val bytes = SeedData.backupPackageBytes(entryCount = 500)
        val validator = BackupPackageValidator()
        val warm = validator.validate(bytes)
        assertTrue("夹具必须产合法包：$warm", warm is PackageValidationResult.Valid)
        val median = runBlocking {
            SeedData.medianMillis {
                val result = validator.validate(bytes)
                assertTrue(result is PackageValidationResult.Valid)
            }
        }
        SeedData.report("backup_validate_500", median, RUNS)
        assertWithinBaseline("backup_validate_500", median)
    }

    @Test
    fun restoreWrite500Median() {
        val session = SeedData.restoreSession(recordCount = 500)
        val writer = RoomRestoreDataWriter(context, RestoreEntityDecoder())
        val workingDir = File(context.cacheDir, "perf-restore-${System.nanoTime()}")
        try {
            val median = runBlocking {
                SeedData.medianMillis {
                    val dir = File(workingDir, "run-${System.nanoTime()}")
                    val written = writer.writeReplacement(session, dir)
                    assertNotNull("恢复写入失败（decoder 契约漂移或磁盘异常）", written)
                    dir.deleteRecursively()
                }
            }
            SeedData.report("restore_write_500", median, RUNS)
            assertWithinBaseline("restore_write_500", median)
        } finally {
            workingDir.deleteRecursively()
        }
    }

    private fun assertWithinBaseline(metric: String, medianMillis: Long) {
        assertTrue("$metric 中位数 ${medianMillis}ms 超生存线 ${SeedData.SURVIVAL_LINE_MS}ms", medianMillis < SeedData.SURVIVAL_LINE_MS)
        val threshold = SeedData.thresholdMillis(metric)
        assertTrue(
            "$metric 中位数 ${medianMillis}ms 超基线阈值 ${threshold}ms（口径 docs/verification/perf-baseline-2026-10.md；测量规模变化后先复测再改阈值）",
            medianMillis <= threshold,
        )
    }

    private fun learningEvent(ordinal: Long) = LearningEvent(
        eventId = "perf-evt-$ordinal",
        profileId = SeedData.PROFILE_ID,
        planId = "perf-plan",
        cardId = "perf-append-card-$ordinal",
        wordBookId = SeedData.WORD_BOOK_ID,
        feedback = ReviewFeedback.Good,
        occurredAt = Instant.ofEpochMilli(1_700_000_000_000L + ordinal),
        algorithmVersion = "fsrs-1",
        paramsVersion = "perf-params",
        dueBefore = null,
        nextReviewAt = Instant.ofEpochMilli(1_700_000_000_000L + ordinal + 864_000_000L),
    )

    private fun reviewState(event: LearningEvent) = CardReviewState(
        cardId = event.cardId,
        wordBookId = event.wordBookId,
        lastFeedback = ReviewFeedback.Good,
        lastReviewedAt = event.occurredAt,
        nextReviewAt = event.nextReviewAt,
    )

    private companion object {
        const val RUNS = 5
        const val LOCAL_DATE = "2026-09-22"
    }
}
