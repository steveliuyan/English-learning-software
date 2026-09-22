package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
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

        override fun save(reference: SecretReference, secret: CharArray): Result<Unit> {
            savedReference = reference
            secret.fill('\u0000')
            return Result.success(Unit)
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
