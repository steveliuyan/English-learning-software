package com.example.englishlearning.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.crypto.SecretKey

class AndroidKeyStoreSecretStoreTest {
    @Test
    fun providerFailure_returnsKeyStoreUnavailable_clearsCallerBuffer_and_leaves_no_ciphertext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alias = "provider-failure-test"
        File(context.filesDir, "secrets").deleteRecursively()
        val store = AndroidKeyStoreSecretStore(context, FailingProvider)
        val secret = "not-recorded".toCharArray()

        val result = store.save(SecretReference(alias), secret)

        assertEquals(AppError.KeyStoreUnavailable, (result.exceptionOrNull() as AppErrorException).appError)
        assertTrue(secret.all { it == '\u0000' })
        assertFalse(File(context.filesDir, "secrets").listFiles().orEmpty().any { it.isFile })
    }

    @Test
    fun save_has_and_delete_do_not_expose_plaintext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AndroidKeyStoreSecretStore(context)
        val reference = SecretReference("instrumented-secret-store-test")

        assertTrue(store.save(reference, "value".toCharArray()).isSuccess)
        assertEquals(true, store.has(reference).getOrThrow())
        assertTrue(store.delete(reference).isSuccess)
        assertEquals(false, store.has(reference).getOrThrow())
    }

    private object FailingProvider : AndroidKeyStoreSecretStore.KeyStoreProvider {
        override fun keyFor(alias: String): SecretKey = error("provider unavailable")

        override fun contains(alias: String): Boolean = error("provider unavailable")

        override fun delete(alias: String) = error("provider unavailable")
    }
}
