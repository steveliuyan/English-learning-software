package com.example.englishlearning.learning.domain

/**
 * Deterministic V1 scheduler used until the FSRS implementation lands.
 *
 * It only produces the next review instant; every submission is recorded as an
 * immutable event by the caller, so replacing this class never rewrites history
 * (only future `nextReviewAt` values are recomputed).
 *
 * Rule: Again = 10 minutes, Hard = 1 day, Good = 3 days, all relative to `now`.
 * Ordering is guaranteed to satisfy AC1-03: Again <= Hard <= Good.
 */
class V1ReviewScheduler : ReviewScheduler {
    override fun schedule(
        state: ReviewState,
        feedback: ReviewFeedback,
        now: java.time.Instant,
    ): ScheduledReview {
        val interval = when (feedback) {
            ReviewFeedback.Again -> AGAIN_INTERVAL
            ReviewFeedback.Hard -> HARD_INTERVAL
            ReviewFeedback.Good -> GOOD_INTERVAL
        }
        val nextReviewAt = now.plus(interval)
        return ScheduledReview(state = state.copy(dueAt = nextReviewAt), nextReviewAt = nextReviewAt)
    }

    companion object {
        const val ALGORITHM_VERSION: String = "v1"
        const val PARAMS_VERSION: String = "v1"

        private val AGAIN_INTERVAL: java.time.Duration = java.time.Duration.ofMinutes(10)
        private val HARD_INTERVAL: java.time.Duration = java.time.Duration.ofDays(1)
        private val GOOD_INTERVAL: java.time.Duration = java.time.Duration.ofDays(3)
    }
}
