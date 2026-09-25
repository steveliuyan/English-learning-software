package com.example.englishlearning.ai.net

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.validateEndpoint
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object TtsRequestBuilder {
    fun joinEndpoint(endpoint: String): Result<String> {
        validateEndpoint(endpoint).getOrElse { return Result.failure(it) }
        val uri = runCatching { java.net.URI(endpoint) }.getOrElse {
            return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
        }
        if (uri.query != null || uri.fragment != null) {
            return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
        }
        return Result.success("${endpoint.trimEnd('/')}/audio/speech")
    }

    fun build(
        profile: AiProfile,
        input: String,
        voice: String,
        responseFormat: String,
        apiKey: CharArray,
    ): Result<AiHttpRequest> {
        if (AiCapability.Speech !in profile.capabilities || input.isBlank()) {
            return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
        }
        val url = joinEndpoint(profile.endpoint).getOrElse { return Result.failure(it) }
        val body = JsonObject(
            mapOf(
                "model" to JsonPrimitive(profile.model),
                "input" to JsonPrimitive(input),
                "voice" to JsonPrimitive(voice),
                "response_format" to JsonPrimitive(responseFormat),
            ),
        ).toString()
        return Result.success(
            AiHttpRequest(
                url = url,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Authorization" to "Bearer ${String(apiKey)}",
                ),
                body = body,
                timeoutSeconds = profile.advancedParameters.timeoutSeconds,
            ),
        )
    }
}
