package com.example.englishlearning.learning.domain

import java.time.Instant

/** Boundary for the future FSRS-compatible scheduling implementation. */
interface ReviewScheduler {
    val algorithmVersion: String
        get() = "v1"

    val paramsVersion: String
        get() = "v1"

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
    val algorithmVersion: String = "v1",
)
