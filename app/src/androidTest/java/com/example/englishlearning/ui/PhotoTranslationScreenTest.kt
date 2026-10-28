package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.language.domain.OcrEngineKind
import com.example.englishlearning.language.domain.OcrModelAssetStatus
import com.example.englishlearning.phototranslation.PhotoTranslationNotConfiguredReason
import com.example.englishlearning.phototranslation.SavedPhotoTranslation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 批 4 Task 4：拍照翻译屏在真机语义树上的形状。
 *
 * 这一屏此前**没有任何 connected 覆盖**（`androidTest` 里搜不到 `photo_translation_` 前缀的标签），
 * 重排后最容易悄悄坏掉的是四件事，正好各钉一条：
 *  1. 返回位归共享页头（旧「← 返回 AI 学」药片与 `photo_translation_back` 标签同时下线，不许回来）；
 *  2. 「5 块收成历史一行 + 离线模型折叠」——两块**默认收起**，收起时展开体一字不组合；
 *  3. 历史「删除」只发请求、不自己执行、也不自己弹框（确认框归宿主 `AppScreen`）；
 *  4. 出站确认框的三要素（域名文案 + 取消 + 确认）仍在，且取消/确认各自回调。
 *
 * 版式侧的逐字判据（页头恰一处、容器三档、`Mint*` 读点上限、成本句位置、31 枚 tag 各恰一处）
 * 由 JVM 的 `PhotoTranslationLayoutContractTest` 承担；这一层只管「语义树上真的点得到、真的收起」。
 */
