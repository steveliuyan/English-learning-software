package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "vocabulary_search_history",
    primaryKeys = ["profileId", "normalizedQuery"],
    indices = [Index(value = ["profileId", "lastSearchedAtEpochMillis"])],
)
data class VocabularySearchHistoryEntity(
    val profileId: String,
    val normalizedQuery: String,
    val displayQuery: String,
    val searchCount: Int,
    val firstSearchedAtEpochMillis: Long,
    val lastSearchedAtEpochMillis: Long,
    val representativeWordBookId: String?,
    val representativeCardId: String?,
)
