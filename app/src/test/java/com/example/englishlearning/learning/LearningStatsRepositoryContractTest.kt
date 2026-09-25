package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.DailyLearningStats
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFloatEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LearningStatsRepositoryContractTest {
    @Test
    fun `completion ratio is zero for zero target and clamped to unit interval`() {
        assertFloatEquals(0f, DailyLearningStats(LocalDate.of(2026, 9, 25), 0, 0, 0, 0).completionRatio)
        assertFloatEquals(0f, DailyLearningStats(LocalDate.of(2026, 9, 25), 0, 0, -1, 0).completionRatio)
        assertFloatEquals(1f, DailyLearningStats(LocalDate.of(2026, 9, 25), 0, 0, 8, 4).completionRatio)
        assertFloatEquals(0f, DailyLearningStats(LocalDate.of(2026, 9, 25), 0, 0, -1, 4).completionRatio)
        assertTrue(DailyLearningStats(LocalDate.of(2026, 9, 25), 0, 0, 1, 4).completionRatio in 0f..1f)
    }

    @Test
    fun `range contract returns ascending contiguous dates with zero values for missing days`() {
        val repository = FakeLearningStatsRepository(
            mapOf(
                "profile-a" to mapOf(
                    LocalDate.of(2026, 9, 25) to DailyLearningStats(LocalDate.of(2026, 9, 25), 3, 1, 2, 4),
                    LocalDate.of(2026, 9, 27) to DailyLearningStats(LocalDate.of(2026, 9, 27), 1, 0, 1, 2),
                ),
            ),
        )

        val result = repository.range("profile-a", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27)).getOrThrow()

        assertEquals(listOf(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 27)), result.map { it.localDate })
        assertEquals(DailyLearningStats(LocalDate.of(2026, 9, 26), 0, 0, 0, 0), result[1])
        assertEquals(emptyList<DailyLearningStats>(), repository.range("profile-b", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 25)).getOrThrow())
    }

    private class FakeLearningStatsRepository(
        private val stats: Map<String, Map<LocalDate, DailyLearningStats>>,
    ) : LearningStatsRepository {
        override fun today(profileId: String, localDate: LocalDate): Result<DailyLearningStats> =
            Result.success(stats[profileId]?.get(localDate) ?: DailyLearningStats(localDate, 0, 0, 0, 0))

        override fun range(profileId: String, from: LocalDate, to: LocalDate): Result<List<DailyLearningStats>> {
            if (from.isAfter(to)) return Result.failure(IllegalArgumentException("from must not be after to"))
            val profileStats = stats[profileId].orEmpty()
            return Result.success(generateSequence(from) { date -> date.plusDays(1).takeUnless { it.isAfter(to) } }
                .map { date -> profileStats[date] ?: DailyLearningStats(date, 0, 0, 0, 0) }
                .toList())
        }
    }
}
