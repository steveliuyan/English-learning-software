package com.example.englishlearning.core.storage.entity

import androidx.room.Entity

@Entity(
    tableName = "word_book_progress_migration_audits",
    primaryKeys = ["profileId", "sourceBookId", "targetBookId", "sourceCardId", "targetCardId"],
)
data class WordBookProgressMigrationAuditEntity(
    val profileId: String,
    val sourceBookId: String,
    val targetBookId: String,
    val sourceCardId: String,
    val targetCardId: String,
    val migratedAtEpochMillis: Long,
)
