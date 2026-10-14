package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.DailyLearningHistory
import com.example.englishlearning.learning.domain.DailyLearningStats
import com.example.englishlearning.learning.domain.LearningTotals
import java.time.LocalDate

interface LearningStatsRepository {
    suspend fun today(profileId: String, localDate: LocalDate): Result<DailyLearningStats>

    suspend fun range(profileId: String, from: LocalDate, to: LocalDate): Result<List<DailyLearningStats>>

    /** F4-01 per-day expanded history for [from]..[to], ordered by date ascending. */
    suspend fun historyRange(profileId: String, from: LocalDate, to: LocalDate): Result<List<DailyLearningHistory>>

    /** F4-01 cumulative totals: learned-word count and DUE review-event count. */
    suspend fun totals(profileId: String): Result<LearningTotals>

    /** Consecutive unlocked days ending at [today] (or yesterday when today is not unlocked). */
    suspend fun streak(profileId: String, today: LocalDate): Result<Int>
}
