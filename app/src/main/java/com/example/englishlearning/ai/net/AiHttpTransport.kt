package com.example.englishlearning.ai.net

/**
 * 一次 AI/抓取出站请求。[headers] 的值必须已经过上游策略审查（`AiRequestPolicy`），
 * 本类型不负责任何校验——它只是运输。
 */
data class AiHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
    val timeoutSeconds: Int,
)

data class AiHttpResponse(
    val statusCode: Int,
    val body: String,
)

/**
 * 出站结果。**非 2xx 不是失败**：状态码与响应体原样交给调用方映射成领域失败
 * （`AiFailure.Unauthorized` 之类），传输层没有资格替业务解释错误。
 */
sealed interface AiHttpResult {
    data class Responded(val response: AiHttpResponse) : AiHttpResult

    /** 连接建立失败（DNS、拒绝、不可达）。 */
    data object NetworkUnavailable : AiHttpResult

    /** 连接或读取超时。 */
    data object TimedOut : AiHttpResult

    /** 协程被取消（用户离开页面）。不是网络错误，不得重试。 */
    data object Cancelled : AiHttpResult

    /** 响应体超过安全上限，读一半即断：上限的意义就是不让远端喂爆内存。 */
    data object ResponseTooLarge : AiHttpResult
}

/**
 * 出站 HTTP 端口。只暴露「发一个请求、拿一个结果」；重试、限流、密钥注入都在调用方。
 * 实现必须**不跟随重定向**：跟随会把「公网域名 → 私网地址」这条被 Endpoint 校验挡掉的
 * 路重新打开。`Cancelled` 必须与 `NetworkUnavailable` 可区分。
 */
interface AiHttpTransport {
    suspend fun send(request: AiHttpRequest): AiHttpResult
}
