package com.example.englishlearning.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.englishlearning.ui.theme.MintGlassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * AC5-04 可达性契约：许可页必须有一条真实入口走到。
 *
 * 2026-11-02（`docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`）通路是：
 * **「我的」hub →「关于」→ 关于页 →「开源许可与隐私说明」→ 许可页**，全站各一枚。
 * （行名 2026-11-03 由 U-12、决策 `2026-11-03-duplication-consolidation.md` §5 改成与落地页同名的「关于」；
 * 旧并列名把两跳说成一跳，本类的 ① 那段反向钉因此必须跟着换成新名——留在旧名上就成了永真的空判。）
 * 本类钉三段：
 *  ① 设置子页不持有任何说明类出口（关于 / 许可）——「关于」行整条住在 hub，同一落点不留第二份入口；
 *  ② hub 那一行真的触发一次回调（假行/死行都会在这里红）；
 *  ③ 关于页那一行与许可页标题逐字同名，且点得到。
 */
class SettingsLicensesEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** ① 设置子页只有「外观」：说明类出口（关于 / 许可）一律不在这里。 */
    @Test
    fun theSettingsSubpageCarriesNeitherAboutNorLicenseRow() {
        composeRule.setContent {
            MintGlassTheme {
                SettingsScreen(onBack = {})
            }
        }
        composeRule.onNodeWithTag("settings_open_licenses").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_open_about").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_open_about").assertDoesNotExist()
        composeRule.onNodeWithText("关于").assertDoesNotExist()
    }

    /** ② hub 的关于行是那条通路的唯一入口：点了真的会开，且只触发一次。 */
    @Test
    fun theHubRowIsTheEntryIntoAbout() {
        var aboutOpened = 0
        composeRule.setContent {
            MintGlassTheme {
                ProfileScreen(
                    profileName = "测试者",
                    streakDays = 0,
                    learnedWordCount = 0,
                    articleCount = 0,
                    onOpenWordBankSection = {},
                    onOpenCheckIn = {},
                    onOpenReadingHistory = {},
                    onOpenLearningRecords = {},
                    onOpenPlan = {},
                    onOpenWorksheet = {},
                    onOpenWordBookTransfer = {},
                    onOpenAiProfiles = {},
                    onOpenReminderSpeech = {},
                    onOpenSettings = {},
                    onOpenBackupHub = {},
                    onOpenAbout = { aboutOpened++ },
                )
            }
        }
        composeRule.onNodeWithTag("profile_open_about").performScrollTo().performClick()
        assertEquals("「关于」行应恰好触发一次回调", 1, aboutOpened)
    }

    /** ③ 关于页 → 许可：唯一一行，标题与许可页同名。 */
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
