package com.example.englishlearning.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.sentence.SentenceAnalysisNotConfiguredReason
import com.example.englishlearning.sentence.SentenceRole
import com.example.englishlearning.sentence.SentenceSegment
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 长难句分析功能页：输入框（数据位，600 上限）、分析按钮、成分卡片渲染、
 * 出站确认弹层。全部文案是常量；AI 失败文案只来自 AiFailureUiText。
 *
 * 批 4 Task 5 口径：返回位归共享 `AppScreenHeader`（contentDescription 固定「返回」，旧
 * `sentence_analysis_back` tag 下线）；结果区末尾两枚文字药片——复制译文走平台剪贴板、
 * 朗读原文只上报宿主（屏内不碰语音引擎）。
 */
@RunWith(AndroidJUnit4::class)
class SentenceAnalysisScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun segments() = listOf(
        SentenceSegment(SentenceRole.Main, "Birds fly.", "鸟会飞。"),
        SentenceSegment(SentenceRole.Phrase, "birds", "鸟"),
    )

    @Test
    fun idleBlankInputDisablesAnalyzeAndBackReportsCallback() {
        var backed = 0
        composeRule.setContent {
            SentenceAnalysisScreen(
                state = SentenceAnalysisUiState.Idle,
                onAnalyze = {}, onConfirmOutbound = {}, onBack = { backed++ }, onOpenAiSettings = {},
                speakMessage = null, onSpeak = {},
            )
        }

        composeRule.onNodeWithTag("sentence_analysis_input").assertExists()
        composeRule.onNodeWithTag("sentence_analysis_analyze").assertIsNotEnabled()
        composeRule.onNodeWithTag("sentence_analysis_cost_hint").assertExists()
        // 返回位只剩页头那一枚：旧药片 tag 与其文案一并下线（2026-11-02 定名「AI 助学」，两代文案一并钉成不存在）。
        composeRule.onNodeWithTag("sentence_analysis_back").assertDoesNotExist()
        composeRule.onNodeWithText("← 返回 AI 学").assertDoesNotExist()
        composeRule.onNodeWithText("← 返回 AI 助学").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()
        assertEquals(1, backed)
    }

    @Test
    fun typingEnablesAnalyzeAndReportsTheSentence() {
        var analyzed: String? = null
        composeRule.setContent {
            SentenceAnalysisScreen(
                state = SentenceAnalysisUiState.Idle,
                onAnalyze = { analyzed = it }, onConfirmOutbound = {}, onBack = {}, onOpenAiSettings = {},
                speakMessage = null, onSpeak = {},
            )
        }

        composeRule.onNodeWithTag("sentence_analysis_input").performTextInput("Birds fly.")
        composeRule.onNodeWithTag("sentence_analysis_analyze").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals("Birds fly.", analyzed)
    }

    @Test
    fun analyzedRendersSegmentsWithRolesAndExplanations() {
        composeRule.setContent {
            SentenceAnalysisScreen(
                state = SentenceAnalysisUiState.Analyzed(segments()),
                onAnalyze = {}, onConfirmOutbound = {}, onBack = {}, onOpenAiSettings = {},
                speakMessage = null, onSpeak = {},
            )
        }

        composeRule.onNodeWithTag("sentence_analysis_result").assertExists()
        composeRule.onNodeWithTag("sentence_analysis_segment_0").assertExists()
        composeRule.onNodeWithTag("sentence_analysis_segment_1").assertExists()
        composeRule.onNodeWithText("主句").assertExists()
        composeRule.onNodeWithText("Birds fly.").assertExists()
        composeRule.onNodeWithText("鸟会飞。").assertExists()
        composeRule.onNodeWithText("短语").assertExists()
    }

    @Test
    fun analyzedOffersCopyAndSpeakAndSpeakReportsTheWholeSentence() {
        var spoken: String? = null
        composeRule.setContent {
            SentenceAnalysisScreen(
                state = SentenceAnalysisUiState.Analyzed(segments()),
                onAnalyze = {}, onConfirmOutbound = {}, onBack = {}, onOpenAiSettings = {},
                speakMessage = null, onSpeak = { spoken = it },
            )
        }

        composeRule.onNodeWithTag("sentence_analysis_copy").assertExists()
        composeRule.onNodeWithTag("sentence_analysis_speak").assertExists()
        // 大字号 2.0 下结果区变高，朗读键落到首屏之外——先滚到它再点（这正是用户的实际动作），
        // 顺带把「可达」钉死：滚不过去就红。
        composeRule.onNodeWithTag("sentence_analysis_speak").performScrollTo().performClick()

        composeRule.waitForIdle()
        // 分段只是看完的视角：朗读对象仍是那句原话（顺序接回，用空格连）。
        assertEquals("Birds fly. birds", spoken)
    }

    @Test
    fun speakFeedbackFromTheHostIsRenderedWhenPresent() {
        composeRule.setContent {
            SentenceAnalysisScreen(
                state = SentenceAnalysisUiState.Analyzed(segments()),
                onAnalyze = {}, onConfirmOutbound = {}, onBack = {}, onOpenAiSettings = {},
                speakMessage = "这句读不了，换个语音服务再试。", onSpeak = {},
            )
        }

        composeRule.onNodeWithTag("sentence_analysis_speak_feedback").assertExists()
        composeRule.onNodeWithText("这句读不了，换个语音服务再试。").assertExists()
    }

    @Test
    fun analyzingShowsProgressAndDisablesInputAndButton() {
        composeRule.setContent {
            SentenceAnalysisScreen(
                state = SentenceAnalysisUiState.Analyzing,
                onAnalyze = {}, onConfirmOutbound = {}, onBack = {}, onOpenAiSettings = {},
                speakMessage = null, onSpeak = {},
            )
        }

        composeRule.onNodeWithText("正在分析…").assertExists()
        composeRule.onNodeWithTag("sentence_analysis_analyze").assertIsNotEnabled()
        composeRule.onNodeWithTag("sentence_analysis_input").assertIsNotEnabled()
    }

    @Test
    fun confirmationStateOffersConfirmAndDecline() {
        var confirmed: Boolean? = null
        composeRule.setContent {
            SentenceAnalysisScreen(
                state = SentenceAnalysisUiState.NeedsOutboundConfirmation("api.example.com"),
                onAnalyze = {}, onConfirmOutbound = { confirmed = it }, onBack = {}, onOpenAiSettings = {},
                speakMessage = null, onSpeak = {},
            )
        }

        composeRule.onNodeWithText("api.example.com", substring = true).assertExists()
        // 费用只讲一处（常驻成本句讲、确认句不复读）由 `SentenceAnalysisLayoutContractTest` 的源码判据钉住：
        // 确认框打开时成本句仍在屏上，语义树里数不出「讲了几次」，所以这里只证确认框在位、两个出口可点。
        composeRule.onNodeWithTag("sentence_analysis_outbound_confirmation_text").assertExists()
        composeRule.onNodeWithTag("sentence_analysis_decline_outbound").performClick()
        composeRule.waitForIdle()
        assertEquals(false, confirmed)
        composeRule.onNodeWithTag("sentence_analysis_confirm_outbound").performClick()
        composeRule.waitForIdle()
        assertEquals(true, confirmed)
    }

    @Test
    fun failureShowsTheFixedTextWithoutInternalDetails() {
        composeRule.setContent {
            SentenceAnalysisScreen(
                state = SentenceAnalysisUiState.Failed(AiFailure.Unauthorized),
                onAnalyze = {}, onConfirmOutbound = {}, onBack = {}, onOpenAiSettings = {},
                speakMessage = null, onSpeak = {},
            )
        }

        composeRule.onNodeWithText("密钥被拒绝，去「设置 · AI」换一份有效的密钥。").assertExists()
        composeRule.onNodeWithText("k3y", substring = true).assertDoesNotExist()
    }

    @Test
    fun notConfiguredShowsTheSetupGuidance() {
        composeRule.setContent {
            SentenceAnalysisScreen(
                state = SentenceAnalysisUiState.NotConfigured(SentenceAnalysisNotConfiguredReason.NoDefaultProfile),
                onAnalyze = {}, onConfirmOutbound = {}, onBack = {}, onOpenAiSettings = {},
                speakMessage = null, onSpeak = {},
            )
        }

        composeRule.onNodeWithTag("sentence_analysis_not_configured").assertExists()
    }
}
