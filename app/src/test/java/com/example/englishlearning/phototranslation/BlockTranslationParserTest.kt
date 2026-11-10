package com.example.englishlearning.phototranslation

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AC3-08 / AC3-09：结构不可信就整批失败（不画半张图）；条数少了或某条译文空白只算该块缺失，
 * 其余照画。危险标记与超长单条一律整批拒绝。
 */
class BlockTranslationParserTest {

    private fun body(content: String) =
        "{\"choices\":[{\"message\":{\"content\":${quote(content)}}}]}"

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun parse(content: String, ids: List<Int> = listOf(1, 2)) =
        BlockTranslationParser.parse(200, body(content), ids)

    private fun failureOf(content: String, ids: List<Int> = listOf(1, 2)): Any? =
        (parse(content, ids).exceptionOrNull() as? AiException)?.failure

    @Test
    fun completeArrayMapsIdToTranslationInRequestOrder() {
        val outcome = parse("""[{"id":2,"translation":"乙"},{"id":1,"translation":"甲"}]""").getOrThrow()

        assertTrue(outcome.isComplete)
        assertEquals(listOf(1, 2), outcome.translations.keys.toList())
        assertEquals(mapOf(1 to "甲", 2 to "乙"), outcome.translations)
        assertEquals(emptyList(), outcome.missing)
    }

    @Test
    fun codeFencedJsonIsUnwrapped() {
        val fenced = listOf(
            "```json",
            """[{"id":1,"translation":"甲"},{"id":2,"translation":"乙"}]""",
            "```",
        ).joinToString("\n")

        val outcome = parse(fenced).getOrThrow()

        assertTrue(outcome.isComplete)
    }

    @Test
    fun fewerEntriesThanRequestedLeaveTheRestUncovered() {
        val outcome = parse("""[{"id":1,"translation":"甲"}]""").getOrThrow()

        assertEquals(mapOf(1 to "甲"), outcome.translations)
        assertEquals(listOf(2), outcome.missing)
        assertEquals(false, outcome.isComplete)
    }

    @Test
    fun blankTranslationCountsAsMissingNotAsBatchFailure() {
        val outcome = parse("""[{"id":1,"translation":"  "},{"id":2,"translation":"乙"}]""").getOrThrow()

        assertEquals(listOf(1), outcome.missing)
        assertEquals(mapOf(2 to "乙"), outcome.translations)
    }

    @Test
    fun untrustworthyStructureFailsTheWholeBatch() {
        val cases = listOf(
            """[{"id":1,"translation":"甲"},{"id":2,"translation":"乙"},{"id":3,"translation":"丙"}]""",
            """[{"id":1,"translation":"甲"},{"id":7,"translation":"乙"}]""",
            """[{"id":1,"translation":"甲"},{"id":1,"translation":"乙"}]""",
            """[{"id":1,"translation":"甲"},{"translation":"乙"}]""",
            """[{"id":"x","translation":"甲"},{"id":2,"translation":"乙"}]""",
            """{"blocks":[{"id":1,"translation":"甲"}]}""",
            "not json at all",
            "",
        )
        for (content in cases) {
            assertEquals(AiFailure.InvalidResponse, failureOf(content), "content=$content")
        }
    }

    @Test
    fun dangerousOrOverlongSingleTranslationFailsTheWholeBatch() {
        val overlong = "a".repeat(BlockTranslationParser.maxTranslationLength + 1)
        val cases = listOf(
            """[{"id":1,"translation":"甲<script>x</script>"},{"id":2,"translation":"乙"}]""",
            """[{"id":1,"translation":"$overlong"},{"id":2,"translation":"乙"}]""",
        )
        for (content in cases) {
            assertEquals(AiFailure.InvalidResponse, failureOf(content), "content=${content.take(40)}")
        }
    }

    @Test
    fun transportStatusesWinOverParsing() {
        assertEquals(
            AiFailure.Unauthorized,
            (BlockTranslationParser.parse(403, """{"code":"GROUP_DELETED"}""", listOf(1)).exceptionOrNull() as? AiException)?.failure,
        )
        assertEquals(
            AiFailure.RateLimited,
            (BlockTranslationParser.parse(429, "{}", listOf(1)).exceptionOrNull() as? AiException)?.failure,
        )
    }
}
