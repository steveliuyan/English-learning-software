package com.example.englishlearning.backup

import com.example.englishlearning.backup.domain.BackupAead
import com.example.englishlearning.backup.domain.BackupCryptoPolicy
import com.example.englishlearning.backup.domain.BackupExportCoordinator
import com.example.englishlearning.backup.domain.BackupExportFailure
import com.example.englishlearning.backup.domain.BackupExportResult
import com.example.englishlearning.backup.domain.BackupMediaGateway
import com.example.englishlearning.backup.domain.BackupMediaOutcome
import com.example.englishlearning.backup.domain.BackupPackageFormat
import com.example.englishlearning.backup.domain.BackupPackageValidator
import com.example.englishlearning.backup.domain.BackupPasswordKdf
import com.example.englishlearning.backup.domain.BackupRowReader
import com.example.englishlearning.backup.domain.BackupSecretEntropy
import com.example.englishlearning.backup.domain.BackupSourceRows
import com.example.englishlearning.backup.domain.MigrationSigner
import com.example.englishlearning.backup.domain.PackageValidationResult
import com.example.englishlearning.backup.domain.ProtectedPackageInputs
import com.example.englishlearning.backup.domain.ProtectedPackagingSpec
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.LocalProfileEntity
import com.example.englishlearning.backup.infrastructure.ZipProtectedPackagePackager
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Protected export through the coordinator: same read-only snapshot as salvage, but packaged as a signed
 * v3 archive. Asserts the QR-only secret, KEK, DEK and password never appear anywhere in the bytes.
 */
class BackupExportProtectedCoordinatorTest {
    private val key = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val nonce = ByteArray(16) { (it + 11).toByte() }
    private val secret = ByteArray(16) { (it + 21).toByte() }
    private val salt = ByteArray(16) { (it + 31).toByte() }
    private val dek = ByteArray(32) { (it + 41).toByte() }
    private val issuedAt = 1_700_000_000L

    private fun emptyRows(): BackupSourceRows = BackupSourceRows(
        profiles = emptyList(),
        learningProfiles = emptyList(),
        learningSettings = emptyList(),
        todayPlans = emptyList(),
        todayPlanTasks = emptyList(),
        learningEvents = emptyList(),
        cardReviewStates = emptyList(),
        wordBooks = emptyList(),
        vocabularyEntries = emptyList(),
        migrationAudits = emptyList(),
        articles = emptyList(),
        readingCompletions = emptyList(),
        readingPreferences = emptyList(),
        searchHistory = emptyList(),
        wordAiNotes = emptyList(),
        photoTranslations = emptyList(),
        favorites = emptyList(),
        assetRecords = emptyList(),
    )

    private class FakeRows(private val asset: AssetRecordEntity) : BackupRowReader {
        override suspend fun readAll() = BackupSourceRows(
            profiles = listOf(LocalProfileEntity("p-1", "Amy", 1L)),
            learningProfiles = emptyList(),
            learningSettings = emptyList(),
            todayPlans = emptyList(),
            todayPlanTasks = emptyList(),
            learningEvents = emptyList(),
            cardReviewStates = emptyList(),
            wordBooks = emptyList(),
            vocabularyEntries = emptyList(),
            migrationAudits = emptyList(),
            articles = emptyList(),
            readingCompletions = emptyList(),
            readingPreferences = emptyList(),
            searchHistory = emptyList(),
            wordAiNotes = emptyList(),
            photoTranslations = emptyList(),
            favorites = emptyList(),
            assetRecords = listOf(asset),
        )
    }

    private class FakeMedia : BackupMediaGateway {
        override suspend fun load(asset: AssetRecordEntity): BackupMediaOutcome = BackupMediaOutcome.Loaded(byteArrayOf(7, 7, 7))
    }

    private inner class FakeEntropy : BackupSecretEntropy {
        private val pool = mutableListOf(salt.copyOf(), dek.copyOf())
        override fun nextBytes(count: Int): ByteArray = pool.removeAt(0)
    }

    private class FakeKdf : BackupPasswordKdf {
        var derived: ByteArray? = null
        override fun deriveKeK(password: CharArray, saltBytes: ByteArray): ByteArray {
            val kek = ByteArray(BackupCryptoPolicy.KDF_OUTPUT_BYTES) { (saltBytes[it % saltBytes.size] * 3 + it).toByte() }
            derived = kek
            password.fill('\u0000')
            return kek
        }
    }

    private class FakeAead : BackupAead {
        override fun encrypt(keyBytes: ByteArray, plaintext: ByteArray, additionalData: ByteArray): ByteArray =
            java.security.MessageDigest.getInstance("SHA-256").digest(keyBytes + additionalData).copyOfRange(0, 28) + plaintext

        override fun decrypt(keyBytes: ByteArray, storageBytes: ByteArray, additionalData: ByteArray): ByteArray? = null
    }

    private fun signer() = MigrationSigner { scope ->
        Signature.getInstance("SHA256withECDSA").run {
            initSign(key.private)
            update(scope)
            sign()
        }
    }

    private fun coordinator(asset: AssetRecordEntity) =
        BackupExportCoordinator(FakeRows(asset), FakeMedia(), com.example.englishlearning.backup.infrastructure.ZipPackagePackager()) { 1_700_000_000_000L }

