package com.example.englishlearning.learning.domain

import java.time.Instant

/**
 * Immutable record of one effective feedback submission (spec F1-03).
 *
 * [eventId] is the idempotency key: replaying the same id must not add a second
 * completion nor adjust the interval again. Events are never updated or deleted,
 * they carry the algorithm/parameter versions and the state before and after the
 * scheduling decision so a later algorithm upgrade stays auditable.
 */
data class LearningEvent(
    val eventId: String,
    val profileId: String,
    val planId: String,
    val cardId: String,
    val wordBookId: String,
    val feedback: ReviewFeedback,
    val occurredAt: Instant,
    val algorithmVersion: String,
    val paramsVersion: String,
    val dueBefore: Instant?,
    val nextReviewAt: Instant,
)

/** Derived, replaceable view of a card's scheduling state (replay result, not authority). */
data class CardReviewState(
    val cardId: String,
    val wordBookId: String,
    val lastFeedback: ReviewFeedback,
    val lastReviewedAt: Instant,
    val nextReviewAt: Instant,
)
