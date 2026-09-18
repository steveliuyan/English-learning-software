package com.example.englishlearning.learning.domain

import java.time.Instant

/** Boundary for the future FSRS-compatible scheduling implementation. */
interface ReviewScheduler {
    fun schedule(
        state: ReviewState,
        feedback: ReviewFeedback,
        now: Instant,
    ): ScheduledReview
}

data class ReviewState(
    val cardId: String,
    val dueAt: Instant?,
)

enum class ReviewFeedback {
    Again,
    Hard,
    Good,
}

data class ScheduledReview(
    val state: ReviewState,
    val nextReviewAt: Instant,
)
