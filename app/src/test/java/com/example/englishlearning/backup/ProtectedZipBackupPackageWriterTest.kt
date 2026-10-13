package com.example.englishlearning.backup

import com.example.englishlearning.backup.domain.BackupCryptoPolicy
import com.example.englishlearning.backup.domain.BackupPackageValidator
import com.example.englishlearning.backup.domain.BackupPasswordKdf
import com.example.englishlearning.backup.domain.PackageValidationFailure
import com.example.englishlearning.backup.domain.PackageValidationResult
import com.example.englishlearning.backup.domain.PackageReadResult
import com.example.englishlearning.backup.domain.ProtectedPackageInputs
import com.example.englishlearning.backup.domain.BackupPackageFormat
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProtectedZipBackupPackageWriterTest {
    private val key: KeyPair = ecKeyPair()
    private val foreignKey: KeyPair = ecKeyPair()
    private val pub = key.public.encoded
    private val nonce = ByteArray(16) { (it + 1).toByte() }
    private val secret = ByteArray(16) { (it + 51).toByte() }
    private val issuedAt = 1_700_000_000L
    private val expiresAt = issuedAt + 1_800L

    private val profileJson = "{\"id\":\"p-1\"}".toByteArray(Charsets.UTF_8)

    private inner class FixedEntropy(private val pools: MutableList<ByteArray>) : com.example.englishlearning.backup.domain.BackupSecretEntropy {
        override fun nextBytes(count: Int): ByteArray {
            check(pools.isNotEmpty()) { "entropy exhausted" }
            val next = pools.removeAt(0)
            check(next.size == count)
            return next.copyOf()
        }
    }

    private inner class FakeKdf : BackupPasswordKdf {
        override fun deriveKeK(password: CharArray, salt: ByteArray): ByteArray? {
            if (password.size < BackupCryptoPolicy.PASSWORD_MIN_CHARS) return null
            return ByteArray(BackupCryptoPolicy.KDF_OUTPUT_BYTES) { (salt[it % salt.size] + it).toByte() }
        }
    }

    /** Deterministic fake: storage = iv(12, derived from aad) || plaintext || tag(16, derived from key+aad). */
    private inner class FakeAead : com.example.englishlearning.backup.domain.BackupAead {
        override fun encrypt(keyBytes: ByteArray, plaintext: ByteArray, additionalData: ByteArray): ByteArray =
            frame(keyBytes, additionalData) + plaintext

        override fun decrypt(keyBytes: ByteArray, storageBytes: ByteArray, additionalData: ByteArray): ByteArray? {
            val header = frame(keyBytes, additionalData)
            if (storageBytes.size <= header.size) return null
            if (!storageBytes.copyOfRange(0, header.size).contentEquals(header)) return null
            return storageBytes.copyOfRange(header.size, storageBytes.size)
        }

        private fun frame(keyBytes: ByteArray, aad: ByteArray): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256").digest(keyBytes + aad)
            return digest.copyOfRange(0, 12) + byteArrayOf(0) + digest.copyOfRange(0, 15)
        }
    }

    private fun signerFor(pair: KeyPair) = com.example.englishlearning.backup.domain.MigrationSigner { scope ->
        Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(scope)
            sign()
        }
    }

    private fun inputs() = ProtectedPackageInputs(pub, nonce, secret, issuedAt, expiresAt)

    private fun writer() = com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter()

    private fun entries() = listOf(
        com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Entry("records/profiles/profile-1.json", profileJson),
    )

    private fun writePlain(): ByteArray {
        val result = writer().write(entries(), 1_700_000_000_000L, protection = protection(password = null))
        return assertIs<com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Result.Written>(result).bytes
    }

    private fun protection(password: CharArray?) = com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Protection(
        inputs = inputs(),
        signer = signerFor(key),
        passwordChars = password,
        kdf = if (password != null) FakeKdf() else null,
        aead = if (password != null) FakeAead() else null,
        entropy = if (password != null) FixedEntropy(mutableListOf(ByteArray(16) { (it + 2).toByte() }, ByteArray(32) { (it + 3).toByte() })) else null,
    )

    @Test
    fun `plain v3 package validates with v3 statuses and pairing block`() {
        val bytes = writePlain()
        val valid = assertIs<PackageValidationResult.Valid>(BackupPackageValidator().validate(bytes))

        assertEquals(3, valid.manifest.formatVersion)
        assertEquals(BackupPackageFormat.SIGNATURE_STATUS_V3, valid.manifest.signatureStatus)
        assertEquals(BackupPackageFormat.AUTHENTICATION_STATUS_V3, valid.manifest.authenticationStatus)
        assertEquals(BackupPackageFormat.ENCRYPTION_STATUS_V3_NONE, valid.manifest.encryptionStatus)
        assertEquals(listOf("profiles"), valid.manifest.scope)
        assertEquals(listOf("records/profiles/profile-1.json"), valid.manifest.entries.map { it.path })
        assertEquals(nonce.toHexLower(), valid.manifest.pairing!!.nonceHex)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(secret).toHexLower(), valid.manifest.pairing!!.secretCommitmentSha256Hex)

        val read = assertIs<PackageReadResult.Read>(BackupPackageValidator().readEntries(bytes))
        assertTrue("pairing.json" in read.entries)
        assertContentEquals(profileJson, read.entries.getValue("records/profiles/profile-1.json"))
    }

    @Test
    fun `protected v3 package carries pinned encryption block and encrypted entries`() {
        val password = "correct horse battery".toCharArray()
        val result = writer().write(entries(), 1_700_000_000_000L, protection = protection(password))
        val bytes = assertIs<com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Result.Written>(result).bytes

        val valid = assertIs<PackageValidationResult.Valid>(BackupPackageValidator().validate(bytes))
        assertEquals(BackupPackageFormat.ENCRYPTION_STATUS_V3_AEAD, valid.manifest.encryptionStatus)
        val encryption = valid.manifest.encryption!!
        assertEquals(BackupCryptoPolicy.KDF_MEMORY_KIB, encryption.kdfMemoryKiB)
        assertEquals(BackupCryptoPolicy.KDF_ITERATIONS, encryption.kdfIterations)
        assertEquals(BackupCryptoPolicy.KDF_PARALLELISM, encryption.kdfParallelism)
        assertEquals(BackupCryptoPolicy.KDF_OUTPUT_BYTES, encryption.kdfOutputBytes)
        assertEquals(BackupCryptoPolicy.WRAPPED_DEK_BYTES, encryption.dekWrapCiphertextHex.decodeHexSize())

        val read = assertIs<PackageReadResult.Read>(BackupPackageValidator().readEntries(bytes))
        val stored = read.entries.getValue("records/profiles/profile-1.json")
        assertNotEquals(profileJson.toList(), stored.toList())
        assertTrue(stored.size.toLong() >= BackupCryptoPolicy.MIN_ENCRYPTED_ENTRY_BYTES)
    }

    @Test
    fun `password is zeroized by the writer after use`() {
        val password = "correct horse battery".toCharArray()
        writer().write(entries(), 1_700_000_000_000L, protection = protection(password))

        assertTrue(password.all { it == '\u0000' }, "writer must clear the supplied password array")
    }

    @Test
    fun `manifest serialization is deterministic for identical inputs`() {
        val first = unzip(writePlain())["manifest.json"]!!
        val second = unzip(writePlain())["manifest.json"]!!

        assertContentEquals(first, second)
    }

    @Test
    fun `missing signing key surfaces as signing key unavailable`() {
        val result = writer().write(
            entries(),
            1_700_000_000_000L,
            protection = com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Protection(
                inputs = inputs(),
                signer = com.example.englishlearning.backup.domain.MigrationSigner { null },
            ),
        )

        assertEquals(
            com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Result.Failed(
                com.example.englishlearning.backup.domain.ProtectedPackageWriteFailure.SigningKeyUnavailable,
            ),
            result,
        )
    }

    @Test
    fun `password shorter than pinned minimum is rejected and cleared`() {
        val password = "short12".toCharArray()
        val result = writer().write(entries(), 1_700_000_000_000L, protection = protection(password))

        assertEquals(
            com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Result.Failed(
                com.example.englishlearning.backup.domain.ProtectedPackageWriteFailure.PasswordRejected,
            ),
            result,
        )
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun `password without crypto ports fails closed before any output`() {
        val password = "correct horse battery".toCharArray()
        val bare = com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Protection(
            inputs = inputs(),
            signer = signerFor(key),
            passwordChars = password,
        )

        val result = writer().write(entries(), 1_700_000_000_000L, protection = bare)
        assertEquals(
            com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Result.Failed(
                com.example.englishlearning.backup.domain.ProtectedPackageWriteFailure.CryptoPortsMissing,
            ),
            result,
        )
    }

    @Test
    fun `unsafe path duplicate and limit violations reuse the shared package categories`() {
        val unsafe = writer().write(
            listOf(com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Entry("records/profiles/../evil.json", profileJson)),
            1_700_000_000_000L,
            protection = protection(password = null),
        )
        assertIs<com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Result.Failed>(unsafe)
            .let { assertEquals(PackageValidationFailure.InvalidPath, assertIs<com.example.englishlearning.backup.domain.ProtectedPackageWriteFailure.RejectedByValidator>(it.reason).reason) }

        val duplicate = writer().write(
            listOf(
                com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Entry("resources/audio", byteArrayOf(1)),
                com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Entry("resources/audio", byteArrayOf(2)),
            ),
            1_700_000_000_000L,
            protection = protection(password = null),
        )
        assertIs<com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter.Result.Failed>(duplicate)
            .let { assertEquals(PackageValidationFailure.DuplicateEntry, assertIs<com.example.englishlearning.backup.domain.ProtectedPackageWriteFailure.RejectedByValidator>(it.reason).reason) }
    }

    private fun ecKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    companion object ArchiveHelpers {
        fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
            val map = LinkedHashMap<String, ByteArray>()
            ZipInputStream(ByteArrayInputStreamFor(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val output = ByteArrayOutputStream()
                    zip.copyTo(output)
                    map[entry.name] = output.toByteArray()
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            return map
        }

        fun repack(entries: Map<String, ByteArray>): ByteArray {
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                entries.forEach { (path, bytes) ->
                    zip.putNextEntry(ZipEntry(path).also { it.time = 0L })
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            return output.toByteArray()
        }

        fun ByteArrayInputStreamFor(bytes: ByteArray) = java.io.ByteArrayInputStream(bytes)
    }

    private fun unzip(bytes: ByteArray, ignored: Int = 0): LinkedHashMap<String, ByteArray> = ArchiveHelpers.unzip(bytes)

    private fun repack(map: LinkedHashMap<String, ByteArray>): ByteArray = ArchiveHelpers.repack(map)
}

fun ByteArray.toHexLower(): String = joinToString("") { "%02x".format(it) }

fun String.decodeHexSize(): Int = run {
    require(length % 2 == 0)
    length / 2
}
