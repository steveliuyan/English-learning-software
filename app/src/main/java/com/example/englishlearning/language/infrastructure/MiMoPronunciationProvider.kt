package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.net.AudioHttpRequest
import com.example.englishlearning.ai.net.AudioHttpResult
import com.example.englishlearning.ai.net.AudioHttpTransport
import com.example.englishlearning.ai.net.TtsRequestBuilder
import com.example.englishlearning.language.domain.PronunciationCapability
import com.example.englishlearning.language.domain.PronunciationProvider
import com.example.englishlearning.language.domain.PronunciationResult

class MiMoPronunciationProvider(
    private val profiles: AiProfileRepository,
    private val secrets: AiProfileSecretUseCase,
    private val transport: AudioHttpTransport,
    private val profileId: String = DEFAULT_PROFILE_ID,
) : PronunciationProvider {
    override fun capabilities(): Set<PronunciationCapability> = setOf(PronunciationCapability.RemoteAudio)

    override suspend fun speak(text: String): PronunciationResult {
        if (text.isBlank()) return PronunciationResult.Unavailable("blank text")
        val profile = profiles.find(profileId).getOrNull() ?: return PronunciationResult.Unavailable("profile unavailable")
        val key = secrets.loadKey(profile).getOrNull() ?: return PronunciationResult.Unavailable("key unavailable")
        return try {
            val request = TtsRequestBuilder.build(profile, text, VOICE, RESPONSE_FORMAT, key)
                .getOrElse { return PronunciationResult.Failed() }
            val audioRequest = AudioHttpRequest(
                url = request.url,
                headers = request.headers,
                timeoutSeconds = request.timeoutSeconds,
                method = request.method,
                body = request.body.toByteArray(),
            )
            when (transport.send(audioRequest)) {
                is AudioHttpResult.Success -> PronunciationResult.Played
                else -> PronunciationResult.Failed()
            }
        } catch (_: Exception) {
            PronunciationResult.Failed()
        } finally {
            key.fill('\u0000')
        }
    }

    companion object {
        const val DEFAULT_PROFILE_ID = "mimo"
        private const val VOICE = "mimo"
        private const val RESPONSE_FORMAT = "mp3"
    }
}
