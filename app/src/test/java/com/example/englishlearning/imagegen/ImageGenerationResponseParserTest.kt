package com.example.englishlearning.imagegen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure

/**
 * 生图响应契约：状态码先于解析；`data[0]` 里优先取 `b64_json`，其次 `url`
 * （必须 https）；大小与格式超限一律 InvalidResponse——AI 输出是不可信输入。
 */
class ImageGenerationResponseParserTest {

    @Test
    fun base64DataIsPreferred() {
        val parsed = ImageGenerationResponseParser.parse(200, """{"data":[{"b64_json":"QUJD"}]}""").getOrThrow()
        assertEquals(GeneratedImage.Base64("QUJD"), parsed)
    }

    @Test
    fun httpsUrlIsAcceptedWhenBase64IsAbsent() {
        val parsed = ImageGenerationResponseParser.parse(200, """{"data":[{"url":"https://cdn.example.com/img.png"}]}""").getOrThrow()
        assertEquals(GeneratedImage.Url("https://cdn.example.com/img.png"), parsed)
    }

    @Test
    fun plainHttpUrlIsRejected() {
        assertEquals(AiFailure.InvalidResponse, failureOf(200, """{"data":[{"url":"http://cdn.example.com/img.png"}]}"""))
    }

    @Test
    fun emptyDataArrayIsInvalidResponse() {
        assertEquals(AiFailure.InvalidResponse, failureOf(200, """{"data":[]}"""))
        assertEquals(AiFailure.InvalidResponse, failureOf(200, "not-json"))
        assertEquals(AiFailure.InvalidResponse, failureOf(200, """{"data":[{}]}"""))
    }

    @Test
    fun overlongBase64IsRejected() {
        val huge = "a".repeat(ImageGenerationResponseParser.maxBase64Length + 1)
        assertEquals(AiFailure.InvalidResponse, failureOf(200, """{"data":[{"b64_json":"$huge"}]}"""))
    }

    @Test
    fun maxBase64LengthIsAccepted() {
        val content = "a".repeat(ImageGenerationResponseParser.maxBase64Length)
        val parsed = ImageGenerationResponseParser.parse(200, """{"data":[{"b64_json":"$content"}]}""").getOrThrow()
        assertEquals(GeneratedImage.Base64(content), parsed)
    }

    @Test
    fun statusCodesMapBeforeAnyBodyParsing() {
        assertEquals(AiFailure.Unauthorized, failureOf(401, "请重新登录"))
        assertEquals(AiFailure.RateLimited, failureOf(429, "太频繁"))
        assertEquals(AiFailure.ServerUnavailable, failureOf(503, "维护中"))
        assertEquals(AiFailure.InvalidResponse, failureOf(404, "不存在"))
    }

    /**
     * N-1（真机 2026-11-08）：带密钥的出图请求被网关以 **403** 挡在模型之前（`GROUP_DELETED`），
     * 这里曾把它折成「返回内容不合格，可以重新生成」——密钥失效时重新生成永远白跑一次。
     * 注意与 `GeneratedImageStore` 区分：那一处下载的是 CDN 图片 URL、**不带密钥**，
     * 它的 401/403 说的是链接不可取，不是密钥被拒，故不入共享映射（决策 §5）。
     */
    @Test
    fun forbiddenIsAnAuthFailureNotAnInvalidResponse() {
        val gatewayBody = """{"code":"GROUP_DELETED","message":"API Key 所属分组已删除"}"""
        assertEquals(AiFailure.Unauthorized, failureOf(403, gatewayBody))
    }

    private fun failureOf(status: Int, body: String): AiFailure = try {
        ImageGenerationResponseParser.parse(status, body).getOrThrow()
        fail("expected failure for status $status")
    } catch (expected: AiException) {
        expected.failure
    }
}
