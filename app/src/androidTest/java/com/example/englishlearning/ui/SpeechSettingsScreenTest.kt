package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
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
        listOf("azure", "volcengine", "tencent", "bailian", "minimax").forEach {
            composeRule.onNodeWithTag("speech_pending_$it").assertHasNoClickAction()
        }
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
