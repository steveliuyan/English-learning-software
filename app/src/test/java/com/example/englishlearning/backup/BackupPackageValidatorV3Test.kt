package com.example.englishlearning.backup

import com.example.englishlearning.backup.domain.BackupAead
import com.example.englishlearning.backup.domain.BackupCryptoPolicy
import com.example.englishlearning.backup.domain.BackupPackageFormat
import com.example.englishlearning.backup.domain.BackupPackageValidator
import com.example.englishlearning.backup.domain.BackupPasswordKdf
import com.example.englishlearning.backup.domain.BackupSecretEntropy
import com.example.englishlearning.backup.domain.MigrationSigner
import com.example.englishlearning.backup.domain.PackageValidationFailure
import com.example.englishlearning.backup.domain.PackageValidationResult
import com.example.englishlearning.backup.domain.ProtectedPackageInputs
import com.example.englishlearning.backup.domain.ProtectedPackageWriteFailure
import com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter
import com.example.englishlearning.backup.infrastructure.ZipBackupPackageWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BackupPackageValidatorV3Test {
    private val key = ecKeyPair()
    private val foreignKey = ecKeyPair()
    private val nonce = ByteArray(16) { (it + 1).toByte() }
    private val secret = ByteArray(16) { (it + 51).toByte() }
    private val issuedAt = 1_700_000_000L
    private val expiresAt = issuedAt + 1_800L
    private val profileJson = "{\"id\":\"p-1\"}".toByteArray(Charsets.UTF_8)

    private fun ecKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun signer(pair: KeyPair) = MigrationSigner { scope ->
        Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(scope)
            sign()
        }
    }

    private class FixedEntropy : BackupSecretEntropy {
        override fun nextBytes(count: Int): ByteArray = ByteArray(count) { (it % 251).toByte() }
    }

    private class FakeKdf : BackupPasswordKdf {
        override fun deriveKeK(password: CharArray, salt: ByteArray): ByteArray =
            ByteArray(BackupCryptoPolicy.KDF_OUTPUT_BYTES) { (salt[it % salt.size] + it).toByte() }
    }

    private class FakeAead : BackupAead {
        override fun encrypt(keyBytes: ByteArray, plaintext: ByteArray, additionalData: ByteArray): ByteArray =
            header(keyBytes, additionalData) + plaintext

        override fun decrypt(keyBytes: ByteArray, storageBytes: ByteArray, additionalData: ByteArray): ByteArray? = null

        private fun header(keyBytes: ByteArray, aad: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(keyBytes + aad).copyOfRange(0, 28)
    }

    private fun protectedWrite(signerKey: KeyPair = key, password: CharArray? = null): ByteArray {
        val result = ProtectedZipBackupPackageWriter().write(
            entries = listOf(
                ProtectedZipBackupPackageWriter.Entry("records/profiles/profile-1.json", profileJson),
                ProtectedZipBackupPackageWriter.Entry("records/articles/a-1.json", "{\"article\":true}".toByteArray()),
            ),
            createdAtEpochMillis = 1_700_000_000_000L,
            protection = ProtectedZipBackupPackageWriter.Protection(
                inputs = ProtectedPackageInputs(signerKey.public.encoded, nonce, secret, issuedAt, expiresAt),
                signer = signer(signerKey),
                passwordChars = password,
                kdf = if (password != null) FakeKdf() else null,
                aead = if (password != null) FakeAead() else null,
                entropy = if (password != null) FixedEntropy() else null,
            ),
        )
        return when (result) {
            is ProtectedZipBackupPackageWriter.Result.Written -> result.bytes
            is ProtectedZipBackupPackageWriter.Result.Failed -> error("test fixture must build: ${result.reason}")
        }
    }

    private fun salvageWrite(): ByteArray {
        val result = ZipBackupPackageWriter().write(
            listOf(ZipBackupPackageWriter.Entry("records/profiles/profile-1.json", profileJson)),
            createdAtEpochMillis = 1_700_000_000_000L,
        )
        return assertIs<ZipBackupPackageWriter.Result.Written>(result).bytes
    }

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
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

    private fun repack(entries: Map<String, ByteArray>): ByteArray {
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

    private fun replaceInManifest(bytes: ByteArray, search: String, replacement: String): ByteArray {
        val entries = unzip(bytes)
        val manifest = String(entries.getValue("manifest.json"), Charsets.UTF_8)
        check(search.length == replacement.length) { "tampering must preserve byte lengths" }
        check(manifest.contains(search)) { "fixture missing '$search'" }
        entries["manifest.json"] = manifest.replaceFirst(search, replacement).toByteArray(Charsets.UTF_8)
        return repack(entries)
    }

    private fun failureFor(bytes: ByteArray): PackageValidationFailure {
        val result = BackupPackageValidator().validate(bytes)
        return assertIs<PackageValidationResult.Invalid>(result).reason
    }

    @Test
    fun `well-formed protected packages pass both plain and aead branches`() {
        assertIs<PackageValidationResult.Valid>(BackupPackageValidator().validate(protectedWrite()))
        assertIs<PackageValidationResult.Valid>(BackupPackageValidator().validate(protectedWrite(password = "correct horse battery".toCharArray())))
        assertIs<PackageValidationResult.Valid>(BackupPackageValidator().validate(salvageWrite()))
    }

    @Test
    fun `tampered manifest creation time breaks the signature gate`() {
        val tampered = replaceInManifest(protectedWrite(), "\"createdAtEpochMillis\":1700000000000", "\"createdAtEpochMillis\":1700000000001")

        assertEquals(PackageValidationFailure.SignatureInvalid, failureFor(tampered))
    }

    @Test
    fun `tampered payload byte fails the entry digest before pairing`() {
        val entries = unzip(protectedWrite())
        entries.updatePayload("records/articles/a-1.json", "{\"article\":tru2}".toByteArray(Charsets.UTF_8))

        assertEquals(PackageValidationFailure.IntegrityFailure, failureFor(repack(entries)))
    }

    @Test
    fun `tampered pairing record nonce is a pairing mismatch`() {
        val entries = unzip(protectedWrite())
        val pairing = String(entries.getValue("pairing.json"), Charsets.UTF_8)
        val flipped = pairing.replaceFirst("\"nonceHex\":\"" + nonce.toHexString(), "\"nonceHex\":\"" + nonce.copyOf().also { it[0] = (it[0] + 1).toByte() }.toHexString())
        entries["pairing.json"] = flipped.toByteArray(Charsets.UTF_8)

        assertEquals(PackageValidationFailure.PairingRecordMismatch, failureFor(repack(entries)))
    }

    @Test
    fun `signature produced by a foreign key is rejected`() {
        val honest = unzip(protectedWrite())
        val manifestBytes = honest.getValue("manifest.json")
        val scope = BackupCryptoPolicy.SIGNATURE_SCOPE_PREFIX.toByteArray(Charsets.UTF_8) + manifestBytes
        val foreignDer = signer(foreignKey).sign(scope)!!
        val pairing = String(honest.getValue("pairing.json"), Charsets.UTF_8)
        val originalDer = Regex("\"derSignatureHex\":\"([0-9a-f]+)\"").find(pairing)!!.groupValues[1]
        honest["pairing.json"] = pairing.replaceFirst(originalDer, foreignDer.toHexString()).toByteArray(Charsets.UTF_8)

        assertEquals(PackageValidationFailure.SignatureInvalid, failureFor(repack(honest)))
    }

    @Test
    fun `v3 package missing pairing json fails closed`() {
        val entries = unzip(protectedWrite())
        entries.remove("pairing.json")

        assertEquals(PackageValidationFailure.MissingPairingRecord, failureFor(repack(entries)))
    }

    @Test
    fun `v2 salvage package cannot smuggle a pairing entry`() {
        val entries = unzip(salvageWrite())
        entries["pairing.json"] = protectedWrite().let { unzip(it).getValue("pairing.json") }

        assertEquals(PackageValidationFailure.InvalidPath, failureFor(repack(entries)))
    }

    @Test
    fun `downgrading a v3 package label to v2 is rejected by the frozen v2 branch`() {
        val withRecord = replaceInManifest(protectedWrite(), "\"formatVersion\":3,", "\"formatVersion\":2,")
        assertEquals(PackageValidationFailure.InvalidPath, failureFor(withRecord))

        val manifestOnly = unzip(withRecord).apply { remove("pairing.json") }
        assertEquals(PackageValidationFailure.InvalidManifest, failureFor(repack(manifestOnly)))
    }

    @Test
    fun `unknown manifest field is rejected in v3`() {
        val entries = unzip(protectedWrite())
        val manifest = String(entries.getValue("manifest.json"), Charsets.UTF_8)
        entries["manifest.json"] = manifest.replaceFirst("\"payloadSha256\":", "\"unknownField\":1,\"payloadSha256\":").toByteArray(Charsets.UTF_8)

        assertEquals(PackageValidationFailure.InvalidManifest, failureFor(repack(entries)))
    }

    @Test
    fun `unknown and legacy format versions fail closed`() {
        assertEquals(PackageValidationFailure.UnsupportedFormat, failureFor(replaceInManifest(protectedWrite(), "\"formatVersion\":3,", "\"formatVersion\":4,")))
        assertEquals(PackageValidationFailure.UnsupportedFormat, failureFor(replaceInManifest(salvageWrite(), "\"formatVersion\":2,", "\"formatVersion\":1,")))
    }

    @Test
    fun `deviated argon2 memory parameter is rejected as unsupported crypto`() {
        val password = "correct horse battery".toCharArray()
        val tampered = replaceInManifest(protectedWrite(password = password), "\"kdfMemoryKiB\":65536", "\"kdfMemoryKiB\":65535")

        assertEquals(PackageValidationFailure.UnsupportedCryptoParameters, failureFor(tampered))
    }

    @Test
    fun `encryption block inside an unencrypted v3 package is rejected`() {
        val entries = unzip(protectedWrite())
        val manifest = String(entries.getValue("manifest.json"), Charsets.UTF_8)
        val block = "\"encryption\":{\"kdfAlgId\":\"argon2id\",\"kdfSaltHex\":\"00112233445566778899aabbccddeeff\",\"kdfMemoryKiB\":65536,\"kdfIterations\":3,\"kdfParallelism\":1,\"kdfOutputBytes\":32,\"dekWrapAlgId\":\"aes-256-gcm\",\"dekWrapIvHex\":\"00112233445566778899aabb\",\"dekWrapCiphertextHex\":\"" + "aa".repeat(60) + "\",\"entryAlgId\":\"aes-256-gcm\",\"entryIvBytes\":12,\"entryTagBytes\":16},"
        entries["manifest.json"] = manifest.replaceFirst("\"payloadSha256\":", block + "\"payloadSha256\":").toByteArray(Charsets.UTF_8)

        assertEquals(PackageValidationFailure.InvalidManifest, failureFor(repack(entries)))
    }

    @Test
    fun `pairing timestamps that deviate from the pinned ttl are rejected`() {
        val tampered = replaceInManifest(protectedWrite(), "\"expiresAtEpochSeconds\":1700001800", "\"expiresAtEpochSeconds\":1700001801")

        assertEquals(PackageValidationFailure.InvalidManifest, failureFor(tampered))
    }

    private fun LinkedHashMap<String, ByteArray>.updatePayload(path: String, bytes: ByteArray) {
        this[path] = bytes
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
