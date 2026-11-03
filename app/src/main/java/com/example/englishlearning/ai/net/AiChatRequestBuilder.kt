package com.example.englishlearning.ai.net

import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.validateEndpoint
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 一段已构造好的提示词。只是数据，不携带任何配置或凭据。 */
data class AiPrompt(val system: String, val user: String)

/**
 * 把 profile、参数与提示词拼成一个出站 [AiHttpRequest]。
 *
 * 头部**只有** `Content-Type` 与 `Authorization` 两个——白名单之外的头一个都不放，
 * 这是「拒绝任意请求覆盖」约束在构造层的落实。Key 只进 `Authorization` 头，
 * 永不进 body，因而也永不进错误消息或日志（body 会被响应解析路径触碰）。
 */
object AiChatRequestBuilder {
    /**
     * Endpoint 指向 `{base}/chat/completions`。base 不允许带 query 或 fragment：
     * 请求路径必须完全由本应用决定，用户配置里出现的 `?x=1` 更可能是配置错误或注入尝试。
     */
    fun joinEndpoint(endpoint: String): Result<String> = baseEndpoint(endpoint).map { "$it/chat/completions" }

    /**
     * 连接探活用的模型清单端点：`{base}/models`。
     *
     * 与 [joinEndpoint] 共用同一份 base 校验，**这是刻意的唯一出处**——「HTTPS 公网 + 拒 userInfo +
     * 拒 query/fragment + 尾部斜杠归一」这套规则一旦被第二条路径复制两份，就会出现
     * 「对话能通、探活能绕」或反过来这种只在一侧生效的漂移（`TtsRequestBuilder` 与
     * `ImageGenerationRequestBuilder` 各自的 `joinEndpoint` 是既有重复，登记在同一份决策的结转里，
     * 本批不动它们）。
     */
    fun modelsUrl(endpoint: String): Result<String> = baseEndpoint(endpoint).map { "$it/models" }

    /** 校验 + 归一：返回不带尾斜杠、不带 query/fragment 的 base；不合法即失败。 */
    private fun baseEndpoint(endpoint: String): Result<String> {
        validateEndpoint(endpoint).getOrElse { return Result.failure(it) }
        val uri = runCatching { java.net.URI(endpoint) }.getOrElse {
            return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
        }
        if (uri.query != null || uri.fragment != null) {
            return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
        }
        return Result.success(endpoint.trimEnd('/'))
    }

    fun build(
        profile: AiProfile,
        parameters: AiAdvancedParameters,
        prompt: AiPrompt,
        apiKey: CharArray,
    ): Result<AiHttpRequest> {
        val url = joinEndpoint(profile.endpoint).getOrElse { return Result.failure(it) }
        val body = JsonObject(
            mapOf(
                "model" to JsonPrimitive(profile.model),
                "messages" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "role" to JsonPrimitive("system"),
                                "content" to JsonPrimitive(prompt.system),
                            ),
                        ),
                        JsonObject(
                            mapOf(
                                "role" to JsonPrimitive("user"),
                                "content" to JsonPrimitive(prompt.user),
                            ),
                        ),
                    ),
                ),
                "temperature" to JsonPrimitive(parameters.temperature),
                "top_p" to JsonPrimitive(parameters.topP),
                "max_tokens" to JsonPrimitive(parameters.maxTokens),
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
                timeoutSeconds = parameters.timeoutSeconds,
            ),
        )
    }
}
