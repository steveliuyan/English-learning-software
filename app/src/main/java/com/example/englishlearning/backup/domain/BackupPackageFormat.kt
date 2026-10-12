package com.example.englishlearning.backup.domain

object BackupPackageFormat {
    const val CURRENT_VERSION: Int = 1
    val DefaultLimits = PackageLimits(
        maxArchiveBytes = BackupPackageLimits.MAX_ARCHIVE_BYTES,
        maxEntries = BackupPackageLimits.MAX_ENTRIES,
        maxEntryBytes = BackupPackageLimits.MAX_ENTRY_BYTES,
        maxTotalBytes = BackupPackageLimits.MAX_TOTAL_BYTES,
    )
}

data class PackageLimits(
    val maxArchiveBytes: Long,
    val maxEntries: Int,
    val maxEntryBytes: Long,
    val maxTotalBytes: Long,
)

object BackupPackageLimits {
    const val MAX_ARCHIVE_BYTES: Long = 268_435_456L
    const val MAX_ENTRIES: Int = 10_000
    const val MAX_ENTRY_BYTES: Long = 67_108_864L
    const val MAX_TOTAL_BYTES: Long = 1_073_741_824L
}

data class PackageEntry(
    val path: String,
    val byteSize: Long,
    val sha256: String,
)

data class PackageManifest(
    val formatVersion: Int,
    val entries: List<PackageEntry>,
    val payloadSha256: String,
)

sealed interface PackageValidationFailure {
    data object InvalidArchive : PackageValidationFailure
    data object InvalidManifest : PackageValidationFailure
    data object UnsupportedFormat : PackageValidationFailure
    data object InvalidPath : PackageValidationFailure
    data object DuplicateEntry : PackageValidationFailure
    data object LimitExceeded : PackageValidationFailure
    data object LengthMismatch : PackageValidationFailure
    data object IntegrityFailure : PackageValidationFailure
}

sealed interface PackageValidationResult {
    data class Valid(val manifest: PackageManifest) : PackageValidationResult
    data class Invalid(val reason: PackageValidationFailure) : PackageValidationResult
}

internal fun isAllowedPackagePath(path: String): Boolean {
    if (path == "manifest.json") return true
    val profilePrefix = "records/profile/"
    if (path.startsWith(profilePrefix) && path.endsWith(".json")) {
        return isSafeId(path.removePrefix(profilePrefix).removeSuffix(".json"))
    }
    val resourcePrefix = "resources/"
    if (path.startsWith(resourcePrefix)) {
        return isSafeId(path.removePrefix(resourcePrefix))
    }
    return false
}

private fun isSafeId(id: String): Boolean =
    id.isNotEmpty() && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
