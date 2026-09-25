package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.DailyLearningStats
import java.time.LocalDate

interface LearningStatsRepository {
    fun today(profileId: String, localDate: LocalDate): Result<DailyLearningStats>

    fun range(profileId: String, from: LocalDate, to: LocalDate): Result<List<DailyLearningStats>>
}
