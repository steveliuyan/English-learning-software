package com.example.englishlearning.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
            SentenceAnalysisScreen(state = SentenceAnalysisUiState.Idle, onAnalyze = {}, onConfirmOutbound = {}, onBack = { backed++ })
        }

        composeRule.onNodeWithTag("sentence_analysis_input").assertExists()
        composeRule.onNodeWithTag("sentence_analysis_analyze").assertIsNotEnabled()
        composeRule.onNodeWithTag("sentence_analysis_cost_hint").assertExists()
        composeRule.onNodeWithTag("sentence_analysis_back").performClick()
        composeRule.waitForIdle()
        assertEquals(1, backed)
    }

    @Test
    fun typingEnablesAnalyzeAndReportsTheSentence() {
        var analyzed: String? = null
        composeRule.setContent {
            SentenceAnalysisScreen(state = SentenceAnalysisUiState.Idle, onAnalyze = { analyzed = it }, onConfirmOutbound = {}, onBack = {})
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
                onAnalyze = {}, onConfirmOutbound = {}, onBack = {},
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
    fun analyzingShowsProgressAndDisablesInputAndButton() {
        composeRule.setContent {
            SentenceAnalysisScreen(state = SentenceAnalysisUiState.Analyzing, onAnalyze = {}, onConfirmOutbound = {}, onBack = {})
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
                onAnalyze = {}, onConfirmOutbound = { confirmed = it }, onBack = {},
            )
        }

        composeRule.onNodeWithText("api.example.com", substring = true).assertExists()
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
                onAnalyze = {}, onConfirmOutbound = {}, onBack = {},
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
                onAnalyze = {}, onConfirmOutbound = {}, onBack = {},
            )
        }

        composeRule.onNodeWithTag("sentence_analysis_not_configured").assertExists()
    }
}
