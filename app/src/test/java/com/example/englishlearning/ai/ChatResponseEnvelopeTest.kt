package com.example.englishlearning.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * chat/completions 响应信封的公共提取：四个功能（wordqa / 句子分析 / 拍照翻译 / 生图）共用这一份。
 *
 * 加固动机（真机观察）：不同 OpenAI 兼容服务对同一 `choices[0].message.content` 的写法不一致——
 * 有的一直给字符串，有的在支持多模态时给 `[{type:"text",text:...}]` 数组，有的推理模型把正文塞进
 * `reasoning_content` 而把 `content` 留空，还有一批老网关只给 `choices[0].text`。
 * 原实现只认「字符串 content」，这些**都是合格回答**却被判成「返回内容不合格」。
 * 这里的判据就是：能取出可用文本就不算畸形，只有真的取不出来才 InvalidResponse。
 */
class ChatResponseEnvelopeTest {

    private fun failureOf(body: String): AiFailure =
        assertFailsWith<AiException> { ChatResponseEnvelope.extractContent(body) }.failure

    @Test
    fun `returns a plain string message content`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"hi"}}]}"""
        assertEquals("hi", ChatResponseEnvelope.extractContent(body))
    }

    @Test
    fun `joins text parts when content is an array of parts`() {
        val body =
            """{"choices":[{"message":{"content":[{"type":"text","text":"Hello "},{"type":"text","text":"world"}]}}]}"""
        assertEquals("Hello world", ChatResponseEnvelope.extractContent(body))
    }

    @Test
    fun `ignores non text parts inside the array`() {
        val body =
            """{"choices":[{"message":{"content":[{"type":"image_url","image_url":{"url":"x"}},{"type":"text","text":"只有文字"}]}}]}"""
        assertEquals("只有文字", ChatResponseEnvelope.extractContent(body))
    }

    @Test
    fun `falls back to reasoning content when content is empty`() {
        val body = """{"choices":[{"message":{"content":"","reasoning_content":"think"}}]}"""
        assertEquals("think", ChatResponseEnvelope.extractContent(body))
    }

    @Test
    fun `prefers content over reasoning content`() {
        val body = """{"choices":[{"message":{"content":"answer","reasoning_content":"think"}}]}"""
        assertEquals("answer", ChatResponseEnvelope.extractContent(body))
    }

    @Test
    fun `supports the legacy completion text field`() {
        val body = """{"choices":[{"text":"legacy answer"}]}"""
        assertEquals("legacy answer", ChatResponseEnvelope.extractContent(body))
    }

    @Test
    fun `treats a blank content without fallbacks as invalid`() {
        val body = """{"choices":[{"message":{"content":"   "}}]}"""
        assertEquals(AiFailure.InvalidResponse, failureOf(body))
    }

    @Test
    fun `rejects an envelope without choices`() {
        assertEquals(AiFailure.InvalidResponse, failureOf("""{"choices":[]}"""))
    }

    @Test
    fun `rejects a body that is not json`() {
        assertEquals(AiFailure.InvalidResponse, failureOf("not json"))
    }

    @Test
    fun `stripCodeFence unwraps a surrounding fence`() {
        assertEquals(
            "a watercolor apple",
            ChatResponseEnvelope.stripCodeFence("```text\na watercolor apple\n```"),
        )
    }

    @Test
    fun `stripCodeFence unwraps the fence even with trailing prose`() {
        assertEquals(
            "{\"title\":\"T\"}",
            ChatResponseEnvelope.stripCodeFence("```json\n{\"title\":\"T\"}\n```\n希望有帮助"),
        )
    }

    @Test
    fun `stripCodeFence leaves plain text untouched`() {
        assertEquals("Birds fly.", ChatResponseEnvelope.stripCodeFence("Birds fly."))
    }

    @Test
    fun `stripCodeFence leaves an unclosed fence untouched`() {
        assertEquals("```text\nno close", ChatResponseEnvelope.stripCodeFence("```text\nno close"))
    }
}
