package com.example.englishlearning.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.wordqa.WordQaKind
import com.example.englishlearning.wordqa.WordQaNotConfiguredReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * 词 AI 问答屏：固定问法 chips、纯文本回答、保存笔记、出站确认弹层。
 * 全部文案是常量；AI 失败文案只来自 AiFailureUiText，不拼接运行时数据。
 *
 * **药片只覆盖 AI 问法，`Personal` 不在其中**：`WordAiQaScreen` 自己在药片列表上过滤掉
 * `WordQaKind.Personal`（`wordqa` 侧注释写明「本地个人笔记，不参与 AI 问答」），改由同屏的
 * `word_qa_personal_note` 输入框 + `word_qa_save_personal_note` 按钮承载。
 * 因此药片断言遍历 [aiChipKinds]，并额外钉住「`Personal` 没有药片」——按 `entries` 全遍历会去
 * 找一个主源刻意不渲染的节点而恒红（真机与模拟器同红）。
 */
private val aiChipKinds = WordQaKind.entries.filter { it != WordQaKind.Personal }

@RunWith(AndroidJUnit4::class)
class WordAiQaScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setIdle() = composeRule.setContent {
        WordAiQaScreen(lemma = "apple", state = WordAiQaUiState.Idle, onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = {}, onOpenAiSettings = {})
    }

    @Test
    fun idleShowsTheFixedQuestionChipsAndBack() {
        var backed = false
        composeRule.setContent {
            WordAiQaScreen(lemma = "apple", state = WordAiQaUiState.Idle, onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = { backed = true }, onOpenAiSettings = {})
        }

        aiChipKinds.forEach { kind ->
            composeRule.onNodeWithTag("word_qa_chip_${kind.name}").assertExists().performClick()
        }
        // 反向：个人笔记不走 AI 问法药片（它有专门的输入框），漏了这个过滤就会多渲染一枚药片。
        composeRule.onNodeWithTag("word_qa_chip_${WordQaKind.Personal.name}").assertDoesNotExist()
        // 批 4 Task 7：`Button` 形态返回（「← 返回上一层」）随版式重做退役，返回位归共享 `AppScreenHeader`。
        // 判据从「点旧 tag」收紧到「旧文案与旧 tag 都必须为 0，且必须点得到页头那一枚」。
        composeRule.onNodeWithTag("word_qa_back").assertDoesNotExist()
        composeRule.onNodeWithText("← 返回上一层").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()
        assertEquals(true, backed)
    }

    @Test
    fun answerSitsAboveTheQuestionChipsAndBreakdownLeadsThem() {
        composeRule.setContent {
            WordAiQaScreen(
                lemma = "apple",
                state = WordAiQaUiState.Answered(WordQaKind.Sentence, "I ate an apple."),
                onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = {}, onOpenAiSettings = {},
            )
        }

        // spec §5「回答上移」：回答与问法标签都在三枚问法之上——点完问法不该先滚过一排按钮才看到答案。
        val answerTop = composeRule.onNodeWithTag("word_qa_answer").fetchSemanticsNode().boundsInRoot.top
        val answerKindTop = composeRule.onNodeWithTag("word_qa_answer_kind").fetchSemanticsNode().boundsInRoot.top
        val chipTop = composeRule.onNodeWithTag("word_qa_chip_Breakdown").fetchSemanticsNode().boundsInRoot.top
        assertTrue("问法标签应在问法之上（$answerKindTop vs $chipTop）", answerKindTop < chipTop)
        assertTrue("回答卡应在问法之上（$answerTop vs $chipTop）", answerTop < chipTop)

        // spec §5「首选『拆词义』」：`Breakdown` 必须排在三枚问法的最前面（枚举序里它本来是第二位）。
        val sentenceTop = composeRule.onNodeWithTag("word_qa_chip_Sentence").fetchSemanticsNode().boundsInRoot.top
        val mnemonicTop = composeRule.onNodeWithTag("word_qa_chip_Mnemonic").fetchSemanticsNode().boundsInRoot.top
        assertTrue("「拆词义」应是第一枚问法（$chipTop vs $sentenceTop）", chipTop < sentenceTop)
        assertTrue("「拆词义」应排在「记法联想」之前（$chipTop vs $mnemonicTop）", chipTop < mnemonicTop)
    }

    @Test
    fun savedNotesAreListedAndTheLemmaLineSurvives() {
        composeRule.setContent {
            WordAiQaScreen(
                lemma = "apple",
                state = WordAiQaUiState.Idle,
                onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = {}, onOpenAiSettings = {},
                savedNotes = listOf(
                    com.example.englishlearning.wordqa.WordAiNote(
                        noteId = "n-1",
                        profileId = "p-1",
                        lemma = "apple",
                        kind = WordQaKind.Breakdown,
                        answer = "拆成 ap + ple。",
                        createdAtEpochMillis = 1_700_000_000_000L,
                    ),
                ),
            )
        }

        composeRule.onNodeWithTag("word_qa_lemma").assertExists()
        // 已保存笔记改裸行（批 4 Task 7）：tag 形状不变，容器的观感变了。
        composeRule.onNodeWithTag("word_qa_saved_note_n-1").assertExists()
    }

    @Test
    fun answeredShowsTheAnswerAndAnEnabledSaveButton() {
        var saved = 0
        composeRule.setContent {
            WordAiQaScreen(
                lemma = "apple",
                state = WordAiQaUiState.Answered(WordQaKind.Sentence, "I ate an apple."),
                onAsk = {}, onConfirmOutbound = {}, onSaveNote = { saved += 1 }, onBack = {}, onOpenAiSettings = {},
            )
        }

        composeRule.onNodeWithTag("word_qa_answer").assertExists()
        composeRule.onNodeWithText("I ate an apple.").assertExists()
        composeRule.onNodeWithTag("word_qa_save_note").performScrollTo().assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(1, saved)
    }

    @Test
    fun savedAnswerDisablesTheSaveButton() {
        composeRule.setContent {
            WordAiQaScreen(
                lemma = "apple",
                state = WordAiQaUiState.Answered(WordQaKind.Sentence, "答案", saved = true),
                onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = {}, onOpenAiSettings = {},
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
                onAsk = {}, onConfirmOutbound = { confirmed = it }, onSaveNote = {}, onBack = {}, onOpenAiSettings = {},
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
                onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = {}, onOpenAiSettings = {},
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
                onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = {}, onOpenAiSettings = {},
            )
        }

        composeRule.onNodeWithTag("word_qa_not_configured").assertExists()
    }

    @Test
    fun askingStateShowsProgressAndDisablesChips() {
        composeRule.setContent {
            WordAiQaScreen(lemma = "apple", state = WordAiQaUiState.Asking, onAsk = {}, onConfirmOutbound = {}, onSaveNote = {}, onBack = {}, onOpenAiSettings = {})
        }

        composeRule.onNodeWithText("正在询问…").assertExists()
        aiChipKinds.forEach { kind ->
            composeRule.onNodeWithTag("word_qa_chip_${kind.name}").assertIsNotEnabled()
        }
    }

    @Test
    fun chipsAreEnabledWhenIdle() {
        setIdle()
        aiChipKinds.forEach { kind ->
            composeRule.onNodeWithTag("word_qa_chip_${kind.name}").assertIsEnabled()
        }
    }
}
