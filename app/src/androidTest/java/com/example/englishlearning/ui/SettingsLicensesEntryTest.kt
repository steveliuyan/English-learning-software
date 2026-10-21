package com.example.englishlearning.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * AC5-04 可达性契约：设置页「外观与关于」组必须有真实可点的「开源许可与隐私说明」入口。
 * 做成占位假行（`SettingsPendingRow` 一族，Task 19 已整族删除）或忘接线都会让本测试红。
 */
class SettingsLicensesEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun licensesEntryIsCallableFromSettingsAppearanceGroup() {
        var opened = 0
        composeRule.setContent {
            SettingsScreen(
                profileName = "测试者",
                wordBookName = null,
                onOpenSetup = {},
                onOpenWorksheet = {},
                onOpenAiProfiles = {},
                onOpenLicenses = { opened++ },
            )
        }
        composeRule.onNodeWithTag("settings_open_licenses").performScrollTo().performClick()
        assertEquals(1, opened)
    }
}
