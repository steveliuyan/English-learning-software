package com.example.englishlearning.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AndroidKeyStoreSecretStore(
    context: Context,
    private val keyStoreProvider: KeyStoreProvider = AndroidKeyStoreProvider,
) : SecretStore {
    private val ciphertextDirectory = File(context.filesDir, "secrets").also { it.mkdirs() }

    override fun save(
        reference: SecretReference,
        secret: CharArray,
    ): Result<Unit> =
        runCatching {
            val destination = ciphertextFile(reference)
            destination.delete()
            val key =
                try {
                    keyStoreProvider.keyFor(reference.alias)
                } catch (_: Throwable) {
                    throw AppErrorException(AppError.KeyStoreUnavailable)
                }
            try {
                val plaintext = secret.concatToString().encodeToByteArray()
                val encrypted =
                    Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
                        .let { cipher -> cipher.iv + cipher.doFinal(plaintext) }
                plaintext.fill(0)
                destination.outputStream().use { it.write(encrypted) }
            } catch (_: Throwable) {
                destination.delete()
                throw AppErrorException(AppError.KeyStoreUnavailable)
            } finally {
                secret.fill('\u0000')
            }
        }

    override fun delete(reference: SecretReference): Result<Unit> =
        runCatching {
            try {
                ciphertextFile(reference).delete()
                keyStoreProvider.delete(reference.alias)
            } catch (_: Throwable) {
                throw AppErrorException(AppError.KeyStoreUnavailable)
            }
        }

    override fun has(reference: SecretReference): Result<Boolean> =
        runCatching {
            try {
                ciphertextFile(reference).isFile && keyStoreProvider.contains(reference.alias)
            } catch (_: Throwable) {
                throw AppErrorException(AppError.KeyStoreUnavailable)
            }
        }

    private fun ciphertextFile(reference: SecretReference): File =
        File(ciphertextDirectory, reference.alias.encodeToByteArray().joinToString("") { "%02x".format(it) })

    interface KeyStoreProvider {
        fun keyFor(alias: String): SecretKey

        fun contains(alias: String): Boolean

        fun delete(alias: String)
    }

    private object AndroidKeyStoreProvider : KeyStoreProvider {
        override fun keyFor(alias: String): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
                init(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }.generateKey()
        }

        override fun contains(alias: String): Boolean =
            KeyStore.getInstance(KEYSTORE)
                .apply { load(null) }
                .containsAlias(alias)

        override fun delete(alias: String) {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(alias)
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
