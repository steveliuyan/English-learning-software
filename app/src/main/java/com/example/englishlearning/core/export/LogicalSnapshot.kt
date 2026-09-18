package com.example.englishlearning.core.export

/**
 * Logical, database-independent data selected for a future backup export.
 * Credential material is intentionally excluded.
 */
data class LogicalSnapshot(
    val formatVersion: Int,
    val records: List<ExportProfileRecord>,
)

/** Explicit, non-sensitive profile metadata eligible for a future logical export. */
data class ExportProfileRecord(
    val profileId: String,
    val displayName: String,
    val createdAtEpochMillis: Long,
)

data class MediaManifestItem(
    val assetId: String,
    val sha256: String,
    val byteSize: Long,
)
