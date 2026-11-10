package com.example.englishlearning.phototranslation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 块文本是数据位：编号化、一行一块、长度受限，超限在发请求前就拦。 */
class BlockTranslationPromptPolicyTest {

    @Test
    fun eachBlockIsNumberedOnItsOwnLine() {
        val prompt = BlockTranslationPromptPolicy.build(listOf("Hello there", "Good morning"))

        val lines = prompt.user.lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("把下面每一块文本翻译成简体中文"))
        assertEquals("1. Hello there", lines[1])
        assertEquals("2. Good morning", lines[2])
    }

    @Test
    fun blockInternalNewlinesCollapseSoOneBlockStaysOneLine() {
        val prompt = BlockTranslationPromptPolicy.build(listOf("Hello\nworld\r\nagain"))

        val lines = prompt.user.lines()
        assertEquals(2, lines.size)
        assertEquals("1. Hello world again", lines[1])
    }

    @Test
    fun systemPromptPinsJsonArrayContract() {
        val prompt = BlockTranslationPromptPolicy.build(listOf("Hello"))

        assertTrue(prompt.system.contains("JSON"))
        assertTrue(prompt.system.contains("translation"))
        assertTrue(prompt.system.contains("id"))
        assertTrue(prompt.system.contains("简体中文"))
    }

    @Test
    fun oversizedCountsAndBlankBlocksAreRejectedBeforeAnyRequest() {
        assertFailsWith<IllegalArgumentException> {
            BlockTranslationPromptPolicy.blocksOf(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            BlockTranslationPromptPolicy.blocksOf(List(BlockTranslationPromptPolicy.maxBlocks + 1) { "w" })
        }
        assertFailsWith<IllegalArgumentException> {
            BlockTranslationPromptPolicy.blocksOf(listOf("ok", "   "))
        }
        assertFailsWith<IllegalArgumentException> {
            BlockTranslationPromptPolicy.blocksOf(listOf("a".repeat(BlockTranslationPromptPolicy.maxBlockTextLength + 1)))
        }
        assertFailsWith<IllegalArgumentException> {
            BlockTranslationPromptPolicy.blocksOf(List(BlockTranslationPromptPolicy.maxBlocks) { "a".repeat(200) })
        }
    }
}
