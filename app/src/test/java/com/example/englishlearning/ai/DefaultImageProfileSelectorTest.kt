package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiPreference
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.storage.AppErrorException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 默认生图服务选择：与默认文本服务同一套语义，但能力要求是 [AiCapability.ImageGeneration]，
 * 且清除失效选择时**必须保留另一个默认字段**——两个默认各自独立，不能互相抹掉。
 */
class DefaultImageProfileSelectorTest {
    @Test
    fun noSavedSelectionDoesNotInspectOtherProfilesOrKeys() = runTest {
        val profiles = FakeProfiles(listOf(profile("p1")))
        val secrets = RecordingSecrets(hasKey = true)
        val preferences = FakePreferences(AiPreference())

        val result = DefaultImageProfileSelector(preferences, profiles, AiProfileSecretUseCase(secrets)).select()

        assertEquals(DefaultImageProfileResult.NoSelection, result)
        assertEquals(0, profiles.findCalls)
        assertEquals(0, secrets.hasCalls)
    }

    @Test
    fun selectedImageProfileWithKeyIsReturned() = runTest {
        val selected = profile("p-img")
        val preferences = FakePreferences(AiPreference(defaultTextProfileId = "p-text", defaultImageProfileId = "p-img"))
        val result = DefaultImageProfileSelector(
            preferences,
            FakeProfiles(listOf(selected)),
            AiProfileSecretUseCase(RecordingSecrets(hasKey = true)),
        ).select()

        assertEquals(DefaultImageProfileResult.Selected(selected), result)
        assertTrue(preferences.saved.isEmpty(), "有效选择不需要写回")
    }

    @Test
    fun selectedProfileWithoutImageGenerationCapabilityIsClearedAndTextSelectionIsPreserved() = runTest {
        val preferences = FakePreferences(AiPreference(defaultTextProfileId = "p-text", defaultImageProfileId = "p1"))
        val result = DefaultImageProfileSelector(
            preferences,
            FakeProfiles(listOf(profile("p1", AiCapability.Text))),
            AiProfileSecretUseCase(RecordingSecrets(hasKey = true)),
        ).select()

        assertEquals(DefaultImageProfileResult.Unavailable, result)
        assertEquals(
            AiPreference(defaultTextProfileId = "p-text"),
            preferences.saved.single(),
            "清除生图默认时文本默认必须原样保留",
        )
    }

    @Test
    fun selectedProfileWithoutKeyIsClearedAndTextSelectionIsPreserved() = runTest {
        val preferences = FakePreferences(AiPreference(defaultTextProfileId = "p-text", defaultImageProfileId = "p1"))
        val result = DefaultImageProfileSelector(
            preferences,
            FakeProfiles(listOf(profile("p1", AiCapability.ImageGeneration))),
            AiProfileSecretUseCase(RecordingSecrets(hasKey = false)),
        ).select()

        assertEquals(DefaultImageProfileResult.Unavailable, result)
        assertEquals(AiPreference(defaultTextProfileId = "p-text"), preferences.saved.single())
    }

    @Test
    fun selectedMissingProfileIsCleared() = runTest {
        val preferences = FakePreferences(AiPreference(defaultImageProfileId = "missing"))
        val result = DefaultImageProfileSelector(
            preferences,
            FakeProfiles(emptyList()),
            AiProfileSecretUseCase(RecordingSecrets(hasKey = true)),
        ).select()

        assertEquals(DefaultImageProfileResult.Unavailable, result)
        assertEquals(AiPreference(), preferences.saved.single())
    }

    @Test
    fun preferenceReadFailureIsStorageUnavailableAndDoesNotClear() = runTest {
        val preferences = FakePreferences(failGet = true)
        val result = DefaultImageProfileSelector(
            preferences,
            FakeProfiles(emptyList()),
            AiProfileSecretUseCase(RecordingSecrets(hasKey = true)),
        ).select()

        assertEquals(DefaultImageProfileResult.StorageUnavailable, result)
        assertTrue(preferences.saved.isEmpty())
    }

    @Test
    fun profileReadFailureIsStorageUnavailableAndDoesNotClear() = runTest {
        val preferences = FakePreferences(AiPreference(defaultImageProfileId = "p1"))
        val result = DefaultImageProfileSelector(
            preferences,
            FakeProfiles(emptyList(), failFind = true),
            AiProfileSecretUseCase(RecordingSecrets(hasKey = true)),
        ).select()

        assertEquals(DefaultImageProfileResult.StorageUnavailable, result)
        assertTrue(preferences.saved.isEmpty())
    }

    private fun profile(id: String, vararg capabilities: AiCapability) = AiProfile(
        profileId = id,
        displayName = id,
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "model",
        capabilities = capabilities.toSet().ifEmpty { setOf(AiCapability.ImageGeneration) },
        secretReference = SecretReference("ai-profile-$id"),
    )
}
