package com.example.englishlearning.backup

import com.example.englishlearning.backup.domain.BackupCryptoPolicy
import com.example.englishlearning.backup.infrastructure.BouncyCastleArgon2idKdf
import com.example.englishlearning.backup.infrastructure.JavaSecurityMigrationSigner
import com.example.englishlearning.backup.infrastructure.TinkAes256GcmAead
import com.example.englishlearning.backup.pairing.JavaSecurityEcSignatureVerifier
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the approved library primitives (Tink AES-256-GCM, Bouncy Castle Argon2id lightweight API,
 * platform SHA256withECDSA) at the device-pinned parameters exactly as decision doc §4/§5 requires.
 */
class BackupCryptoImplementationsTest {
    private val aead = TinkAes256GcmAead()
    private val key = ByteArray(32) { (it * 7 + 1).toByte() }
    private val aad = "3|nonce|records/profiles/p.json".toByteArray(Charsets.UTF_8)

    @Test
    fun `tink aes-gcm round trips with aad and stores iv ct tag`() {
        val plaintext = "hello backup".toByteArray(Charsets.UTF_8)

        val storage = assertNotNull(aead.encrypt(key, plaintext, aad))
        assertEquals(BackupCryptoPolicy.AEAD_IV_BYTES + plaintext.size + BackupCryptoPolicy.AEAD_TAG_BYTES, storage.size)
        assertContentEquals(plaintext, aead.decrypt(key, storage, aad))
    }

    @Test
    fun `tink aes-gcm rejects wrong aad wrong key and tampered storage`() {
        val storage = assertNotNull(aead.encrypt(key, byteArrayOf(1, 2, 3, 4), aad))

        assertNull(aead.decrypt(key, storage, "other".toByteArray()))
        assertNull(aead.decrypt(ByteArray(32) { (it * 5 + 2).toByte() }, storage, aad))
        val flipped = storage.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertNull(aead.decrypt(key, flipped, aad))
    }

    @Test
    fun `tink aes-gcm uses a fresh random iv per message`() {
        val first = assertNotNull(aead.encrypt(key, byteArrayOf(9), aad))
        val second = assertNotNull(aead.encrypt(key, byteArrayOf(9), aad))

        assertTrue(!first.contentEquals(second), "same key+aad must still produce distinct iv-prefixed storage")
    }

    @Test
    fun `tink aes-gcm refuses keys that are not 32 bytes`() {
        assertNull(aead.encrypt(ByteArray(16), byteArrayOf(1), aad))
    }

    @Test
    fun `argon2id derives pinned length keys deterministically per salt at device parameters`() {
        val kdf = BouncyCastleArgon2idKdf()
        val salt = ByteArray(BackupCryptoPolicy.KDF_SALT_BYTES) { (it + 1).toByte() }
        val password = "correct horse battery".toCharArray()
        val otherSalt = ByteArray(BackupCryptoPolicy.KDF_SALT_BYTES) { (it + 2).toByte() }

        val first = assertNotNull(kdf.deriveKeK("correct horse battery".toCharArray(), salt))
        val second = assertNotNull(kdf.deriveKeK(password.copyOf(), salt))
        val differentSalt = assertNotNull(kdf.deriveKeK("correct horse battery".toCharArray(), otherSalt))

        assertEquals(BackupCryptoPolicy.KDF_OUTPUT_BYTES, first.size)
        assertContentEquals(first, second)
        assertTrue(!first.contentEquals(differentSalt))
    }

    @Test
    fun `argon2id clears the supplied password array and rejects out-of-shape input`() {
        val kdf = BouncyCastleArgon2idKdf()
        val password = "correct horse battery".toCharArray()
        val shortPassword = "short123".toCharArray()
        val salt = ByteArray(BackupCryptoPolicy.KDF_SALT_BYTES)

        assertNotNull(kdf.deriveKeK(password, salt))
        assertTrue(password.all { it == '\u0000' }, "KDF must zero the caller's password array")
        assertNull(kdf.deriveKeK(shortPassword, salt))
        assertNull(kdf.deriveKeK("correct horse battery".toCharArray(), ByteArray(8)))
    }

    @Test
    fun `platform signer output verifies through the platform verifier on identical scope bytes`() {
        val pair: KeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val scope = "elpair-v3/{\"formatVersion\":3}".toByteArray(Charsets.UTF_8)

        val der = assertNotNull(JavaSecurityMigrationSigner(pair.private).sign(scope))
        assertTrue(der.size <= 72, "P-256 DER signatures stay well inside the pinned cap")
        assertTrue(JavaSecurityEcSignatureVerifier().verify(pair.public.encoded, scope, der))
        assertTrue(!JavaSecurityEcSignatureVerifier().verify(pair.public.encoded, scope.copyOf().also { it[0] = (it[0] + 1).toByte() }, der))
    }

    @Test
    fun `secure random entropy source honors requested sizes`() {
        val entropy = com.example.englishlearning.backup.infrastructure.SecureRandomBackupEntropy()

        assertEquals(32, entropy.nextBytes(32).size)
        assertTrue(!MessageDigest.isEqual(entropy.nextBytes(16), entropy.nextBytes(16)))
    }
}
