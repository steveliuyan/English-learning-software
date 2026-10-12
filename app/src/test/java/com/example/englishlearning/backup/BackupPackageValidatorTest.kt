package com.example.englishlearning.backup

import com.example.englishlearning.backup.domain.BackupPackageValidator
import com.example.englishlearning.backup.domain.PackageLimits
import com.example.englishlearning.backup.domain.PackageManifest
import com.example.englishlearning.backup.domain.PackageEntry
import com.example.englishlearning.backup.domain.PackageValidationFailure
import com.example.englishlearning.backup.domain.PackageValidationResult
import com.example.englishlearning.backup.domain.PackageReadResult
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BackupPackageValidatorTest {
    @Test
    fun `accepts version two manifest and safe profiles path`() {
        val result = BackupPackageValidator().validate(packageBytes("records/profiles/profile-1.json"))

        assertIs<PackageValidationResult.Valid>(result)
        assertEquals(2, result.manifest.formatVersion)
    }

    @Test
    fun `version two manifest exposes creation time scope and protection status`() {
        val result = BackupPackageValidator().validate(
            packageBytes("records/profiles/profile-1.json", createdAt = 1_700_000_000_000L),
        )

        val valid = assertIs<PackageValidationResult.Valid>(result)
        assertEquals(1_700_000_000_000L, valid.manifest.createdAtEpochMillis)
        assertEquals(listOf("profiles"), valid.manifest.scope)
        assertEquals("not-protected", valid.manifest.signatureStatus)
        assertEquals("not-supported", valid.manifest.authenticationStatus)
        assertEquals("none", valid.manifest.encryptionStatus)
    }

    @Test
    fun `scope lists media only when resources are present`() {
        val withMedia = BackupPackageValidator().validate(
            packageBytes("records/profiles/profile-1.json", extraPayload = listOf("resources/audio" to byteArrayOf(9))),
        )
        assertEquals(listOf("media", "profiles"), assertIs<PackageValidationResult.Valid>(withMedia).manifest.scope)

        val withoutMedia = BackupPackageValidator().validate(packageBytes("records/articles/a-1.json"))
        assertEquals(listOf("articles"), assertIs<PackageValidationResult.Valid>(withoutMedia).manifest.scope)
    }

    @Test
    fun `accepts every whitelisted record type path`() {
        com.example.englishlearning.backup.domain.BackupRecordTypes.all.forEach { type ->
            val result = BackupPackageValidator().validate(packageBytes("records/$type/item-1.json"))
            assertIs<PackageValidationResult.Valid>(result, "type $type must be accepted")
        }
    }

    @Test
    fun `rejects traversal path`() {
        val result = BackupPackageValidator().validate(packageBytes("records/profiles/../profile.json"))

        assertEquals(
            PackageValidationResult.Invalid(PackageValidationFailure.InvalidPath),
            result,
        )
    }

    @Test
    fun `rejects traversal path declared only in manifest`() {
        val result = BackupPackageValidator().validate(packageBytes("records/profiles/../profile.json", includePayload = false))

        assertEquals(
            PackageValidationResult.Invalid(PackageValidationFailure.InvalidPath),
            result,
        )
    }

    @Test
    fun `rejects version one manifest`() {
        val result = BackupPackageValidator().validate(
            packageBytes("records/profiles/profile-1.json", formatVersion = 1),
        )

        assertEquals(
            PackageValidationResult.Invalid(PackageValidationFailure.UnsupportedFormat),
            result,
        )
    }

    @Test
    fun `rejects v1 shaped manifest without v2 fields`() {
        val manifest = """{"formatVersion":1,"entries":[],"payloadSha256":"${sha256(ByteArray(0))}"}""".toByteArray()

        assertInvalid(
            PackageValidationFailure.UnsupportedFormat,
            BackupPackageValidator().validate(rawArchive(listOf("manifest.json" to manifest))),
        )
    }

    @Test
    fun `rejects non-whitelisted record type`() {
        val result = BackupPackageValidator().validate(packageBytes("records/secret/secret-1.json"))

        assertEquals(
            PackageValidationResult.Invalid(PackageValidationFailure.InvalidPath),
            result,
        )
    }

    @Test
    fun `rejects manifest declared path outside whitelist when payload is absent`() {
        val result = BackupPackageValidator().validate(packageBytes("records/secret/secret-1.json", includePayload = false))

        assertEquals(
            PackageValidationResult.Invalid(PackageValidationFailure.InvalidPath),
            result,
        )
    }

    @Test
    fun `rejects manifest declared traversal path when payload is absent`() {
        val result = BackupPackageValidator().validate(packageBytes("records/profiles/../profile.json", includePayload = false))

        assertEquals(
            PackageValidationResult.Invalid(PackageValidationFailure.InvalidPath),
            result,
        )
    }

    @Test
    fun `rejects extra path segment inside record id`() {
        val result = BackupPackageValidator().validate(packageBytes("records/profiles/a/b.json"))

        assertEquals(
            PackageValidationResult.Invalid(PackageValidationFailure.InvalidPath),
            result,
        )
    }

    @Test
    fun `rejects falsified signature status claim`() {
        val bytes = archive(
            listOf("resources/a" to byteArrayOf(1)),
            signatureStatus = "signed",
        )

        assertInvalid(PackageValidationFailure.InvalidManifest, BackupPackageValidator().validate(bytes))
    }

    @Test
    fun `rejects falsified authentication status claim`() {
        val bytes = archive(listOf("resources/a" to byteArrayOf(1)), authenticationStatus = "paired")

        assertInvalid(PackageValidationFailure.InvalidManifest, BackupPackageValidator().validate(bytes))
    }

    @Test
    fun `rejects falsified encryption status claim`() {
        val bytes = archive(listOf("resources/a" to byteArrayOf(1)), encryptionStatus = "aes-gcm")

        assertInvalid(PackageValidationFailure.InvalidManifest, BackupPackageValidator().validate(bytes))
    }

    @Test
    fun `rejects scope missing a present record type`() {
        val bytes = archive(
            listOf("records/articles/a-1.json" to byteArrayOf(1)),
            scopeOverride = listOf("profiles"),
        )

        assertInvalid(PackageValidationFailure.InvalidManifest, BackupPackageValidator().validate(bytes))
    }

    @Test
    fun `rejects scope containing media without resources`() {
        val bytes = archive(
            listOf("records/articles/a-1.json" to byteArrayOf(1)),
            scopeOverride = listOf("articles", "media"),
        )

        assertInvalid(PackageValidationFailure.InvalidManifest, BackupPackageValidator().validate(bytes))
    }

    @Test
    fun `rejects unknown scope token`() {
        val bytes = archive(
            listOf("records/articles/a-1.json" to byteArrayOf(1)),
            scopeOverride = listOf("api-keys", "articles"),
        )

        assertInvalid(PackageValidationFailure.InvalidManifest, BackupPackageValidator().validate(bytes))
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
        val bytes = archive(
            listOf("resources/a" to byteArrayOf(1, 2)),
            declaredPaths = listOf("resources/a", "resources/a"),
            scopeOverride = listOf("media"),
        )
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
    fun `empty package with empty scope is valid`() {
        val bytes = archive(emptyList(), scopeOverride = emptyList())

        val valid = assertIs<PackageValidationResult.Valid>(BackupPackageValidator().validate(bytes))
        assertEquals(emptyList(), valid.manifest.entries)
        assertEquals(emptyList(), valid.manifest.scope)
    }

    @Test
    fun `rejects malformed utf8 manifest`() {
        val malformedManifest = """{"formatVersion":2,"createdAtEpochMillis":1,"scope":[],"signatureStatus":"not-protected","authenticationStatus":"not-supported","encryptionStatus":"none","entries":[],"payloadSha256":""".toByteArray() +
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

    private fun packageBytes(
        path: String,
        includePayload: Boolean = true,
        formatVersion: Int = 2,
        createdAt: Long = 1_700_000_000_000L,
        extraPayload: List<Pair<String, ByteArray>> = emptyList(),
    ): ByteArray {
        val payload = if (includePayload) listOf(path to byteArrayOf(7)) + extraPayload else emptyList()
        val declared = listOf(path) + extraPayload.map { it.first }
        return archive(payload, declaredPaths = declared, formatVersionOverride = formatVersion, createdAt = createdAt)
    }

    private fun archive(
        payload: List<Pair<String, ByteArray>>,
        declaredPaths: List<String> = payload.map { it.first },
        declaredSizeDelta: Long = 0,
        corruptEntryDigest: Boolean = false,
        corruptPayloadDigest: Boolean = false,
        version: Int = 2,
        formatVersionOverride: Int = version,
        createdAt: Long = 1_700_000_000_000L,
        scopeOverride: List<String>? = null,
        signatureStatus: String = "not-protected",
        authenticationStatus: String = "not-supported",
        encryptionStatus: String = "none",
    ): ByteArray {
        val entries = declaredPaths.mapIndexed { index, path ->
            val data = payload.getOrNull(index)?.second ?: byteArrayOf()
            PackageEntry(path, data.size.toLong() + declaredSizeDelta, sha256(data) + if (corruptEntryDigest) "x" else "")
        }
        val scope = scopeOverride ?: derivedScope(entries.map { it.path })
        val canonical = entries.sortedBy { it.path }.joinToString("") { "${it.path}\t${it.byteSize}\t${it.sha256}\n" }.toByteArray()
        val payloadSha = sha256(canonical) + if (corruptPayloadDigest) "x" else ""
        val scopeJson = scope.joinToString(",") { "\"$it\"" }
        val manifest = (
            """{"formatVersion":$formatVersionOverride,"createdAtEpochMillis":$createdAt,"scope":[""" + scopeJson + """],"signatureStatus":"$signatureStatus",""" +
                """"authenticationStatus":"$authenticationStatus","encryptionStatus":"$encryptionStatus","entries":[""" + entries.joinToString(",") {
                """{"path":"${it.path}","byteSize":${it.byteSize},"sha256":"${it.sha256}"}"""
            } + """],"payloadSha256":"$payloadSha"}"""
            ).toByteArray()
        return rawArchive(listOf("manifest.json" to manifest) + payload)
    }

    private fun derivedScope(paths: List<String>): List<String> {
        val types = paths.filter { it.startsWith("records/") }.map { it.removePrefix("records/").substringBefore("/") }.toSet()
        val tokens = types.toMutableSet()
        if (paths.any { it.startsWith("resources/") }) tokens += "media"
        return tokens.sorted()
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
}
