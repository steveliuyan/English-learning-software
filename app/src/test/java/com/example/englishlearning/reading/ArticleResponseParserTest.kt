package com.example.englishlearning.reading

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleResponseParserTest {
    private fun envelope(content: String) =
        """{"choices":[{"message":{"role":"assistant","content":${JsonPrimitive(content)}}}]}"""

    private val good = """{"title":"A Day","english":"Apple is brief.","chinese":"苹果很简短。"}"""

    /** 从失败结果里取出 [AiFailure]；断言失败类型用，失败本身也必须是 AiException。 */
    private fun Result<RawArticle>.appFailure(): AiFailure {
        val error = exceptionOrNull() ?: error("expected a failure but the result succeeded")
        return (error as? AiException)?.failure ?: error("expected AiException but was $error")
    }

    @Test
    fun extractsTheInnerJsonFromTheOpenAiEnvelope() {
        val raw = ArticleResponseParser.parse(200, envelope(good)).getOrThrow()
        assertEquals("A Day", raw.title)
        assertEquals("Apple is brief.", raw.englishText)
        assertEquals("苹果很简短。", raw.chineseText)
    }

    @Test
    fun extractsTheInnerJsonEvenWhenWrappedInACodeFence() {
        val fenced = "```json\n$good\n```"
        val raw = ArticleResponseParser.parse(200, envelope(fenced)).getOrThrow()
        assertEquals("A Day", raw.title)
    }

    @Test
    fun ignoresAnyCoordinatesTheModelVolunteers() {
        // spec：禁止信任模型提供的高亮坐标。多出来的字段只能被丢掉，绝不能进入领域模型。
        val withOffsets = """{"title":"T","english":"apple","chinese":"苹果","highlights":[{"start":0,"end":5}]}"""
        val raw = ArticleResponseParser.parse(200, envelope(withOffsets)).getOrThrow()
        assertEquals("apple", raw.englishText)
    }

    @Test
    fun maps401ToUnauthorized() {
        assertEquals(AiFailure.Unauthorized, ArticleResponseParser.parse(401, "{}").appFailure())
    }

    @Test
    fun maps429ToRateLimited() {
        assertEquals(AiFailure.RateLimited, ArticleResponseParser.parse(429, "{}").appFailure())
    }

    @Test
    fun maps5xxToServerUnavailable() {
        assertEquals(AiFailure.ServerUnavailable, ArticleResponseParser.parse(503, "{}").appFailure())
    }

    @Test
    fun mapsAnUnknownStatusToInvalidResponse() {
        assertEquals(AiFailure.InvalidResponse, ArticleResponseParser.parse(418, "{}").appFailure())
    }

    @Test
    fun rejectsABodyThatIsNotJson() {
        assertTrue(ArticleResponseParser.parse(200, "not json").isFailure)
    }

    @Test
    fun rejectsAnEnvelopeWithoutChoices() {
        assertTrue(ArticleResponseParser.parse(200, """{"choices":[]}""").isFailure)
    }

    @Test
    fun rejectsContentThatIsNotTheRequestedJson() {
        assertTrue(ArticleResponseParser.parse(200, envelope("Sure! Here is your article:")).isFailure)
    }

    @Test
    fun rejectsContentMissingAnyOfTheThreeFields() {
        assertTrue(ArticleResponseParser.parse(200, envelope("""{"title":"T","english":"E"}""")).isFailure)
    }
}
