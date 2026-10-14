package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.dao.CompletedCardRow
import com.example.englishlearning.core.storage.dao.DateCountRow
import com.example.englishlearning.core.storage.dao.InternalLearningStatsDao
import com.example.englishlearning.core.storage.dao.InternalTodayPlanDao
import com.example.englishlearning.core.storage.dao.PlanRow
import com.example.englishlearning.core.storage.dao.PlanTaskRow
import com.example.englishlearning.core.storage.dao.TaskKindDoneRow
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.time.FixedClockProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RoomLearningStatsRepositoryTest {
    @Test
    fun `range aggregates profile scoped events and fills missing dates`() = runTest {
        val database = mockk<AppDatabase>()
        val stats = mockk<InternalLearningStatsDao>()
        val plans = mockk<InternalTodayPlanDao>()
        every { database.internalLearningStatsDao() } returns stats
        every { database.internalTodayPlanDao() } returns plans
        coEvery { stats.countReadings("a", "2026-09-25", "2026-09-27") } returns
            listOf(DateCountRow("2026-09-25", 2), DateCountRow("2026-09-27", 1))
        coEvery { stats.countReviewedCards("a", any(), any()) } returnsMany listOf(3, 0, 1)
        coEvery { plans.findPlan("a", any()) } returns null

        val result = RoomLearningStatsRepository(
            database,
            FixedClockProvider(Instant.parse("2026-09-25T00:00:00Z"), ZoneId.of("UTC")),
            Dispatchers.Unconfined,
        ).range("a", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27)).getOrThrow()

        assertEquals(listOf(2, 0, 1), result.map { it.completedReadingCount })
        assertEquals(listOf(3, 0, 1), result.map { it.reviewedWordCount })
        assertEquals(listOf(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 27)), result.map { it.localDate })
    }

    @Test
    fun `range rejects inverted dates without touching database`() = runTest {
        val database = mockk<AppDatabase>(relaxed = true)
        val result = RoomLearningStatsRepository(
            database,
            FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")),
            Dispatchers.Unconfined,
        ).range("a", LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 25))

        assertIs<IllegalArgumentException>(result.exceptionOrNull())
    }

    @Test
    fun `today maps database failure to storage unavailable`() = runTest {
        val database = mockk<AppDatabase>()
        every { database.internalLearningStatsDao() } throws IllegalStateException("private db detail")
        val result = RoomLearningStatsRepository(
            database,
            FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")),
            Dispatchers.Unconfined,
        ).today("a", LocalDate.of(2026, 9, 25))

        assertEquals(com.example.englishlearning.core.error.AppError.StorageUnavailable,
            assertIs<com.example.englishlearning.core.storage.AppErrorException>(result.exceptionOrNull()).appError)
    }

    private fun historyRepository(stats: InternalLearningStatsDao): RoomLearningStatsRepository {
        val database = mockk<AppDatabase>()
        every { database.internalLearningStatsDao() } returns stats
        return RoomLearningStatsRepository(
            database,
            FixedClockProvider(Instant.parse("2026-09-27T00:00:00Z"), ZoneId.of("UTC")),
            Dispatchers.Unconfined,
        )
    }

    @Test
    fun `historyRange returns detail fields and clamps fast-path done counts to targets`() = runTest {
        val stats = mockk<InternalLearningStatsDao>()
        coEvery { stats.planRows("a", "2026-09-25", "2026-09-26") } returns listOf(
            PlanRow("p25", "a", "2026-09-25", "UTC", "wb1", "考研核心词", 2, 1, "v1", 1L, true),
            PlanRow("p26", "a", "2026-09-26", "UTC", "wb1", "考研核心词", 1, 1, "v1-relaxed", 2L, true),
        )
        coEvery { stats.planTaskRowsForRange("a", any(), any()) } returns listOf(
            PlanTaskRow("p25", "wb1:cat", "NEW"),
            PlanTaskRow("p25", "wb1:dog", "NEW"),
            PlanTaskRow("p25", "wb1:run", "DUE"),
            PlanTaskRow("p26", "wb1:one", "NEW"),
            PlanTaskRow("p26", "wb1:two", "DUE"),
        )
        coEvery { stats.completedCardsForRange("a", any(), any()) } returns listOf(
            CompletedCardRow("p25", "wb1:cat"),
            CompletedCardRow("p25", "wb1:dog"),
            CompletedCardRow("p25", "wb1:run"),
            CompletedCardRow("p26", "wb1:one"),
        )
        coEvery { stats.dailyDoneByTaskKind("a", any(), any()) } returns listOf(
            TaskKindDoneRow("2026-09-25", "NEW", 2),
            TaskKindDoneRow("2026-09-25", "DUE", 1),
            TaskKindDoneRow("2026-09-26", "NEW", 3),
        )
        coEvery { stats.countReadings("a", any(), any()) } returns listOf(DateCountRow("2026-09-25", 1))

        val result = historyRepository(stats)
            .historyRange("a", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26))
            .getOrThrow()

        assertEquals(2, result.size)
        val day25 = result[0]
        assertEquals(LocalDate.of(2026, 9, 25), day25.localDate)
        assertEquals("考研核心词", day25.activeWordBookName)
        assertEquals(2, day25.newDone)
        assertEquals(1, day25.dueDone)
        assertEquals(UnlockMode.Strict, day25.unlockMode, "无 -relaxed 后缀即严格模式")
        assertTrue(day25.unlocked, "严格模式下新词与复习全部完成必须解锁")
        assertTrue(day25.articleGenerated)
        assertTrue(day25.articleRead)

        val day26 = result[1]
        assertEquals(1, day26.newDone, "快路径 3 必须被钉回目标 1")
        assertEquals(0, day26.dueDone)
        assertEquals(UnlockMode.Relaxed, day26.unlockMode, "ruleVersion 的 -relaxed 后缀派生宽松模式")
        assertTrue(day26.unlocked, "宽松模式完成新词即解锁，到期复习仅保留待办")
        assertTrue(day26.articleGenerated)
        assertFalse(day26.articleRead)
    }

    @Test
    fun `totals returns learned-card and review-event counts`() = runTest {
        val stats = mockk<InternalLearningStatsDao>()
        coEvery { stats.totalLearnedCards("a") } returns 42
        coEvery { stats.totalReviewEvents("a") } returns 17

        val totals = historyRepository(stats).totals("a").getOrThrow()

        assertEquals(42, totals.learnedWordCount)
        assertEquals(17, totals.reviewEventCount)
    }

    @Test
    fun `streak follows snapshot unlock and ignores reading activity`() = runTest {
        val stats = mockk<InternalLearningStatsDao>()
        // 25 日严格模式全完成=解锁；26 日宽松模式完成新词=解锁；27 日复习未完成=未解锁。
        coEvery { stats.planRows("a", any(), any()) } returns listOf(
            PlanRow("p25", "a", "2026-09-25", "UTC", "wb1", "书", 1, 0, "v1", 1L, true),
            PlanRow("p26", "a", "2026-09-26", "UTC", "wb1", "书", 1, 1, "v1-relaxed", 2L, true),
            PlanRow("p27", "a", "2026-09-27", "UTC", "wb1", "书", 1, 1, "v1", 3L, true),
        )
        coEvery { stats.planTaskRowsForRange("a", any(), any()) } returns listOf(
            PlanTaskRow("p25", "wb1:x", "NEW"),
            PlanTaskRow("p26", "wb1:y", "NEW"),
            PlanTaskRow("p26", "wb1:t", "DUE"),
            PlanTaskRow("p27", "wb1:z", "NEW"),
            PlanTaskRow("p27", "wb1:u", "DUE"),
        )
        coEvery { stats.completedCardsForRange("a", any(), any()) } returns listOf(
            CompletedCardRow("p25", "wb1:x"),
            CompletedCardRow("p26", "wb1:y"),
            CompletedCardRow("p27", "wb1:z"),
        )
        coEvery { stats.dailyDoneByTaskKind("a", any(), any()) } returns listOf(
            TaskKindDoneRow("2026-09-25", "NEW", 1),
            TaskKindDoneRow("2026-09-26", "NEW", 1),
            TaskKindDoneRow("2026-09-27", "NEW", 1),
        )
        // 只有「未解锁的今日」有阅读记录：若实现错误地以阅读判定连续，结果将归零或错位。
        coEvery { stats.countReadings("a", any(), any()) } returns listOf(DateCountRow("2026-09-27", 1))

        val streak = historyRepository(stats).streak("a", LocalDate.of(2026, 9, 27)).getOrThrow()

        assertEquals(2, streak, "今日未解锁必须从昨日起算，且与阅读行为无关")
    }

    @Test
    fun `historyRange rejects inverted dates without touching database`() = runTest {
        val database = mockk<AppDatabase>(relaxed = true)
        val result = RoomLearningStatsRepository(
            database,
            FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")),
            Dispatchers.Unconfined,
        ).historyRange("a", LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 25))

        assertIs<IllegalArgumentException>(result.exceptionOrNull())
    }
}
