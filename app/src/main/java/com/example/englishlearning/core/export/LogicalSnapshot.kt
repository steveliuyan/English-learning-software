package com.example.englishlearning.core.export

/**
 * Logical, database-independent data selected for a future backup export.
 * Credential material is intentionally excluded.
 */
data class LogicalSnapshot(
    val formatVersion: Int,
    val records: List<LogicalRecord>,
)

data class LogicalRecord(
    val type: String,
    val id: String,
    val attributes: Map<String, String>,
)

data class MediaManifestItem(
    val assetId: String,
    val sha256: String,
    val byteSize: Long,
)
