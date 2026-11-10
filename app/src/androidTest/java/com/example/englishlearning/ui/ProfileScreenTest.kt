package com.example.englishlearning.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
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
 * 2026-11-10（用户 m03373 + m03681 拍板选项 1）：设置子页整体退役，「外观」（主题三态）搬到本页页头统计卡之下，
 * 因此本文件既钉「统计卡下方真有外观段与三枚分段」，也钉「hub 里不再有设置行」（`profile_open_settings` 必须消失）。
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
        // 批 C-2：三枚统计默认给真值；传 `null` = 「宿主还没读到快照」那一档（`PendingStats` 那条用例）。
        streakDays: Int? = 5,
        learnedWordCount: Int? = 42,
        articleCount: Int? = 7,
    ) {
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.System) {
                val hub: @androidx.compose.runtime.Composable () -> Unit = {
                    ProfileScreen(
                        profileName = "小刘",
                        streakDays = streakDays,
                        learnedWordCount = learnedWordCount,
                        articleCount = articleCount,
                        onOpenWordBankSection = { opened += "section:${it.name}" },
                        onOpenCheckIn = { opened += "checkIn" },
                        onOpenReadingHistory = { opened += "readingHistory" },
                        onOpenLearningRecords = { opened += "records" },
                        onOpenPlan = { opened += "plan" },
                        onOpenWorksheet = { opened += "worksheet" },
                        onOpenWordBookTransfer = { opened += "transfer" },
                        onOpenAiProfiles = { opened += "aiProfiles" },
                        onOpenReminderSettings = { opened += "reminder" },
                        onOpenSpeechSettings = { opened += "speech" },
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
     * 批 C-2（Spec 05 F5-05 / 决策 2026-11-07 决定 3）：宿主「还没读到快照」时这一屏不许把 pending
     * 说成 0。旧形状是 `?: 0` 之后页头念「连续打卡 0 天 · 累计学过 0 个词」、三格画三个 0，
     * 那是把「还没读到」说成「一个都没学」（2026-11-06 占位诚实化同一条禁令的数字化身）。
     * 现在三格保留外壳与命中区、数字位换成占位条，读屏整句换成「标签，正在统计」。
     */
    @Test
    fun pendingStatsRenderPlaceholderCellsInsteadOfZeros() {
        setHub(streakDays = null, learnedWordCount = null, articleCount = null)
        composeRule.onNodeWithTag("profile_screen").assertExists()
        composeRule.onNodeWithTag("profile_stats").assertExists()
        listOf("profile_stat_learned", "profile_stat_streak", "profile_stat_articles").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists().assertHasClickAction()
        }
        // 数字一个都不许在场：0 属于「读了且确实是 0」，pending 只能有形状。
        composeRule.onNodeWithText("0").assertDoesNotExist()
        composeRule.onNodeWithText("连续打卡 0 天 · 累计学过 0 个词").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("已学词，正在统计").assertExists()
        composeRule.onNodeWithContentDescription("连续天数，正在统计").assertExists()
        composeRule.onNodeWithContentDescription("文章，正在统计").assertExists()
        composeRule.onNodeWithContentDescription("已学词，当前 null").assertDoesNotExist()
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
            "profile_open_reminder" to "reminder",
            "profile_open_speech" to "speech",
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
                "aiProfiles", "reminder", "speech", "backup", "about",
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
     * 2026-11-10（用户 m03373 + m03681 选项 1）：外观不再是子页，「外观」段标题与主题三态搬到 hub 页头统计卡之下（m04040 从页末上提）。
     * 本条钉两件「在场」+ 一件「消失」：
     *  - 段标题与三档分段真在 hub 里，且都能滚到、都能点（搬了位置却没接线 = 死控件）；
     *  - 旧 tag 词表 `settings_theme_*` 与设置行 `profile_open_settings` 一律不得回来（否则就是两份真值）。
     */
    @Test
    fun theAppearanceSectionIsInlinedRightUnderTheStatsCard() {
        setHub()
        composeRule.onNodeWithTag("profile_group_appearance").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("profile_theme_mode").performScrollTo().assertIsDisplayed()
        listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark).forEach { option ->
            composeRule.onNodeWithTag("profile_theme_${option.storageKey}")
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
        }
        // TalkBack 走查口径随迁（Task 26 Step 4 的老判据）：三档是**单选**，当前档 Selected 且角色 RadioButton。
        composeRule.onNodeWithTag("profile_theme_${ThemeMode.System.storageKey}")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNodeWithTag("settings_theme_mode").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_open_settings").assertDoesNotExist()
        ProfileGroups.all.flatMap { it.rows }.forEach { row ->
            composeRule.onNodeWithTag(row.tag).performScrollTo().assertExists()
        }
    }

    /**
     * `ThemeModeRow` 仍是**纯展示件**（2026-11-10 从 `SettingsScreenTest` 整条搬来，tag 换词表）：
     * 三档标签固定，点击把选中档原样送出——写偏好是调用方的事，组件自己不许碰存储。
     */
    @Test
    fun themeModeRowIsAPureTristateSelector() {
        val picked = mutableListOf<ThemeMode>()
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.System) {
                ThemeModeRow(mode = ThemeMode.System, onSelect = { picked.add(it) })
            }
        }
        listOf("跟随系统", "浅色", "深色").forEach { label ->
            composeRule.onNodeWithText(label).performClick()
            composeRule.waitForIdle()
        }
        assertEquals(listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark), picked)
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
            // 2026-11-10 起统计卡下方多出「外观」段：大字号下它也必须滚得到（搬来却不滚得到 = 白搬）。
            add("profile_group_appearance")
            add("profile_theme_mode")
        }
        anchors.forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
        // 末组最后一行的**文字**也在视口内：只有 tag 节点可见而文案被裁，用户仍读不到那一行。
        // 行名 2026-11-03 起是「关于」（U-12，决策 §5）——锚必须跟着改名，否则这条永远在找一个不存在的串。
        composeRule.onNodeWithText("关于").performScrollTo().assertIsDisplayed()
    }
}
