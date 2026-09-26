package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.ai.net.AudioHttpRequest
import com.example.englishlearning.ai.net.AudioHttpResult
import com.example.englishlearning.ai.net.AudioHttpTransport
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.language.domain.PronunciationCapability
import com.example.englishlearning.language.domain.PronunciationResult
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MiMoPronunciationProviderTest {
    private val wav = byteArrayOf(1, 2, 3, 4, 5)
    private val responseBody = """
        {"choices":[{"message":{"role":"assistant","content":"","audio":{"data":"${Base64.getEncoder().encodeToString(wav)}"}}}]}
    """.trimIndent().encodeToByteArray()

    private val profile = AiProfile(
        profileId = "mimo", displayName = "MiMo", websiteUrl = "https://mimo.test",
        endpoint = "https://api.xiaomimimo.com/v1/chat/completions", model = "mimo-v2.5-tts",
        capabilities = setOf(AiCapability.Speech),
        secretReference = SecretReference("ai-profile-mimo"),
        advancedParameters = AiAdvancedParameters(timeoutSeconds = 12),
        providerKind = AiProviderKind.XIAOMI_MIMO,
    )

    @Test fun `successful speech plays decoded wav via chat completions protocol`() = runTest {
        val transport = RecordingTransport(AudioHttpResult.Success(responseBody))
        val player = RecordingAudioPlayer(AudioPlaybackResult.Played)
        val key = "secret".toCharArray()
        val store = FakeSecretStore(key)
        val provider = provider(transport, store, player)

        assertIs<PronunciationResult.Played>(provider.speak("hello"))
        assertContentEquals(wav, player.bytes)
        assertEquals("wav", player.format)
        // URL 是 Profile 端点原样（完整 chat/completions），不追加 /audio/speech。
        assertEquals("https://api.xiaomimimo.com/v1/chat/completions", transport.request!!.url)
        assertEquals("POST", transport.request!!.method)
        assertEquals("application/json", transport.request!!.headers["Content-Type"])
        assertEquals("secret", transport.request!!.headers["api-key"])
        assertTrue(transport.request!!.headers.keys.none { it.equals("Authorization", ignoreCase = true) })
        assertEquals(12, transport.request!!.timeoutSeconds)
        val body = transport.request!!.body.decodeToString()
        assertTrue(body.contains("\"role\":\"assistant\""))
        assertTrue(body.contains("\"content\":\"hello\""))
        assertTrue(body.contains("\"stream\":false"))
        assertTrue(body.contains("\"voice\":\"Mia\""))
        // 密钥读出用完必须清零。
        assertTrue(store.lastRead!!.all { it == '\u0000' })
        // 响应体（含 base64 音频）用完清零。
        assertTrue(responseBody.all { it == 0.toByte() })
    }

    @Test fun `profile with openai kind is unavailable without touching key`() = runTest {
        val openAiProfile = profile.copy(providerKind = AiProviderKind.OPENAI_COMPATIBLE)
        val store = FakeSecretStore("secret".toCharArray())
        val transport = RecordingTransport(AudioHttpResult.Success(responseBody))

        val result = provider(transport, store, profile = openAiProfile).speak("hello")

        assertIs<PronunciationResult.Unavailable>(result)
        assertEquals(0, store.readCalls)
        assertEquals(null, transport.request)
    }

    @Test fun `response without audio returns failed and clears body`() = runTest {
        val body = """{"choices":[{"message":{"role":"assistant","content":"no audio"}}]}""".encodeToByteArray()
        val transport = RecordingTransport(AudioHttpResult.Success(body))
        val player = RecordingAudioPlayer(AudioPlaybackResult.Played)

        val result = provider(transport, player = player).speak("hello")

        assertIs<PronunciationResult.Failed>(result)
        assertEquals(null, player.bytes)
        assertTrue(body.all { it == 0.toByte() })
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
        assertContentEquals(wav, player.bytes)
    }

    @Test fun `profile id is injectable`() = runTest {
        val custom = profile.copy(profileId = "custom-profile")
        val result = provider(profile = custom, profileId = "custom-profile").speak("hello")
        assertIs<PronunciationResult.Played>(result)
    }

    @Test fun `blank text does not access dependencies`() = runTest {
        val profiles = CountingProfileRepository(profile)
        val store = FakeSecretStore("secret".toCharArray())
        val result = MiMoPronunciationProvider(profiles, AiProfileSecretUseCase(store), RecordingTransport(AudioHttpResult.Success(byteArrayOf())), RecordingAudioPlayer(AudioPlaybackResult.Played), "mimo").speak("   ")
        assertIs<PronunciationResult.Unavailable>(result)
        assertEquals(0, profiles.findCalls)
        assertEquals(0, store.readCalls)
    }

    @Test fun `insecure endpoint returns failed`() = runTest {
        val result = provider(profile = profile.copy(endpoint = "http://api.xiaomimimo.com/v1/chat/completions")).speak("hello")
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
        for (outcome in listOf(AudioHttpResult.NetworkUnavailable, AudioHttpResult.TimedOut, AudioHttpResult.Cancelled, AudioHttpResult.HttpError(500, byteArrayOf(9)))) {
            val transport = RecordingTransport(outcome)
            val result = provider(transport).speak("hello")
            assertIs<PronunciationResult.Failed>(result)
            assertTrue(transport.responseBody.all { it == 0.toByte() })
        }
    }

    @Test fun `capabilities expose remote audio`() {
        assertEquals(setOf(PronunciationCapability.RemoteAudio), provider().capabilities())
    }

    private fun provider(
        transport: AudioHttpTransport = RecordingTransport(AudioHttpResult.Success(responseBody.copyOf())),
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
        val responseBody: ByteArray get() = when (outcome) {
            is AudioHttpResult.Success -> outcome.body
            is AudioHttpResult.HttpError -> outcome.body
            else -> byteArrayOf()
        }
        override suspend fun send(request: AudioHttpRequest): AudioHttpResult { this.request = request; return outcome }
    }
}
