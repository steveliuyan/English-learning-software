package com.example.englishlearning.backup.domain

import com.example.englishlearning.core.export.LogicalSnapshot
import com.example.englishlearning.core.export.MediaManifestItem

/**
 * Defines a future logical backup boundary without copying database files.
 *
 * A future protocol must use an old-device one-time challenge, integrity signing, and an
 * optional user password. Any verification failure must leave official data/正式数据 with zero writes.
 * Encryption, file formats, and restore behavior are intentionally not implemented here.
 */
interface BackupProvider {
    fun export(
        snapshot: LogicalSnapshot,
        media: List<MediaManifestItem>,
    ): BackupExport
}

data class BackupExport(
    val formatVersion: Int,
)
