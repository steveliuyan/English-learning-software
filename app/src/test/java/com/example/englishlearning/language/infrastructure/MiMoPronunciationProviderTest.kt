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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MiMoPronunciationProviderTest {
    private val profile = AiProfile(
        profileId = "mimo", displayName = "MiMo", websiteUrl = "https://mimo.test",
        endpoint = "https://api.test/v1", model = "mimo-tts", capabilities = setOf(AiCapability.Speech),
        secretReference = SecretReference("ai-profile-mimo"), advancedParameters = AiAdvancedParameters(timeoutSeconds = 12),
    )

    @Test fun `successful speech plays audio and returns played`() = runTest {
        val transport = RecordingTransport(AudioHttpResult.Success(byteArrayOf(1, 2)))
        val player = RecordingAudioPlayer(AudioPlaybackResult.Played)
        val key = "secret".toCharArray()
        val store = FakeSecretStore(key)
        val provider = provider(transport, store, player)

        assertIs<PronunciationResult.Played>(provider.speak("hello"))
        assertContentEquals(byteArrayOf(1, 2), player.bytes)
        assertEquals("mp3", player.format)
        assertEquals("https://api.test/v1/audio/speech", transport.request!!.url)
        assertEquals("POST", transport.request!!.method)
        assertEquals("application/json", transport.request!!.headers["Content-Type"])
        assertEquals("Bearer secret", transport.request!!.headers["Authorization"])
        assertEquals(12, transport.request!!.timeoutSeconds)
        assertTrue(transport.request!!.body.decodeToString().contains("\"input\":\"hello\""))
        assertTrue(store.lastRead!!.all { it == '\u0000' })
    }

    @Test fun `empty audio returns failed without playing`() = runTest {
        val player = RecordingAudioPlayer(AudioPlaybackResult.Played)
        val result = provider(RecordingTransport(AudioHttpResult.Success(byteArrayOf())), player = player).speak("hello")
        assertIs<PronunciationResult.Failed>(result)
        assertEquals(null, player.bytes)
    }

    @Test fun `playback failure returns failed`() = runTest {
        val player = RecordingAudioPlayer(AudioPlaybackResult.Failed)
        val result = provider(player = player).speak("hello")
        assertIs<PronunciationResult.Failed>(result)
        assertContentEquals(byteArrayOf(1), player.bytes)
    }

    @Test fun `profile id is injectable`() = runTest {
        val custom = profile.copy(profileId = "custom-profile")
        val result = provider(profile = custom, profileId = "custom-profile").speak("hello")
        assertIs<PronunciationResult.Played>(result)
    }

    @Test fun `blank text does not access dependencies`() = runTest {
        val profiles = CountingProfileRepository(profile)
        val store = FakeSecretStore("secret".toCharArray())
        val result = MiMoPronunciationProvider(profiles, AiProfileSecretUseCase(store), RecordingTransport(AudioHttpResult.Success(byteArrayOf())), "mimo").speak("   ")
        assertIs<PronunciationResult.Unavailable>(result)
        assertEquals(0, profiles.findCalls)
        assertEquals(0, store.readCalls)
    }

    @Test fun `builder failure returns failed`() = runTest {
        val result = provider(profile = profile.copy(endpoint = "http://api.test")).speak("hello")
        assertIs<PronunciationResult.Failed>(result)
    }

    @Test fun `transport exception returns failed`() = runTest {
        val result = provider(ThrowingTransport(IllegalStateException("transport"))).speak("hello")
        assertIs<PronunciationResult.Failed>(result)
    }

    @Test fun `cancellation is rethrown`() = runTest {
        assertFailsWith<CancellationException> { provider(ThrowingTransport(CancellationException("cancel"))).speak("hello") }
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
        player: AudioPlayer = RecordingAudioPlayer(AudioPlaybackResult.Played),
        profile: AiProfile? = this.profile,
        profileId: String = profile?.profileId ?: "mimo",
    ) = MiMoPronunciationProvider(
        profiles = FakeProfileRepository(profile),
        secrets = AiProfileSecretUseCase(secretStore),
        transport = transport,
        player = player,
        profileId = profileId,
    )

    private class RecordingAudioPlayer(private val outcome: AudioPlaybackResult) : AudioPlayer {
        var bytes: ByteArray? = null
        var format: String? = null
        override suspend fun play(bytes: ByteArray, format: String): AudioPlaybackResult {
            this.bytes = bytes.copyOf()
            this.format = format
            return outcome
        }
    }

    private open class FakeProfileRepository(private val profile: AiProfile?) : AiProfileRepository {
        override suspend fun list() = Result.success(listOfNotNull(profile))
        override suspend fun find(profileId: String) = Result.success(profile?.takeIf { it.profileId == profileId })
        override suspend fun save(profile: AiProfile) = Result.success(Unit)
        override suspend fun delete(profileId: String) = Result.success(Unit)
    }

    private class FakeSecretStore(private val value: CharArray?, private val fail: Boolean = false) : SecretStore {
        var readCalls = 0
        var lastRead: CharArray? = null
        override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
        override fun read(reference: SecretReference): Result<CharArray> {
            readCalls++
            return if (fail) Result.failure(IllegalStateException("secret unavailable")) else value?.copyOf()?.also { lastRead = it }?.let { Result.success(it) } ?: Result.failure(IllegalStateException("missing"))
        }
        override fun delete(reference: SecretReference) = Result.success(Unit)
        override fun has(reference: SecretReference) = Result.success(value != null)
    }

    private class CountingProfileRepository(private val profile: AiProfile) : FakeProfileRepository(profile) {
        var findCalls = 0
        override suspend fun find(profileId: String): Result<AiProfile?> { findCalls++; return super.find(profileId) }
    }

    private class ThrowingTransport(private val error: Throwable) : AudioHttpTransport {
        override suspend fun send(request: AudioHttpRequest): AudioHttpResult = throw error
    }

    private class RecordingTransport(private val outcome: AudioHttpResult) : AudioHttpTransport {
        var request: AudioHttpRequest? = null
        val outcomeBody: ByteArray get() = (outcome as? AudioHttpResult.Success)?.body ?: byteArrayOf()
        override suspend fun send(request: AudioHttpRequest): AudioHttpResult { this.request = request; return outcome }
    }
}