@RunWith(AndroidJUnit4::class)
class PhotoTranslationScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setScreen(
        state: PhotoTranslationUiState = PhotoTranslationUiState(),
        speakMessage: String? = null,
        onTranslate: () -> Unit = {},
        onConfirmOutbound: (Boolean) -> Unit = {},
        onSave: () -> Unit = {},
        onRequestDeleteSaved: (String) -> Unit = {},
        onOpenDictionary: (String) -> Unit = {},
        onSpeak: (String) -> Unit = {},
        onBack: () -> Unit = {},
        onOpenAiSettings: () -> Unit = {},
    ) {
        composeRule.setContent {
            PhotoTranslationScreen(
                state = state,
                onImageBytes = {},
                onOcrTextEdited = {},
                onTranslate = onTranslate,
                onConfirmOutbound = onConfirmOutbound,
                onRefreshModelStatus = {},
                onInstallModel = {},
                onUninstallModel = {},
                onOpenDictionary = onOpenDictionary,
                speakMessage = speakMessage,
                onSpeak = onSpeak,
                onSave = onSave,
                onRequestDeleteSaved = onRequestDeleteSaved,
                onBack = onBack,
                onOpenAiSettings = onOpenAiSettings,
            )
        }
    }

    /**
     * 返回位必须走共享页头：可及名固定「返回」，旧版那枚带箭头的文字药片（连同它的 `photo_translation_back`
     * 标签）一起下线。旧文案「← 返回 AI 学」若回来，用户会以为这一屏还挂在 AI 学下面。
     *
     * 2026-11-02 定名「AI 助学」后，同款药片若被重写会读作「← 返回 AI 助学」，故两代文案一并钉成不存在。
     */
    @Test fun header_owns_the_back_action_and_the_old_back_pill_is_gone() {
        var backs = 0
        setScreen(onBack = { backs++ })

        composeRule.onNodeWithTag("photo_translation_screen").assertExists()
        composeRule.onNodeWithTag("app_screen_header").assertExists()
        composeRule.onNodeWithContentDescription("返回").assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, backs)
        composeRule.onNodeWithTag("photo_translation_back").assertDoesNotExist()
        composeRule.onNodeWithText("← 返回 AI 学").assertDoesNotExist()
        composeRule.onNodeWithText("← 返回 AI 助学").assertDoesNotExist()
    }

    /**
     * 两块折叠行默认收起：收起时展开体（历史列表 / 模型卡 / 隐私说明）**一字不组合**。
     * 这一条挡的是「折叠写成了纯装饰」——那会让两个展开体永远摊在页面上，等于没收。
     */
    @Test fun history_and_model_folds_start_collapsed_and_hide_their_bodies() {
        setScreen(state = PhotoTranslationUiState(history = listOf(record("r1"))))

        composeRule.onNodeWithTag("photo_translation_history_entry").performScrollTo().assertExists()
        composeRule.onNodeWithTag("photo_translation_model_entry").performScrollTo().assertExists()

        composeRule.onNodeWithTag("photo_translation_history_list").assertDoesNotExist()
        composeRule.onNodeWithTag("photo_translation_model_card").assertDoesNotExist()
        composeRule.onNodeWithTag("photo_translation_privacy_note").assertDoesNotExist()
        // 旧版把历史摊成一整张卡；那枚标签必须一起消失，否则「折叠」只是多长了一枚入口行。
        composeRule.onNodeWithTag("photo_translation_history_card").assertDoesNotExist()
    }

    /** 展开历史才渲染记录，且不牵连模型折叠（两块状态各自独立）。 */
    @Test fun expanding_the_history_row_reveals_the_records_only() {
        setScreen(state = PhotoTranslationUiState(history = listOf(record("r1"))))

        composeRule.onNodeWithTag("photo_translation_history_entry").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("photo_translation_history_list").assertExists()
        composeRule.onNodeWithTag("photo_translation_history_r1").assertExists()
        composeRule.onNodeWithText("A page of English prose.").assertExists()
        composeRule.onNodeWithTag("photo_translation_model_card").assertDoesNotExist()
    }

    /** 展开模型折叠：模型卡与隐私说明一起出现（隐私说明跟着模型走，不再独占一块）。 */
    @Test fun expanding_the_model_row_reveals_the_offline_card_and_the_privacy_note() {
        setScreen(state = PhotoTranslationUiState(modelStatus = OcrModelAssetStatus.NotInstalled))

        composeRule.onNodeWithTag("photo_translation_model_entry").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("photo_translation_model_card").assertExists()
        composeRule.onNodeWithTag("photo_translation_privacy_note").assertExists()
        composeRule.onNodeWithText("下载模型（约 4.1 MB）").assertExists()
        composeRule.onNodeWithTag("photo_translation_history_list").assertDoesNotExist()
    }

    /**
     * 历史「删除」是不可逆动作：这一层只把 recordId 交出去，**自己不删、也不自己弹框**。
     * 确认框归宿主 `AppScreen` 的 `ConfirmDestructiveDialog`（`AppScreenOverlayContainerContractTest`
     * 钉住了那枚守卫站点），所以这里同时断言屏内两枚确认框按钮都不在树上、记录也还在。
     */
    @Test fun deleting_a_saved_record_only_reports_a_request_and_opens_no_dialog() {
        var requested: String? = null
        setScreen(
            state = PhotoTranslationUiState(history = listOf(record("r1"))),
            onRequestDeleteSaved = { requested = it },
        )

        composeRule.onNodeWithTag("photo_translation_history_entry").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("photo_translation_history_delete_r1").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("r1", requested)
        composeRule.onNodeWithTag("confirm_destructive_confirm").assertDoesNotExist()
        composeRule.onNodeWithTag("confirm_destructive_dismiss").assertDoesNotExist()
        // 没有被就地删掉：状态是宿主的，确认前那一条必须原地还在。
        composeRule.onNodeWithTag("photo_translation_history_r1").assertExists()
    }

    /**
     * 出站确认门每次都过：`pendingConfirmationHost` 一到就弹玻璃确认框，域名写进正文，
     * 取消 / 确认各走 `onConfirmOutbound(false/true)`。这一条挡的是「重排时把确认框搬丢」。
     */
    @Test fun outbound_confirmation_still_gates_the_request() {
        // 用可变状态驱动：`setContent` 每测只能调一次，而且「关掉再弹」本身就是宿主的真实行为
        // （`AppScreen` 在回调里清 `pendingConfirmationHost`，下一次翻译再置回来）。
        var decisions = mutableListOf<Boolean>()
        var state by mutableStateOf(
            PhotoTranslationUiState(
                ocrText = "A page of English prose.",
                pendingConfirmationHost = "api.example.com",
            ),
        )
        composeRule.setContent {
            PhotoTranslationScreen(
                state = state,
                onImageBytes = {},
                onOcrTextEdited = {},
                onTranslate = {},
                onConfirmOutbound = {
                    decisions += it
                    state = state.copy(pendingConfirmationHost = null)
                },
                onRefreshModelStatus = {},
                onInstallModel = {},
                onUninstallModel = {},
                onOpenDictionary = {},
                speakMessage = null,
                onSpeak = {},
                onSave = {},
                onRequestDeleteSaved = {},
                onBack = {},
                onOpenAiSettings = {},
            )
        }

        composeRule.onNodeWithTag("photo_translation_outbound_confirmation_text").assertExists()
        composeRule.onNodeWithText("api.example.com", substring = true).assertExists()

        composeRule.onNodeWithTag("photo_translation_decline_outbound").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(false), decisions)
        composeRule.onNodeWithTag("photo_translation_outbound_confirmation_text").assertDoesNotExist()

        state = state.copy(pendingConfirmationHost = "api.example.com")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("photo_translation_confirm_outbound").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(false, true), decisions)
    }

    /**
     * 成本句与引擎标签各只留一处：旧版把成本句写了两遍（翻译按钮上下各一）、引擎标签也重复。
     * 语义树上的「枚数」就是这一裁决的可执行面。
     */
    @Test fun cost_hint_and_engine_label_appear_exactly_once() {
        setScreen(
            state = PhotoTranslationUiState(
                ocrText = "A page of English prose.",
                engine = OcrEngineKind.MlKit,
                translation = "A page of English prose." to "一页英文散文。",
            ),
        )

        composeRule.onNodeWithTag("photo_translation_cost_hint").performScrollTo().assertExists()
        composeRule.onNodeWithTag("photo_translation_engine_label").performScrollTo().assertExists()
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithTag("photo_translation_cost_hint").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithTag("photo_translation_engine_label").fetchSemanticsNodes().size)
    }

    /** 未配置时的出口仍在：共享 `NotConfiguredCard` 的那枚「去设置」必须接上 `onOpenAiSettings`。 */
    @Test fun not_configured_card_keeps_its_exit() {
        var opened = 0
        setScreen(
            state = PhotoTranslationUiState(
                ocrText = "A page of English prose.",
                translationIssue = PhotoTranslationFailureCategory.NotConfigured(PhotoTranslationNotConfiguredReason.NoDefaultProfile),
            ),
            onOpenAiSettings = { opened++ },
        )

        composeRule.onNodeWithTag("photo_translation_not_configured").performScrollTo().assertExists()
        composeRule.onNodeWithTag("not_configured_goto").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, opened)
    }

    private fun record(id: String, source: String = "A page of English prose.") = SavedPhotoTranslation(
        recordId = id,
        profileId = "p1",
        sourceText = source,
        translatedText = "一页英文散文。",
        engine = OcrEngineKind.MlKit,
        imageAssetId = null,
        createdAtEpochMillis = 1_700_000_000_000L,
    )
}
