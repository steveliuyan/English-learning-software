package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Internal metadata for explicitly versioned application schema changes. */
@Entity(tableName = "schema_meta")
data class SchemaMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
