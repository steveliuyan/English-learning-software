package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AudioHttpRequest
import com.example.englishlearning.ai.net.AudioHttpResult
import com.example.englishlearning.ai.net.AudioHttpTransport
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.language.domain.PronunciationCapability
import com.example.englishlearning.language.domain.PronunciationResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MiMoPronunciationProviderTest {
    private val profile = AiProfile(
        profileId = "mimo", displayName = "MiMo", websiteUrl = "https://mimo.test",
        endpoint = "https://api.test/v1", model = "mimo-tts", capabilities = setOf(AiCapability.Speech),
        secretReference = SecretReference("ai-profile-mimo"), advancedParameters = AiAdvancedParameters(timeoutSeconds = 12),
    )

    @Test fun `successful speech sends request and returns played`() = runTest {
        val transport = RecordingTransport(AudioHttpResult.Success(byteArrayOf(1)))
        val key = "secret".toCharArray()
        val provider = provider(transport, FakeSecretStore(key))

        assertIs<PronunciationResult.Played>(provider.speak("hello"))
        assertEquals("https://api.test/v1/audio/speech", transport.request!!.url)
        assertEquals("Bearer secret", transport.request!!.headers["Authorization"])
        assertTrue(key.all { it == '\u0000' })
    }

    @Test fun `missing profile is unavailable`() = runTest {
        val result = provider(profile = null).speak("hello")
        assertIs<PronunciationResult.Unavailable>(result)
    }

    @Test fun `missing key is unavailable`() = runTest {
        val result = provider(secretStore = FakeSecretStore(null)).speak("hello")
        assertIs<PronunciationResult.Unavailable>(result)
    }

    @Test fun `secret read failure is unavailable`() = runTest {
        val result = provider(secretStore = FakeSecretStore(null, fail = true)).speak("hello")
        assertIs<PronunciationResult.Unavailable>(result)
    }

    @Test fun `network timeout cancellation and http errors are failed`() = runTest {
        for (outcome in listOf(AudioHttpResult.NetworkUnavailable, AudioHttpResult.TimedOut, AudioHttpResult.Cancelled, AudioHttpResult.HttpError(500, byteArrayOf()))) {
            val result = provider(RecordingTransport(outcome)).speak("hello")
            assertIs<PronunciationResult.Failed>(result)
        }
    }

    @Test fun `capabilities expose remote audio`() {
        assertEquals(setOf(PronunciationCapability.RemoteAudio), provider().capabilities())
    }

    private fun provider(
        transport: AudioHttpTransport = RecordingTransport(AudioHttpResult.Success(byteArrayOf(1))),
        secretStore: SecretStore = FakeSecretStore("secret".toCharArray()),
        profile: AiProfile? = this.profile,
    ) = MiMoPronunciationProvider(
        profiles = FakeProfileRepository(profile),
        secrets = AiProfileSecretUseCase(secretStore),
        transport = transport,
        profileId = "mimo",
    )

    private class FakeProfileRepository(private val profile: AiProfile?) : AiProfileRepository {
        override suspend fun list() = Result.success(listOfNotNull(profile))
        override suspend fun find(profileId: String) = Result.success(profile?.takeIf { it.profileId == profileId })
        override suspend fun save(profile: AiProfile) = Result.success(Unit)
        override suspend fun delete(profileId: String) = Result.success(Unit)
    }

    private class FakeSecretStore(private val value: CharArray?, private val fail: Boolean = false) : SecretStore {
        override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
        override fun read(reference: SecretReference): Result<CharArray> = if (fail) Result.failure(IllegalStateException("secret unavailable")) else value?.copyOf()?.let { Result.success(it) } ?: Result.failure(IllegalStateException("missing"))
        override fun delete(reference: SecretReference) = Result.success(Unit)
        override fun has(reference: SecretReference) = Result.success(value != null)
    }

    private class RecordingTransport(private val outcome: AudioHttpResult) : AudioHttpTransport {
        var request: AudioHttpRequest? = null
        override suspend fun send(request: AudioHttpRequest): AudioHttpResult { this.request = request; return outcome }
    }
}
