package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "vocabulary_entries",
    primaryKeys = ["profileId", "wordBookId", "cardId"],
    indices = [
        Index(value = ["profileId", "addedAtEpochMillis"]),
        Index(value = ["profileId", "wordBookId", "addedAtEpochMillis"]),
    ],
)
data class VocabularyEntryEntity(
    val profileId: String,
    val wordBookId: String,
    val cardId: String,
    val addedAtEpochMillis: Long,
    val lastFeedback: String,
)
