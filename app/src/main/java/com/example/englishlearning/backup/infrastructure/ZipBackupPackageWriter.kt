package com.example.englishlearning.backup.infrastructure

import com.example.englishlearning.backup.domain.BackupPackageFormat
import com.example.englishlearning.backup.domain.BackupPackageValidator
import com.example.englishlearning.backup.domain.PackageLimits
import com.example.englishlearning.backup.domain.PackageValidationFailure
import com.example.englishlearning.backup.domain.PackageValidationResult
import com.example.englishlearning.backup.domain.derivePackageScope
import com.example.englishlearning.backup.domain.isAllowedPayloadPath
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipBackupPackageWriter(
    private val limits: PackageLimits = BackupPackageFormat.DefaultLimits,
) {
    data class Entry(val path: String, val bytes: ByteArray)

    sealed interface Result {
        data class Written(val bytes: ByteArray) : Result
        data class Invalid(val reason: PackageValidationFailure) : Result
    }

    fun write(entries: List<Entry>, createdAtEpochMillis: Long): Result {
        if (createdAtEpochMillis < 0L) return Result.Invalid(PackageValidationFailure.InvalidManifest)
        val sorted = entries.sortedBy { it.path }
        val seen = mutableSetOf<String>()
        var totalBytes = 0L
        val descriptors = mutableListOf<Descriptor>()
        for (entry in sorted) {
            if (!isAllowedPayloadPath(entry.path)) return Result.Invalid(PackageValidationFailure.InvalidPath)
            if (!seen.add(entry.path)) return Result.Invalid(PackageValidationFailure.DuplicateEntry)
            if (descriptors.size >= limits.maxEntries) return Result.Invalid(PackageValidationFailure.LimitExceeded)
            if (entry.bytes.size.toLong() > limits.maxEntryBytes) return Result.Invalid(PackageValidationFailure.LimitExceeded)
            totalBytes += entry.bytes.size.toLong()
            if (totalBytes > limits.maxTotalBytes) return Result.Invalid(PackageValidationFailure.LimitExceeded)
            descriptors += Descriptor(entry.path, entry.bytes, sha256(entry.bytes))
        }

        val canonical = descriptors.joinToString("") { "${it.path}\t${it.bytes.size}\t${it.sha256}\n" }
        val scope = derivePackageScope(descriptors.map { it.path })
        val manifest = buildString {
            append("{\"formatVersion\":")
            append(BackupPackageFormat.CURRENT_VERSION)
            append(",\"createdAtEpochMillis\":")
            append(createdAtEpochMillis)
            append(",\"scope\":[")
            scope.forEachIndexed { index, token ->
                if (index > 0) append(',')
                append('"').append(token).append('"')
            }
            append("],\"signatureStatus\":\"")
            append(BackupPackageFormat.SIGNATURE_STATUS)
            append("\",\"authenticationStatus\":\"")
            append(BackupPackageFormat.AUTHENTICATION_STATUS)
            append("\",\"encryptionStatus\":\"")
            append(BackupPackageFormat.ENCRYPTION_STATUS)
            append("\",\"entries\":[")
            descriptors.forEachIndexed { index, descriptor ->
                if (index > 0) append(',')
                append("{\"path\":\"").append(descriptor.path)
                append("\",\"byteSize\":").append(descriptor.bytes.size)
                append(",\"sha256\":\"").append(descriptor.sha256).append("\"}")
            }
            append("],\"payloadSha256\":\"")
            append(sha256(canonical.toByteArray(Charsets.UTF_8)))
            append("\"}")
        }.toByteArray(Charsets.UTF_8)
        if (manifest.size.toLong() > limits.maxEntryBytes) return Result.Invalid(PackageValidationFailure.LimitExceeded)

        return try {
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                put(zip, "manifest.json", manifest)
                descriptors.forEach { put(zip, it.path, it.bytes) }
            }
            val bytes = output.toByteArray()
            if (bytes.size.toLong() > limits.maxArchiveBytes) return Result.Invalid(PackageValidationFailure.LimitExceeded)
            when (val validation = BackupPackageValidator(limits).validate(bytes)) {
                is PackageValidationResult.Valid -> Result.Written(bytes)
                is PackageValidationResult.Invalid -> Result.Invalid(validation.reason)
            }
        } catch (_: Exception) {
            Result.Invalid(PackageValidationFailure.InvalidArchive)
        }
    }

    private fun put(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        ZipEntry(path).also { it.time = 0L }.let { entry ->
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class Descriptor(val path: String, val bytes: ByteArray, val sha256: String)
}
