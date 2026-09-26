package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.ai.net.AudioHttpRequest
import com.example.englishlearning.ai.net.AudioHttpResult
import com.example.englishlearning.ai.net.AudioHttpTransport
import com.example.englishlearning.ai.net.MiMoTtsRequestBuilder
import com.example.englishlearning.ai.net.MiMoTtsResponseParser
import com.example.englishlearning.language.domain.PronunciationCapability
import com.example.englishlearning.language.domain.PronunciationProvider
import com.example.englishlearning.language.domain.PronunciationResult
import kotlinx.coroutines.CancellationException

/**
 * 小米 MiMo v2.5 TTS 发音 Provider（chat/completions 协议，非流式 WAV）。
 *
 * 只服务 `providerKind == XIAOMI_MIMO` 的 Profile：协议不匹配直接不可用，
 * 绝不把 OpenAI 兼容端点当 MiMo 打（真机实证过那样只会得到 404）。
 * 密钥经 [AiProfileSecretUseCase] 读出、进 `api-key` 头、finally 清零；
 * 响应音频与错误正文用完即清零，任何日志只允许状态种类，不允许 body。
 */
class MiMoPronunciationProvider(
    private val profiles: AiProfileRepository,
    private val secrets: AiProfileSecretUseCase,
    private val transport: AudioHttpTransport,
    private val player: AudioPlayer,
    private val profileId: String = DEFAULT_PROFILE_ID,
) : PronunciationProvider {
    override fun capabilities(): Set<PronunciationCapability> = setOf(PronunciationCapability.RemoteAudio)

    override suspend fun speak(text: String): PronunciationResult {
        if (text.isBlank()) return PronunciationResult.Unavailable("blank text")
        val profile = profiles.find(profileId).getOrNull() ?: return PronunciationResult.Unavailable("profile unavailable")
        if (profile.providerKind != AiProviderKind.XIAOMI_MIMO) {
            return PronunciationResult.Unavailable("profile kind mismatch")
        }
        val key = secrets.loadKey(profile).getOrNull() ?: return PronunciationResult.Unavailable("key unavailable")
        return try {
            val request = MiMoTtsRequestBuilder.build(profile, text, key)
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
                    val wav = MiMoTtsResponseParser.extractWav(response.body)
                    if (wav == null || wav.isEmpty()) {
                        PronunciationResult.Failed()
                    } else if (player.play(wav, MiMoTtsRequestBuilder.AUDIO_FORMAT) is AudioPlaybackResult.Played) {
                        PronunciationResult.Played
                    } else {
                        PronunciationResult.Failed()
                    }
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

    companion object {
        const val DEFAULT_PROFILE_ID = "mimo"
    }
}
