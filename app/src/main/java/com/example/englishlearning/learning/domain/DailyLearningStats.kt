package com.example.englishlearning.learning.domain

import com.example.englishlearning.learning.UnlockMode
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

/**
 * Per-day expanded history for F4-01 (spec §F4-01 L15): the immutable plan snapshot of that
 * calendar day, its completion counts, the unlock rule derived from the snapshot's
 * [ruleVersion], and article generation/read state. Derived from authoritative tables only;
 * writing a new day never rewrites an older [DailyLearningHistory] (AC4-01).
 */
data class DailyLearningHistory(
    val localDate: LocalDate,
    val activeWordBookId: String,
    val activeWordBookName: String,
    val newDone: Int,
    val newTarget: Int,
    val dueDone: Int,
    val dueTarget: Int,
    val unlockMode: UnlockMode,
    val unlocked: Boolean,
    val articleGenerated: Boolean,
    val articleRead: Boolean,
)

/** F4-01 basic totals: distinct cards ever learned, and DUE-task feedback events, per profile. */
data class LearningTotals(val learnedWordCount: Int, val reviewEventCount: Int)

/**
 * Consecutive-day counter for F4-01 (spec L17): a day counts iff its plan snapshot's unlock
 * condition was met — reading the article is deliberately NOT required. Anchored on calendar
 * days: if today is not yet unlocked the chain starts at yesterday; a day without a snapshot
 * (or an unlocked=false entry) breaks the chain.
 */
fun computeStreak(unlockedByDate: Map<LocalDate, Boolean>, today: LocalDate): Int {
    var cursor = if (unlockedByDate[today] == true) today else today.minusDays(1)
    var count = 0
    while (unlockedByDate[cursor] == true) {
        count++
        cursor = cursor.minusDays(1)
    }
    return count
}
