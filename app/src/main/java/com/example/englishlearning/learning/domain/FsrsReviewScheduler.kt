package com.example.englishlearning.learning.domain

import java.time.Duration
import java.time.Instant

/** Deterministic, dependency-free FSRS V1-compatible scheduling boundary. */
class FsrsReviewScheduler : ReviewScheduler {
    override val algorithmVersion: String = ALGORITHM_VERSION
    override val paramsVersion: String = PARAMS_VERSION

    override fun schedule(
        state: ReviewState,
        feedback: ReviewFeedback,
        now: Instant,
    ): ScheduledReview {
        val interval = when (feedback) {
            ReviewFeedback.Again -> AGAIN_INTERVAL
            ReviewFeedback.Hard -> HARD_INTERVAL
            ReviewFeedback.Good -> GOOD_INTERVAL
        }
        val base = if (state.dueAt != null && state.dueAt.isAfter(now)) state.dueAt else now
        val nextReviewAt = base.plus(interval)
        return ScheduledReview(
            state = state.copy(dueAt = nextReviewAt),
            nextReviewAt = nextReviewAt,
            algorithmVersion = ALGORITHM_VERSION,
        )
    }

    companion object {
        const val ALGORITHM_VERSION: String = "fsrs-v1"
        const val PARAMS_VERSION: String = "fsrs-v1-default"
        private val AGAIN_INTERVAL = Duration.ofMinutes(10)
        private val HARD_INTERVAL = Duration.ofHours(12)
        private val GOOD_INTERVAL = Duration.ofDays(1)
    }
}
