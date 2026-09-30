package com.example.englishlearning.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.wordqa.WordQaKind
import com.example.englishlearning.wordqa.WordQaNotConfiguredReason
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * 词 AI 问答屏：三个固定问题 chips、纯文本回答、保存笔记、出站确认弹层。
 * 全部文案是常量；AI 失败文案只来自 AiFailureUiText，不拼接运行时数据。
 */
@RunWith(AndroidJUnit4::class)
class WordAiQaScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setIdle() = composeRule.setContent {
        WordAiQaScreen(lemma = "apple", state = WordAiQaUiState.Idle, onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = {})
    }

    @Test
    fun idleShowsTheThreeFixedQuestionChipsAndBack() {
        var backed = false
        composeRule.setContent {
            WordAiQaScreen(lemma = "apple", state = WordAiQaUiState.Idle, onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = { backed = true })
        }

        WordQaKind.entries.forEach { kind ->
            composeRule.onNodeWithTag("word_qa_chip_${kind.name}").assertExists().performClick()
        }
        composeRule.onNodeWithTag("word_qa_back").performClick()
        composeRule.waitForIdle()
        assertEquals(true, backed)
    }

    @Test
    fun answeredShowsTheAnswerAndAnEnabledSaveButton() {
        var saved = 0
        composeRule.setContent {
            WordAiQaScreen(
                lemma = "apple",
                state = WordAiQaUiState.Answered(WordQaKind.Sentence, "I ate an apple."),
                onAsk = {}, onConfirmOutbound = {}, onSaveNote = { saved += 1 }, onBack = {},
            )
        }

        composeRule.onNodeWithTag("word_qa_answer").assertExists()
        composeRule.onNodeWithText("I ate an apple.").assertExists()
        composeRule.onNodeWithTag("word_qa_save_note").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(1, saved)
    }

    @Test
    fun savedAnswerDisablesTheSaveButton() {
        composeRule.setContent {
            WordAiQaScreen(
                lemma = "apple",
                state = WordAiQaUiState.Answered(WordQaKind.Sentence, "答案", saved = true),
                onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = {},
            )
        }

        composeRule.onNodeWithTag("word_qa_save_note").assertIsNotEnabled()
    }

    @Test
    fun confirmationStateOffersConfirmAndDecline() {
        var confirmed: Boolean? = null
        composeRule.setContent {
            WordAiQaScreen(
                lemma = "apple",
                state = WordAiQaUiState.NeedsOutboundConfirmation("api.example.com"),
                onAsk = {}, onConfirmOutbound = { confirmed = it }, onSaveNote = {}, onBack = {},
            )
        }

        composeRule.onNodeWithText("api.example.com", substring = true).assertExists()
        composeRule.onNodeWithTag("word_qa_decline_outbound").performClick()
        composeRule.waitForIdle()
        assertEquals(false, confirmed)
        composeRule.onNodeWithTag("word_qa_confirm_outbound").performClick()
        composeRule.waitForIdle()
        assertEquals(true, confirmed)
    }

    @Test
    fun failureShowsTheFixedTextWithoutInternalDetails() {
        composeRule.setContent {
            WordAiQaScreen(
                lemma = "apple",
                state = WordAiQaUiState.Failed(AiFailure.Unauthorized),
                onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = {},
            )
        }

        composeRule.onNodeWithText("密钥被拒绝，去「设置 · AI」换一份有效的密钥。").assertExists()
        composeRule.onNodeWithText("k3y", substring = true).assertDoesNotExist()
    }

    @Test
    fun notConfiguredShowsTheSetupGuidance() {
        composeRule.setContent {
            WordAiQaScreen(
                lemma = "apple",
                state = WordAiQaUiState.NotConfigured(WordQaNotConfiguredReason.NoDefaultProfile),
                onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = {},
            )
        }

        composeRule.onNodeWithTag("word_qa_not_configured").assertExists()
    }

    @Test
    fun askingStateShowsProgressAndDisablesChips() {
        composeRule.setContent {
            WordAiQaScreen(lemma = "apple", state = WordAiQaUiState.Asking, onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = {})
        }

        composeRule.onNodeWithText("正在询问…").assertExists()
        WordQaKind.entries.forEach { kind ->
            composeRule.onNodeWithTag("word_qa_chip_${kind.name}").assertIsNotEnabled()
        }
    }

    @Test
    fun chipsAreEnabledWhenIdle() {
        setIdle()
        WordQaKind.entries.forEach { kind ->
            composeRule.onNodeWithTag("word_qa_chip_${kind.name}").assertIsEnabled()
        }
    }
}