    @Test
    fun `plain protected export produces a valid signed v3 package with honest counts`() = runTest {
        val asset = AssetRecordEntity("asset-1", "aa", "assets/asset-1", 3L, 1L)
        val result = coordinator(asset).prepareProtected(
            includeMediaAssets = true,
            packager = ZipProtectedPackagePackager(),
            protection = ProtectedPackagingSpec(
                inputs = ProtectedPackageInputs(key.public.encoded, nonce, secret, issuedAt, issuedAt + 1_800L),
                signer = signer(),
            ),
        )

        val prepared = assertIs<BackupExportResult.Prepared>(result)
        assertEquals(2, prepared.recordCount)
        assertEquals(1, prepared.mediaCount)
        assertEquals(BackupPackageFormat.PAIRED_VERSION, assertIs<PackageValidationResult.Valid>(BackupPackageValidator().validate(prepared.bytes)).manifest.formatVersion)
        assertTrue(prepared.uncompressedBytes > 0L)
    }

    @Test
    fun `protected package bytes never carry the qr secret kek dek or password`() = runTest {
        val asset = AssetRecordEntity("asset-1", "aa", "assets/asset-1", 3L, 1L)
        val password = "my backup passphrase".toCharArray()
        val kdf = FakeKdf()
        val result = coordinator(asset).prepareProtected(
            includeMediaAssets = true,
            packager = ZipProtectedPackagePackager(),
            protection = ProtectedPackagingSpec(
                inputs = ProtectedPackageInputs(key.public.encoded, nonce, secret, issuedAt, issuedAt + 1_800L),
                signer = signer(),
                passwordChars = password,
                kdf = kdf,
                aead = FakeAead(),
                entropy = FakeEntropy(),
            ),
        )

        val prepared = assertIs<BackupExportResult.Prepared>(result)
        val manifest = assertIs<PackageValidationResult.Valid>(BackupPackageValidator().validate(prepared.bytes)).manifest
        assertEquals(BackupPackageFormat.ENCRYPTION_STATUS_V3_AEAD, manifest.encryptionStatus)
        val text = String(prepared.bytes, Charsets.ISO_8859_1)
        val secretHex = secret.toHexString()
        val dekHex = dek.toHexString()
        val kekHex = kdf.derived!!.toHexString()

        assertTrue(secretHex !in text, "QR-only secret must not be in the package")
        assertTrue(secret.toList() !in slidingWindowsOf(prepared.bytes, 16), "raw secret bytes must not appear")
        assertTrue(dekHex !in text && kekHex !in text)
        assertTrue(dek.toList() !in slidingWindowsOf(prepared.bytes, 32), "raw DEK must not appear")
        assertTrue("my backup passphrase" !in text)
        assertTrue(password.all { it == '\u0000' })
        // The package still must be restorable with the real secret: commitment C is what ships.
        assertEquals(java.security.MessageDigest.getInstance("SHA-256").digest(secret).toHexString(), manifest.pairing!!.secretCommitmentSha256Hex)
    }

    @Test
    fun `rejected password and unavailable signing key map to fixed export categories`() = runTest {
        val asset = AssetRecordEntity("asset-1", "aa", "assets/asset-1", 3L, 1L)

        val passwordFailure = coordinator(asset).prepareProtected(
            includeMediaAssets = false,
            packager = ZipProtectedPackagePackager(),
            protection = ProtectedPackagingSpec(
                inputs = ProtectedPackageInputs(key.public.encoded, nonce, secret, issuedAt, issuedAt + 1_800L),
                signer = signer(),
                passwordChars = "short".toCharArray(),
                kdf = FakeKdf(),
                aead = FakeAead(),
                entropy = FakeEntropy(),
            ),
        )
        assertEquals(BackupExportFailure.PasswordRejected, assertIs<BackupExportResult.Failed>(passwordFailure).reason)

        val signerFailure = coordinator(asset).prepareProtected(
            includeMediaAssets = false,
            packager = ZipProtectedPackagePackager(),
            protection = ProtectedPackagingSpec(
                inputs = ProtectedPackageInputs(key.public.encoded, nonce, secret, issuedAt, issuedAt + 1_800L),
                signer = MigrationSigner { null },
            ),
        )
        assertEquals(BackupExportFailure.CryptoUnavailable, assertIs<BackupExportResult.Failed>(signerFailure).reason)
    }

    @Test
    fun `salvage prepare stays at version two unchanged next to the protected path`() = runTest {
        val asset = AssetRecordEntity("asset-1", "aa", "assets/asset-1", 3L, 1L)
        val prepared = assertIs<BackupExportResult.Prepared>(coordinator(asset).prepare(includeMediaAssets = false))

        val manifest = assertIs<PackageValidationResult.Valid>(BackupPackageValidator().validate(prepared.bytes)).manifest
        assertEquals(BackupPackageFormat.SALVAGE_VERSION, manifest.formatVersion)
        assertEquals(BackupPackageFormat.SIGNATURE_STATUS, manifest.signatureStatus)
    }

    private fun slidingWindowsOf(bytes: ByteArray, size: Int): Set<List<Byte>> =
        if (bytes.size < size) emptySet() else (0..bytes.size - size).map { bytes.copyOfRange(it, it + size).toList() }.toSet()

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
