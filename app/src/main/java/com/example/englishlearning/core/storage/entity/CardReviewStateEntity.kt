package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Derived scheduling state per card, rebuilt from the event log (spec F1-03).
 * It is a cache for fast due-lookups, never the authority: `learning_events`
 * stays the source of truth so an algorithm upgrade can recompute future
 * `nextReviewAtEpochMillis` without rewriting history.
 *
 * The `(wordBookId, nextReviewAtEpochMillis)` index serves the due-card query that
 * builds a plan's `dueTarget`.
 */
@Entity(
    tableName = "card_review_states",
    primaryKeys = ["cardId"],
    indices = [Index(value = ["wordBookId", "nextReviewAtEpochMillis"])],
)
data class CardReviewStateEntity(
    val cardId: String,
    val wordBookId: String,
    val lastFeedback: String,
    val lastReviewedAtEpochMillis: Long,
    val nextReviewAtEpochMillis: Long,
)
