package com.example.englishlearning.wordqa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 词上下文 AI 问答的提示词契约：模板受控（V1 三种固定问题），同一请求
 * 永远产出同一提示；lemma 是唯一必须内插的用户数据，空白值直接拒绝。
 */
class WordQaPromptPolicyTest {

    @Test
    fun sentencePromptCarriesTheLemma() {
        val prompt = WordQaPromptPolicy.build(WordQaRequest(WordQaKind.Sentence, "apple"))
        assertTrue(prompt.user.contains("apple"), "user 提示必须携带目标词")
        assertTrue(prompt.system.isNotBlank())
    }

    @Test
    fun breakdownAndMnemonicPromptsCarryTheLemmaAndDifferFromSentence() {
        val sentence = WordQaPromptPolicy.build(WordQaRequest(WordQaKind.Sentence, "apple"))
        val breakdown = WordQaPromptPolicy.build(WordQaRequest(WordQaKind.Breakdown, "apple"))
        val mnemonic = WordQaPromptPolicy.build(WordQaRequest(WordQaKind.Mnemonic, "apple"))

        assertTrue(breakdown.user.contains("apple"))
        assertTrue(mnemonic.user.contains("apple"))
        // 三种问题必须真的问的是三件不同的事，复制粘贴模板会在这里被抓住。
        assertNotEquals(sentence.user, breakdown.user)
        assertNotEquals(sentence.user, mnemonic.user)
        assertNotEquals(breakdown.user, mnemonic.user)
    }

    @Test
    fun systemPromptIsFixedRegardlessOfInput() {
        val kinds = WordQaKind.entries.filter { it != WordQaKind.Personal }
        val baseline = WordQaPromptPolicy.build(WordQaRequest(kinds.first(), "apple")).system
        kinds.drop(1).forEach { kind ->
            assertEquals(baseline, WordQaPromptPolicy.build(WordQaRequest(kind, "banana")).system)
        }
    }

    @Test
    fun promptIsDeterministicForTheSameInput() {
        val request = WordQaRequest(WordQaKind.Breakdown, "brief", context = "Please send a brief reply.")
        assertEquals(WordQaPromptPolicy.build(request), WordQaPromptPolicy.build(request))
    }

    @Test
    fun contextSentenceEntersTheUserPromptWhenPresent() {
        val withContext = WordQaPromptPolicy.build(
            WordQaRequest(WordQaKind.Sentence, "apple", context = "An apple a day keeps the doctor away."),
        )
        val withoutContext = WordQaPromptPolicy.build(WordQaRequest(WordQaKind.Sentence, "apple"))
        assertTrue(withContext.user.contains("An apple a day keeps the doctor away."), "上下文句必须进入提示")
        assertTrue(withContext.user.length > withoutContext.user.length)
    }

    @Test
    fun overlongContextIsTruncatedNotPassedThrough() {
        val longContext = "word ".repeat(400) // 2000 字符
        val prompt = WordQaPromptPolicy.build(WordQaRequest(WordQaKind.Sentence, "apple", context = longContext))
        // 上下文只是「这个词在哪里见过」的线索，不是要模型复述的正文：必须截断。
        assertTrue(prompt.user.length < 800, "过长的上下文必须截断，防止把整篇文章塞进提示")
    }

    @Test
    fun blankLemmaIsRejected() {
        try {
            WordQaPromptPolicy.build(WordQaRequest(WordQaKind.Sentence, "   "))
            fail("空白 lemma 必须被拒绝")
        } catch (expected: IllegalArgumentException) {
            assertEquals("lemma", expected.message)
        }
    }
}
