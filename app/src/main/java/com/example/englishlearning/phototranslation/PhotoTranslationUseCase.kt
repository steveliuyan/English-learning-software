package com.example.englishlearning.phototranslation

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.ConfirmationRequirement
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.net.AiChatRequestBuilder
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.ai.requiredConfirmation
import kotlinx.coroutines.CancellationException

sealed interface PhotoTranslationResult {
    data class Translated(
        val source: String,
        val target: String,
    ) : PhotoTranslationResult

    /** 需要用户先给出站确认；界面拿 host 与 payloadKind 去弹确认框（AC3-06 每次发送前都要确认）。 */
    data class NeedsConfirmation(
        val host: String,
        val payloadKind: AiPayloadKind,
    ) : PhotoTranslationResult

    data class Failed(
        val failure: AiFailure,
    ) : PhotoTranslationResult

    data class NotConfigured(
        val reason: PhotoTranslationNotConfiguredReason,
    ) : PhotoTranslationResult
}

enum class PhotoTranslationNotConfiguredReason {
    NoDefaultProfile,
    DefaultProfileUnavailable,
    InvalidEndpoint,
}

/**
 * 拍照翻译用例：默认 Profile → 出站确认（发请求前，每次都要）→ 受控提示词 → 出站 → 译文校验。
 *
 * 编排与句子分析/词问答同构：确认在任何网络字节出去之前；Key 以 `CharArray` 流转并在 `finally`
 * 清零；结果不落库。翻译失败（[Failed]）与「未配置 AI」是独立分类，OCR 失败由识别链单独表达。
 */
class PhotoTranslationUseCase(
    private val defaultTextProfile: DefaultTextProfileResolver,
    private val secrets: AiProfileSecretUseCase,
    private val transport: AiHttpTransport,
) {
    suspend fun translate(
        text: String,
        confirmedTextHost: String?,
    ): PhotoTranslationResult {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "text" }
        require(trimmed.length <= PhotoTranslationPromptPolicy.maxTextLength) { "text too long" }

        val profile =
            when (val selected = defaultTextProfile.select()) {
                is DefaultTextProfileResult.Selected -> selected.profile
                DefaultTextProfileResult.NoSelection ->
                    return PhotoTranslationResult.NotConfigured(PhotoTranslationNotConfiguredReason.NoDefaultProfile)
                DefaultTextProfileResult.Unavailable,
                DefaultTextProfileResult.StorageUnavailable,
                ->
                    return PhotoTranslationResult.NotConfigured(PhotoTranslationNotConfiguredReason.DefaultProfileUnavailable)
            }
        val key =
            secrets.loadKey(profile).getOrElse {
                return PhotoTranslationResult.NotConfigured(PhotoTranslationNotConfiguredReason.DefaultProfileUnavailable)
            }
        if (key.isEmpty()) {
            key.fill('\u0000')
            return PhotoTranslationResult.NotConfigured(PhotoTranslationNotConfiguredReason.DefaultProfileUnavailable)
        }

        try {
            val confirmation =
                requiredConfirmation(profile, AiPayloadKind.Text, confirmedTextHost)
                    .getOrElse { return PhotoTranslationResult.NotConfigured(PhotoTranslationNotConfiguredReason.InvalidEndpoint) }
            when (confirmation) {
                is ConfirmationRequirement.Required ->
                    return PhotoTranslationResult.NeedsConfirmation(confirmation.host, confirmation.payloadKind)
                is ConfirmationRequirement.Satisfied -> Unit
            }

            val prompt = PhotoTranslationPromptPolicy.build(trimmed)
            val outbound =
                AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt, key)
                    .getOrElse { return PhotoTranslationResult.NotConfigured(PhotoTranslationNotConfiguredReason.InvalidEndpoint) }

            return when (val response = transport.send(outbound)) {
                is AiHttpResult.Responded ->
                    PhotoTranslationParser.parse(response.response.statusCode, response.response.body).fold(
                        onSuccess = { PhotoTranslationResult.Translated(source = trimmed, target = it) },
                        onFailure = {
                            PhotoTranslationResult.Failed((it as? AiException)?.failure ?: AiFailure.InvalidResponse)
                        },
                    )
                AiHttpResult.NetworkUnavailable -> PhotoTranslationResult.Failed(AiFailure.NetworkUnavailable)
                AiHttpResult.TimedOut -> PhotoTranslationResult.Failed(AiFailure.Timeout)
                AiHttpResult.ResponseTooLarge -> PhotoTranslationResult.Failed(AiFailure.InvalidResponse)
                AiHttpResult.Cancelled -> throw CancellationException("photo translation cancelled")
            }
        } finally {
            key.fill('\u0000')
        }
    }
}
