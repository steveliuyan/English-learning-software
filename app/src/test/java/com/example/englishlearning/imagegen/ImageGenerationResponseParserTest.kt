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

    private fun failureOf(status: Int, body: String): AiFailure = try {
        ImageGenerationResponseParser.parse(status, body).getOrThrow()
        fail("expected failure for status $status")
    } catch (expected: AiException) {
        expected.failure
    }
}
