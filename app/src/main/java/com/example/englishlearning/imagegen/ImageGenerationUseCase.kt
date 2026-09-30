package com.example.englishlearning.imagegen

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.ChatResponseEnvelope
import com.example.englishlearning.ai.DefaultImageProfileResult
import com.example.englishlearning.ai.DefaultImageProfileResolver
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.ConfirmationRequirement
import com.example.englishlearning.ai.AiOutboundConfirmation
import com.example.englishlearning.ai.requiredConfirmation
import com.example.englishlearning.ai.resolvedBy
import com.example.englishlearning.ai.net.AiChatRequestBuilder
import com.example.englishlearning.ai.net.AiHttpResponse
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import kotlinx.coroutines.CancellationException

/** 生图工作台自己的「未配置」语义。生图两个阶段（提示词/生图）同域共享这一枚举。 */
enum class ImageStudioNotConfiguredReason {
    NoDefaultProfile,
    DefaultProfileUnavailable,
    InvalidEndpoint,
}

/** 第一阶段（文本生成英文提示词）的结果。 */
sealed interface DrawingPromptResult {
    data class Draft(val prompt: String) : DrawingPromptResult
    data class NeedsConfirmation(val host: String, val payloadKind: AiPayloadKind) : DrawingPromptResult
    data class Failed(val failure: AiFailure) : DrawingPromptResult
    data class NotConfigured(val reason: ImageStudioNotConfiguredReason) : DrawingPromptResult
}

/**
 * 生图第一阶段用例：默认**文本** Profile → 按域名确认一次 → 受控模板出站 →
 * 提取纯文本提示词（≤600 字符）。编排与长难句分析同构。
 */
class DrawingPromptUseCase(
    private val defaultTextProfile: DefaultTextProfileResolver,
    private val secrets: AiProfileSecretUseCase,
    private val transport: AiHttpTransport,
) {
    suspend fun generate(subject: String, confirmedTextHost: String?): DrawingPromptResult {
        // 编程契约：空白/超长主题在出站前即拒绝（输入框有长度约束）。
        val promptPolicy = DrawingPromptPolicy.build(subject)

        val profile = when (val selected = defaultTextProfile.select()) {
            is DefaultTextProfileResult.Selected -> selected.profile
            DefaultTextProfileResult.NoSelection ->
                return DrawingPromptResult.NotConfigured(ImageStudioNotConfiguredReason.NoDefaultProfile)
            DefaultTextProfileResult.Unavailable ->
                return DrawingPromptResult.NotConfigured(ImageStudioNotConfiguredReason.DefaultProfileUnavailable)
            DefaultTextProfileResult.StorageUnavailable ->
                return DrawingPromptResult.NotConfigured(ImageStudioNotConfiguredReason.DefaultProfileUnavailable)
        }
        val key = secrets.loadKey(profile).getOrElse {
            return DrawingPromptResult.NotConfigured(ImageStudioNotConfiguredReason.DefaultProfileUnavailable)
        }
        if (key.isEmpty()) {
            key.fill('\u0000')
            return DrawingPromptResult.NotConfigured(ImageStudioNotConfiguredReason.DefaultProfileUnavailable)
        }

        try {
            val confirmation = requiredConfirmation(profile, AiPayloadKind.Text, confirmedTextHost)
                .getOrElse { return DrawingPromptResult.NotConfigured(ImageStudioNotConfiguredReason.InvalidEndpoint) }
            when (confirmation) {
                is ConfirmationRequirement.Required ->
                    return DrawingPromptResult.NeedsConfirmation(confirmation.host, confirmation.payloadKind)
                is ConfirmationRequirement.Satisfied -> Unit
            }

            val outbound = AiChatRequestBuilder.build(profile, profile.advancedParameters, promptPolicy, key)
                .getOrElse { return DrawingPromptResult.NotConfigured(ImageStudioNotConfiguredReason.InvalidEndpoint) }

            return when (val response = transport.send(outbound)) {
                is AiHttpResult.Responded -> when (response.response.statusCode) {
                    // 状态码先于解析：401/429/5xx 的响应体是服务端错误说明，解析无意义。
                    401 -> DrawingPromptResult.Failed(AiFailure.Unauthorized)
                    429 -> DrawingPromptResult.Failed(AiFailure.RateLimited)
                    in 500..599 -> DrawingPromptResult.Failed(AiFailure.ServerUnavailable)
                    else -> parseDraft(response.response)
                }
                AiHttpResult.NetworkUnavailable -> DrawingPromptResult.Failed(AiFailure.NetworkUnavailable)
                AiHttpResult.TimedOut -> DrawingPromptResult.Failed(AiFailure.Timeout)
                AiHttpResult.ResponseTooLarge -> DrawingPromptResult.Failed(AiFailure.InvalidResponse)
                AiHttpResult.Cancelled -> throw CancellationException("drawing prompt cancelled")
            }
        } finally {
            key.fill('\u0000')
        }
    }

    private fun parseDraft(response: AiHttpResponse): DrawingPromptResult = try {
        val content = ChatResponseEnvelope.extractContent(response.body)
        val prompt = ChatResponseEnvelope.stripCodeFence(content).trim()
        if (prompt.isEmpty() || prompt.length > DrawingPromptPolicy.maxPromptLength) {
            throw AiException(AiFailure.InvalidResponse)
        }
        DrawingPromptResult.Draft(prompt)
    } catch (ai: AiException) {
        DrawingPromptResult.Failed(ai.failure)
    } catch (_: Exception) {
        DrawingPromptResult.Failed(AiFailure.InvalidResponse)
    }
}

