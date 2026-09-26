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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.language.domain.PronunciationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechSettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun expandCandidates() {
        composeRule.onNodeWithTag("speech_toggle_candidates").performClick()
        composeRule.waitForIdle()
    }

    @Test fun marksCurrentProviderAndLeavesPendingProvidersUnclickable() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(PronunciationEngine.SystemTts) }
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(selectedEngine = selected),
                onSelect = { engine, _ -> selected = engine },
                onOpenAiProfiles = {},
                onAddMiMoPreset = {},
                onPreview = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithContentDescription("选择 系统 TTS").assertHasClickAction()
        composeRule.onNodeWithText("当前供应商").assertExists()
        // 试听卡常驻页首：输入、按钮、默认文案齐备。
        composeRule.onNodeWithTag("speech_preview_input").assertExists()
        composeRule.onNodeWithTag("speech_preview_button").assertHasClickAction()
        composeRule.onNodeWithText("Hello! 你好，世界！").assertExists()
        // 待接入厂商默认收进折叠区：一级语音页主体不再被 6 行死占位淹没。
        composeRule.onNodeWithTag("speech_zipvoice").assertDoesNotExist()
        composeRule.onNodeWithTag("speech_pending_azure").assertDoesNotExist()
        composeRule.onNodeWithTag("speech_more_providers").assertHasClickAction().performClick()
        composeRule.onNodeWithTag("speech_zipvoice").assertExists().assertHasNoClickAction()
        listOf("azure", "volcengine", "tencent", "bailian", "minimax").forEach {
            composeRule.onNodeWithTag("speech_pending_$it").assertExists().assertHasNoClickAction()
        }
        composeRule.onNodeWithTag("speech_more_providers").performClick()
        composeRule.onNodeWithTag("speech_pending_azure").assertDoesNotExist()
    }

    @Test fun candidatesAreCollapsedUntilTheUserExpandsThem() {
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(
                    selectedEngine = PronunciationEngine.OpenAi,
                    openAiProfileId = "bound",
                    candidates = listOf(
                        SpeechProfileCandidate("bound", "原配置", SpeechProfileStatus.Available),
                        SpeechProfileCandidate("other", "新配置", SpeechProfileStatus.Available),
                    ),
                ),
                onSelect = { _, _ -> }, onOpenAiProfiles = {}, onAddMiMoPreset = {}, onPreview = {}, onBack = {},
            )
        }

        // 引擎行直接显示绑定配置名（不展开也能看到）；候选列表默认收起。
        composeRule.onNodeWithTag("speech_engine_status_openai", useUnmergedTree = true).assertTextEquals("原配置 · 可用")
        composeRule.onNodeWithTag("speech_profile_bound").assertDoesNotExist()
        composeRule.onNodeWithTag("speech_open_ai_profiles").assertDoesNotExist()

        expandCandidates()
        composeRule.onNodeWithTag("speech_profile_bound").assertExists()
        composeRule.onNodeWithTag("speech_profile_other").assertExists()
        composeRule.onNodeWithTag("speech_open_ai_profiles").assertExists()
    }

    @Test fun selectingACandidateBindsItAndCollapsesTheList() {
        var selected: Pair<PronunciationEngine, String?>? = null
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(
                    selectedEngine = PronunciationEngine.OpenAi,
                    candidates = listOf(
                        SpeechProfileCandidate("bound", "原配置", SpeechProfileStatus.Available),
                        SpeechProfileCandidate("other", "新配置", SpeechProfileStatus.Available),
                    ),
                ),
                onSelect = { engine, profileId -> selected = engine to profileId },
                onOpenAiProfiles = {}, onAddMiMoPreset = {}, onPreview = {}, onBack = {},
            )
        }

        expandCandidates()
        composeRule.onNodeWithContentDescription("选择 新配置").performClick()
        composeRule.waitForIdle()

        assertEquals(PronunciationEngine.OpenAi to "other", selected)
        // 选中即收起：列表不再常驻。
        composeRule.onNodeWithTag("speech_profile_other").assertDoesNotExist()
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
                onAddMiMoPreset = {},
                onPreview = {},
                onBack = {},
            )
        }

        expandCandidates()
        composeRule.onNodeWithTag("speech_profile_bound").assertExists()
        composeRule.onNodeWithTag("speech_bound_profile_bound", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("speech_bound_profile_other", useUnmergedTree = true).assertDoesNotExist()
        // 点击即收起（屏幕拿不到保存结果，失败与否都收起）；保存失败时状态不变，
        // 重新展开后旧绑定标记必须还留在「原配置」上。
        composeRule.onNodeWithContentDescription("选择 新配置").performClick()
        composeRule.onNodeWithTag("speech_profile_bound").assertDoesNotExist()
        expandCandidates()
        composeRule.onNodeWithTag("speech_bound_profile_bound", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("speech_bound_profile_other", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun selectedEngineShowsMissingKeyOnItsOwnRow() {
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(
                    selectedEngine = PronunciationEngine.OpenAi,
                    openAiProfileId = "bound",
                    candidates = listOf(SpeechProfileCandidate("bound", "原配置", SpeechProfileStatus.MissingKey)),
                ),
                onSelect = { _, _ -> }, onOpenAiProfiles = {}, onAddMiMoPreset = {}, onPreview = {}, onBack = {},
            )
        }
        composeRule.onNodeWithTag("speech_engine_status_openai", useUnmergedTree = true).assertTextEquals("原配置 · 缺少密钥")
    }

    @Test fun selectedEngineShowsInvalidBindingOnItsOwnRow() {
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(selectedEngine = PronunciationEngine.OpenAi, openAiProfileId = "removed"),
                onSelect = { _, _ -> }, onOpenAiProfiles = {}, onAddMiMoPreset = {}, onPreview = {}, onBack = {},
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
                onAddMiMoPreset = {},
                onPreview = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithContentDescription("选择 OpenAI TTS").performClick()
        expandCandidates()
        composeRule.onNodeWithContentDescription("选择 OpenAI").performClick()
        assertEquals(PronunciationEngine.OpenAi to "openai", selected)
    }

    @Test fun mimoEngineWithoutCompatibleCandidateOffersOneTapPresetInsideTheExpandedList() {
        var presetRequested = false
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(
                    selectedEngine = PronunciationEngine.MiMo,
                    candidates = listOf(
                        // 只有 OpenAI 兼容 Profile：协议不匹配，不能算 MiMo 可选配置。
                        SpeechProfileCandidate("openai", "中转", SpeechProfileStatus.Available, AiProviderKind.OPENAI_COMPATIBLE),
                    ),
                ),
                onSelect = { _, _ -> },
                onOpenAiProfiles = {},
                onAddMiMoPreset = { presetRequested = true },
                onPreview = {},
                onBack = {},
            )
        }

        // 收起状态下预设入口不可见；MiMo 槽位没有任何协议匹配的候选 → 如实显示「未配置」
        // （「绑定失效」只用于绑定 id 还在但候选消失的情形，与 VM 测试语义一致）。
        composeRule.onNodeWithTag("speech_add_mimo_preset").assertDoesNotExist()
        composeRule.onNodeWithTag("speech_engine_status_mimo", useUnmergedTree = true).assertTextEquals("未配置")

        expandCandidates()
        composeRule.onNodeWithTag("speech_add_mimo_preset").assertExists()
        // 协议不匹配的候选不得出现在 MiMo 的候选列表里。
        composeRule.onNodeWithTag("speech_profile_openai").assertDoesNotExist()
        composeRule.onNodeWithTag("speech_add_mimo_preset").performClick()
        assertTrue(presetRequested)
    }

    @Test fun mimoEngineWithCompatibleCandidateHidesPresetEntry() {
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(
                    selectedEngine = PronunciationEngine.MiMo,
                    miMoProfileId = "mimo-1",
                    candidates = listOf(
                        SpeechProfileCandidate("mimo-1", "小米 MiMo TTS", SpeechProfileStatus.MissingKey, AiProviderKind.XIAOMI_MIMO),
                        SpeechProfileCandidate("openai", "中转", SpeechProfileStatus.Available, AiProviderKind.OPENAI_COMPATIBLE),
                    ),
                ),
                onSelect = { _, _ -> }, onOpenAiProfiles = {}, onAddMiMoPreset = {}, onPreview = {}, onBack = {},
            )
        }

        expandCandidates()
        composeRule.onNodeWithTag("speech_add_mimo_preset").assertDoesNotExist()
        composeRule.onNodeWithTag("speech_profile_mimo-1").assertExists()
        composeRule.onNodeWithTag("speech_profile_openai").assertDoesNotExist()
    }

    @Test fun openAiEngineNeverOffersMimoPresetEntry() {
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(selectedEngine = PronunciationEngine.OpenAi),
                onSelect = { _, _ -> }, onOpenAiProfiles = {}, onAddMiMoPreset = {}, onPreview = {}, onBack = {},
            )
        }

        expandCandidates()
        composeRule.onNodeWithTag("speech_add_mimo_preset").assertDoesNotExist()
    }

    @Test fun previewCardSendsTheTypedTextToTheCallbackAndShowsFeedback() {
        var previewed: String? = null
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(previewMessage = "试听已播放。"),
                onSelect = { _, _ -> },
                onOpenAiProfiles = {},
                onAddMiMoPreset = {},
                onPreview = { previewed = it },
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("speech_preview_input").performTextReplacement("ability")
        composeRule.onNodeWithTag("speech_preview_button").performClick()
        assertEquals("ability", previewed)
        composeRule.onNodeWithTag("speech_preview_message").assertTextEquals("试听已播放。")
    }
}
