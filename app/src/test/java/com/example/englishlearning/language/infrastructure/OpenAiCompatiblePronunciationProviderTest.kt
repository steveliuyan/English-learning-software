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
import com.example.englishlearning.language.domain.PronunciationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenAiCompatiblePronunciationProviderTest {
    private val profile = AiProfile(
        profileId = "openai",
        displayName = "OpenAI",
        websiteUrl = "https://openai.test",
        endpoint = "https://api.test/v1",
        model = "tts-1",
        capabilities = setOf(AiCapability.Speech),
        secretReference = SecretReference("ai-profile-openai"),
        advancedParameters = AiAdvancedParameters(timeoutSeconds = 12),
    )

    @Test
    fun `OpenAI voice sends alloy and plays audio while clearing sensitive buffers`() = runTest {
        val key = "secret".toCharArray()
        val store = FakeSecretStore(key)
        val transport = RecordingTransport(AudioHttpResult.Success(byteArrayOf(1, 2)))
        val player = RecordingAudioPlayer(AudioPlaybackResult.Played)
        val provider = provider(transport = transport, store = store, player = player)

        assertIs<PronunciationResult.Played>(provider.speak("hello"))
        assertTrue(transport.request!!.body.decodeToString().contains("\"voice\":\"alloy\""))
        assertEquals("mp3", player.format)
        assertTrue(store.lastRead!!.all { it == '\u0000' })
        assertTrue(transport.responseBody.all { it == 0.toByte() })
    }

    @Test
    fun `empty audio and playback failure return failed without reporting played`() = runTest {
        val emptyPlayer = RecordingAudioPlayer(AudioPlaybackResult.Played)
        assertIs<PronunciationResult.Failed>(
            provider(transport = RecordingTransport(AudioHttpResult.Success(byteArrayOf())), player = emptyPlayer).speak("hello"),
        )
        assertEquals(null, emptyPlayer.bytes)

        assertIs<PronunciationResult.Failed>(
            provider(player = RecordingAudioPlayer(AudioPlaybackResult.Failed)).speak("hello"),
        )
    }

    @Test
    fun `HTTP failure returns failed and clears error audio`() = runTest {
        val transport = RecordingTransport(AudioHttpResult.HttpError(500, byteArrayOf(1, 2)))

        assertIs<PronunciationResult.Failed>(provider(transport = transport).speak("hello"))
        assertTrue(transport.responseBody.all { it == 0.toByte() })
    }

    @Test
    fun `cancellation is rethrown`() = runTest {
        assertFailsWith<CancellationException> {
            provider(transport = ThrowingTransport(CancellationException("cancel"))).speak("hello")
        }
    }

    @Test
    fun `profile voice overrides the factory default`() = runTest {
        val voiced = profile.copy(voice = "nova")
        val transport = RecordingTransport(AudioHttpResult.Success(byteArrayOf(1, 2)))
        val provider = OpenAiCompatiblePronunciationProvider(
            profileId = voiced.profileId,
            voice = "alloy",
            responseFormat = "mp3",
            profiles = FakeProfileRepository(voiced),
            secrets = AiProfileSecretUseCase(FakeSecretStore("secret".toCharArray())),
            transport = transport,
            player = RecordingAudioPlayer(AudioPlaybackResult.Played),
        )

        assertIs<PronunciationResult.Played>(provider.speak("hello"))
        assertTrue(transport.request!!.body.decodeToString().contains("\"voice\":\"nova\""))
    }

    @Test
    fun `mimo kind profile is unavailable without touching key or transport`() = runTest {
        val mimoProfile = profile.copy(providerKind = AiProviderKind.XIAOMI_MIMO)
        val store = FakeSecretStore("secret".toCharArray())
        val transport = RecordingTransport(AudioHttpResult.Success(byteArrayOf(1, 2)))
        val provider = OpenAiCompatiblePronunciationProvider(
            profileId = mimoProfile.profileId,
            voice = "alloy",
            responseFormat = "mp3",
            profiles = FakeProfileRepository(mimoProfile),
            secrets = AiProfileSecretUseCase(store),
            transport = transport,
            player = RecordingAudioPlayer(AudioPlaybackResult.Played),
        )

        assertIs<PronunciationResult.Unavailable>(provider.speak("hello"))
        assertTrue(store.lastRead == null)
        assertTrue(transport.request == null)
    }

    private fun provider(
        transport: AudioHttpTransport = RecordingTransport(AudioHttpResult.Success(byteArrayOf(1))),
        store: FakeSecretStore = FakeSecretStore("secret".toCharArray()),
        player: AudioPlayer = RecordingAudioPlayer(AudioPlaybackResult.Played),
    ) = OpenAiCompatiblePronunciationProvider(
        profileId = profile.profileId,
        voice = "alloy",
        responseFormat = "mp3",
        profiles = FakeProfileRepository(profile),
        secrets = AiProfileSecretUseCase(store),
        transport = transport,
        player = player,
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

    private class FakeProfileRepository(private val profile: AiProfile) : AiProfileRepository {
        override suspend fun list() = Result.success(listOf(profile))
        override suspend fun find(profileId: String) = Result.success(profile.takeIf { it.profileId == profileId })
        override suspend fun save(profile: AiProfile) = Result.success(Unit)
        override suspend fun delete(profileId: String) = Result.success(Unit)
    }

    private class FakeSecretStore(private val value: CharArray) : SecretStore {
        var lastRead: CharArray? = null
        override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
        override fun read(reference: SecretReference): Result<CharArray> = Result.success(value.copyOf().also { lastRead = it })
        override fun delete(reference: SecretReference) = Result.success(Unit)
        override fun has(reference: SecretReference) = Result.success(true)
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
        override suspend fun send(request: AudioHttpRequest): AudioHttpResult {
            this.request = request
            return outcome
        }
    }
}
