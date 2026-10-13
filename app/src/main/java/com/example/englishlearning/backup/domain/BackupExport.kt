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
    PasswordRejected,
    CryptoUnavailable,
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
    private class Collected(
        val entries: List<PackagerEntry>,
        val recordCount: Int,
        val mediaCount: Int,
        val uncompressedBytes: Long,
    )

    suspend fun prepare(includeMediaAssets: Boolean): BackupExportResult {
        val collected = collectEntries(includeMediaAssets)
        if (collected is CollectOutcome.Failed) return collected.result
        val data = (collected as CollectOutcome.Ok).value
        val createdAt = clock()
        return when (val packaging = packager.write(data.entries, createdAt)) {
            is PackagePackagingOutcome.Written -> BackupExportResult.Prepared(
                bytes = packaging.bytes,
                createdAtEpochMillis = createdAt,
                recordCount = data.recordCount,
                mediaCount = data.mediaCount,
                uncompressedBytes = data.uncompressedBytes,
            )
            is PackagePackagingOutcome.Invalid -> BackupExportResult.Failed(packaging.reason.toExportFailure())
        }
    }

    /**
     * Protected v3 export: same read-only snapshot, but packaged with the migration pairing block,
     * ECDSA signature and, when a backup password is supplied, Argon2id+AES-256-GCM protection.
     * The v2 salvage path above is untouched.
     */
    suspend fun prepareProtected(
        includeMediaAssets: Boolean,
        packager: ProtectedPackagePackager,
        protection: ProtectedPackagingSpec,
    ): BackupExportResult {
        val collected = collectEntries(includeMediaAssets)
        if (collected is CollectOutcome.Failed) return collected.result
        val data = (collected as CollectOutcome.Ok).value
        val createdAt = clock()
        return when (val packaging = packager.write(data.entries, createdAt, protection)) {
            is ProtectedPackagePackagingOutcome.Written -> BackupExportResult.Prepared(
                bytes = packaging.bytes,
                createdAtEpochMillis = createdAt,
                recordCount = data.recordCount,
                mediaCount = data.mediaCount,
                uncompressedBytes = data.uncompressedBytes,
            )
            is ProtectedPackagePackagingOutcome.Failed ->
                BackupExportResult.Failed(packaging.reason.toExportFailure())
        }
    }

    private sealed interface CollectOutcome {
        data class Ok(val value: Collected) : CollectOutcome
        data class Failed(val result: BackupExportResult.Failed) : CollectOutcome
    }

    private suspend fun collectEntries(includeMediaAssets: Boolean): CollectOutcome {
        val rows = rowReader.readAll()
        val records = BackupSnapshotBuilder.buildRecords(rows, includeMediaAssets)
        val entries = records.map { PackagerEntry(it.path, it.jsonBytes) }.toMutableList()
        var mediaCount = 0
        var mediaBytes = 0L

        if (includeMediaAssets) {
            for (asset in rows.assetRecords.sortedBy { it.id }) {
                if (!isSafePackageId(asset.id)) return CollectOutcome.Failed(BackupExportResult.Failed(BackupExportFailure.InvalidSnapshot))
                when (val outcome = mediaGateway.load(asset)) {
                    is BackupMediaOutcome.Loaded -> {
                        entries += PackagerEntry(mediaPackagePath(asset.id), outcome.bytes)
                        mediaCount += 1
                        mediaBytes += outcome.bytes.size.toLong()
                    }
                    BackupMediaOutcome.Missing -> return CollectOutcome.Failed(BackupExportResult.Failed(BackupExportFailure.ResourceUnavailable))
                    BackupMediaOutcome.Mismatch -> return CollectOutcome.Failed(BackupExportResult.Failed(BackupExportFailure.IntegrityFailure))
                }
            }
        }
        val recordBytes = records.sumOf { it.jsonBytes.size.toLong() }
        return CollectOutcome.Ok(
            Collected(
                entries = entries,
                recordCount = records.size,
                mediaCount = mediaCount,
                uncompressedBytes = recordBytes + mediaBytes,
            ),
        )
    }

    private fun PackageValidationFailure.toExportFailure(): BackupExportFailure = when (this) {
        PackageValidationFailure.LimitExceeded -> BackupExportFailure.LimitExceeded
        PackageValidationFailure.IntegrityFailure -> BackupExportFailure.IntegrityFailure
        PackageValidationFailure.SignatureInvalid -> BackupExportFailure.IntegrityFailure
        else -> BackupExportFailure.ArchiveRejected
    }

    private fun ProtectedPackageWriteFailure.toExportFailure(): BackupExportFailure = when (this) {
        ProtectedPackageWriteFailure.PasswordRejected -> BackupExportFailure.PasswordRejected
        is ProtectedPackageWriteFailure.RejectedByValidator -> reason.toExportFailure()
        ProtectedPackageWriteFailure.SigningKeyUnavailable,
        ProtectedPackageWriteFailure.CryptoPortsMissing,
        ProtectedPackageWriteFailure.CryptoOperationFailed,
        -> BackupExportFailure.CryptoUnavailable
    }
}
