package com.example.englishlearning.backup.infrastructure

import com.example.englishlearning.backup.domain.BackupAead
import com.example.englishlearning.backup.domain.BackupCryptoPolicy
import com.example.englishlearning.backup.domain.BackupPasswordKdf
import com.example.englishlearning.backup.domain.BackupSecretEntropy
import com.example.englishlearning.backup.domain.MigrationSigner
import java.security.SecureRandom
import java.text.Normalizer

/**
 * Argon2id KEK derivation via the Bouncy Castle lightweight API (decision doc §4, C3):
 * only the pinned [BackupCryptoPolicy] parameters are ever used and the generator is never
 * registered as a JCE provider, which is the standard Android-safe BC usage.
 */
class BouncyCastleArgon2idKdf : BackupPasswordKdf {
    override fun deriveKeK(password: CharArray, salt: ByteArray): ByteArray? {
        if (!BackupCryptoPolicy.isPasswordShapeValid(password.size)) return null
        if (salt.size != BackupCryptoPolicy.KDF_SALT_BYTES) return null
        return try {
            val normalized = Normalizer.normalize(String(password), Normalizer.Form.NFKC).toCharArray()
            password.fill('\u0000')
            if (!BackupCryptoPolicy.isPasswordShapeValid(normalized.size)) {
                normalized.fill('\u0000')
                return null
            }
            val output = ByteArray(BackupCryptoPolicy.KDF_OUTPUT_BYTES)
            val parameters = org.bouncycastle.crypto.params.Argon2Parameters.Builder(
                org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_id,
            )
                .withVersion(org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(BackupCryptoPolicy.KDF_MEMORY_KIB)
                .withIterations(BackupCryptoPolicy.KDF_ITERATIONS)
                .withParallelism(BackupCryptoPolicy.KDF_PARALLELISM)
                .build()
            org.bouncycastle.crypto.generators.Argon2BytesGenerator().apply {
                init(parameters)
                generateBytes(normalized, output)
            }
            normalized.fill('\u0000')
            output
        } catch (_: Exception) {
            null
        } finally {
            password.fill('\u0000')
        }
    }
}

/** AES-256-GCM via Tink's `AesGcmJce`: storage bytes are `iv(12) || ct || tag(16)` with a fresh random IV per message. */
class TinkAes256GcmAead : BackupAead {
    override fun encrypt(key: ByteArray, plaintext: ByteArray, additionalData: ByteArray): ByteArray? = try {
        require(key.size == BackupCryptoPolicy.KEK_BYTES || key.size == BackupCryptoPolicy.DEK_BYTES)
        com.google.crypto.tink.subtle.AesGcmJce(key).encrypt(plaintext, additionalData)
    } catch (_: Exception) {
        null
    }

    override fun decrypt(key: ByteArray, storageBytes: ByteArray, additionalData: ByteArray): ByteArray? = try {
        require(key.size == BackupCryptoPolicy.KEK_BYTES || key.size == BackupCryptoPolicy.DEK_BYTES)
        com.google.crypto.tink.subtle.AesGcmJce(key).decrypt(storageBytes, additionalData)
    } catch (_: Exception) {
        null
    }
}

class SecureRandomBackupEntropy : BackupSecretEntropy {
    private val random = SecureRandom()

    override fun nextBytes(count: Int): ByteArray = ByteArray(count).also { random.nextBytes(it) }
}

/**
 * Platform `SHA256withECDSA` signer used by JVM tests; on device the production implementation signs
 * through the Android Keystore migration key behind user authentication (same scope bytes, same code path).
 */
class JavaSecurityMigrationSigner(private val privateKey: java.security.PrivateKey) : MigrationSigner {
    override fun sign(scopeBytes: ByteArray): ByteArray? = try {
        java.security.Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(scopeBytes)
            sign()
        }
    } catch (_: Exception) {
        null
    }
}
