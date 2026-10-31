package com.example.englishlearning.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「我的」功能中心（hub，spec §3.6 + `docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`）的
 * connected 装配 smoke。
 *
 * 源码契约（`ProfileHubLedgerTest` / `ProfilePageContractTest`）只证「13 行在册、宿主那侧逐行有接线」；
 * **「点了真的去哪」只有把 hub 挂进 Compose 树才看得见**。hub 比旧版四行页多出九条通路，
 * 少接一环时用户看到的是一行画得好好的、按下去什么也没发生的死行——比缺一行更坏。
 *
 * 三件事各自钉住：
 *  ① 页头两句真话与三格统计（含 TalkBack 整句）；
 *  ② 13 行逐行落点，其中词汇三行 + 「词书」行 + 「已学词」格共用同一枚**按段**回调，
 *     段位必须由行自己点名（不点名就全落默认段）；
 *  ③ 动态副标题取宿主真值：词书名与 AI 配置摘要给了就照说，没给就说「尚未…」，两种都不许写死。
 *
 * 设置内容不在本页（它是「我的 → 设置」子页）：本文件末尾钉「主题分段与外观标题不出现在 hub」。
 * 大字号档用**组合期** `LocalDensity provides Density(density, fontScale = 2f)`
 * （先例 `BackupEntryUnificationTest` / `AppScreenOverlayContainerTest`），不碰设备全局 `font_scale`。
 */
