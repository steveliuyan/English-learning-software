package com.example.englishlearning.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「提醒与发音」二级导航页 connected 判据（决策 §4，2026-11-02）。
 *
 * 参考 `sMeReminder` 把提醒与发音画在同一屏；本应用是两套独立 ViewModel 与两屏，
 * 决策裁定**先以导航页保真、不重写两屏**。因此本页承重三件事：
 *  ① 两行各归各的子屏（少接一环就是一枚死行），返回位真的回「我的」；
 *  ② 发音行的副标题报**真实当前供应商**（`SpeechEngineStatuses.currentProvider`）；
 *  ③ 「待接入」厂商名与引擎明细行不得回到这一层（修复轮 1 / C1 那批反退化护栏随发音行搬到这里）。
 *
 * 提醒子屏自身的权限时序与发音子屏的引擎选择各由自己的 connected 用例承重，本文件不越界。
 * 一个 `@Test` 只 `setContent` 一次；大字号档用组合期 `LocalDensity provides Density(…, fontScale = 2f)`
 * （先例 `BackupEntryUnificationTest`）。
 */
@RunWith(AndroidJUnit4::class)
class ReminderSpeechScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setScreen(
        opened: MutableList<String> = mutableListOf(),
        speechEngineStatuses: SpeechEngineStatuses = SpeechEngineStatuses(),
        fontScale: Float? = null,
    ) {
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.System) {
                val page: @androidx.compose.runtime.Composable () -> Unit = {
                    ReminderSpeechScreen(
                        speechEngineStatuses = speechEngineStatuses,
                        onOpenReminderSettings = { opened += "reminder" },
                        onOpenSpeechSettings = { opened += "speech" },
                        onBack = { opened += "back" },
                    )
                }
                if (fontScale == null) {
                    page()
                } else {
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(density.density, fontScale = fontScale),
                    ) { page() }
                }
            }
        }
    }

    @Test
    fun theTwoRowsAndTheHeaderBackEachFireTheirOwnCallback() {
        val opened = mutableListOf<String>()
        setScreen(opened = opened)
        composeRule.onNodeWithTag("reminder_speech_screen").assertExists()
        composeRule.onNodeWithText("提醒与发音").assertIsDisplayed()
        listOf(
            "reminder_speech_open_reminder" to "reminder",
            "reminder_speech_open_speech" to "speech",
        ).forEach { (tag, expectation) ->
            val row = composeRule.onNodeWithTag(tag)
            row.performScrollTo().assertHasClickAction().performClick()
            composeRule.waitForIdle()
            assertEquals("$tag 必须只触发一次回调（多一次 = 一行接了两个落点）", listOf(expectation), opened.takeLast(1))
        }
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("reminder", "speech", "back"), opened)
    }

    /** 注入 MiMo 状态那一腿：行上说的是真供应商，明细行与占位厂商名都不在这一层。 */
    @Test
    fun theSpeechRowNamesTheCurrentProviderAndKeepsVendorDetailsOffThisLayer() {
        val opened = mutableListOf<String>()
        setScreen(
            opened = opened,
            speechEngineStatuses = SpeechEngineStatuses(miMo = "已配置", currentProvider = "小米 MiMo"),
        )
        composeRule.onNodeWithText("当前供应商：小米 MiMo").assertExists()
        listOf("settings_speech_mimo", "settings_speech_openai", "settings_speech_zipvoice").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
        SPEECH_VENDOR_PLACEHOLDERS.forEach { vendor ->
            composeRule.onNodeWithText(vendor).assertDoesNotExist()
        }
        composeRule.onNodeWithText("当前供应商 · 已配置").assertDoesNotExist()
    }

    /** 默认态（宿主没读出任何引擎状态）必须说「当前供应商：系统 TTS」，不拿空串糊过去。 */
    @Test
    fun theDefaultRowNamesSystemTtsAndNoVendorPlaceholderReturns() {
        setScreen()
        composeRule.onNodeWithText("当前供应商：系统 TTS").assertExists()
        SPEECH_VENDOR_PLACEHOLDERS.forEach { vendor ->
            composeRule.onNodeWithText(vendor).assertDoesNotExist()
        }
        composeRule.onNodeWithText("当前供应商 · 已配置").assertDoesNotExist()
    }

    /** 决策 §风险：大字号档两行都要滚得到——导航页只剩两行，滚不到就是整条通路断掉。 */
    @Test
    fun bothRowsStayReachableAtDoubleFontScale() {
        setScreen(fontScale = 2f)
        listOf("reminder_speech_open_reminder", "reminder_speech_open_speech").forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("语音朗读").performScrollTo().assertIsDisplayed()
    }
}

/**
 * 「待接入」厂商名占位清单：**本文件独立书写一份**（不从生产码取），沿 `SettingsScreenTest`
 * 修复轮 2 / M-2 的口径——这一族只被缺席侧断言读，生产侧新增第七枚占位名时这里必须一起补。
 */
private val SPEECH_VENDOR_PLACEHOLDERS = listOf("Azure", "火山引擎", "腾讯云", "阿里云百炼", "MiniMax", "本地 ZipVoice-Distill")
