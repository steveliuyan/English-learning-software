package com.example.englishlearning.backup.domain

object BackupPackageFormat {
    const val CURRENT_VERSION: Int = 2

    /** Honest protection status of v2 packages; F4-04 must replace these before restore is possible. */
    const val SIGNATURE_STATUS: String = "not-protected"
    const val AUTHENTICATION_STATUS: String = "not-supported"
    const val ENCRYPTION_STATUS: String = "none"
    const val MEDIA_SCOPE_TOKEN: String = "media"

    val DefaultLimits = PackageLimits(
        maxArchiveBytes = BackupPackageLimits.MAX_ARCHIVE_BYTES,
        maxEntries = BackupPackageLimits.MAX_ENTRIES,
        maxEntryBytes = BackupPackageLimits.MAX_ENTRY_BYTES,
        maxTotalBytes = BackupPackageLimits.MAX_TOTAL_BYTES,
    )
}

/** Stable record type names accepted in `records/<stable-type>/<stable-id>.json`. */
object BackupRecordTypes {
    val all: Set<String> = setOf(
        "articles",
        "asset-records",
        "card-review-states",
        "learning-events",
        "learning-profiles",
        "learning-settings",
        "migration-audits",
        "profiles",
        "reading-completions",
        "reading-preferences",
        "search-history",
        "today-plan-tasks",
        "today-plans",
        "vocabulary-entries",
        "word-ai-notes",
        "word-books",
    )

    fun isValidType(type: String): Boolean = type in all

    fun scopeTokenFor(type: String): String = type
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
    val createdAtEpochMillis: Long,
    val scope: List<String>,
    val signatureStatus: String,
    val authenticationStatus: String,
    val encryptionStatus: String,
    val entries: List<PackageEntry>,
    val payloadSha256: String,
)

fun recordPackagePath(type: String, stableId: String): String = "records/$type/$stableId.json"

fun mediaPackagePath(assetId: String): String = "resources/$assetId"

fun isSafePackageId(id: String): Boolean =
    id.isNotEmpty() && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }

/** Scope tokens derived from payload paths: present record types plus `media` when resources exist. */
fun derivePackageScope(paths: List<String>): List<String> {
    val tokens = paths
        .filter { it.startsWith("records/") }
        .map { it.removePrefix("records/").substringBefore("/") }
        .toMutableSet()
    if (paths.any { it.startsWith("resources/") }) tokens += BackupPackageFormat.MEDIA_SCOPE_TOKEN
    return tokens.sorted()
}

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
    if (path.startsWith("records/") && path.endsWith(".json")) {
        val segments = path.removePrefix("records/").removeSuffix(".json").split('/')
        return segments.size == 2 && BackupRecordTypes.isValidType(segments[0]) && isSafeId(segments[1])
    }
    val resourcePrefix = "resources/"
    if (path.startsWith(resourcePrefix)) {
        return isSafeId(path.removePrefix(resourcePrefix))
    }
    return false
}

internal fun isAllowedPayloadPath(path: String): Boolean =
    path != "manifest.json" && isAllowedPackagePath(path)

private fun isSafeId(id: String): Boolean =
    id.isNotEmpty() && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
