package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Metadata for an application-private asset; its bytes are never stored in Room. */
@Entity(tableName = "asset_records")
data class AssetRecordEntity(
    @PrimaryKey val id: String,
    val sha256: String,
    val relativePath: String,
    val byteSize: Long,
    val createdAt: Long,
)
