package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Append-only learning event log (spec F1-03). [eventId] is the primary key so a
 * replayed submission is rejected by the database itself instead of by a
 * read-then-write check, which would race.
 *
 * Rows are never updated or deleted: `dueBeforeEpochMillis` and
 * `nextReviewAtEpochMillis` together capture the scheduling state before/after,
 * next to the algorithm and parameter versions that produced the decision.
 */
@Entity(
    tableName = "learning_events",
    primaryKeys = ["eventId"],
    indices = [Index(value = ["profileId", "cardId"]), Index(value = ["planId"])],
)
data class LearningEventEntity(
    val eventId: String,
    val profileId: String,
    val planId: String,
    val cardId: String,
    val wordBookId: String,
    val feedback: String,
    val occurredAtEpochMillis: Long,
    val algorithmVersion: String,
    val paramsVersion: String,
    val dueBeforeEpochMillis: Long?,
    val nextReviewAtEpochMillis: Long,
)
