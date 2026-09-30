package com.example.englishlearning.sentence

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * 长难句分析响应契约：状态码先于解析；内容是行协议（角色\t原句片段\t中文解释）。
 * 任何一行不合格就整体拒绝——AI 输出是不可信输入，不渲染半份数据。
 */
class SentenceAnalysisParserTest {

    private fun chatBody(content: String): String {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t")
        return """{"choices":[{"message":{"role":"assistant","content":"$escaped"},"finish_reason":"stop"}]}"""
    }

    private fun line(role: String, text: String, zh: String) = "$role\t$text\t$zh"

    @Test
    fun validSegmentsAreParsedWithTheirRoles() {
        val content = listOf(
            line("主句", "She has the ability to explain complex ideas simply.", "她有把复杂想法解释清楚的能力。"),
            line("从句", "to explain complex ideas", "去解释复杂的想法"),
            line("短语", "complex ideas", "复杂的想法"),
        ).joinToString("\n")
        val segments = SentenceAnalysisParser.parse(200, chatBody(content)).getOrThrow()
        assertEquals(3, segments.size)
        assertEquals(SentenceRole.Main, segments[0].role)
        assertEquals("She has the ability to explain complex ideas simply.", segments[0].text)
        assertEquals("她有把复杂想法解释清楚的能力。", segments[0].explanation)
        assertEquals(SentenceRole.Clause, segments[1].role)
        assertEquals(SentenceRole.Phrase, segments[2].role)
    }

    @Test
    fun connectorRoleIsParsed() {
        val segments = SentenceAnalysisParser.parse(200, chatBody(line("连词", "although", "尽管"))).getOrThrow()
        assertEquals(SentenceRole.Connector, segments.single().role)
    }

    @Test
    fun codeFenceAroundTheContentIsStripped() {
        val content = "```text\n${line("主句", "Birds fly.", "鸟会飞。")}\n```"
        val segments = SentenceAnalysisParser.parse(200, chatBody(content)).getOrThrow()
        assertEquals(1, segments.size)
    }

    @Test
    fun blankLinesAreIgnoredButNothingElseIs() {
        val content = "\n${line("主句", "Birds fly.", "鸟会飞。")}\n\n${line("短语", "birds", "鸟")}\n"
        val segments = SentenceAnalysisParser.parse(200, chatBody(content)).getOrThrow()
        assertEquals(2, segments.size)
    }

    @Test
    fun statusCodesMapBeforeAnyBodyParsing() {
        assertEquals(AiFailure.Unauthorized, failureOf(401, "请重新登录"))
        assertEquals(AiFailure.RateLimited, failureOf(429, "太频繁"))
        assertEquals(AiFailure.ServerUnavailable, failureOf(503, "维护中"))
        assertEquals(AiFailure.InvalidResponse, failureOf(404, "不存在"))
    }

    @Test
    fun malformedEnvelopeIsInvalidResponse() {
        assertEquals(AiFailure.InvalidResponse, failureOf(200, "not-json"))
        assertEquals(AiFailure.InvalidResponse, failureOf(200, """{"choices":[]}"""))
        assertEquals(AiFailure.InvalidResponse, failureOf(200, chatBody("   ")))
    }

    @Test
    fun unknownRoleRejectsTheWholeResponse() {
        val content = listOf(
            line("主句", "Birds fly.", "鸟会飞。"),
            line("神秘成分", "birds", "鸟"),
        ).joinToString("\n")
        assertEquals(AiFailure.InvalidResponse, failureOf(200, chatBody(content)))
    }

    @Test
    fun wrongFieldCountIsRejected() {
        assertEquals(AiFailure.InvalidResponse, failureOf(200, chatBody("主句\t只有两段")))
        assertEquals(AiFailure.InvalidResponse, failureOf(200, chatBody("主句\t一段\t二段\t三段")))
        assertEquals(AiFailure.InvalidResponse, failureOf(200, chatBody("没有分隔符的一行")))
    }

    @Test
    fun blankFieldIsRejected() {
        assertEquals(AiFailure.InvalidResponse, failureOf(200, chatBody(line("主句", "   ", "鸟会飞。"))))
        assertEquals(AiFailure.InvalidResponse, failureOf(200, chatBody(line("主句", "Birds fly.", ""))))
    }

    @Test
    fun overlongFieldIsRejected() {
        val content = line("主句", "a".repeat(SentenceAnalysisParser.maxFieldLength + 1), "太长")
        assertEquals(AiFailure.InvalidResponse, failureOf(200, chatBody(content)))
    }

    @Test
    fun maxFieldLengthIsAccepted() {
        val content = line("主句", "a".repeat(SentenceAnalysisParser.maxFieldLength), "中文")
        assertEquals(1, SentenceAnalysisParser.parse(200, chatBody(content)).getOrThrow().size)
    }

    @Test
    fun overTwentySegmentsAreRejected() {
        val content = (1..21).joinToString("\n") { line("短语", "p$it", "第 $it 段") }
        assertEquals(AiFailure.InvalidResponse, failureOf(200, chatBody(content)))
    }

    @Test
    fun twentyShortSegmentsAreAccepted() {
        val content = (1..20).joinToString("\n") { line("短语", "p$it", "第 $it 段") }
        assertEquals(20, SentenceAnalysisParser.parse(200, chatBody(content)).getOrThrow().size)
    }

    @Test
    fun overlongTotalIsRejected() {
        // 每行都合规（≤300 字符），但总长超过 4000：仍要整体拒绝。
        val content = (1..20).joinToString("\n") { line("短语", "a".repeat(290), "第 $it 段") }
        assertEquals(AiFailure.InvalidResponse, failureOf(200, chatBody(content)))
    }

    private fun failureOf(status: Int, body: String): AiFailure = try {
        SentenceAnalysisParser.parse(status, body).getOrThrow()
        fail("expected failure for status $status")
    } catch (expected: AiException) {
        expected.failure
    }
}
