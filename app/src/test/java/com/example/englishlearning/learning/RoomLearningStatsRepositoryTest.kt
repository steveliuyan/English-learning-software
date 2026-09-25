package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.dao.DateCountRow
import com.example.englishlearning.core.storage.dao.InternalLearningStatsDao
import com.example.englishlearning.core.storage.dao.InternalTodayPlanDao
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.time.FixedClockProvider
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
        every { stats.countReadings("a", "2026-09-25", "2026-09-27") } returns
            listOf(DateCountRow("2026-09-25", 2), DateCountRow("2026-09-27", 1))
        every { stats.countReviewedCards("a", any(), any()) } returnsMany listOf(3, 0, 1)
        every { plans.findPlan("a", any()) } returns null

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
}
