package com.example.englishlearning.imagegen

import com.example.englishlearning.ai.net.AiPrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 生图提示词的受控模板契约：主题是数据位（≤100 字符），指令固定；
 * 同一输入产出确定相同的提示。
 */
class DrawingPromptPolicyTest {

    @Test
    fun systemPromptIsFixedAndDoesNotVaryWithTheSubject() {
        val first = DrawingPromptPolicy.build("apple")
        val second = DrawingPromptPolicy.build("volcano")
        assertEquals(first.system, second.system)
        assertTrue(first.system.isNotBlank())
    }

    @Test
    fun userPromptCarriesTheSubjectAndTheOutputConstraint() {
        val prompt: AiPrompt = DrawingPromptPolicy.build("apple")
        assertTrue(prompt.user.contains("apple"))
        assertTrue(prompt.user.contains("英文"))
        assertTrue(prompt.user.contains("600"))
    }

    @Test
    fun sameInputProducesTheSamePrompt() {
        assertEquals(
            DrawingPromptPolicy.build("apple").user,
            DrawingPromptPolicy.build("apple").user,
        )
    }

    @Test
    fun blankSubjectsAreRejected() {
        assertFailsWith<IllegalArgumentException> { DrawingPromptPolicy.build("   ") }
        assertFailsWith<IllegalArgumentException> { DrawingPromptPolicy.build("") }
    }

    @Test
    fun overlongSubjectsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            DrawingPromptPolicy.build("a".repeat(DrawingPromptPolicy.maxSubjectLength + 1))
        }
    }

    @Test
    fun maxLengthSubjectIsAccepted() {
        assertTrue(DrawingPromptPolicy.build("a".repeat(DrawingPromptPolicy.maxSubjectLength)).user.isNotBlank())
    }
}
