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

class DefaultTextProfileSelectorTest {
    @Test
    fun noSavedSelectionDoesNotInspectOtherProfilesOrKeys() = runTest {
        val profiles = FakeProfiles(listOf(profile("p1")))
        val secrets = RecordingSecrets(hasKey = true)
        val preferences = FakePreferences(AiPreference())

        val result = DefaultTextProfileSelector(preferences, profiles, AiProfileSecretUseCase(secrets)).select()

        assertEquals(DefaultTextProfileResult.NoSelection, result)
        assertEquals(0, profiles.findCalls)
        assertEquals(0, secrets.hasCalls)
    }

    @Test
    fun selectedTextProfileWithKeyIsReturned() = runTest {
        val selected = profile("p1")
        val result = selector(AiPreference(defaultTextProfileId = "p1"), listOf(selected), hasKey = true).select()

        assertEquals(DefaultTextProfileResult.Selected(selected), result)
    }

    @Test
    fun selectedProfileWithoutTextCapabilityIsClearedAndReportedUnavailable() = runTest {
        val preferences = FakePreferences(AiPreference(defaultTextProfileId = "p1"))
        val result = DefaultTextProfileSelector(
            preferences,
            FakeProfiles(listOf(profile("p1", AiCapability.Speech))),
            AiProfileSecretUseCase(RecordingSecrets(hasKey = true)),
        ).select()

        assertEquals(DefaultTextProfileResult.Unavailable, result)
        assertEquals(AiPreference(), preferences.saved.single())
    }

    @Test
    fun selectedProfileWithoutKeyIsClearedAndReportedUnavailable() = runTest {
        val preferences = FakePreferences(AiPreference(defaultTextProfileId = "p1"))
        val result = DefaultTextProfileSelector(
            preferences,
            FakeProfiles(listOf(profile("p1"))),
            AiProfileSecretUseCase(RecordingSecrets(hasKey = false)),
        ).select()

        assertEquals(DefaultTextProfileResult.Unavailable, result)
        assertEquals(AiPreference(), preferences.saved.single())
    }

    @Test
    fun selectedMissingProfileIsClearedAndReportedUnavailable() = runTest {
        val preferences = FakePreferences(AiPreference(defaultTextProfileId = "missing"))
        val result = DefaultTextProfileSelector(
            preferences,
            FakeProfiles(emptyList()),
            AiProfileSecretUseCase(RecordingSecrets(hasKey = true)),
        ).select()

        assertEquals(DefaultTextProfileResult.Unavailable, result)
        assertEquals(AiPreference(), preferences.saved.single())
    }

    @Test
    fun preferenceReadFailureIsStorageUnavailableAndDoesNotClear() = runTest {
        val preferences = FakePreferences(failGet = true)
        val result = DefaultTextProfileSelector(
            preferences,
            FakeProfiles(emptyList()),
            AiProfileSecretUseCase(RecordingSecrets(hasKey = true)),
        ).select()

        assertEquals(DefaultTextProfileResult.StorageUnavailable, result)
        assertTrue(preferences.saved.isEmpty())
    }

    @Test
    fun profileReadFailureIsStorageUnavailableAndDoesNotClear() = runTest {
        val preferences = FakePreferences(AiPreference(defaultTextProfileId = "p1"))
        val result = DefaultTextProfileSelector(
            preferences,
            FakeProfiles(emptyList(), failFind = true),
            AiProfileSecretUseCase(RecordingSecrets(hasKey = true)),
        ).select()

        assertEquals(DefaultTextProfileResult.StorageUnavailable, result)
        assertTrue(preferences.saved.isEmpty())
    }

    private fun selector(preference: AiPreference, profiles: List<AiProfile>, hasKey: Boolean) =
        DefaultTextProfileSelector(
            FakePreferences(preference),
            FakeProfiles(profiles),
            AiProfileSecretUseCase(RecordingSecrets(hasKey)),
        )

    private fun profile(id: String, vararg capabilities: AiCapability) = AiProfile(
        profileId = id,
        displayName = id,
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "model",
        capabilities = capabilities.toSet().ifEmpty { setOf(AiCapability.Text) },
        secretReference = SecretReference("ai-profile-$id"),
    )
}

internal class FakePreferences(
    private val value: AiPreference = AiPreference(),
    private val failGet: Boolean = false,
) : AiPreferenceRepository {
    val saved = mutableListOf<AiPreference>()

    override suspend fun get(): Result<AiPreference> = if (failGet) {
        Result.failure(AppErrorException(AppError.StorageUnavailable))
    } else {
        Result.success(value)
    }

    override suspend fun save(preference: AiPreference): Result<Unit> {
        saved += preference
        return Result.success(Unit)
    }
}

internal class FakeProfiles(
    private val values: List<AiProfile>,
    private val failFind: Boolean = false,
) : AiProfileRepository {
    var findCalls = 0

    override suspend fun list(): Result<List<AiProfile>> = Result.success(values)

    override suspend fun find(profileId: String): Result<AiProfile?> {
        findCalls++
        return if (failFind) Result.failure(AppErrorException(AppError.StorageUnavailable))
        else Result.success(values.firstOrNull { it.profileId == profileId })
    }

    override suspend fun save(profile: AiProfile): Result<Unit> = Result.success(Unit)

    override suspend fun delete(profileId: String): Result<Unit> = Result.success(Unit)
}

internal class RecordingSecrets(private val hasKey: Boolean) : SecretStore {
    var hasCalls = 0

    override fun save(reference: SecretReference, secret: CharArray): Result<Unit> = Result.success(Unit)

    override fun read(reference: SecretReference): Result<CharArray> =
        Result.failure(AppErrorException(AppError.KeyStoreUnavailable))

    override fun delete(reference: SecretReference): Result<Unit> = Result.success(Unit)

    override fun has(reference: SecretReference): Result<Boolean> {
        hasCalls++
        return Result.success(hasKey)
    }
}
