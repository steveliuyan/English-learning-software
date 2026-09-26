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
import kotlinx.coroutines.CancellationException

class OpenAiCompatiblePronunciationProvider(
    private val profileId: String,
    private val voice: String,
    private val responseFormat: String,
    private val profiles: AiProfileRepository,
    private val secrets: AiProfileSecretUseCase,
    private val transport: AudioHttpTransport,
    private val player: AudioPlayer,
) : PronunciationProvider {
    override fun capabilities(): Set<PronunciationCapability> = setOf(PronunciationCapability.RemoteAudio)

    override suspend fun speak(text: String): PronunciationResult {
        if (text.isBlank()) return PronunciationResult.Unavailable("blank text")
        val profile = profiles.find(profileId).getOrNull() ?: return PronunciationResult.Unavailable("profile unavailable")
        val key = secrets.loadKey(profile).getOrNull() ?: return PronunciationResult.Unavailable("key unavailable")
        return try {
            val request = TtsRequestBuilder.build(profile, text, voice, responseFormat, key)
                .getOrElse { return PronunciationResult.Failed() }
            val response = transport.send(
                AudioHttpRequest(
                    url = request.url,
                    headers = request.headers,
                    timeoutSeconds = request.timeoutSeconds,
                    method = request.method,
                    body = request.body.toByteArray(),
                ),
            )
            when (response) {
                is AudioHttpResult.Success -> try {
                    if (response.body.isEmpty()) PronunciationResult.Failed()
                    else if (player.play(response.body, responseFormat) is AudioPlaybackResult.Played) PronunciationResult.Played
                    else PronunciationResult.Failed()
                } finally {
                    response.body.fill(0)
                }
                is AudioHttpResult.HttpError -> try {
                    PronunciationResult.Failed()
                } finally {
                    response.body.fill(0)
                }
                else -> PronunciationResult.Failed()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PronunciationResult.Failed()
        } finally {
            key.fill('\u0000')
        }
    }
}
