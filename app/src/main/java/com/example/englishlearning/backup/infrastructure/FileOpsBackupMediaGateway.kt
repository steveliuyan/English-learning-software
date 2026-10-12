package com.example.englishlearning.backup.infrastructure

import com.example.englishlearning.backup.domain.BackupMediaGateway
import com.example.englishlearning.backup.domain.BackupMediaOutcome
import com.example.englishlearning.core.storage.FileOps
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import java.security.MessageDigest

/**
 * Loads AI audio bytes from the private media store, verifying size and
 * SHA-256 against the asset record before anything enters the export.
 * IO problems are reported as [BackupMediaOutcome.Missing] so the export
 * fails closed instead of shipping unverified bytes.
 */
class FileOpsBackupMediaGateway(
    private val files: FileOps,
) : BackupMediaGateway {
    override suspend fun load(asset: AssetRecordEntity): BackupMediaOutcome = try {
        if (!files.exists(asset.id)) {
            BackupMediaOutcome.Missing
        } else {
            val bytes = files.read(asset.id)
            when {
                bytes.size.toLong() != asset.byteSize -> BackupMediaOutcome.Mismatch
                !sha256Hex(bytes).equals(asset.sha256, ignoreCase = true) -> BackupMediaOutcome.Mismatch
                else -> BackupMediaOutcome.Loaded(bytes)
            }
        }
    } catch (_: Exception) {
        BackupMediaOutcome.Missing
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
