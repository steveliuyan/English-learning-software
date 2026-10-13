package com.example.englishlearning.phototranslation

import com.example.englishlearning.ai.AiFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 模型输出是不可信输入：围栏要剥、危险标记与空白要拒、超长要拒，且绝不产出半份译文。 */
class PhotoTranslationParserTest {

    private fun body(content: String) = """{"choices":[{"message":{"content":${json(content)}}}]}"""

    private fun json(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    @Test
    fun plainContentBecomesTheTranslation() {
        val result = PhotoTranslationParser.parse(200, body("鸟儿会飞。"))

        assertTrue(result.isSuccess)
        assertEquals("鸟儿会飞。", result.getOrThrow())
    }

    @Test
    fun codeFencedTranslationIsUnwrapped() {
        val result = PhotoTranslationParser.parse(200, body("```\n鸟儿会飞。\n```"))

        assertEquals("鸟儿会飞。", result.getOrThrow())
    }

    @Test
    fun blankMultiParagraphOrScriptBearingOutputIsRejected() {
        for (content in listOf("", "  \n ", "译文。\n<script>x</script>", "<iframe src=evil>", "a".repeat(9000))) {
            val result = PhotoTranslationParser.parse(200, body(content))

            assertEquals(
                AiFailure.InvalidResponse,
                result.exceptionOrNull()?.let { (it as? com.example.englishlearning.ai.AiException)?.failure },
                "content=${content.take(24)}",
            )
        }
    }

    @Test
    fun transportErrorStatusesMapToActionableFailures() {
        assertEquals(AiFailure.Unauthorized, PhotoTranslationParser.parse(401, "{}").failure())
        assertEquals(AiFailure.RateLimited, PhotoTranslationParser.parse(429, "{}").failure())
        assertEquals(AiFailure.ServerUnavailable, PhotoTranslationParser.parse(500, "{}").failure())
        assertEquals(AiFailure.InvalidResponse, PhotoTranslationParser.parse(200, "not json").failure())
    }

    private fun <T> Result<T>.failure(): Any? =
        (exceptionOrNull() as? com.example.englishlearning.ai.AiException)?.failure
}
