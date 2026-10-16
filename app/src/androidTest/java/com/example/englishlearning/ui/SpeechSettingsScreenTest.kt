package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        // v32 语速 Row 把本页抬高，折叠按钮会落在视口外：performClick 前必须 performScrollTo，
        // 否则点击命中别处、展开态不变（与 AppScreenTest 本页族同一写法、同一理由）。
        composeRule.onNodeWithTag("speech_toggle_candidates").performScrollTo().performClick()
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
        composeRule.onNodeWithTag("speech_more_providers").assertHasClickAction().performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_zipvoice").assertExists().assertHasNoClickAction()
        listOf("azure", "volcengine", "tencent", "bailian", "minimax").forEach {
            composeRule.onNodeWithTag("speech_pending_$it").assertExists().assertHasNoClickAction()
        }
        composeRule.onNodeWithTag("speech_more_providers").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_pending_azure").assertDoesNotExist()
    }

    @Test fun openAiEngineRowNoLongerExists() {
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(selectedEngine = PronunciationEngine.MiMo),
                onSelect = { _, _ -> }, onOpenAiProfiles = {}, onAddMiMoPreset = {}, onPreview = {}, onBack = {},
            )
        }
        composeRule.onNodeWithContentDescription("选择 OpenAI TTS").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("选择 系统 TTS").assertExists()
        composeRule.onNodeWithContentDescription("选择 小米 MiMo").assertExists()
    }

    @Test fun candidatesAreCollapsedUntilTheUserExpandsThem() {
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(
                    selectedEngine = PronunciationEngine.MiMo,
                    miMoProfileId = "bound",
                    candidates = listOf(
                        SpeechProfileCandidate("bound", "原配置", SpeechProfileStatus.Available, AiProviderKind.XIAOMI_MIMO),
                        SpeechProfileCandidate("other", "新配置", SpeechProfileStatus.Available, AiProviderKind.XIAOMI_MIMO),
                    ),
                ),
                onSelect = { _, _ -> }, onOpenAiProfiles = {}, onAddMiMoPreset = {}, onPreview = {}, onBack = {},
            )
        }

        // 引擎行直接显示绑定配置名（不展开也能看到）；候选列表默认收起。
        composeRule.onNodeWithTag("speech_engine_status_mimo", useUnmergedTree = true).assertTextEquals("原配置 · 可用")
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
                    selectedEngine = PronunciationEngine.MiMo,
                    candidates = listOf(
                        SpeechProfileCandidate("bound", "原配置", SpeechProfileStatus.Available, AiProviderKind.XIAOMI_MIMO),
                        SpeechProfileCandidate("other", "新配置", SpeechProfileStatus.Available, AiProviderKind.XIAOMI_MIMO),
                    ),
                ),
                onSelect = { engine, profileId -> selected = engine to profileId },
                onOpenAiProfiles = {}, onAddMiMoPreset = {}, onPreview = {}, onBack = {},
            )
        }

        expandCandidates()
        composeRule.onNodeWithContentDescription("选择 新配置").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(PronunciationEngine.MiMo to "other", selected)
        // 选中即收起：列表不再常驻。
        composeRule.onNodeWithTag("speech_profile_other").assertDoesNotExist()
    }

    @Test fun marksOnlyTheBoundCandidateAndKeepsThatMarkWhenSelectionFails() {
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(
                    selectedEngine = PronunciationEngine.MiMo,
                    miMoProfileId = "bound",
                    candidates = listOf(
                        SpeechProfileCandidate("bound", "原配置", SpeechProfileStatus.Available, AiProviderKind.XIAOMI_MIMO),
                        SpeechProfileCandidate("other", "新配置", SpeechProfileStatus.Available, AiProviderKind.XIAOMI_MIMO),
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
        composeRule.onNodeWithContentDescription("选择 新配置").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_profile_bound").assertDoesNotExist()
        expandCandidates()
        composeRule.onNodeWithTag("speech_bound_profile_bound", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("speech_bound_profile_other", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun selectedEngineShowsMissingKeyOnItsOwnRow() {
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(
                    selectedEngine = PronunciationEngine.MiMo,
                    miMoProfileId = "bound",
                    candidates = listOf(SpeechProfileCandidate("bound", "原配置", SpeechProfileStatus.MissingKey, AiProviderKind.XIAOMI_MIMO)),
                ),
                onSelect = { _, _ -> }, onOpenAiProfiles = {}, onAddMiMoPreset = {}, onPreview = {}, onBack = {},
            )
        }
        composeRule.onNodeWithTag("speech_engine_status_mimo", useUnmergedTree = true).assertTextEquals("原配置 · 缺少密钥")
    }

    @Test fun selectedEngineShowsInvalidBindingOnItsOwnRow() {
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "removed"),
                onSelect = { _, _ -> }, onOpenAiProfiles = {}, onAddMiMoPreset = {}, onPreview = {}, onBack = {},
            )
        }
        composeRule.onNodeWithTag("speech_engine_status_mimo", useUnmergedTree = true).assertTextEquals("绑定失效")
    }

    @Test fun profileSelectionUsesRememberedStateAndReportsTheSelectedProfile() {
        var selected: Pair<PronunciationEngine, String?>? = null
        composeRule.setContent {
            var engine by remember { mutableStateOf(PronunciationEngine.SystemTts) }
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(
                    selectedEngine = engine,
                    candidates = listOf(SpeechProfileCandidate("mimo", "MiMo 语音", SpeechProfileStatus.Available, AiProviderKind.XIAOMI_MIMO)),
                ),
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

        composeRule.onNodeWithContentDescription("选择 小米 MiMo").performClick()
        expandCandidates()
        composeRule.onNodeWithContentDescription("选择 MiMo 语音").performScrollTo().performClick()
        assertEquals(PronunciationEngine.MiMo to "mimo", selected)
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
        composeRule.onNodeWithTag("speech_add_mimo_preset").performScrollTo().performClick()
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

    /** D1：自动发音开关行显示当前状态，点击把相反值发给回调。 */
    @Test fun autoSpeakToggleShowsStateAndForwardsTheInvertedValue() {
        var toggled: Boolean? = null
        composeRule.setContent {
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(autoSpeakOnCardReady = true),
                onSelect = { _, _ -> },
                onOpenAiProfiles = {},
                onAddMiMoPreset = {},
                onPreview = {},
                onToggleAutoSpeakOnCardReady = { toggled = it },
                onBack = {},
            )
        }

        // 状态文本在可点击 Row 内被语义合并，必须查未合并树。
        composeRule.onNodeWithTag("speech_auto_speak_state", useUnmergedTree = true).assertTextEquals("开")
        composeRule.onNodeWithTag("speech_auto_speak_toggle").performClick()
        assertEquals(false, toggled)
    }

    /**
     * I-A1（终审 A §2）：语速行**自身**的无障碍描述必须携带当前档位值。
     *
     * 该行是 `clickable` 合并节点，`contentDescription` 会盖掉子节点文字；档位文字
     * （`1x`→`1.25x`…）只在兄弟 `Text` 里、且本行不在任何 liveRegion 常驻容器内。
     * 描述若是静态「发音语速」，读屏用户点完一次就不知道当前倍率——正是决策 §4 给
     * Slider 立下的「描述动态带上当前值」口径（`AppScreen` 的「每日新词目标，当前 N 词」）
     * 在本轮新增循环按钮上的漏配。这里钉两件事：切换前后描述都含当前档位、`stateDescription` 等于档位。
     */
    @Test
    fun speechRateRowAnnouncesTheCurrentRateThroughItsOwnSemantics() {
        val steps = listOf(0.75f, 1f, 1.25f, 1.5f)
        var cycled = 0
        composeRule.setContent {
            // 档位必须存进 Compose 状态：本页是无状态屏幕，state 由外部驱动，
            // 裸 var 不触发重组（本页 autoSpeak/profileSelection 用例同写法）。
            var index by remember { mutableStateOf(1) }
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(speechRate = steps[index]),
                onSelect = { _, _ -> },
                onOpenAiProfiles = {},
                onAddMiMoPreset = {},
                onPreview = {},
                onCycleSpeechRate = { index = (index + 1) % steps.size; cycled++ },
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("speech_rate_toggle").performScrollTo()
        assertEquals(listOf("发音语速，当前 1x"), contentDescriptionsOfRateRow())
        assertEquals("1x", stateDescriptionOfRateRow())

        composeRule.onNodeWithTag("speech_rate_toggle").performClick()
        composeRule.waitForIdle()
        assertEquals(1, cycled)
        // 点击后档位文字在兄弟 Text 里，读屏拿不到——描述与状态必须自己把它带出来。
        composeRule.onNodeWithTag("speech_rate_value", useUnmergedTree = true).assertTextEquals("1.25x")
        assertEquals(listOf("发音语速，当前 1.25x"), contentDescriptionsOfRateRow())
        assertEquals("1.25x", stateDescriptionOfRateRow())

        composeRule.onNodeWithTag("speech_rate_toggle").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("发音语速，当前 1.5x"), contentDescriptionsOfRateRow())
        assertEquals("1.5x", stateDescriptionOfRateRow())
    }

    private fun contentDescriptionsOfRateRow(): List<String> =
        composeRule.onNodeWithTag("speech_rate_toggle")
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
            ?: emptyList()

    private fun stateDescriptionOfRateRow(): String =
        composeRule.onNodeWithTag("speech_rate_toggle")
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
            ?: ""
}
