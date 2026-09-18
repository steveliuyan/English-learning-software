package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Auditable metadata only for a locally available word book.
 * Word entries and learning progress deliberately live in later-stage tables.
 */
@Entity(tableName = "word_books")
data class WordBookEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val level: String,
    val totalWords: Int,
    val dataVersion: String,
    val sourceId: String,
)
