package com.example.englishlearning.backup

import com.example.englishlearning.backup.domain.BackupPackageValidator
import com.example.englishlearning.backup.domain.PackageLimits
import com.example.englishlearning.backup.domain.PackageManifest
import com.example.englishlearning.backup.domain.PackageEntry
import com.example.englishlearning.backup.domain.PackageValidationFailure
import com.example.englishlearning.backup.domain.PackageValidationResult
import com.example.englishlearning.backup.domain.PackageReadResult
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BackupPackageValidatorTest {
    @Test
    fun `accepts version one manifest and safe profile path`() {
        val result = BackupPackageValidator().validate(packageBytes("records/profile/profile-1.json"))

        assertIs<PackageValidationResult.Valid>(result)
        assertEquals(1, result.manifest.formatVersion)
    }

    @Test
    fun `rejects traversal path`() {
        val result = BackupPackageValidator().validate(packageBytes("records/profile/../profile.json"))

        assertEquals(
            PackageValidationResult.Invalid(PackageValidationFailure.InvalidPath),
            result,
        )
    }

    @Test
    fun `rejects traversal path declared only in manifest`() {
        val result = BackupPackageValidator().validate(
            packageBytes("records/profile/../profile.json", includePayload = false),
        )

        assertEquals(
            PackageValidationResult.Invalid(PackageValidationFailure.InvalidPath),
            result,
        )
    }

    @Test
    fun `rejects unsupported version`() {
        val result = BackupPackageValidator().validate(
            packageBytes("records/profile/profile-1.json", formatVersion = 2),
        )

        assertEquals(
            PackageValidationResult.Invalid(PackageValidationFailure.UnsupportedFormat),
            result,
        )
    }

    @Test
    fun `rejects non-profile record type`() {
        val result = BackupPackageValidator().validate(packageBytes("records/secret/secret-1.json"))

        assertEquals(
            PackageValidationResult.Invalid(PackageValidationFailure.InvalidPath),
            result,
        )
    }

    @Test
    fun `rejects manifest declared path outside profile when payload is absent`() {
        val result = BackupPackageValidator().validate(packageBytes("records/secret/secret-1.json", includePayload = false))

        assertEquals(
            PackageValidationResult.Invalid(PackageValidationFailure.InvalidPath),
            result,
        )
    }

    @Test
    fun `rejects manifest declared traversal path when payload is absent`() {
        val result = BackupPackageValidator().validate(packageBytes("records/profile/../profile.json", includePayload = false))

        assertEquals(
            PackageValidationResult.Invalid(PackageValidationFailure.InvalidPath),
            result,
        )
    }

    @Test
    fun `rejects duplicate zip names`() {
        val bytes = archive(listOf("resources/a" to byteArrayOf(1), "resources/b" to byteArrayOf(1)))
        val duplicate = bytes.toMutableList()
        val from = "resources/b".toByteArray()
        val to = "resources/a".toByteArray()
        for (i in duplicate.indices) {
            if (i + from.size <= duplicate.size && from.indices.all { duplicate[i + it] == from[it] }) {
                to.indices.forEach { duplicate[i + it] = to[it] }
            }
        }
        assertInvalid(PackageValidationFailure.DuplicateEntry, BackupPackageValidator().validate(duplicate.toByteArray()))
    }

    @Test
    fun `rejects duplicate manifest paths`() {
        val bytes = archive(listOf("resources/a" to byteArrayOf(1, 2)), declaredPaths = listOf("resources/a", "resources/a"))
        assertInvalid(PackageValidationFailure.DuplicateEntry, BackupPackageValidator().validate(bytes))
    }

    @Test
    fun `rejects missing manifest`() {
        assertInvalid(PackageValidationFailure.InvalidManifest, BackupPackageValidator().validate(rawArchive(listOf("resources/a" to byteArrayOf(1)))))
    }

    @Test
    fun `rejects unknown root`() {
        assertInvalid(PackageValidationFailure.InvalidPath, BackupPackageValidator().validate(archive(listOf("unknown/a" to byteArrayOf(1)))))
    }

    @Test
    fun `rejects entry count over limit`() {
        val bytes = archive(listOf("resources/a" to byteArrayOf(1), "resources/b" to byteArrayOf(2)))
        assertInvalid(PackageValidationFailure.LimitExceeded, BackupPackageValidator(PackageLimits(4096, 1, 10, 10)).validate(bytes))
    }

    @Test
    fun `rejects per entry uncompressed size over limit`() {
        val bytes = archive(listOf("resources/a" to ByteArray(5)))
        assertInvalid(PackageValidationFailure.LimitExceeded, BackupPackageValidator(PackageLimits(4096, 10, 4, 10)).validate(bytes))
    }

    @Test
    fun `rejects aggregate uncompressed bytes over limit`() {
        val bytes = archive(listOf("resources/a" to ByteArray(3), "resources/b" to ByteArray(3)))
        assertInvalid(PackageValidationFailure.LimitExceeded, BackupPackageValidator(PackageLimits(4096, 10, 10, 5)).validate(bytes))
    }

    @Test
    fun `rejects archive bytes over limit`() {
        val bytes = archive(listOf("resources/a" to byteArrayOf(1)))
        assertInvalid(PackageValidationFailure.LimitExceeded, BackupPackageValidator(PackageLimits(bytes.size.toLong() - 1, 10, 10, 10)).validate(bytes))
    }

    @Test
    fun `rejects declared size mismatch`() {
        assertInvalid(PackageValidationFailure.LengthMismatch, BackupPackageValidator().validate(archive(listOf("resources/a" to byteArrayOf(1)), declaredSizeDelta = 1)))
    }

    @Test
    fun `rejects entry sha mismatch`() {
        assertInvalid(PackageValidationFailure.IntegrityFailure, BackupPackageValidator().validate(archive(listOf("resources/a" to byteArrayOf(1)), corruptEntryDigest = true)))
    }

    @Test
    fun `rejects payload digest mismatch`() {
        assertInvalid(PackageValidationFailure.IntegrityFailure, BackupPackageValidator().validate(archive(listOf("resources/a" to byteArrayOf(1)), corruptPayloadDigest = true)))
    }

    @Test
    fun `empty and short archives return invalid archive`() {
        for (size in 0..3) {
            assertInvalid(PackageValidationFailure.InvalidArchive, BackupPackageValidator().validate(ByteArray(size)))
        }
    }

    @Test
    fun `rejects malformed utf8 manifest`() {
        val malformedManifest = """{"formatVersion":1,"entries":[],"payloadSha256":""".toByteArray() +
            byteArrayOf(0xc3.toByte()) + """"}""".toByteArray()
        val bytes = rawArchive(listOf("manifest.json" to malformedManifest))

        assertInvalid(PackageValidationFailure.InvalidManifest, BackupPackageValidator().validate(bytes))
    }

    @Test
    fun `readEntries returns payload for valid archive`() {
        val expected = byteArrayOf(1, 2, 3)
        val result = BackupPackageValidator().readEntries(archive(listOf("resources/a" to expected)))

        assertIs<PackageReadResult.Read>(result)
        assertEquals(expected.toList(), result.entries.getValue("resources/a").toList())
    }

    @Test
    fun `readEntries rejects invalid archive`() {
        assertEquals(
            PackageReadResult.Invalid(PackageValidationFailure.InvalidArchive),
            BackupPackageValidator().readEntries(byteArrayOf(1, 2, 3)),
        )
    }

    @Test
    fun `rejects corrupted central directory signature`() {
        val bytes = archive(listOf("resources/a" to byteArrayOf(1)))
        val centralDirectory = findSignature(bytes, byteArrayOf(0x50, 0x4b, 0x01, 0x02))
        bytes[centralDirectory] = 0

        assertInvalid(PackageValidationFailure.InvalidArchive, BackupPackageValidator().validate(bytes))
    }

    @Test
    fun `rejects central directory name mismatch`() {
        val bytes = archive(listOf("resources/a" to byteArrayOf(1)))
        val centralDirectory = findSignature(bytes, byteArrayOf(0x50, 0x4b, 0x01, 0x02))
        val nameOffset = centralDirectory + 46
        bytes[nameOffset + "resources/".length] = 'b'.code.toByte()

        assertInvalid(PackageValidationFailure.InvalidArchive, BackupPackageValidator().validate(bytes))
    }

    @Test
    fun `rejects central directory entry count mismatch`() {
        val bytes = archive(listOf("resources/a" to byteArrayOf(1)))
        val endRecord = findSignature(bytes, byteArrayOf(0x50, 0x4b, 0x05, 0x06))
        bytes[endRecord + 10] = (bytes[endRecord + 10].toInt() + 1).toByte()

        assertInvalid(PackageValidationFailure.InvalidArchive, BackupPackageValidator().validate(bytes))
    }

    @Test
    fun `rejects truncated zip`() {
        val complete = archive(listOf("resources/a" to byteArrayOf(1)))
        assertInvalid(PackageValidationFailure.InvalidArchive, BackupPackageValidator().validate(complete.copyOf(complete.size - 8)))
    }

    @Test
    fun `rejects unknown major version`() {
        assertInvalid(PackageValidationFailure.UnsupportedFormat, BackupPackageValidator().validate(archive(listOf("resources/a" to byteArrayOf(1)), version = 65537)))
    }

    private fun assertInvalid(reason: PackageValidationFailure, result: PackageValidationResult) {
        assertEquals(PackageValidationResult.Invalid(reason), result)
    }

    private fun archive(
        payload: List<Pair<String, ByteArray>>,
        declaredPaths: List<String> = payload.map { it.first },
        declaredSizeDelta: Long = 0,
        corruptEntryDigest: Boolean = false,
        corruptPayloadDigest: Boolean = false,
        version: Int = 1,
    ): ByteArray {
        val entries = declaredPaths.mapIndexed { index, path ->
            val data = payload.getOrNull(index)?.second ?: byteArrayOf()
            PackageEntry(path, data.size.toLong() + declaredSizeDelta, sha256(data) + if (corruptEntryDigest) "x" else "")
        }
        val canonical = entries.sortedBy { it.path }.joinToString("") { "${it.path}\t${it.byteSize}\t${it.sha256}\n" }.toByteArray()
        val payloadSha = sha256(canonical) + if (corruptPayloadDigest) "x" else ""
        val manifest = ("""{"formatVersion":$version,"entries":[""" + entries.joinToString(",") {
            """{"path":"${it.path}","byteSize":${it.byteSize},"sha256":"${it.sha256}"}"""
        } + """],"payloadSha256":"$payloadSha"}""").toByteArray()
        return rawArchive(listOf("manifest.json" to manifest) + payload)
    }

    private fun rawArchive(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (path, data) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun findSignature(bytes: ByteArray, signature: ByteArray): Int =
        (0..bytes.size - signature.size).first { offset ->
            signature.indices.all { bytes[offset + it] == signature[it] }
        }

    private fun packageBytes(
        path: String,
        includePayload: Boolean = true,
        formatVersion: Int = 1,
    ): ByteArray = archive(
        payload = if (includePayload) listOf(path to "{}".toByteArray()) else emptyList(),
        declaredPaths = listOf(path),
        version = formatVersion,
    )
}
