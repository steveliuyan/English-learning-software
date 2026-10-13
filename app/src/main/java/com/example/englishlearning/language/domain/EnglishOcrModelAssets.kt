package com.example.englishlearning.language.domain

import java.io.File

/** On-demand English OCR model asset: status, explicit install with progress, and user-initiated removal. */
interface EnglishOcrModelAssets {
    suspend fun status(): OcrModelAssetStatus

    /** Caller-facing install entry; UI must disclose download size and license before invoking this. */
    suspend fun install(
        onProgress: (Float) -> Unit,
    ): OcrModelAssetInstallOutcome

    suspend fun uninstall()

    /** Verified local file, or null when nothing usable is installed. */
    fun installedFile(): File? = null
}

sealed interface OcrModelAssetStatus {
    data object NotInstalled : OcrModelAssetStatus

    data class Installed(val sizeBytes: Long) : OcrModelAssetStatus
}

sealed interface OcrModelAssetInstallOutcome {
    data class Installed(val sizeBytes: Long) : OcrModelAssetInstallOutcome

    data object IntegrityMismatch : OcrModelAssetInstallOutcome

    data class DownloadFailed(val reason: String?) : OcrModelAssetInstallOutcome
}
