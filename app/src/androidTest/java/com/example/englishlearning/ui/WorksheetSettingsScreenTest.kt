package com.example.englishlearning.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.worksheet.WorksheetDirection
import com.example.englishlearning.learning.worksheet.WorksheetSettings
import com.example.englishlearning.learning.worksheet.WorksheetTemplate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorksheetSettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun settingsExposeBothDirectionsAndPreviewAction() {
        composeRule.setContent {
            WorksheetSettingsScreen(
                settings = WorksheetSettings(directions = setOf(WorksheetDirection.ZH_TO_EN)),
                selectedCount = 15,
                onToggleDirection = {},
                onToggleGrid = {},
                onToggleAnswers = {},
                onPreview = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("worksheet_settings_screen").assertExists()
        composeRule.onNodeWithTag("worksheet_direction_zh_to_en").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("worksheet_direction_en_to_zh").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("worksheet_preview_action").assertExists().assertHasClickAction()
    }

    /**
     * 入口从已删除的「学习工具」页搬到了「设置」栏，返回按钮的文案必须跟着走；留着旧文案
     * 会让用户以为要回到一个不存在的页面。
     */
    @Test
    fun backEntryNamesTheSettingsTabItActuallyReturnsTo() {
        composeRule.setContent {
            WorksheetSettingsScreen(
                settings = WorksheetSettings(),
                selectedCount = 15,
                onToggleDirection = {},
                onToggleGrid = {},
                onToggleAnswers = {},
                onPreview = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithContentDescription("返回设置").assertExists().assertHasClickAction()
        composeRule.onNodeWithContentDescription("返回学习工具").assertDoesNotExist()
    }

    @Test
    fun settingsExposeTheThreeReferenceTemplates() {
        composeRule.setContent {
            WorksheetSettingsScreen(
                settings = WorksheetSettings(template = WorksheetTemplate.FULL_LIST),
                selectedCount = 15,
                onToggleDirection = {},
                onToggleGrid = {},
                onToggleAnswers = {},
                onPreview = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("worksheet_template_full_list").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("worksheet_template_spelling_test").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("worksheet_template_ebbinghaus").assertExists().assertHasClickAction()
        // 完整词表不需要默写方向，方向选择应隐藏。
        composeRule.onNodeWithTag("worksheet_direction_zh_to_en").assertDoesNotExist()
    }
}
