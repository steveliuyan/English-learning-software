package com.example.englishlearning.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.worksheet.WorksheetDirection
import com.example.englishlearning.learning.worksheet.WorksheetSettings
import com.example.englishlearning.learning.worksheet.WorksheetTemplate
import org.junit.Assert.assertEquals
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
     * 返回位改由共享页头承载（批 2 Task 4 / spec §3 页头族）：页头的返回位自带
     * `contentDescription = "返回"` 与 48dp 可点区，「回到设置栏」这条**去向**不再写在按钮文案里。
     *
     * 判据不降级：仍要求「页头上有一枚可点的返回键」，并且旧文案（含那张已删除的
     * 「学习工具」页）一个都不许再出现——少了一半就红。
     */
    @Test
    fun backEntryIsTheSharedHeaderBackAffordance() {
        var backs = 0
        composeRule.setContent {
            WorksheetSettingsScreen(
                settings = WorksheetSettings(),
                selectedCount = 15,
                onToggleDirection = {},
                onToggleGrid = {},
                onToggleAnswers = {},
                onPreview = {},
                onBack = { backs++ },
            )
        }

        composeRule.onNodeWithTag("app_screen_header").assertExists()
        composeRule.onNodeWithContentDescription("返回").assertExists().assertHasClickAction().performClick()
        assertEquals(1, backs)
        composeRule.onNodeWithContentDescription("返回设置").assertDoesNotExist()
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
