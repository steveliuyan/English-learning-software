package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.theme.MintGlassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 我的页（spec §3.6）的 connected 装配 smoke。
 *
 * 源码契约只证「三枚回调在册」；「点了真的去哪」只有挂进 Compose 树才看得见。
 * 三格统计是本页唯一把数字变成动作的地方（已学词 → 词库、连续天数 → 打卡、文章 → 阅读历史），
 * 少接一环时用户看到的是一个死数字——这比缺一个入口更坏，因为它看起来可点。
 *
 * 设置内容用一枚带 tag 的替身：四组九行自己的测试在 `SettingsScreenTest`，
 * 本文件只核「内容槽真的被宿主内容填进这一页」。
 */
@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun threeStatCellsRenderTheirNumbersUnderTheProfileHeader() {
        composeRule.setContent {
            MintGlassTheme {
                ProfileScreen(
                    profileName = "小刘",
                    streakDays = 5,
                    learnedWordCount = 42,
                    articleCount = 7,
                    onOpenWordBank = {},
                    onOpenCheckIn = {},
                    onOpenReadingHistory = {},
                    onOpenLearningRecords = {},
                    settingsContent = {},
                )
            }
        }
        composeRule.onNodeWithTag("profile_screen").assertExists()
        composeRule.onNodeWithText("小刘").assertExists()
        composeRule.onNodeWithText("连续学习 5 天 · API Key 只保存在本机加密区。").assertExists()
        composeRule.onNodeWithTag("profile_stat_learned").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("profile_stat_streak").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("profile_stat_articles").assertExists().assertHasClickAction()
        // 三格的可读性走一整句「标签，当前 数值」：TalkBack 不会先念一个孤零零的数字。
        // 「当前」是 `AccessibilityLabelContractTest` 禁令1a 要求的汉字字面量（纯插值 + 全角标点不算）。
        composeRule.onNodeWithContentDescription("已学词，当前 42").assertExists()
        composeRule.onNodeWithContentDescription("连续天数，当前 5").assertExists()
        composeRule.onNodeWithContentDescription("文章，当前 7").assertExists()
    }

    @Test
    fun eachStatCellAndTheLearningRecordsRowOpenTheirOwnDestinations() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            MintGlassTheme {
                ProfileScreen(
                    profileName = "小刘",
                    streakDays = 5,
                    learnedWordCount = 42,
                    articleCount = 7,
                    onOpenWordBank = { opened += "wordBank" },
                    onOpenCheckIn = { opened += "checkIn" },
                    onOpenReadingHistory = { opened += "readingHistory" },
                    onOpenLearningRecords = { opened += "records" },
                    settingsContent = {},
                )
            }
        }
        composeRule.onNodeWithTag("profile_stat_learned").performClick()
        composeRule.onNodeWithTag("profile_stat_streak").performClick()
        composeRule.onNodeWithTag("profile_stat_articles").performClick()
        composeRule.onNodeWithTag("profile_open_learning_records").performScrollTo().performClick()
        assertEquals(listOf("wordBank", "checkIn", "readingHistory", "records"), opened)
    }

    @Test
    fun theSettingsContentIsRenderedInsideThisPage() {
        composeRule.setContent {
            MintGlassTheme {
                ProfileScreen(
                    profileName = "小刘",
                    streakDays = 0,
                    learnedWordCount = 0,
                    articleCount = 0,
                    onOpenWordBank = {},
                    onOpenCheckIn = {},
                    onOpenReadingHistory = {},
                    onOpenLearningRecords = {},
                    settingsContent = {
                        Column {
                            Text("设置内容替身", modifier = Modifier.testTag("profile_settings_stub"))
                        }
                    },
                )
            }
        }
        // `performScrollTo()` 本身就是「住在本页滚动容器里」的凭据：内容槽若落在滚动容器之外，
        // 这一步会因找不到可滚动祖先而当场失败。
        composeRule.onNodeWithTag("profile_settings_stub").performScrollTo().assertExists()
    }
}
