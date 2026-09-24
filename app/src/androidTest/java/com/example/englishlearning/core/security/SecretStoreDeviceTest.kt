package com.example.englishlearning.core.security

import androidx.test.core.app.ApplicationProvider
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真实 AndroidKeyStore 上的 `save → read → delete → read 失败` 全链路。
 *
 * **这个测试是 `IV_LENGTH = 12` 的唯一外部证据**，不要当成可选的重复覆盖删掉。
 * 写入端存的是 `iv || ciphertext` 且没有记录 IV 长度，读回时必须假定它是 12 字节——
 * 这个假定写死在代码里、JVM 测试用的是假密钥源，只有真机上走一次真实 Keystore 的加解密，
 * 才能证明这个格式约定与设备实际行为一致。
 *
 * 同时它也是「非 ASCII 密钥能原样往返」的证据：加解密走的是字符→字节→字符，
 * 任何一步的编码假设错了都会在这里露出来。
 *
 * 注意本文件用 **JUnit 4**（`androidTest` 源集）：断言来自 `org.junit.Assert`，
 * 其 `assertEquals` 是**消息在前**，与 JVM 测试用的 `kotlin.test`（实际值在前）相反。
 */
class SecretStoreDeviceTest {
    private fun store() = AndroidKeyStoreSecretStore(ApplicationProvider.getApplicationContext())

    private fun reference(name: String) = SecretReference("secret-store-device-$name")

    @Test
    fun realKeystoreRoundTripReturnsExactlyWhatWasStored() {
        val reference = reference("round-trip")
        val store = store()
        // 含多字节字符，确保字符↔字节的编码假设在真实设备上也成立。
        val secret = "sk-live-0123456789abcdefghijklmnopqrstuvwxyz-密钥"
        try {
            assertTrue("保存应成功", store.save(reference, secret.toCharArray()).isSuccess)

            val loaded = store.read(reference).getOrThrow()
            try {
                assertEquals("真机读回的内容必须与存入的逐字相同", secret, loaded.concatToString())
            } finally {
                loaded.fill('\u0000')
            }
        } finally {
            store.delete(reference)
        }
    }

    @Test
    fun readAfterDeleteFailsAndNeverReturnsTheOldValue() {
        val reference = reference("read-after-delete")
        val store = store()
        try {
            assertTrue(store.save(reference, "sk-to-be-deleted".toCharArray()).isSuccess)
            assertTrue("删除应成功", store.delete(reference).isSuccess)

            val result = store.read(reference)

            assertTrue("删除后读取必须失败", result.isFailure)
            assertEquals(
                AppError.KeyStoreUnavailable,
                (result.exceptionOrNull() as AppErrorException).appError,
            )
        } finally {
            store.delete(reference)
        }
    }

    @Test
    fun twoReadsReturnIndependentBuffers() {
        val reference = reference("independent-buffers")
        val store = store()
        try {
            assertTrue(store.save(reference, "sk-independent".toCharArray()).isSuccess)

            val first = store.read(reference).getOrThrow()
            first.fill('\u0000')
            val second = store.read(reference).getOrThrow()
            try {
                // 若两次读取复用同一个数组，清零第一次就会破坏第二次——真机上同样要成立。
                assertEquals("清零上一次结果不得影响下一次读取", "sk-independent", second.concatToString())
            } finally {
                second.fill('\u0000')
            }
        } finally {
            store.delete(reference)
        }
    }

    @Test
    fun readingAReferenceThatWasNeverSavedFails() {
        val reference = reference("never-saved")
        val store = store()
        try {
            val result = store.read(reference)

            assertTrue("从未保存过的引用必须读取失败", result.isFailure)
            assertEquals(
                AppError.KeyStoreUnavailable,
                (result.exceptionOrNull() as AppErrorException).appError,
            )
        } finally {
            store.delete(reference)
        }
    }
}
