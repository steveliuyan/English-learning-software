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
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 AndroidKeyStore 里的 AES 密钥加密 `filesDir` 下的密文文件。
 *
 * 主构造只要求**一个目录**，而不是 `Context`——它真正需要的只是一个存放密文的位置。
 * 这样 JVM 测试可以直接传 `@TempDir`，不必为了拿一个 `filesDir` 而引入 Robolectric。
 * 生产入口保留接受 `Context` 的次级构造函数，调用方与依赖注入不受影响。
 */
class AndroidKeyStoreSecretStore(
    private val ciphertextDirectory: File,
    private val keyStoreProvider: KeyStoreProvider = AndroidKeyStoreProvider,
) : SecretStore {
    constructor(
        context: Context,
        keyStoreProvider: KeyStoreProvider = AndroidKeyStoreProvider,
    ) : this(File(context.filesDir, SECRETS_DIRECTORY_NAME), keyStoreProvider)

    init {
        ciphertextDirectory.mkdirs()
    }

    override fun save(
        reference: SecretReference,
        secret: CharArray,
    ): Result<Unit> {
        var destination: File? = null
        return try {
            destination = ciphertextFile(reference)
            destination.delete()
            val key = keyStoreProvider.keyFor(reference.alias)
            val plaintext = secret.concatToString().encodeToByteArray()
            try {
                val encrypted =
                    Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
                        .let { cipher -> cipher.iv + cipher.doFinal(plaintext) }
                destination.outputStream().use { it.write(encrypted) }
                Result.success(Unit)
            } finally {
                plaintext.fill(0)
            }
        } catch (_: Exception) {
            destination?.delete()
            Result.failure(AppErrorException(AppError.KeyStoreUnavailable))
        } finally {
            secret.fill('\u0000')
        }
    }

    override fun read(reference: SecretReference): Result<CharArray> =
        runCatching {
            try {
                decrypt(ciphertextFile(reference), reference)
            } catch (_: Throwable) {
                throw AppErrorException(AppError.KeyStoreUnavailable)
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

    /**
     * 解出明文。返回的数组是本次新建的，调用方拥有它。
     *
     * **`IV_LENGTH = 12` 是一个格式约定，不是一个可以随手改的常量。** 写入端存的是 `iv || ciphertext`
     * 且**没有记录 IV 长度**，所以读回时必须知道它。AndroidKeyStore 的 AES/GCM 在
     * `init(ENCRYPT_MODE, key)` 下生成的 IV 恒为 12 字节，这是既有磁盘格式的隐含前提。
     * 该前提不能只靠注释保证：`SecretStoreDeviceTest` 在真机上跑 `save → read` 往返，是它的外部证据。
     *
     * 这里**不做**「旧格式 / 被篡改 / 密钥丢失」的区分：GCM 带认证标签，上述情况都会让
     * `doFinal` 抛 `AEADBadTagException`，统一映射成 `KeyStoreUnavailable`。区分对用户没有价值，
     * 却会给出误导性的提示。
     */
    private fun decrypt(file: File, reference: SecretReference): CharArray {
        if (!file.isFile) {
            // 没有密文就是没有密文，不返回空串冒充成功。
            throw AppErrorException(AppError.KeyStoreUnavailable)
        }
        val bytes = file.readBytes()
        try {
            if (bytes.size <= IV_LENGTH) throw AppErrorException(AppError.KeyStoreUnavailable)
            val iv = bytes.copyOfRange(0, IV_LENGTH)
            val ciphertext = bytes.copyOfRange(IV_LENGTH, bytes.size)
            val plaintext = try {
                Cipher.getInstance(TRANSFORMATION)
                    .apply {
                        init(
                            Cipher.DECRYPT_MODE,
                            keyStoreProvider.keyFor(reference.alias),
                            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
                        )
                    }
                    .doFinal(ciphertext)
            } finally {
                ciphertext.fill(0)
            }
            return try {
                plaintext.decodeToString().toCharArray()
            } finally {
                plaintext.fill(0)
            }
        } finally {
            // 密文不必留在内存里；明文数组是调用方的，不在这里清。
            bytes.fill(0)
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
        const val SECRETS_DIRECTORY_NAME = "secrets"
        const val IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
