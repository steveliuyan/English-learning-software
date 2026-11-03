package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiChatRequestBuilder
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import kotlinx.coroutines.CancellationException

/**
 * 连接探活的结果。
 *
 * 除 [NeedsConfirmation] 携带确认所需的 host 之外，成员一律**无字段**——沿用 `AiFailure` 那条泄露防线
 * （`ai/AiFailure.kt` 的类注释）：没有能装自由文本的槽位，响应体、Endpoint 与密钥就顺不出错误对象
 * 流到界面、日志或截图。界面侧的文案只能是常量。
 *
 * 分类口径见 `docs/decisions/2026-11-04-ai-profile-vision-and-connection-test.md` §3：
 * 「超时」「连不上」「对方活着但返回错误状态」是三件事，折叠任意两件都会把用户支到错误的处置动作上。
 */
sealed interface ConnectionProbeResult {
    /** 2xx：地址通、密钥被接受。 */
    data object Reachable : ConnectionProbeResult

    /** 401/403：地址通、密钥被拒。 */
    data object Unauthorized : ConnectionProbeResult

    /** 连接或读取超时（等待上限取这份配置自己的 `timeout_seconds`）。 */
    data object Timeout : ConnectionProbeResult

    /** DNS 失败、连接被拒等「压根没连上」。 */
    data object NetworkUnavailable : ConnectionProbeResult

    /** 其余非 2xx（含 404/405/429/5xx）与响应体超上限：对方活着，但不是预期端点。 */
    data object ServiceError : ConnectionProbeResult

    /** Endpoint 未通过端点策略：连「要确认哪个域名」都答不出来。 */
    data object ConfigurationInvalid : ConnectionProbeResult

    /** 该域名还没经用户确认；界面据此弹共享出站确认框，取消即零出站。 */
    data class NeedsConfirmation(val host: String, val payloadKind: AiPayloadKind) : ConnectionProbeResult
}

/**
 * 「测试连接」用例：向 `{endpoint}/models` 发一次最小 `GET`，只验证地址与密钥能不能通。
 *
 * 顺序与其余 AI 用例一致，且每一步都在「字节出去」之前：
 * 出站确认（[requiredConfirmation]，Text 档 = 按域名记一次）→ base 校验取路径（[AiChatRequestBuilder.modelsUrl]）
 * → 请求构造（头部只有 `Authorization`，body 必须为空）→ transport → 状态分类。
 *
 * 与生成类调用的两点区别：不带模型名与消息体，因此**不产生内容计费**；不落任何本地记录
 * （探活是一次诊断动作，写成历史反而会让人以为配置被改过）。
 * 密钥由调用方按「草稿优先」解析后传入（见 `ui/AiProfileSettingsViewModel.testConnection`），
 * 本用例负责在 `finally` 里清零。
 */
class AiConnectionProbe(
    private val transport: AiHttpTransport,
) {
    suspend fun probe(
        profile: AiProfile,
        apiKey: CharArray,
        confirmedTextHost: String?,
    ): ConnectionProbeResult {
        // 编程契约：「没有密钥」属于界面侧判定（`MissingKey`），收到空数组是接线错误，
        // 必须响亮失败，而不是静默发一枚 `Bearer ` 空头出去。
        require(apiKey.isNotEmpty()) { "apiKey" }
        return try {
            val confirmation = requiredConfirmation(profile, AiPayloadKind.Text, confirmedTextHost)
                .getOrElse { return ConnectionProbeResult.ConfigurationInvalid }
            when (confirmation) {
                is ConfirmationRequirement.Required ->
                    return ConnectionProbeResult.NeedsConfirmation(confirmation.host, confirmation.payloadKind)
                is ConfirmationRequirement.Satisfied -> Unit
            }

            val url = AiChatRequestBuilder.modelsUrl(profile.endpoint)
                .getOrElse { return ConnectionProbeResult.ConfigurationInvalid }

            val request = AiHttpRequest(
                url = url,
                headers = mapOf("Authorization" to "Bearer ${String(apiKey)}"),
                body = "",
                timeoutSeconds = profile.advancedParameters.timeoutSeconds,
                method = "GET",
            )
            when (val response = transport.send(request)) {
                is AiHttpResult.Responded -> classifyStatus(response.response.statusCode)
                AiHttpResult.NetworkUnavailable -> ConnectionProbeResult.NetworkUnavailable
                AiHttpResult.TimedOut -> ConnectionProbeResult.Timeout
                // 回来的东西大到必须掐断：这不是「连接正常」，说成可达就是在骗人。
                AiHttpResult.ResponseTooLarge -> ConnectionProbeResult.ServiceError
                // 用户取消就是取消：吞成网络失败会把没坏的网络说成坏了。
                AiHttpResult.Cancelled -> throw CancellationException("connection probe cancelled")
            }
        } finally {
            apiKey.fill('\u0000')
        }
    }

    private fun classifyStatus(statusCode: Int): ConnectionProbeResult = when (statusCode) {
        in 200..299 -> ConnectionProbeResult.Reachable
        401, 403 -> ConnectionProbeResult.Unauthorized
        else -> ConnectionProbeResult.ServiceError
    }
}
