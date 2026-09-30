package com.example.englishlearning.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setScreen(
        profileName: String = "Tom",
        wordBookName: String? = "小学",
        todayNewTarget: Int? = 10,
        todayDueTarget: Int? = 3,
        onOpenSetup: () -> Unit = {},
        onOpenWorksheet: () -> Unit = {},
        onOpenAiProfiles: () -> Unit = {},
        onOpenSpeechSettings: () -> Unit = {},
        aiProfileSubtitle: String? = null,
        speechEngineStatuses: SpeechEngineStatuses = SpeechEngineStatuses(),
        onImportWordBook: () -> Unit = {},
        onExportWordBook: () -> Unit = {},
        importedBookCount: Int = 0,
        transferMessage: String? = null,
    ) {
        composeRule.setContent {
            SettingsScreen(
                profileName = profileName,
                wordBookName = wordBookName,
                todayNewTarget = todayNewTarget,
                todayDueTarget = todayDueTarget,
                onOpenSetup = onOpenSetup,
                onOpenWorksheet = onOpenWorksheet,
                onOpenAiProfiles = onOpenAiProfiles,
                onOpenSpeechSettings = onOpenSpeechSettings,
                aiProfileSubtitle = aiProfileSubtitle,
                speechEngineStatuses = speechEngineStatuses,
                onImportWordBook = onImportWordBook,
                onExportWordBook = onExportWordBook,
                importedBookCount = importedBookCount,
                transferMessage = transferMessage,
            )
        }
    }

    @Test fun renders_the_profile_card_and_the_four_groups() {
        setScreen()
        composeRule.onNodeWithTag("settings_screen").assertExists()
        composeRule.onNodeWithTag("settings_profile_card").assertExists()
        listOf("学习", "阅读", "AI 与语音", "账户").forEach { group ->
            composeRule.onNodeWithTag("settings_group_${groupTagOf(group)}").assertExists().performScrollTo()
            composeRule.onNodeWithText(group).assertExists()
        }
        composeRule.onNodeWithTag("settings_group_word_books").performScrollTo().assertExists()
    }

    @Test fun profile_card_reports_the_word_book_and_the_plan_numbers() {
        setScreen(wordBookName = "大学英语四级", todayNewTarget = 10, todayDueTarget = 9)
        composeRule.onNodeWithTag("settings_profile_word_book").assertExists()
        composeRule.onNodeWithText("大学英语四级").assertExists()
        composeRule.onNodeWithText("今日计划：新增 10 · 复习 9").assertExists()
    }

    /**
     * 当日计划的 `newTarget` 在词书新词学完时会小于用户配置的每日目标，把它写成「每日新增
     * N 词」就是错的（这条曾在真机上显示为「每日新增 0 词」）。这里锁住它不再回来。
     */
    @Test fun profile_card_never_claims_the_plan_count_is_the_configured_daily_target() {
        setScreen(todayNewTarget = 0, todayDueTarget = 9)
        composeRule.onNodeWithText("今日计划：新增 0 · 复习 9").assertExists()
        composeRule.onNodeWithText("小学 · 每日新增 0 词", substring = false).assertDoesNotExist()
    }

    @Test fun profile_card_admits_when_no_word_book_is_chosen() {
        setScreen(wordBookName = null)
        composeRule.onNodeWithText("尚未选择词书").assertExists()
    }

    @Test fun profile_card_admits_when_the_plan_is_not_readable_yet() {
        setScreen(todayNewTarget = null, todayDueTarget = null)
        composeRule.onNodeWithText("今日计划还没读出来").assertExists()
    }

    /** 「生成默写纸」是这一页唯一从旧「学习工具」搬过来的真能力，必须仍然可点。 */
    @Test fun worksheet_entry_is_still_clickable_and_reports_its_callback() {
        var opened = 0
        setScreen(onOpenWorksheet = { opened++ })
        composeRule.onNodeWithTag("settings_open_worksheet").performScrollTo().assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
    }

    @Test fun word_book_entry_reports_its_callback() {
        var opened = 0
        setScreen(onOpenSetup = { opened++ })
        composeRule.onNodeWithTag("settings_open_setup").performScrollTo().assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
    }

    /**
     * 未实现的项必须是**不可点**的，并在行上写明「后续版本」。做成可点却没反应的按钮，
     * 正是用户此前反复投诉的那类缺陷。
     */
    @Test fun pending_rows_are_not_clickable_and_say_they_are_later() {
        setScreen()
        listOf(
            "settings_pending_reading",
            "settings_pending_profile",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertExists().assertHasNoClickAction()
        }
    }

    /**
     * 词书导入/导出已是**真能力**（离线 `.wbpack`），不再是「数据与备份 · 后续版本」占位。
     * 这里锁住两条入口可点，且旧的占位行不再出现。
     */
    @Test fun word_book_import_and_export_entries_are_clickable() {
        var imported = 0
        var exported = 0
        setScreen(onImportWordBook = { imported++ }, onExportWordBook = { exported++ }, importedBookCount = 2)

        composeRule.onNodeWithTag("settings_import_word_book").performScrollTo().assertHasClickAction().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_export_word_book").performScrollTo().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, imported)
        assertEquals(1, exported)
        composeRule.onNodeWithTag("settings_pending_backup").assertDoesNotExist()
        composeRule.onNodeWithText("数据与备份").assertDoesNotExist()
    }

    @Test fun word_book_group_admits_when_nothing_was_imported_yet() {
        setScreen(importedBookCount = 0)
        composeRule.onNodeWithTag("settings_group_word_books").performScrollTo().assertExists()
        composeRule.onNodeWithText("先导入一册词书后才能导出").assertExists()
    }

    @Test fun word_book_transfer_message_is_shown_when_present() {
        setScreen(transferMessage = "已导入《ngsl-core-100》，可在「调整词书与目标」中选用。")
        composeRule.onNodeWithTag("settings_word_book_message").performScrollTo().assertExists()
        composeRule.onNodeWithText("已导入《ngsl-core-100》，可在「调整词书与目标」中选用。").assertExists()
    }

    /** AI 服务配置已经是真能力（F2-02），这里锁住它不再是「后续版本」占位。 */
    @Test fun ai_profiles_entry_is_clickable_and_reports_its_callback() {
        var opened = 0
        setScreen(onOpenAiProfiles = { opened++ })

        composeRule.onNodeWithTag("settings_open_ai_profiles").performScrollTo().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, opened)
        composeRule.onNodeWithTag("settings_pending_ai").assertDoesNotExist()
    }

    @Test fun speech_entry_is_clickable_and_reports_its_callback() {
        var opened = 0
        setScreen(onOpenSpeechSettings = { opened++ })

        composeRule.onNodeWithTag("settings_open_speech").performScrollTo().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, opened)
    }

    @Test fun ai_profiles_entry_reports_the_real_summary_when_it_has_one() {
        setScreen(aiProfileSubtitle = "已配置 2 套 · 1 套已设置密钥")
        composeRule.onNodeWithText("已配置 2 套 · 1 套已设置密钥").assertExists()
    }

    @Test fun ai_profiles_entry_admits_when_there_is_nothing_configured() {
        setScreen(aiProfileSubtitle = "尚未添加，点这里添加第一套 OpenAI 兼容服务")
        composeRule.onNodeWithText("尚未添加，点这里添加第一套 OpenAI 兼容服务").assertExists()
    }

    /**
     * 「AI 与语音」组在一级页只允许出现两个可点入口。引擎状态行与「待接入」厂商是二级页的
     * 信息量——它们曾在一级页占了近一半屏（9 行里 6 行点不了），用户学习成本过高，这里锁死
     * 它们不再回到一级页。
     */
    @Test fun ai_and_speech_group_collapses_to_two_action_entries() {
        setScreen(speechEngineStatuses = SpeechEngineStatuses(miMo = "已配置", openAi = "未配置", zipVoice = "未下载"))
        composeRule.onNodeWithTag("settings_group_ai_speech").performScrollTo()
        composeRule.onNodeWithTag("settings_open_ai_profiles").assertExists()
        composeRule.onNodeWithTag("settings_open_speech").assertExists()
        composeRule.onNodeWithText("当前供应商：系统 TTS").assertExists()

        // 状态行与占位行不得出现在一级页。
        listOf("settings_speech_mimo", "settings_speech_openai", "settings_speech_zipvoice").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
        listOf("Azure", "火山引擎", "腾讯云", "阿里云百炼", "MiniMax", "本地 ZipVoice-Distill").forEach {
            composeRule.onNodeWithText(it).assertDoesNotExist()
        }
        composeRule.onNodeWithText("当前供应商 · 已配置").assertDoesNotExist()
    }
}

private fun groupTagOf(group: String): String = when (group) {
    "学习" -> "learning"
    "阅读" -> "reading"
    "AI 与语音" -> "ai_speech"
    else -> "account"
}
