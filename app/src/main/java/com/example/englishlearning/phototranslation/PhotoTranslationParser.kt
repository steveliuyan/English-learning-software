package com.example.englishlearning.phototranslation

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiStatusFailure
import com.example.englishlearning.ai.ChatResponseEnvelope

/**
 * HTTP 响应 → 译文。状态码先于解析，状态码归哪一档只由 [AiStatusFailure] 说（决策
 * `docs/decisions/2026-11-08-http-403-auth-failure-single-source.md`）；模型输出是不可信输入：
 * 空白、超长或带脚本/框架等危险标记的内容整体判 InvalidResponse，绝不渲染半份译文。
 */
object PhotoTranslationParser {
    const val maxTranslationLength = 8000

    private val dangerousMarkers = Regex("<script|<iframe|<img|<a\\s|javascript:|on\\w+\\s*=", RegexOption.IGNORE_CASE)

    fun parse(
        httpStatusCode: Int,
        body: String,
    ): Result<String> =
        runCatching {
            AiStatusFailure.of(httpStatusCode)?.let { throw AiException(it) }
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
