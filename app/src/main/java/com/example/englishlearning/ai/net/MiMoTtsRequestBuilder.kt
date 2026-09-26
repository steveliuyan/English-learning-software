package com.example.englishlearning.ai.net

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.validateEndpoint
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 小米 MiMo v2.5 TTS 请求构造。MiMo 的语音合成承载在 chat/completions 协议上，与
 * OpenAI `/audio/speech` 完全不同：合成文本放 `assistant` role，鉴权用 `api-key` 头，
 * 音频参数放 `audio` 对象。密钥只进 `api-key` 头，绝不进 body、URL 或日志。
 */
object MiMoTtsRequestBuilder {
    /** 英文文本音色（预置音色，官方列表内）。 */
    const val VOICE_ENGLISH = "Mia"

    /** 中文文本音色（预置音色「冰糖」；显式写死，不用 mimo_default，避免集群路由差异）。 */
    const val VOICE_CHINESE = "冰糖"

    /** 非流式一次性返回整段 WAV，第一版不做 SSE 流式。 */
    const val AUDIO_FORMAT = "wav"

    fun build(
        profile: AiProfile,
        input: String,
        apiKey: CharArray,
    ): Result<AiHttpRequest> {
        if (AiCapability.Speech !in profile.capabilities || input.isBlank()) {
            return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
        }
        val uri = validateEndpoint(profile.endpoint).getOrElse { return Result.failure(it) }
        if (uri.query != null || uri.fragment != null) {
            return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
        }
        // 音色优先级：Profile 显式选择 > 按语言自动（中文冰糖 / 英文 Mia）。
        val voice = profile.voice.takeIf { it.isNotBlank() }
            ?: if (input.any { it.isCjkIdeograph() }) VOICE_CHINESE else VOICE_ENGLISH
        val body = buildJsonObject {
            put("model", profile.model)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "assistant")
                            put("content", input)
                        },
                    )
                },
            )
            put(
                "audio",
                buildJsonObject {
                    put("format", AUDIO_FORMAT)
                    put("voice", voice)
                },
            )
            put("stream", false)
        }
        return Result.success(
            AiHttpRequest(
                url = profile.endpoint.trimEnd('/'),
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "api-key" to String(apiKey),
                ),
                body = JsonObject(body).toString(),
                timeoutSeconds = profile.advancedParameters.timeoutSeconds,
            ),
        )
    }

    private fun Char.isCjkIdeograph(): Boolean =
        (this in '\u4E00'..'\u9FFF') || (this in '\u3400'..'\u4DBF')
}
