package com.example.englishlearning.sentence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 长难句分析提示词契约：受控模板纯函数，句子是数据位不是提示词。
 * 句子上限 600 字符，空白拒绝；同一输入产出确定相同的提示。
 */
class SentenceAnalysisPromptPolicyTest {

    @Test
    fun systemPromptIsFixedAndDoesNotVaryWithTheSentence() {
        val first = SentenceAnalysisPromptPolicy.build("She has the ability to explain complex ideas simply.")
        val second = SentenceAnalysisPromptPolicy.build("Totally different sentence here.")
        assertEquals(first.system, second.system)
        assertTrue(first.system.isNotBlank())
    }

    @Test
    fun userPromptCarriesTheSentenceAndTheLineProtocolInstruction() {
        val prompt = SentenceAnalysisPromptPolicy.build("She has the ability to explain complex ideas simply.")
        assertTrue(prompt.user.contains("She has the ability to explain complex ideas simply."))
        assertTrue(prompt.user.contains("主句"))
        assertTrue(prompt.user.contains("\t"))
    }

    @Test
    fun sameInputProducesTheSamePrompt() {
        val sentence = "Wilbur built the first airplane that could fly."
        assertEquals(
            SentenceAnalysisPromptPolicy.build(sentence).user,
            SentenceAnalysisPromptPolicy.build(sentence).user,
        )
    }

    @Test
    fun blankSentencesAreRejected() {
        assertFailsWith<IllegalArgumentException> { SentenceAnalysisPromptPolicy.build("   ") }
        assertFailsWith<IllegalArgumentException> { SentenceAnalysisPromptPolicy.build("") }
    }

    @Test
    fun overlongSentencesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            SentenceAnalysisPromptPolicy.build("a".repeat(SentenceAnalysisPromptPolicy.maxSentenceLength + 1))
        }
    }

    @Test
    fun maxLengthSentenceIsAccepted() {
        val prompt = SentenceAnalysisPromptPolicy.build("a".repeat(SentenceAnalysisPromptPolicy.maxSentenceLength))
        assertTrue(prompt.user.isNotBlank())
    }
}
