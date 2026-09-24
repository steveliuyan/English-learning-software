package com.example.englishlearning.core.security

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * `AndroidKeyStoreSecretStore` 读回能力的 JVM 测试。
 *
 * 与 `androidTest` 里同名的 `AndroidKeyStoreSecretStoreTest` 分工不同，不要混淆：
 * - **本文件（JVM）**：注入假的 [AndroidKeyStoreSecretStore.KeyStoreProvider]，但用的是**真实 JCE AES 密钥**，
 *   所以加解密是真跑的，只有密钥来源被换掉。覆盖 `read` 的各种失败路径。
 * - **`androidTest` 同名类**：走真实的 AndroidKeyStore，覆盖真机上的 `save/has/delete`。
 * - **`SecretStoreDeviceTest`**：真机上跑 `save → read → delete → read 失败` 全链路，是
 *   `IV_LENGTH = 12` 这个隐含格式约定的外部证据。
 *
 * 直接用 `@TempDir` 提供密钥目录、不走 Robolectric：该类的真实依赖只是一个目录，而不是 `Context`。
 * （另一层原因是 Robolectric 的运行期 `android-all` jar 在本机没有缓存，首次使用要联网下载上百 MB。）
 */
class AndroidKeyStoreSecretStoreTest {
    @TempDir
    lateinit var secretsDirectory: Path

    /** 与真实 Keystore 语义一致：别名不存在时**自动生成**，而不是报错。 */
    private class FakeKeyStoreProvider : AndroidKeyStoreSecretStore.KeyStoreProvider {
        private val keys = mutableMapOf<String, SecretKey>()

        override fun keyFor(alias: String): SecretKey = keys.getOrPut(alias) {
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        }

        override fun contains(alias: String): Boolean = alias in keys

        override fun delete(alias: String) {
            keys.remove(alias)
        }
    }

    private fun store(provider: AndroidKeyStoreSecretStore.KeyStoreProvider = FakeKeyStoreProvider()) =
        AndroidKeyStoreSecretStore(secretsDirectory.toFile(), provider)

    private fun ciphertextFile(): File = secretsDirectory.toFile().listFiles().orEmpty()
        .single { it.isFile }

    @Test
    fun `read returns exactly what save stored`() {
        val reference = SecretReference("ai-profile-p1")
        val store = store()

        store.save(reference, "sk-secret".toCharArray()).getOrThrow()
        val loaded = store.read(reference).getOrThrow()

        assertEquals("sk-secret", loaded.concatToString(), "读回的内容必须与存入的逐字相同")
        loaded.fill('\u0000')
    }

    @Test
    fun `read fails when nothing was stored`() {
        val result = store().read(SecretReference("never-saved"))

        assertTrue(result.isFailure, "没有任何密文时读取必须失败，不能返回空串冒充成功")
        assertTrue(result.exceptionOrNull() is AppErrorException)
        assertEquals(
            AppError.KeyStoreUnavailable,
            (result.exceptionOrNull() as AppErrorException).appError,
        )
    }

    @Test
    fun `read of a tampered ciphertext fails instead of returning garbage`() {
        val reference = SecretReference("ai-profile-tampered")
        val store = store()
        store.save(reference, "sk-secret".toCharArray()).getOrThrow()

        // GCM 带认证标签，翻转密文一个字节就该验证失败。这里刻意不区分「被篡改」与
        // 「密钥丢失」——两者对用户的处置相同（重新录入），区分反而会给出误导性的提示。
        val file = ciphertextFile()
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)

        val result = store.read(reference)

        assertTrue(result.isFailure, "被篡改的密文必须读取失败，绝不能返回乱码")
        assertEquals(
            AppError.KeyStoreUnavailable,
            (result.exceptionOrNull() as AppErrorException).appError,
        )
    }

    @Test
    fun `read fails when the key is gone but the ciphertext file remains`() {
        // 还原备份、换设备等场景下会出现「密文还在、Keystore 条目没了」。
        // 此时 keyFor 会**新建一把密钥**，解密必然失败——这条路径不能落成返回乱码。
        val reference = SecretReference("ai-profile-key-lost")
        val provider = FakeKeyStoreProvider()
        val store = store(provider)
        store.save(reference, "sk-secret".toCharArray()).getOrThrow()

        provider.delete(reference.alias)

        val result = store.read(reference)

        assertTrue(result.isFailure, "密钥丢失后必须读取失败，哪怕密文文件还在")
        assertTrue(result.exceptionOrNull() is AppErrorException)
    }

    @Test
    fun `save overwrites a previous value for the same reference`() {
        val reference = SecretReference("ai-profile-p2")
        val store = store()

        store.save(reference, "first".toCharArray()).getOrThrow()
        store.save(reference, "second".toCharArray()).getOrThrow()
        val loaded = store.read(reference).getOrThrow()

        assertEquals("second", loaded.concatToString())
        assertEquals(1, secretsDirectory.toFile().listFiles().orEmpty().count { it.isFile }, "同一引用只该留一份密文")
        loaded.fill('\u0000')
    }

    @Test
    fun `save clears the caller buffer`() {
        val buffer = "sk-secret".toCharArray()

        store().save(SecretReference("ai-profile-p3"), buffer).getOrThrow()

        assertTrue(buffer.all { it == '\u0000' }, "保存后调用方的缓冲区必须被清零")
    }

    @Test
    fun `read returns a caller-owned buffer that is not shared between reads`() {
        val reference = SecretReference("ai-profile-p4")
        val store = store()
        store.save(reference, "sk-secret".toCharArray()).getOrThrow()

        val first = store.read(reference).getOrThrow()
        first.fill('\u0000')
        val second = store.read(reference).getOrThrow()

        // 若两次 read 复用同一个数组，清零第一次的结果就会破坏第二次读取。
        assertEquals("sk-secret", second.concatToString(), "清零上一次的结果不得影响下一次读取")
        second.fill('\u0000')
    }
}
