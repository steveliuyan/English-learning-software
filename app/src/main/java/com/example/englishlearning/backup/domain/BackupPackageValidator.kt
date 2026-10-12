package com.example.englishlearning.backup.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class BackupPackageValidator(
    private val limits: PackageLimits = BackupPackageFormat.DefaultLimits,
    private val json: Json = Json,
) {
    fun validate(bytes: ByteArray): PackageValidationResult {
        val snapshot = bytes.copyOf()
        if (snapshot.size.toLong() > limits.maxArchiveBytes) return invalid(PackageValidationFailure.LimitExceeded)
        if (snapshot.size < 22 || !hasEndRecord(snapshot)) return invalid(PackageValidationFailure.InvalidArchive)

        return try {
            ZipInputStream(ByteArrayInputStream(snapshot)).use { zip ->
                var manifest: PackageManifest? = null
                val seen = mutableSetOf<String>()
                val actual = linkedMapOf<String, PackageEntry>()
                var totalBytes = 0L
                val buffer = ByteArray(8192)
                var entry = zip.nextEntry
                while (entry != null) {
                    val path = entry.name
                    if (entry.isDirectory || !isAllowedPackagePath(path)) return invalid(PackageValidationFailure.InvalidPath)
                    val normalized = path.replace('\\', '/')
                    if (!seen.add(normalized)) return invalid(PackageValidationFailure.DuplicateEntry)
                    if (path != "manifest.json" && actual.size >= limits.maxEntries) {
                        return invalid(PackageValidationFailure.LimitExceeded)
                    }

                    val digest = MessageDigest.getInstance("SHA-256")
                    val content = if (path == "manifest.json") ByteArrayOutputStreamBounded(limits.maxEntryBytes) else null
                    var size = 0L
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > limits.maxEntryBytes) return invalid(PackageValidationFailure.LimitExceeded)
                        if (path != "manifest.json") {
                            totalBytes += count
                            if (totalBytes > limits.maxTotalBytes) return invalid(PackageValidationFailure.LimitExceeded)
                        }
                        digest.update(buffer, 0, count)
                        content?.write(buffer, 0, count)
                    }
                    zip.closeEntry()
                    val sha = digest.digest().toHex()
                    if (path == "manifest.json") {
                        if (manifest != null) return invalid(PackageValidationFailure.DuplicateEntry)
                        val manifestText = try {
                            Charsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(content!!.toByteArray()))
                                .toString()
                        } catch (_: CharacterCodingException) {
                            return invalid(PackageValidationFailure.InvalidManifest)
                        }
                        manifest = json.decodeFromString<ManifestDto>(manifestText).toModel()
                    } else {
                        actual[path] = PackageEntry(path, size, sha)
                        if (actual.size > limits.maxEntries) return invalid(PackageValidationFailure.LimitExceeded)
                    }
                    entry = zip.nextEntry
                }

                val parsed = manifest ?: return invalid(PackageValidationFailure.InvalidManifest)
                if (parsed.formatVersion != BackupPackageFormat.CURRENT_VERSION) return invalid(PackageValidationFailure.UnsupportedFormat)
                if (parsed.entries.any { !isAllowedPackagePath(it.path) || it.path == "manifest.json" }) return invalid(PackageValidationFailure.InvalidPath)
                if (parsed.entries.map { it.path }.toSet().size != parsed.entries.size) return invalid(PackageValidationFailure.DuplicateEntry)
                if (parsed.entries.size > limits.maxEntries) return invalid(PackageValidationFailure.LimitExceeded)
                if (parsed.entries.map { it.path }.toSet() != actual.keys) return invalid(PackageValidationFailure.LengthMismatch)

                for (declared in parsed.entries) {
                    val found = actual.getValue(declared.path)
                    if (declared.byteSize != found.byteSize) return invalid(PackageValidationFailure.LengthMismatch)
                    if (!declared.sha256.equals(found.sha256, ignoreCase = true)) return invalid(PackageValidationFailure.IntegrityFailure)
                }
                // Canonical digest input: path-sorted UTF-8 lines of path<TAB>size<TAB>lowercase SHA-256<LF>.
                val canonical = actual.values.sortedBy { it.path }
                    .joinToString("") { "${it.path}\t${it.byteSize}\t${it.sha256}\n" }.toByteArray(Charsets.UTF_8)
                if (!parsed.payloadSha256.equals(canonical.sha256(), ignoreCase = true)) return invalid(PackageValidationFailure.IntegrityFailure)
                if (!hasConsistentCentralDirectory(snapshot)) return invalid(PackageValidationFailure.InvalidArchive)
                PackageValidationResult.Valid(parsed)
            }
        } catch (_: SerializationException) {
            invalid(PackageValidationFailure.InvalidManifest)
        } catch (_: IllegalArgumentException) {
            invalid(PackageValidationFailure.InvalidManifest)
        } catch (_: Exception) {
            invalid(PackageValidationFailure.InvalidArchive)
        }
    }

    fun readEntries(bytes: ByteArray): PackageReadResult {
        val snapshot = bytes.copyOf()
        val validation = validate(snapshot)
        if (validation is PackageValidationResult.Invalid) return PackageReadResult.Invalid(validation.reason)
        return try {
            val entries = linkedMapOf<String, ByteArray>()
            var totalBytes = 0L
            ZipInputStream(ByteArrayInputStream(snapshot)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name != "manifest.json") {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var entryBytes = 0L
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalBytes += count
                            if (entryBytes > limits.maxEntryBytes || totalBytes > limits.maxTotalBytes) {
                                return PackageReadResult.Invalid(PackageValidationFailure.LimitExceeded)
                            }
                            output.write(buffer, 0, count)
                        }
                        entries[entry.name] = output.toByteArray()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            PackageReadResult.Read(entries)
        } catch (_: Exception) {
            PackageReadResult.Invalid(PackageValidationFailure.InvalidArchive)
        }
    }

    private fun hasEndRecord(bytes: ByteArray): Boolean = findEndRecord(bytes) != null

    private fun hasConsistentCentralDirectory(bytes: ByteArray): Boolean {
        val end = findEndRecord(bytes) ?: return false
        val disk = readShortLE(bytes, end + 4)
        val centralDisk = readShortLE(bytes, end + 6)
        val diskEntries = readShortLE(bytes, end + 8)
        val totalEntries = readShortLE(bytes, end + 10)
        val centralSize = readIntLE(bytes, end + 12).toLong() and 0xffff_ffffL
        val centralOffset = readIntLE(bytes, end + 16).toLong() and 0xffff_ffffL
        if (disk != 0 || centralDisk != 0 || diskEntries != totalEntries || totalEntries == 0xffff) return false
        if (centralSize == 0xffff_ffffL || centralOffset == 0xffff_ffffL) return false
        if (centralOffset + centralSize != end.toLong() || centralOffset > Int.MAX_VALUE) return false

        val names = mutableSetOf<String>()
        var cursor = centralOffset.toInt()
        val centralEnd = end
        repeat(totalEntries) {
            if (cursor < 0 || cursor + 46 > centralEnd || readIntLE(bytes, cursor) != 0x02014b50) return false
            val flags = readShortLE(bytes, cursor + 8)
            val method = readShortLE(bytes, cursor + 10)
            val crc = readIntLE(bytes, cursor + 16).toLong() and 0xffff_ffffL
            val compressedSize = readIntLE(bytes, cursor + 20).toLong() and 0xffff_ffffL
            val uncompressedSize = readIntLE(bytes, cursor + 24).toLong() and 0xffff_ffffL
            val nameLength = readShortLE(bytes, cursor + 28)
            val extraLength = readShortLE(bytes, cursor + 30)
            val commentLength = readShortLE(bytes, cursor + 32)
            val startDisk = readShortLE(bytes, cursor + 34)
            val localOffset = readIntLE(bytes, cursor + 42).toLong() and 0xffff_ffffL
            val recordEnd = cursor.toLong() + 46 + nameLength + extraLength + commentLength
            if (recordEnd > centralEnd || startDisk != 0 || localOffset >= centralOffset) return false
            if (compressedSize == 0xffff_ffffL || uncompressedSize == 0xffff_ffffL || localOffset == 0xffff_ffffL) return false
            if (flags and 0x0001 != 0 || method !in setOf(0, 8)) return false
            val nameBytes = bytes.copyOfRange(cursor + 46, cursor + 46 + nameLength)
            val name = decodeZipName(nameBytes, flags) ?: return false
            if (!names.add(name)) return false

            val local = localOffset.toInt()
            if (local + 30 > centralOffset || readIntLE(bytes, local) != 0x04034b50) return false
            val localFlags = readShortLE(bytes, local + 6)
            val localMethod = readShortLE(bytes, local + 8)
            val localCrc = readIntLE(bytes, local + 14).toLong() and 0xffff_ffffL
            val localCompressed = readIntLE(bytes, local + 18).toLong() and 0xffff_ffffL
            val localUncompressed = readIntLE(bytes, local + 22).toLong() and 0xffff_ffffL
            val localNameLength = readShortLE(bytes, local + 26)
            val localExtraLength = readShortLE(bytes, local + 28)
            val localData = local.toLong() + 30 + localNameLength + localExtraLength
            if (localData > centralOffset || hasZip64Extra(bytes, local + 30 + localNameLength, localExtraLength)) return false
            if (localFlags != flags || localMethod != method || localData + compressedSize > centralOffset) return false
            if (localNameLength != nameLength) return false
            val localName = decodeZipName(bytes.copyOfRange(local + 30, local + 30 + localNameLength), localFlags) ?: return false
            if (localName != name) return false
            val usesDescriptor = flags and 0x0008 != 0
            if (!usesDescriptor && (localCrc != crc || localCompressed != compressedSize || localUncompressed != uncompressedSize)) return false
            if (usesDescriptor && ((localCrc != 0L && localCrc != crc) || (localCompressed != 0L && localCompressed != compressedSize) || (localUncompressed != 0L && localUncompressed != uncompressedSize))) return false
            if (hasZip64Extra(bytes, cursor + 46 + nameLength, extraLength)) return false
            cursor = recordEnd.toInt()
        }
        return cursor == centralEnd && names.size == totalEntries
    }

    private fun findEndRecord(bytes: ByteArray): Int? {
        val minimum = (bytes.size - 22).coerceAtLeast(0)
        val maximum = (bytes.size - 22 - 0xffff).coerceAtLeast(0)
        for (offset in minimum downTo maximum) {
            if (readIntLE(bytes, offset) == 0x06054b50 && offset + 22 <= bytes.size) {
                val commentLength = readShortLE(bytes, offset + 20)
                if (offset + 22 + commentLength == bytes.size) return offset
            }
        }
        return null
    }

    private fun decodeZipName(bytes: ByteArray, flags: Int): String? = try {
        val charset = if (flags and 0x0800 != 0) Charsets.UTF_8 else Charsets.US_ASCII
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun hasZip64Extra(bytes: ByteArray, offset: Int, length: Int): Boolean {
        var cursor = offset
        val end = offset + length
        while (cursor + 4 <= end) {
            val id = readShortLE(bytes, cursor)
            val size = readShortLE(bytes, cursor + 2)
            if (cursor + 4 + size > end) return true
            if (id == 0x0001) return true
            cursor += 4 + size
        }
        return cursor != end
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun readShortLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun invalid(reason: PackageValidationFailure) = PackageValidationResult.Invalid(reason)

    private class ByteArrayOutputStreamBounded(private val maximum: Long) {
        private val output = java.io.ByteArrayOutputStream()
        fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (output.size().toLong() + length > maximum) throw IllegalArgumentException()
            output.write(bytes, offset, length)
        }
        fun toByteArray() = output.toByteArray()
    }

    @Serializable
    private data class ManifestDto(val formatVersion: Int, val entries: List<EntryDto>, val payloadSha256: String) {
        fun toModel() = PackageManifest(formatVersion, entries.map { it.toModel() }, payloadSha256)
    }

    @Serializable
    private data class EntryDto(val path: String, val byteSize: Long, val sha256: String) {
        fun toModel() = PackageEntry(path, byteSize, sha256)
    }
}

sealed interface PackageReadResult {
    data class Read(val entries: Map<String, ByteArray>) : PackageReadResult
    data class Invalid(val reason: PackageValidationFailure) : PackageReadResult
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
