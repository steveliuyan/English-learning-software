package com.example.englishlearning.learning.domain

import java.time.LocalDate

data class DailyLearningStats(
    val localDate: LocalDate,
    val reviewedWordCount: Int,
    val completedReadingCount: Int,
    val completedTaskCount: Int,
    val targetTaskCount: Int,
) {
    val completionRatio: Float
        get() = if (targetTaskCount <= 0) {
            0f
        } else {
            (completedTaskCount.toFloat() / targetTaskCount).coerceIn(0f, 1f)
        }
}