@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule val composeRule = createComposeRule()

    /** 一次 `@Test` 只 `setContent` 一次（本包纪律：同一次测试两次 setContent 必崩）。 */
    private fun setHub(
        opened: MutableList<String> = mutableListOf(),
        wordBookName: String? = null,
        aiProfileSubtitle: String? = null,
        fontScale: Float? = null,
    ) {
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.System) {
                val hub: @androidx.compose.runtime.Composable () -> Unit = {
                    ProfileScreen(
                        profileName = "小刘",
                        streakDays = 5,
                        learnedWordCount = 42,
                        articleCount = 7,
                        onOpenWordBankSection = { opened += "section:${it.name}" },
                        onOpenCheckIn = { opened += "checkIn" },
                        onOpenReadingHistory = { opened += "readingHistory" },
                        onOpenLearningRecords = { opened += "records" },
                        onOpenPlan = { opened += "plan" },
                        onOpenWorksheet = { opened += "worksheet" },
                        onOpenWordBookTransfer = { opened += "transfer" },
                        onOpenAiProfiles = { opened += "aiProfiles" },
                        onOpenReminderSpeech = { opened += "reminderSpeech" },
                        onOpenSettings = { opened += "settings" },
                        onOpenBackupHub = { opened += "backup" },
                        onOpenAbout = { opened += "about" },
                        wordBookName = wordBookName,
                        aiProfileSubtitle = aiProfileSubtitle,
                    )
                }
                if (fontScale == null) {
                    hub()
                } else {
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(density.density, fontScale = fontScale),
                    ) { hub() }
                }
            }
        }
    }

    @Test
    fun headerAndThreeStatCellsRenderTheRealNumbers() {
        setHub()
        composeRule.onNodeWithTag("profile_screen").assertExists()
        composeRule.onNodeWithText("小刘").assertExists()
        // 原型 `.head-sub`：「连续打卡 N 天 · 累计学过 N 个词」（两枚真值）。
        composeRule.onNodeWithText("连续打卡 5 天 · 累计学过 42 个词").assertExists()
        composeRule.onNodeWithTag("profile_stat_learned").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("profile_stat_streak").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("profile_stat_articles").assertExists().assertHasClickAction()
        // 三格的可读性走一整句「标签，当前 数值」：TalkBack 不会先念一个孤零零的数字。
        // 「当前」是 `AccessibilityLabelContractTest` 禁令1a 要求的汉字字面量（纯插值 + 全角标点不算）。
        composeRule.onNodeWithContentDescription("已学词，当前 42").assertExists()
        composeRule.onNodeWithContentDescription("连续天数，当前 5").assertExists()
        composeRule.onNodeWithContentDescription("文章，当前 7").assertExists()
    }

    /**
     * 13 行 + 三格各归各的落点。点击顺序即断言顺序，任何一行接到别处或接到空气上都会在这里红。
     *
     * 词汇三行与「词书」行、以及「已学词」统计格都走**同一枚按段回调**：本条同时钉住
     * 「各自点名的段位」——收藏段、生词段、错词段、词书段一个都不能错。
     */
    @Test
    fun everyHubRowAndStatCellOpensItsOwnDestination() {
        val opened = mutableListOf<String>()
        setHub(opened = opened)
        // 三格（第一格是词库词书段——参考 `sMeHome` 的三格本来就是深链，数字必须能点开出处）。
        composeRule.onNodeWithTag("profile_stat_learned").performClick()
        composeRule.onNodeWithTag("profile_stat_streak").performClick()
        composeRule.onNodeWithTag("profile_stat_articles").performClick()
        // 13 行按台账顺序逐行点：行在折叠卡里，先滚到再点。点击顺序即断言顺序。
        listOf(
            "profile_open_favorite_words" to "section:Favorite",
            "profile_open_new_words" to "section:New",
            "profile_open_wrong_words" to "section:Wrong",
            "profile_open_learning_records" to "records",
            "profile_open_plan" to "plan",
            "profile_open_word_book" to "section:Book",
            "profile_open_worksheet" to "worksheet",
            "profile_open_word_book_transfer" to "transfer",
            "profile_open_ai_profiles" to "aiProfiles",
            "profile_open_reminder_speech" to "reminderSpeech",
            "profile_open_settings" to "settings",
            "profile_open_backup" to "backup",
            "profile_open_about" to "about",
        ).forEach { (tag, _) ->
            val row = composeRule.onNodeWithTag(tag)
            row.performScrollTo()
            row.assertHasClickAction().performClick()
        }
        assertEquals(
            listOf(
                "section:Book", "checkIn", "readingHistory",
                "section:Favorite", "section:New", "section:Wrong",
                "records", "plan", "section:Book", "worksheet", "transfer",
                "aiProfiles", "reminderSpeech", "settings", "backup", "about",
            ),
            opened,
        )
    }

    /**
     * 动态副标题：宿主给了词书名与 AI 摘要就照说，没给就说「尚未…」——两条都不许写死文案。
     * 「尚未选择词书」在词书未选时是真话；把它换成任何固定书名就是假信息。
     */
    @Test
    fun theDynamicSubtitlesSpeakTheHostFactsOrSayTheyAreMissing() {
        setHub(wordBookName = "大学英语四级", aiProfileSubtitle = "已配置 3 套 · 1 套已设置密钥")
        composeRule.onNodeWithText("大学英语四级 · 换词书与每日目标").performScrollTo().assertExists()
        composeRule.onNodeWithText("大学英语四级 · 学习中").performScrollTo().assertExists()
        composeRule.onNodeWithText("已配置 3 套 · 1 套已设置密钥").performScrollTo().assertExists()
    }

    @Test
    fun theDynamicSubtitlesFallBackToTheMissingPreferenceTruth() {
        setHub()
        composeRule.onNodeWithText("尚未选择词书 · 去选一本").performScrollTo().assertExists()
        composeRule.onNodeWithText("尚未选择词书").performScrollTo().assertExists()
        // 宿主没给 AI 摘要时退回台账那句（它描述的是这行通向哪里，不是假数字）。
        composeRule.onNodeWithText("添加多套 OpenAI 兼容服务，管理 Endpoint、模型与密钥")
            .performScrollTo()
            .assertExists()
    }

    /**
     * 设置内容不在这页（决策 §3）：主题分段与「外观」标题都属于「我的 → 设置」子页
     * （`SettingsScreenTest` 承重）。同屏两枚页头、两个滚动容器是撤回前的老毛病。
     */
    @Test
    fun theHubDoesNotInlineTheSettingsContent() {
        setHub()
        composeRule.onNodeWithTag("settings_theme_mode").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_appearance").assertDoesNotExist()
        composeRule.onNodeWithText("跟随系统").assertDoesNotExist()
        ProfileGroups.all.flatMap { it.rows }.forEach { row ->
            composeRule.onNodeWithTag(row.tag).performScrollTo().assertExists()
        }
    }

    /**
     * 决策 §风险 第 1 条：行数从 4 增到 13，小屏与最大字号下整页必须可滚。
     * 设备字号 2.0 档逐枚入口滚到并显示——末组「应用」的关于行若在视口外且滚不到，就是被底导裁掉。
     */
    @Test
    fun theWholeHubScrollsWithEveryRowReachableAtDoubleFontScale() {
        setHub(fontScale = 2f)
        val anchors = buildList {
            add("profile_stats")
            ProfileGroups.all.forEach { group ->
                add(group.tag)
                group.rows.forEach { add(it.tag) }
            }
        }
        anchors.forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
        // 末组最后一行的**文字**也在视口内：只有 tag 节点可见而文案被裁，用户仍读不到那一行。
        composeRule.onNodeWithText("关于与许可").performScrollTo().assertIsDisplayed()
    }
}
