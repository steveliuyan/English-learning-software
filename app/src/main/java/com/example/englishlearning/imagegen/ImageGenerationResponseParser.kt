package com.example.englishlearning.imagegen

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiStatusFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * images/generations 响应 → [GeneratedImage]。
 *
 * 映射顺序与文章/问答解析器一致：**状态码先于解析**，且归档判据只在 [AiStatusFailure] 一处
 * （决策 `docs/decisions/2026-11-08-http-403-auth-failure-single-source.md`：网关对失效密钥的 403
 * 曾被这里折成「内容不合格」）。已知失败档的响应体是
 * 服务端错误说明，解析无意义；其余不合规状态与一切结构畸形（坏 JSON、空 data、
 * 两个产物字段都不可用）统一 [AiFailure.InvalidResponse]。
 *
 * `data[0]` 里优先取 `b64_json`，其次 `url`（必须 https）；b64 存在但超长
 * 判 InvalidResponse 而不是回退取 url——超限即不可信。AI 输出是不可信输入。
 */
object ImageGenerationResponseParser {

    /** b64 大小上限：约 10MB 原始图像（10 * 1024 * 1024 字节 ≈ base64 后 13981012 字符）。 */
    const val maxBase64Length = 13_981_012

    fun parse(httpStatusCode: Int, body: String): Result<GeneratedImage> = runCatching {
        AiStatusFailure.of(httpStatusCode)?.let { throw AiException(it) }
        if (httpStatusCode !in 200..299) throw AiException(AiFailure.InvalidResponse) // 非 2xx 且非已知档：不解析
        try {
            parseBody(body)
        } catch (ai: AiException) {
            throw ai
        } catch (_: Exception) {
            throw AiException(AiFailure.InvalidResponse)
        }
    }

    private fun parseBody(body: String): GeneratedImage {
        val envelope = Json.parseToJsonElement(body) as? JsonObject ?: throw invalid()
        val data = envelope.get("data") as? JsonArray ?: throw invalid()
        val item = data.firstOrNull() as? JsonObject ?: throw invalid()
        val b64 = (item.get("b64_json") as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (b64 != null) {
            if (b64.isBlank() || b64.length > maxBase64Length) throw invalid()
            return GeneratedImage.Base64(b64)
        }
        val url = (item.get("url") as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw invalid()
        if (!url.startsWith("https://")) throw invalid()
        return GeneratedImage.Url(url)
    }

    private fun invalid() = AiException(AiFailure.InvalidResponse)
}
