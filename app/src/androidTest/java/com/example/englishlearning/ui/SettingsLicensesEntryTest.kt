package com.example.englishlearning.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * AC5-04 可达性契约：许可页必须有一条真实入口走到。
 *
 * 2026-10-31 去重后通路收成一条：**设置 → 关于 →「开源许可与隐私说明」**
 * （`docs/decisions/2026-10-31-settings-as-fifth-tab.md`）。本类钉两段：
 *  ① 设置页只剩「关于」一条说明入口，不再有第二份许可行；
 *  ② 关于页那一行与许可页标题逐字同名，且点得到（假行/死行都会让本类红）。
 */
class SettingsLicensesEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsPageReachesAboutAndCarriesNoSecondLicenseRow() {
        var aboutOpened = 0
        composeRule.setContent {
            SettingsScreen(
                profileName = "测试者",
                wordBookName = null,
                onOpenSetup = {},
                onOpenWorksheet = {},
                onOpenAiProfiles = {},
                onOpenAbout = { aboutOpened++ },
            )
        }
        composeRule.onNodeWithTag("settings_open_licenses").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_open_about").performScrollTo().performClick()
        assertEquals("「关于」行应恰好触发一次回调", 1, aboutOpened)
    }

    @Test
    fun theAboutRowIsTheOnlyEntryIntoTheLicensesPageAndItWorks() {
        var licensesOpened = 0
        composeRule.setContent {
            AboutScreen(
                versionName = "test",
                licenseCount = null,
                onBack = {},
                onOpenLicenses = { licensesOpened++ },
            )
        }
        composeRule.onNodeWithTag("about_privacy_row").assertDoesNotExist()
        composeRule.onNodeWithTag("about_backup_row").assertDoesNotExist()
        composeRule.onNodeWithText("开源许可与隐私说明").assertExists()
        composeRule.onNodeWithTag("about_licenses_row").performScrollTo().performClick()
        assertEquals("许可行应恰好触发一次回调", 1, licensesOpened)
    }
}
