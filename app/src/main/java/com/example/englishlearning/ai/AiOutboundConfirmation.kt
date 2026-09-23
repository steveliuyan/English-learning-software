package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.validateEndpoint
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException

/** 出站载荷类型。文本按域名确认一次，图片每次都问。 */
enum class AiPayloadKind {
    Text,
    Image,
}

/**
 * 用户在出站确认上的答复。
 *
 * 只记录域名与载荷类型。Key 不属于这个模型，因此确认状态不可能把密钥带到别处。
 */
data class AiOutboundConfirmation(
    val host: String,
    val payloadKind: AiPayloadKind,
    val confirmed: Boolean,
)

/** 一次出站调用当前的确认状态。 */
sealed interface ConfirmationRequirement {
    /** 还没拿到用户对这一目标的确认，调用必须先停下来问。 */
    data class Required(
        val host: String,
        val payloadKind: AiPayloadKind,
    ) : ConfirmationRequirement

    /** 已有可复用的确认，可以直接出站。 */
    data class Satisfied(
        val host: String,
        val payloadKind: AiPayloadKind,
    ) : ConfirmationRequirement
}

/**
 * 判断这次出站是否还需要用户确认。
 *
 * @param confirmedTextHost 本次会话里**已经确认过的文本出站域名**，没有则为 `null`。
 *   这里刻意收成 `String?` 而不是计划里写的 `Boolean`：布尔量分不清确认的是哪个域名，
 *   用户换过 Endpoint 之后旧域名的确认会被顺延到新域名上，等于绕过了「首次调用第三方
 *   Endpoint 前展示域名」的要求。用域名本身做比较，换域名就一定重新问。
 *
 * 返回 `Result` 而不是裸的 [ConfirmationRequirement]：Endpoint 不合法时连「要确认哪个域名」
 * 都答不出来，这必须是一个失败，不能靠第三种状态去表示。
 */
fun requiredConfirmation(
    profile: AiProfile,
    payloadKind: AiPayloadKind,
    confirmedTextHost: String?,
): Result<ConfirmationRequirement> {
    val uri = validateEndpoint(profile.endpoint).getOrElse { return Result.failure(it) }
    val host = uri.host ?: return Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
    val requirement = when {
        // 图片每一次都要明确确认，已确认过的文本域名不能顶替。
        payloadKind == AiPayloadKind.Image -> ConfirmationRequirement.Required(host, payloadKind)
        confirmedTextHost == host -> ConfirmationRequirement.Satisfied(host, payloadKind)
        else -> ConfirmationRequirement.Required(host, payloadKind)
    }
    return Result.success(requirement)
}

/**
 * 用用户的答复推进确认状态。
 *
 * 答复与当前目标不一致时（换了域名、或确认的是文本却要发图片）保持 [ConfirmationRequirement.Required]，
 * 否则「确认 A 域名」会被当成「授权 B 域名」。
 */
fun ConfirmationRequirement.resolvedBy(answer: AiOutboundConfirmation?): ConfirmationRequirement = when (this) {
    is ConfirmationRequirement.Satisfied -> this
    is ConfirmationRequirement.Required ->
        if (answer != null && answer.confirmed && answer.host == host && answer.payloadKind == payloadKind) {
            ConfirmationRequirement.Satisfied(host, payloadKind)
        } else {
            this
        }
}
