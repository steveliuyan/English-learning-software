package com.example.englishlearning.imagegen

import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.validateEndpoint
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 把 profile 与提示词拼成一个 OpenAI 兼容 `POST {endpoint}/images/generations` 请求。
 *
 * body 只有 `model` / `prompt` / `n` / `size` 四个白名单字段；头部只有
 * `Content-Type` 与 `Authorization`——Key 只进 Authorization 头，永不进 body，
 * 因而也永不进错误消息或日志。
 */
object ImageGenerationRequestBuilder {

    /** 与 chat 端点同一约束：base 不允许带 query 或 fragment，路径完全由本应用决定。 */
    fun joinEndpoint(endpoint: String): Result<String> {
        validateEndpoint(endpoint).getOrElse { return Result.failure(it) }
        val uri = runCatching { java.net.URI(endpoint) }.getOrElse {
            return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
        }
        if (uri.query != null || uri.fragment != null) {
            return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
        }
        return Result.success("${endpoint.trimEnd('/')}/images/generations")
    }

    fun build(profile: AiProfile, prompt: String, apiKey: CharArray): Result<AiHttpRequest> {
        val url = joinEndpoint(profile.endpoint).getOrElse { return Result.failure(it) }
        val body = JsonObject(
            mapOf(
                "model" to JsonPrimitive(profile.model),
                "prompt" to JsonPrimitive(prompt),
                "n" to JsonPrimitive(1),
                "size" to JsonPrimitive("1024x1024"),
            ),
        ).toString()
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer ${String(apiKey)}",
        )
        return Result.success(
            AiHttpRequest(
                url = url,
                headers = headers,
                body = body,
                timeoutSeconds = profile.advancedParameters.timeoutSeconds,
            ),
        )
    }
}
