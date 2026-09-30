package com.example.englishlearning.sentence

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.ConfirmationRequirement
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.requiredConfirmation
import com.example.englishlearning.ai.net.AiChatRequestBuilder
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import kotlinx.coroutines.CancellationException

sealed interface SentenceAnalysisResult {
    data class Analyzed(val segments: List<SentenceSegment>) : SentenceAnalysisResult

    /** 需要用户先给出站确认；界面拿 host 与 payloadKind 去弹确认框。 */
    data class NeedsConfirmation(val host: String, val payloadKind: AiPayloadKind) : SentenceAnalysisResult
    data class Failed(val failure: AiFailure) : SentenceAnalysisResult
    data class NotConfigured(val reason: SentenceAnalysisNotConfiguredReason) : SentenceAnalysisResult
}

/**
 * 句子分析自己的「未配置」语义。与 wordqa 的同名枚举保持概念一致但不共享类型：
 * 两个领域的处置入口可能各自演进，跨域耦合枚举会让一次界面调整变成两处联动。
 */
enum class SentenceAnalysisNotConfiguredReason {
    NoDefaultProfile,
    DefaultProfileUnavailable,
    InvalidEndpoint,
}

/**
 * 长难句分析用例：默认 Profile → 出站确认（发请求前）→ 受控提示词 → 出站 → 行协议解析。
 *
 * 编排顺序与词问答完全一致：确认在任何网络字节出去之前；Key 以 `CharArray` 流转
 * 并在 `finally` 清零；分析结果不落库——渲染由调用方显式持有。
 */
class SentenceAnalysisUseCase(
    private val defaultTextProfile: DefaultTextProfileResolver,
    private val secrets: AiProfileSecretUseCase,
    private val transport: AiHttpTransport,
) {
    suspend fun analyze(sentence: String, confirmedTextHost: String?): SentenceAnalysisResult {
        require(sentence.trim().isNotEmpty()) { "sentence" } // 编程契约：输入框有长度约束

        val profile = when (val selected = defaultTextProfile.select()) {
            is DefaultTextProfileResult.Selected -> selected.profile
            DefaultTextProfileResult.NoSelection ->
                return SentenceAnalysisResult.NotConfigured(SentenceAnalysisNotConfiguredReason.NoDefaultProfile)
            DefaultTextProfileResult.Unavailable ->
                return SentenceAnalysisResult.NotConfigured(SentenceAnalysisNotConfiguredReason.DefaultProfileUnavailable)
            DefaultTextProfileResult.StorageUnavailable ->
                return SentenceAnalysisResult.NotConfigured(SentenceAnalysisNotConfiguredReason.DefaultProfileUnavailable)
        }
        val key = secrets.loadKey(profile).getOrElse {
            return SentenceAnalysisResult.NotConfigured(SentenceAnalysisNotConfiguredReason.DefaultProfileUnavailable)
        }
        if (key.isEmpty()) {
            key.fill('\u0000')
            return SentenceAnalysisResult.NotConfigured(SentenceAnalysisNotConfiguredReason.DefaultProfileUnavailable)
        }

        try {
            // 出站确认在任何字节发出去之前。Endpoint 不合法时连「要确认哪个域名」都答不出。
            val confirmation = requiredConfirmation(profile, AiPayloadKind.Text, confirmedTextHost)
                .getOrElse { return SentenceAnalysisResult.NotConfigured(SentenceAnalysisNotConfiguredReason.InvalidEndpoint) }
            when (confirmation) {
                is ConfirmationRequirement.Required ->
                    return SentenceAnalysisResult.NeedsConfirmation(confirmation.host, confirmation.payloadKind)
                is ConfirmationRequirement.Satisfied -> Unit
            }

            val prompt = SentenceAnalysisPromptPolicy.build(sentence)
            val outbound = AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt, key)
                .getOrElse { return SentenceAnalysisResult.NotConfigured(SentenceAnalysisNotConfiguredReason.InvalidEndpoint) }

            return when (val response = transport.send(outbound)) {
                is AiHttpResult.Responded ->
                    SentenceAnalysisParser.parse(response.response.statusCode, response.response.body).fold(
                        onSuccess = { SentenceAnalysisResult.Analyzed(it) },
                        onFailure = { SentenceAnalysisResult.Failed((it as? AiException)?.failure ?: AiFailure.InvalidResponse) },
                    )
                AiHttpResult.NetworkUnavailable -> SentenceAnalysisResult.Failed(AiFailure.NetworkUnavailable)
                AiHttpResult.TimedOut -> SentenceAnalysisResult.Failed(AiFailure.Timeout)
                AiHttpResult.ResponseTooLarge -> SentenceAnalysisResult.Failed(AiFailure.InvalidResponse)
                // 用户取消就是取消：吞成网络失败会把没坏的网络说成坏了。
                AiHttpResult.Cancelled -> throw CancellationException("sentence analysis cancelled")
            }
        } finally {
            key.fill('\u0000')
        }
    }
}
