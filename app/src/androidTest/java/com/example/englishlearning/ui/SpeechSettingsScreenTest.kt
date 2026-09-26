package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.language.domain.PronunciationEngine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class SpeechSettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun marksCurrentProviderAndLeavesPendingProvidersUnclickable() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(PronunciationEngine.SystemTts) }
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(selectedEngine = selected),
                onSelect = { engine, _ -> selected = engine },
                onOpenAiProfiles = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithContentDescription("选择 系统 TTS").assertHasClickAction()
        composeRule.onNodeWithText("当前供应商").assertExists()
        composeRule.onNodeWithTag("speech_zipvoice").assertHasNoClickAction()
        listOf("azure", "volcengine", "tencent", "bailian", "minimax").forEach {
            composeRule.onNodeWithTag("speech_pending_$it").assertHasNoClickAction()
        }
    }

    @Test fun marksOnlyTheBoundCandidateAndKeepsThatMarkWhenSelectionFails() {
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(
                    selectedEngine = PronunciationEngine.OpenAi,
                    openAiProfileId = "bound",
                    candidates = listOf(
                        SpeechProfileCandidate("bound", "原配置", SpeechProfileStatus.Available),
                        SpeechProfileCandidate("other", "新配置", SpeechProfileStatus.Available),
                    ),
                    message = "本机存储暂时不可用，改动没有保存。",
                ),
                onSelect = { _, _ -> },
                onOpenAiProfiles = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("speech_profile_bound").assertExists()
        composeRule.onNodeWithTag("speech_bound_profile_bound", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("speech_bound_profile_other", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("选择 新配置").performClick()
        composeRule.onNodeWithTag("speech_bound_profile_bound", useUnmergedTree = true).assertExists()
    }

    @Test fun selectedEngineShowsMissingKeyOnItsOwnRow() {
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(
                    selectedEngine = PronunciationEngine.OpenAi,
                    openAiProfileId = "bound",
                    candidates = listOf(SpeechProfileCandidate("bound", "原配置", SpeechProfileStatus.MissingKey)),
                ),
                onSelect = { _, _ -> }, onOpenAiProfiles = {}, onBack = {},
            )
        }
        composeRule.onNodeWithTag("speech_engine_status_openai", useUnmergedTree = true).assertTextEquals("缺少密钥")
    }

    @Test fun selectedEngineShowsInvalidBindingOnItsOwnRow() {
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(selectedEngine = PronunciationEngine.OpenAi, openAiProfileId = "removed"),
                onSelect = { _, _ -> }, onOpenAiProfiles = {}, onBack = {},
            )
        }
        composeRule.onNodeWithTag("speech_engine_status_openai", useUnmergedTree = true).assertTextEquals("绑定失效")
    }

    @Test fun profileSelectionUsesRememberedStateAndReportsTheSelectedProfile() {
        var selected: Pair<PronunciationEngine, String?>? = null
        composeRule.setContent {
            var engine by remember { mutableStateOf(PronunciationEngine.SystemTts) }
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(selectedEngine = engine, candidates = listOf(SpeechProfileCandidate("openai", "OpenAI", SpeechProfileStatus.Available))),
                onSelect = { next, profile ->
                    engine = next
                    selected = next to profile
                },
                onOpenAiProfiles = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithContentDescription("选择 OpenAI TTS").performClick()
        composeRule.onNodeWithContentDescription("选择 OpenAI").performClick()
        assertEquals(PronunciationEngine.OpenAi to "openai", selected)
    }
}
