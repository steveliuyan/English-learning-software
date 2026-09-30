package com.example.englishlearning.wordqa

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

sealed interface WordQaResult {
    data class Answered(val answer: String) : WordQaResult

    /** 需要用户先给出站确认；界面拿 host 与 payloadKind 去弹确认框。 */
    data class NeedsConfirmation(val host: String, val payloadKind: AiPayloadKind) : WordQaResult
    data class Failed(val failure: AiFailure) : WordQaResult
    data class NotConfigured(val reason: WordQaNotConfiguredReason) : WordQaResult
}

/**
 * 词问答自己的「未配置」语义。与 reading 的同名枚举保持概念一致但不共享类型：
 * 两个领域的处置入口可能各自演进，跨域耦合枚举会让一次界面调整变成两处联动。
 */
enum class WordQaNotConfiguredReason {
    NoDefaultProfile,
    DefaultProfileUnavailable,
    InvalidEndpoint,
}

/**
 * 词上下文 AI 问答用例：默认 Profile → 出站确认（发请求前）→ 受控提示词 → 出站 → 解析。
 *
 * 编排顺序是需求本身，与文章生成一致：确认在任何网络字节出去之前；Key 以
 * `CharArray` 流转并在 `finally` 清零；失败分支不落任何数据。
 * 问答不落库——保存笔记由调用方显式触发（用户主动「保存」），不自动写。
 */
class WordQaUseCase(
    private val defaultTextProfile: DefaultTextProfileResolver,
    private val secrets: AiProfileSecretUseCase,
    private val transport: AiHttpTransport,
) {
    suspend fun ask(request: WordQaRequest, confirmedTextHost: String?): WordQaResult {
        val lemma = request.lemma.trim()
        require(lemma.isNotEmpty()) { "lemma" } // 编程契约：词来自词卡数据

        val profile = when (val selected = defaultTextProfile.select()) {
            is DefaultTextProfileResult.Selected -> selected.profile
            DefaultTextProfileResult.NoSelection ->
                return WordQaResult.NotConfigured(WordQaNotConfiguredReason.NoDefaultProfile)
            DefaultTextProfileResult.Unavailable ->
                return WordQaResult.NotConfigured(WordQaNotConfiguredReason.DefaultProfileUnavailable)
            DefaultTextProfileResult.StorageUnavailable ->
                return WordQaResult.NotConfigured(WordQaNotConfiguredReason.DefaultProfileUnavailable)
        }
        val key = secrets.loadKey(profile).getOrElse {
            return WordQaResult.NotConfigured(WordQaNotConfiguredReason.DefaultProfileUnavailable)
        }
        if (key.isEmpty()) {
            key.fill('\u0000')
            return WordQaResult.NotConfigured(WordQaNotConfiguredReason.DefaultProfileUnavailable)
        }

        try {
            // 出站确认在任何字节发出去之前。Endpoint 不合法时连「要确认哪个域名」都答不出。
            val confirmation = requiredConfirmation(profile, AiPayloadKind.Text, confirmedTextHost)
                .getOrElse { return WordQaResult.NotConfigured(WordQaNotConfiguredReason.InvalidEndpoint) }
            when (confirmation) {
                is ConfirmationRequirement.Required ->
                    return WordQaResult.NeedsConfirmation(confirmation.host, confirmation.payloadKind)
                is ConfirmationRequirement.Satisfied -> Unit
            }

            val prompt = WordQaPromptPolicy.build(request.copy(lemma = lemma))
            val outbound = AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt, key)
                .getOrElse { return WordQaResult.NotConfigured(WordQaNotConfiguredReason.InvalidEndpoint) }

            return when (val response = transport.send(outbound)) {
                is AiHttpResult.Responded ->
                    WordQaResponseParser.parse(response.response.statusCode, response.response.body).fold(
                        onSuccess = { WordQaResult.Answered(it.answer) },
                        onFailure = { WordQaResult.Failed((it as? com.example.englishlearning.ai.AiException)?.failure ?: AiFailure.InvalidResponse) },
                    )
                AiHttpResult.NetworkUnavailable -> WordQaResult.Failed(AiFailure.NetworkUnavailable)
                AiHttpResult.TimedOut -> WordQaResult.Failed(AiFailure.Timeout)
                AiHttpResult.ResponseTooLarge -> WordQaResult.Failed(AiFailure.InvalidResponse)
                // 用户取消就是取消：吞成网络失败会把没坏的网络说成坏了。
                AiHttpResult.Cancelled -> throw CancellationException("word qa cancelled")
            }
        } finally {
            key.fill('\u0000')
        }
    }
}
