package com.example.englishlearning.wordqa

import com.example.englishlearning.ai.AiFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * 词问答响应契约：状态码先于解析；回答是纯文本（含剥 code fence）；
 * 空白与超长回答一律 InvalidResponse——AI 输出是不可信输入。
 */
class WordQaResponseParserTest {

    private fun chatBody(content: String): String {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"choices":[{"message":{"role":"assistant","content":"$escaped"},"finish_reason":"stop"}]}"""
    }

    @Test
    fun okResponseYieldsTheAssistantText() {
        val parsed = WordQaResponseParser.parse(200, chatBody("I ate an apple.")).getOrThrow()
        assertEquals("I ate an apple.", parsed.answer)
    }

    @Test
    fun codeFenceAroundTheAnswerIsStripped() {
        val parsed = WordQaResponseParser.parse(200, chatBody("```text\nI ate an apple.\n```")).getOrThrow()
        assertEquals("I ate an apple.", parsed.answer)
    }

    @Test
    fun statusCodesMapBeforeAnyBodyParsing() {
        assertEquals(AiFailure.Unauthorized, failureOf(401, "请重新登录"))
        assertEquals(AiFailure.RateLimited, failureOf(429, "太频繁"))
        assertEquals(AiFailure.ServerUnavailable, failureOf(503, "维护中"))
        // 其余非 2xx（如 404）不是三类已知失败：按不合规响应处理
        assertEquals(AiFailure.InvalidResponse, failureOf(404, "不存在"))
    }

    /**
     * N-1（真机 2026-11-08）：网关 `www.sssai.xin` 对失效密钥答 **403** +
     * `{"code":"GROUP_DELETED",...}`，本屏却念「返回内容不合格，可以重新生成」，
     * 把人推向重试一条永远不会成功的路。鉴权档与探活（`AiConnectionProbe` 早就认 403）必须一致。
     * 体就用真机取到的那份答复，判的是这次真撞上的事，不是凭空造的边界。
     */
    @Test
    fun forbiddenIsAnAuthFailureNotAnInvalidResponse() {
        val gatewayBody = """{"code":"GROUP_DELETED","message":"API Key 所属分组已删除"}"""
        assertEquals(AiFailure.Unauthorized, failureOf(403, gatewayBody))
    }

    @Test
    fun malformedBodiesAreInvalidResponses() {
        assertEquals(AiFailure.InvalidResponse, failureOf(200, "not-json"))
        assertEquals(AiFailure.InvalidResponse, failureOf(200, """{"choices":[]}"""))
        assertEquals(AiFailure.InvalidResponse, failureOf(200, chatBody("   ")))
    }

    @Test
    fun overlongAnswersAreRejected() {
        val longAnswer = "a".repeat(WordQaResponseParser.maxAnswerLength + 1)
        assertEquals(AiFailure.InvalidResponse, failureOf(200, chatBody(longAnswer)))
    }

    @Test
    fun maxLengthAnswerIsAccepted() {
        val answer = "a".repeat(WordQaResponseParser.maxAnswerLength)
        assertEquals(answer, WordQaResponseParser.parse(200, chatBody(answer)).getOrThrow().answer)
    }

    private fun failureOf(status: Int, body: String): AiFailure = try {
        WordQaResponseParser.parse(status, body).getOrThrow()
        fail("expected failure for status $status")
    } catch (expected: com.example.englishlearning.ai.AiException) {
        expected.failure
    }
}
