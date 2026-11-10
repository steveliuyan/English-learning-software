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

sealed interface BlockTranslationResult {
    /** [translations] 的键是块编号（1 起，与上行顺序一致），[missing] 的块不覆盖、其余照画。 */
    data class Translated(
        val translations: Map<Int, String>,
        val missing: List<Int>,
    ) : BlockTranslationResult

    /** 出站确认按域名一次台账（与查词问答/长难句共享，F2-02）；界面拿它弹框，确认后重入本用例。 */
    data class NeedsConfirmation(
        val host: String,
        val payloadKind: AiPayloadKind,
    ) : BlockTranslationResult

    data class Failed(
        val failure: AiFailure,
    ) : BlockTranslationResult

    data class NotConfigured(
        val reason: BlockTranslationNotConfiguredReason,
    ) : BlockTranslationResult
}

enum class BlockTranslationNotConfiguredReason {
    NoDefaultProfile,
    DefaultProfileUnavailable,
    InvalidEndpoint,
}

/**
 * 一屏多块的批量翻译用例（F3-04）：与 PhotoTranslationUseCase 同构，只是上下行都是块数组。
 *
 * 编排：默认 Profile → 出站确认（发请求前，逐次）→ 受控提示词 → 出站 → 逐块校验。
 * 上行只有 OCR 出来的文本，不含图片字节；Key 以 CharArray 流转并在 finally 清零。
 */
class BlockTranslationUseCase(
    private val defaultTextProfile: DefaultTextProfileResolver,
    private val secrets: AiProfileSecretUseCase,
    private val transport: AiHttpTransport,
) {
    suspend fun translate(
        blockTexts: List<String>,
        confirmedTextHosts: Set<String>,
    ): BlockTranslationResult {
        val blocks = BlockTranslationPromptPolicy.blocksOf(blockTexts)

        val profile =
            when (val selected = defaultTextProfile.select()) {
                is DefaultTextProfileResult.Selected -> selected.profile
                DefaultTextProfileResult.NoSelection ->
                    return BlockTranslationResult.NotConfigured(BlockTranslationNotConfiguredReason.NoDefaultProfile)
                DefaultTextProfileResult.Unavailable,
                DefaultTextProfileResult.StorageUnavailable,
                ->
                    return BlockTranslationResult.NotConfigured(BlockTranslationNotConfiguredReason.DefaultProfileUnavailable)
            }
        val key =
            secrets.loadKey(profile).getOrElse {
                return BlockTranslationResult.NotConfigured(BlockTranslationNotConfiguredReason.DefaultProfileUnavailable)
            }
        if (key.isEmpty()) {
            key.fill('\u0000')
            return BlockTranslationResult.NotConfigured(BlockTranslationNotConfiguredReason.DefaultProfileUnavailable)
        }

        try {
            val confirmation =
                requiredConfirmation(profile, AiPayloadKind.Text, confirmedTextHosts)
                    .getOrElse { return BlockTranslationResult.NotConfigured(BlockTranslationNotConfiguredReason.InvalidEndpoint) }
            when (confirmation) {
                is ConfirmationRequirement.Required ->
                    return BlockTranslationResult.NeedsConfirmation(confirmation.host, confirmation.payloadKind)
                is ConfirmationRequirement.Satisfied -> Unit
            }

            val prompt = BlockTranslationPromptPolicy.build(blocks)
            val outbound =
                AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt, key)
                    .getOrElse { return BlockTranslationResult.NotConfigured(BlockTranslationNotConfiguredReason.InvalidEndpoint) }

            return when (val response = transport.send(outbound)) {
                is AiHttpResult.Responded ->
                    BlockTranslationParser.parse(
                        httpStatusCode = response.response.statusCode,
                        body = response.response.body,
                        requestedIds = blocks.indices.map { it + 1 },
                    ).fold(
                        onSuccess = { BlockTranslationResult.Translated(it.translations, it.missing) },
                        onFailure = {
                            BlockTranslationResult.Failed((it as? AiException)?.failure ?: AiFailure.InvalidResponse)
                        },
                    )
                AiHttpResult.NetworkUnavailable -> BlockTranslationResult.Failed(AiFailure.NetworkUnavailable)
                AiHttpResult.TimedOut -> BlockTranslationResult.Failed(AiFailure.Timeout)
                AiHttpResult.ResponseTooLarge -> BlockTranslationResult.Failed(AiFailure.InvalidResponse)
                AiHttpResult.Cancelled -> throw CancellationException("block translation cancelled")
            }
        } finally {
            key.fill('\u0000')
        }
    }
}
