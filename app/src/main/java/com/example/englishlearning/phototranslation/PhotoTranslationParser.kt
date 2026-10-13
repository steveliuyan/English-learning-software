package com.example.englishlearning.phototranslation

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.ChatResponseEnvelope

/**
 * HTTP 响应 → 译文。状态码先于解析；模型输出是不可信输入：空白、超长或带
 * 脚本/框架等危险标记的内容整体判 InvalidResponse，绝不渲染半份译文。
 */
object PhotoTranslationParser {
    const val maxTranslationLength = 8000

    private val dangerousMarkers = Regex("<script|<iframe|<img|<a\\s|javascript:|on\\w+\\s*=", RegexOption.IGNORE_CASE)

    fun parse(
        httpStatusCode: Int,
        body: String,
    ): Result<String> =
        runCatching {
            when (httpStatusCode) {
                401 -> throw AiException(AiFailure.Unauthorized)
                429 -> throw AiException(AiFailure.RateLimited)
                in 500..599 -> throw AiException(AiFailure.ServerUnavailable)
                else -> Unit
            }
            val content =
                try {
                    ChatResponseEnvelope.extractContent(body)
                } catch (ai: AiException) {
                    throw ai
                } catch (_: Exception) {
                    throw AiException(AiFailure.InvalidResponse)
                }
            val translation = ChatResponseEnvelope.stripCodeFence(content).trim()
            if (translation.isEmpty() || translation.length > maxTranslationLength || dangerousMarkers.containsMatchIn(translation)) {
                throw AiException(AiFailure.InvalidResponse)
            }
            translation
        }
}
