package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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

    /** 批 5 Task 1：试听卡收进一枚裸行的折叠体，用例要看得见卡内控件，必须先把那行点开。 */
    private fun expandPreview() {
        composeRule.onNodeWithTag("speech_preview_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    /**
     * 当前供应商如实播报；2026-10-25 简化批删掉了「更多供应商」折叠区——六枚未接入厂商只报状态、
     * 点不了，展开之后用户拿到的仍是一条死路。这里反向钉住它们不再出现，
     * 正向仍保留「现在能选什么」（系统 TTS / MiMo）与试听卡。
     * 裁决见 `docs/decisions/2026-10-25-ui-simplification-deletions.md`。
     */
    @Test fun marksCurrentProviderAndKeepsPendingVendorsOut() {
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
        // 批 5 Task 1（spec §5「试听卡降为一行」）：卡默认收起，屏上只有一枚裸行；
        // 展开后输入、按钮、默认文案齐备——两张状态都钉，避免「折叠成死路」被当成重排合格。
        composeRule.onNodeWithTag("speech_preview_toggle").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("speech_preview_input").assertDoesNotExist()
        expandPreview()
        composeRule.onNodeWithTag("speech_preview_input").assertExists()
        composeRule.onNodeWithTag("speech_preview_button").assertHasClickAction()
        composeRule.onNodeWithText("Hello! 你好，世界！").assertExists()
        listOf(
            "speech_more_providers",
            "speech_zipvoice",
            "speech_pending_azure",
            "speech_pending_volcengine",
            "speech_pending_tencent",
            "speech_pending_bailian",
            "speech_pending_minimax",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
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

        composeRule.onNodeWithTag("speech_preview_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("speech_preview_input").performTextReplacement("ability")
        composeRule.onNodeWithTag("speech_preview_button").performClick()
        assertEquals("ability", previewed)
        composeRule.onNodeWithTag("speech_preview_message").assertTextEquals("试听已播放。")
    }

    /**
     * D1：自动发音改成一枚 Material `Switch`（批 5 Task 1，spec §5「开关统一 Switch」）。
     *
     * 旧形态是「整行 clickable + 行尾一枚「开/关」文字」：读屏念得到字、念不出开关状态，
     * 且文字既不可点也不参与语义。新版三件事一起钉：
     *  ① 开关节点本身带开关语义（`assertIsOn` / `assertIsOff`）；
     *  ② 可及名仍是「学习时自动发音」（`AccessibilitySwitchLabelContractTest` 的同一条口径）；
     *  ③ 点开关把相反值发给既有回调 `onToggleAutoSpeakOnCardReady`——状态由外部（VM）回写。
     * 外加反向钉住旧文字锚 `speech_auto_speak_state` 不再存在，防止「文字 + Switch 双写」回潮。
     */
    @Test fun autoSpeakSwitchCarriesItsLabelAndForwardsTheInvertedValue() {
        var toggled: Boolean? = null
        composeRule.setContent {
            var on by remember { mutableStateOf(true) }
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(autoSpeakOnCardReady = on),
                onSelect = { _, _ -> },
                onOpenAiProfiles = {},
                onAddMiMoPreset = {},
                onPreview = {},
                onToggleAutoSpeakOnCardReady = { next ->
                    toggled = next
                    on = next
                },
                onBack = {},
            )
        }

        composeRule.onNodeWithContentDescription("学习时自动发音").assertExists()
        composeRule.onNodeWithTag("speech_auto_speak_state", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("speech_auto_speak_toggle").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("speech_auto_speak_toggle").performClick()
        composeRule.waitForIdle()
        assertEquals(false, toggled)
        composeRule.onNodeWithTag("speech_auto_speak_toggle").assertIsOff()
    }

    /**
     * I-A1（终审 A §2）：语速行的无障碍描述必须携带当前档位值——批 5 Task 1 把承载控件从
     * 「点按循环的行」换成 Material `Slider` 后，这条口径**不变**：滑杆默认播报的是百分比，
     * 用户滑完仍不知道这是「倍速」。所以 `contentDescription` 与 `stateDescription` 继续自带档位。
     *
     * 交互口径随之从「点一下进一档」变成「设置进度」：用例直接打滑杆的 `SetProgress` 语义动作，
     * 与拖动手势走同一条 `onValueChange`，断言三件事——
     *  ① 下发的是档位表里的值（`SpeechRateSteps[index]`，1.25x 而不是 raw 2f 这类刻度值）；
     *  ② 行尾读数跟着变；
     *  ③ 描述与状态在每次变化后都带新值。
     */
    @Test
    fun speechRateSliderAnnouncesTheCurrentRateThroughItsOwnSemantics() {
        val requested = mutableListOf<Float>()
        composeRule.setContent {
            // 档位必须存进 Compose 状态：本页是无状态屏幕，state 由外部驱动，
            // 裸 var 不触发重组（本页 autoSpeak/profileSelection 用例同写法）。
            var rate by remember { mutableStateOf(1f) }
            SpeechSettingsScreen(
                state = SpeechSettingsUiState(speechRate = rate),
                onSelect = { _, _ -> },
                onOpenAiProfiles = {},
                onAddMiMoPreset = {},
                onPreview = {},
                onSetSpeechRate = { next ->
                    requested += next
                    rate = next
                },
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("speech_rate_toggle").performScrollTo()
        assertEquals(listOf("发音语速，当前 1x"), contentDescriptionsOfRateRow())
        assertEquals("1x", stateDescriptionOfRateRow())

        // 滑到第 3 档（刻度 index 2 = 1.25x）。
        composeRule.onNodeWithTag("speech_rate_toggle")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        composeRule.waitForIdle()
        assertEquals(listOf(1.25f), requested)
        // 档位文字在兄弟 Text 里，读屏拿不到——描述与状态必须自己把它带出来。
        composeRule.onNodeWithTag("speech_rate_value", useUnmergedTree = true).assertTextEquals("1.25x")
        assertEquals(listOf("发音语速，当前 1.25x"), contentDescriptionsOfRateRow())
        assertEquals("1.25x", stateDescriptionOfRateRow())

        composeRule.onNodeWithTag("speech_rate_toggle")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        composeRule.waitForIdle()
        assertEquals(listOf(1.25f, 1.5f), requested)
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
