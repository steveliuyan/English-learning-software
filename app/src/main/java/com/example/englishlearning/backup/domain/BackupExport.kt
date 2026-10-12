package com.example.englishlearning.backup.domain

import com.example.englishlearning.core.storage.entity.AssetRecordEntity

/** Read-only source of the whitelisted backup rows. Implementations must never write Room data. */
interface BackupRowReader {
    suspend fun readAll(): BackupSourceRows
}

sealed interface BackupMediaOutcome {
    data class Loaded(val bytes: ByteArray) : BackupMediaOutcome
    data object Missing : BackupMediaOutcome
    data object Mismatch : BackupMediaOutcome
}

/**
 * Reads AI audio bytes after verifying identity, size and SHA-256 against the
 * asset record. Mismatch must surface as [BackupMediaOutcome.Mismatch]; the
 * export fails closed instead of silently skipping media.
 */
interface BackupMediaGateway {
    suspend fun load(asset: AssetRecordEntity): BackupMediaOutcome
}

data class PackagerEntry(val path: String, val bytes: ByteArray)

sealed interface PackagePackagingOutcome {
    data class Written(val bytes: ByteArray) : PackagePackagingOutcome
    data class Invalid(val reason: PackageValidationFailure) : PackagePackagingOutcome
}

interface PackagePackager {
    fun write(entries: List<PackagerEntry>, createdAtEpochMillis: Long): PackagePackagingOutcome
}

/** Fixed, non-sensitive failure categories for the whole export flow. */
enum class BackupExportFailure {
    InvalidSnapshot,
    ResourceUnavailable,
    IntegrityFailure,
    LimitExceeded,
    ArchiveRejected,
    Cancelled,
    DestinationFailure,
}

sealed interface BackupExportResult {
    data class Prepared(
        val bytes: ByteArray,
        val createdAtEpochMillis: Long,
        val recordCount: Int,
        val mediaCount: Int,
        val uncompressedBytes: Long,
    ) : BackupExportResult

    data class Failed(val reason: BackupExportFailure) : BackupExportResult
}

/**
 * Assembles the F4-03 salvage package from read-only sources. This class never
 * touches Room, SAF, or the filesystem; failures carry fixed categories only.
 */
class BackupExportCoordinator(
    private val rowReader: BackupRowReader,
    private val mediaGateway: BackupMediaGateway,
    private val packager: PackagePackager,
    private val clock: () -> Long,
) {
    suspend fun prepare(includeMediaAssets: Boolean): BackupExportResult {
        val rows = rowReader.readAll()
        val records = BackupSnapshotBuilder.buildRecords(rows, includeMediaAssets)
        val entries = records.map { PackagerEntry(it.path, it.jsonBytes) }.toMutableList()
        var mediaCount = 0
        var mediaBytes = 0L

        if (includeMediaAssets) {
            for (asset in rows.assetRecords.sortedBy { it.id }) {
                if (!isSafePackageId(asset.id)) return BackupExportResult.Failed(BackupExportFailure.InvalidSnapshot)
                when (val outcome = mediaGateway.load(asset)) {
                    is BackupMediaOutcome.Loaded -> {
                        entries += PackagerEntry(mediaPackagePath(asset.id), outcome.bytes)
                        mediaCount += 1
                        mediaBytes += outcome.bytes.size.toLong()
                    }
                    BackupMediaOutcome.Missing -> return BackupExportResult.Failed(BackupExportFailure.ResourceUnavailable)
                    BackupMediaOutcome.Mismatch -> return BackupExportResult.Failed(BackupExportFailure.IntegrityFailure)
                }
            }
        }

        val createdAt = clock()
        val recordBytes = records.sumOf { it.jsonBytes.size.toLong() }
        return when (val packaging = packager.write(entries, createdAt)) {
            is PackagePackagingOutcome.Written -> BackupExportResult.Prepared(
                bytes = packaging.bytes,
                createdAtEpochMillis = createdAt,
                recordCount = records.size,
                mediaCount = mediaCount,
                uncompressedBytes = recordBytes + mediaBytes,
            )
            is PackagePackagingOutcome.Invalid -> BackupExportResult.Failed(packaging.reason.toExportFailure())
        }
    }

    private fun PackageValidationFailure.toExportFailure(): BackupExportFailure = when (this) {
        PackageValidationFailure.LimitExceeded -> BackupExportFailure.LimitExceeded
        PackageValidationFailure.IntegrityFailure -> BackupExportFailure.IntegrityFailure
        else -> BackupExportFailure.ArchiveRejected
    }
}
