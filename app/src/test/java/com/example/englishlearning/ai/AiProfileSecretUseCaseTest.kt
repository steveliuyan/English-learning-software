package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.storage.AppErrorException
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiProfileSecretUseCaseTest {
    @Test
    fun profileIdDerivesIndependentStableSecretReferences() {
        assertEquals("ai-profile-p1", AiProfileSecretUseCase.referenceFor("p1").alias)
        assertEquals("ai-profile-p2", AiProfileSecretUseCase.referenceFor("p2").alias)
    }

    @Test
    fun savingKeyUsesOnlyProfileReferenceAndClearsInput() {
        val store = FakeSecretStore()
        val key = "secret".toCharArray()

        val result = AiProfileSecretUseCase(store).saveKey(profile("p1"), key)

        assertTrue(result.isSuccess)
        assertEquals("ai-profile-p1", store.savedReference?.alias)
        assertContentEquals(CharArray(key.size), key)
    }

    @Test
    fun deleteAndHasKeyUseSameProfileReference() {
        val store = FakeSecretStore(hasValue = true)
        val useCase = AiProfileSecretUseCase(store)

        assertTrue(useCase.hasKey(profile("p2")).getOrThrow())
        assertTrue(useCase.deleteKey(profile("p2")).isSuccess)
        assertEquals("ai-profile-p2", store.checkedReference?.alias)
        assertEquals("ai-profile-p2", store.deletedReference?.alias)
    }

    @Test
    fun loadingKeyUsesTheSameProfileReferenceAndReturnsTheValueUnchanged() {
        val store = FakeSecretStore().apply { storedValue = "sk-live".toCharArray() }
        val useCase = AiProfileSecretUseCase(store)

        val loaded = useCase.loadKey(profile("p3")).getOrThrow()

        assertEquals("ai-profile-p3", store.readReference?.alias)
        assertContentEquals("sk-live".toCharArray(), loaded)
    }

    @Test
    fun loadingKeyPassesTheStorageFailureThroughUnchanged() {
        // 用例层不得把失败改写成别的类型、也不得吞掉它——界面要按 KeyStoreUnavailable 决定提示什么。
        val store = FakeSecretStore()
        val useCase = AiProfileSecretUseCase(store)

        val result = useCase.loadKey(profile("p4"))

        assertTrue(result.isFailure)
        assertEquals(AppError.KeyStoreUnavailable, (result.exceptionOrNull() as AppErrorException).appError)
    }

    private fun profile(id: String) = AiProfile(
        profileId = id,
        displayName = "Profile",
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "model",
        capabilities = setOf(AiCapability.Text),
        secretReference = SecretReference("ai-profile-$id"),
    )

    private class FakeSecretStore(private val hasValue: Boolean = false) : SecretStore {
        var savedReference: SecretReference? = null
        var checkedReference: SecretReference? = null
        var deletedReference: SecretReference? = null
        var readReference: SecretReference? = null
        var storedValue: CharArray? = null

        override fun save(reference: SecretReference, secret: CharArray): Result<Unit> {
            savedReference = reference
            secret.fill('\u0000')
            return Result.success(Unit)
        }

        override fun read(reference: SecretReference): Result<CharArray> {
            readReference = reference
            val value = storedValue
                ?: return Result.failure(AppErrorException(AppError.KeyStoreUnavailable))
            // 每次都返回新数组：真实实现就是这个契约，假实现不能把它放宽。
            return Result.success(value.copyOf())
        }

        override fun delete(reference: SecretReference): Result<Unit> {
            deletedReference = reference
            return Result.success(Unit)
        }

        override fun has(reference: SecretReference): Result<Boolean> {
            checkedReference = reference
            return Result.success(hasValue)
        }
    }
}
