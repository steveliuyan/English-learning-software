package com.example.englishlearning.backup.device

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.example.englishlearning.backup.pairing.MigrationKeyOutcome
import com.example.englishlearning.backup.pairing.MigrationSigningKey
import com.example.englishlearning.backup.domain.MigrationSigner
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.SignatureException
import java.security.spec.ECGenParameterSpec

/**
 * Device-side migration signing key (decision doc §3.1/§3.3): a non-exportable
 * AndroidKeyStore EC P-256 key pair created on first pairing after user
 * authentication. The private key never leaves the Keystore; every signing
 * operation goes through the platform `SHA256withECDSA`.
 *
 * - API 30+: `setUserAuthenticationParameters(30, BIOMETRIC_STRONG | DEVICE_CREDENTIAL)`.
 * - API 26–29: `setUserAuthenticationValidityDurationSeconds(30)`; the platform
 *   cannot restrict authenticator kinds in this range (recorded device-capability
 *   boundary); the UI still requires authentication before any signing.
 * - No device security lock screen ⇒ key generation throws ⇒ fixed
 *   [MigrationKeyOutcome.Unavailable] (never a bypass without authentication).
 */
object AndroidMigrationKey {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val KEY_ALIAS = "backup-migration-signer-p256"
    const val AUTH_WINDOW_SECONDS = 30

    fun ensureKey(): Boolean = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                builder.setUserAuthenticationParameters(
                    AUTH_WINDOW_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(AUTH_WINDOW_SECONDS)
            }
            generator.initialize(builder.build())
            generator.generateKeyPair()
        }
        true
    } catch (error: Exception) {
        Log.w("AndroidMigrationKey", "migration key unavailable", error)
        false
    }

    fun publicKeySpki(): ByteArray? = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getCertificate(KEY_ALIAS)?.publicKey?.encoded
    } catch (_: Exception) {
        null
    }
}

/** Read-only view for the pairing issuer; generates the key on first access. */
class AndroidMigrationSigningKey : MigrationSigningKey {
    override fun publicKeySpki(): MigrationKeyOutcome =
        if (AndroidMigrationKey.ensureKey()) {
            AndroidMigrationKey.publicKeySpki()
                ?.let { MigrationKeyOutcome.Available(it) }
                ?: MigrationKeyOutcome.Unavailable
        } else {
            MigrationKeyOutcome.Unavailable
        }
}

/** Signs with the Keystore key; the 30s authentication window gates every operation. */
class AndroidMigrationSigner : MigrationSigner {
    override fun sign(scopeBytes: ByteArray): ByteArray? = try {
        val keyStore = KeyStore.getInstance(AndroidMigrationKey.ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = keyStore.getKey(AndroidMigrationKey.KEY_ALIAS, null)
            as? java.security.PrivateKey ?: return null
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(scopeBytes)
            sign()
        }
    } catch (_: SignatureException) {
        null // user-authentication window closed / auth required
    } catch (_: Exception) {
        null
    }
}