/** 第二阶段（生图调用）的结果。 */
sealed interface ImageGenerationResult {
    data class Generated(val image: GeneratedImage) : ImageGenerationResult
    data class NeedsConfirmation(val host: String, val payloadKind: AiPayloadKind) : ImageGenerationResult
    data class Failed(val failure: AiFailure) : ImageGenerationResult
    data class NotConfigured(val reason: ImageStudioNotConfiguredReason) : ImageGenerationResult
}

/**
 * 生图第二阶段用例：默认**生图** Profile → **每次**出站确认（图片不适用「按域名记住」，
 * 也没有跨调用可存储的确认状态——靠调用方每次显式传 [AiOutboundConfirmation] 答复）→
 * OpenAI 兼容 `images/generations` 出站 → 不可信响应解析。
 *
 * 失败不自动重试：生图按张计费，重试等于重复扣费。
 */
class ImageGenerationUseCase(
    private val defaultImageProfile: DefaultImageProfileResolver,
    private val secrets: AiProfileSecretUseCase,
    private val transport: AiHttpTransport,
) {
    suspend fun generate(prompt: String, answer: AiOutboundConfirmation?): ImageGenerationResult {
        // 编程契约：提示词由第一阶段产出（≤600 字符），空白/超长在出站前即拒绝。
        require(prompt.trim().isNotEmpty()) { "prompt" }
        require(prompt.trim().length <= DrawingPromptPolicy.maxPromptLength) { "prompt" }

        val profile = when (val selected = defaultImageProfile.select()) {
            is DefaultImageProfileResult.Selected -> selected.profile
            DefaultImageProfileResult.NoSelection ->
                return ImageGenerationResult.NotConfigured(ImageStudioNotConfiguredReason.NoDefaultProfile)
            DefaultImageProfileResult.Unavailable ->
                return ImageGenerationResult.NotConfigured(ImageStudioNotConfiguredReason.DefaultProfileUnavailable)
            DefaultImageProfileResult.StorageUnavailable ->
                return ImageGenerationResult.NotConfigured(ImageStudioNotConfiguredReason.DefaultProfileUnavailable)
        }
        val key = secrets.loadKey(profile).getOrElse {
            return ImageGenerationResult.NotConfigured(ImageStudioNotConfiguredReason.DefaultProfileUnavailable)
        }
        if (key.isEmpty()) {
            key.fill('\u0000')
            return ImageGenerationResult.NotConfigured(ImageStudioNotConfiguredReason.DefaultProfileUnavailable)
        }

        try {
            // 图片每次都要明确确认；confirmedTextHost 恒为 null，文本域名的旧确认不能顶替。
            val confirmation = requiredConfirmation(profile, AiPayloadKind.Image, confirmedTextHost = null)
                .getOrElse { return ImageGenerationResult.NotConfigured(ImageStudioNotConfiguredReason.InvalidEndpoint) }
                .resolvedBy(answer)
            when (confirmation) {
                is ConfirmationRequirement.Required ->
                    return ImageGenerationResult.NeedsConfirmation(confirmation.host, confirmation.payloadKind)
                is ConfirmationRequirement.Satisfied -> Unit
            }

            val outbound = ImageGenerationRequestBuilder.build(profile, prompt.trim(), key)
                .getOrElse { return ImageGenerationResult.NotConfigured(ImageStudioNotConfiguredReason.InvalidEndpoint) }

            return when (val response = transport.send(outbound)) {
                is AiHttpResult.Responded ->
                    ImageGenerationResponseParser.parse(response.response.statusCode, response.response.body).fold(
                        onSuccess = { ImageGenerationResult.Generated(it) },
                        onFailure = {
                            ImageGenerationResult.Failed((it as? AiException)?.failure ?: AiFailure.InvalidResponse)
                        },
                    )
                AiHttpResult.NetworkUnavailable -> ImageGenerationResult.Failed(AiFailure.NetworkUnavailable)
                AiHttpResult.TimedOut -> ImageGenerationResult.Failed(AiFailure.Timeout)
                AiHttpResult.ResponseTooLarge -> ImageGenerationResult.Failed(AiFailure.InvalidResponse)
                AiHttpResult.Cancelled -> throw CancellationException("image generation cancelled")
            }
        } finally {
            key.fill('\u0000')
        }
    }
}
