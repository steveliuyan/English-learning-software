package com.example.englishlearning.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppColorSet
import com.example.englishlearning.ui.theme.AppThemePreferences
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import com.example.englishlearning.ui.theme.rememberAppThemeMode
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设置一级页 connected 回归（Task 19 / AC-R-21）：**4 组 9 行、行行可点、主题三态真像素**。
 *
 * 三条口径约束（本包既有纪律，逐条落到用例形状上）：
 *  - **一个 `@Test` 只 `setContent` 一次**（本仓真机怪癖：同一次测试两次 `setContent` 必崩）。
 *  - **SAF 子行（导入/导出词书）只断可达、绝不点击**：`SettingsScreen` 直测里回调虽是注入的
 *    空 lambda，判据仍按宿主链路同一口径钉死，防止日后这里换回真 ViewModel 时用例会拉起系统面板。
 *  - **像素断言沿用 `ThemeModeSwitchTest` 的容差口径**：期望值取 `AppColorLight/AppColorDark`
 *    的 `background` token（不重抄 hex），逐通道容差 [BACKGROUND_CHANNEL_TOLERANCE]，
 *    另配「容差 ×2 远小于两套页底间距」的自检防判据退化。
 *
 * 主题三态用例起手把偏好写成「与平台相反」的那一档：首帧页底只能由「偏好压过平台」解释，
 * 排除了「本机恰好在夜间模式、prefs 没生效也撞色」的空用例路径（同一手法见 `ThemeModeSwitchTest`）。
 * 设置页直测会真写 `ui_display_prefs`（显示偏好，可丢项、不进备份），`@Before`/`@After` 双道复位
 * `System`，不把人为档留给下一枚用例。
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    /** 平台当前是否夜间模式：独立观测点（`Configuration.uiMode`），不走被测逻辑。 */
    private val platformInDark: Boolean
        get() = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    /** 起手清 + 收尾清：上一轮的强制深/浅档不替本轮说话。 */
    @Before
    @After
    fun resetThePreferenceToFollowSystem() {
        AppThemePreferences.write(appContext, ThemeMode.System)
    }

    private fun setScreen(
        profileName: String = "Tom",
        wordBookName: String? = "小学",
        onOpenSetup: () -> Unit = {},
        onOpenWorksheet: () -> Unit = {},
        onOpenAiProfiles: () -> Unit = {},
        onOpenSpeechSettings: () -> Unit = {},
        onOpenReminderSettings: () -> Unit = {},
        onOpenLicenses: () -> Unit = {},
        onOpenBackupExport: () -> Unit = {},
        onOpenBackupRestore: () -> Unit = {},
        onOpenBackupPairingExport: () -> Unit = {},
        onDeleteAllLearningData: () -> Unit = {},
        aiProfileSubtitle: String? = null,
        speechEngineStatuses: SpeechEngineStatuses = SpeechEngineStatuses(),
        onImportWordBook: () -> Unit = {},
        onExportWordBook: () -> Unit = {},
        importedBookCount: Int = 0,
        transferBusy: Boolean = false,
        transferMessage: String? = null,
        deleteAllLearningDataNotice: String? = null,
    ) {
        composeRule.setContent {
            SettingsScreen(
                profileName = profileName,
                wordBookName = wordBookName,
                onOpenSetup = onOpenSetup,
                onOpenWorksheet = onOpenWorksheet,
                onOpenAiProfiles = onOpenAiProfiles,
                onOpenSpeechSettings = onOpenSpeechSettings,
                onOpenReminderSettings = onOpenReminderSettings,
                onOpenLicenses = onOpenLicenses,
                onOpenBackupExport = onOpenBackupExport,
                onOpenBackupRestore = onOpenBackupRestore,
                onOpenBackupPairingExport = onOpenBackupPairingExport,
                onDeleteAllLearningData = onDeleteAllLearningData,
                aiProfileSubtitle = aiProfileSubtitle,
                speechEngineStatuses = speechEngineStatuses,
                onImportWordBook = onImportWordBook,
                onExportWordBook = onExportWordBook,
                importedBookCount = importedBookCount,
                transferBusy = transferBusy,
                transferMessage = transferMessage,
                deleteAllLearningDataNotice = deleteAllLearningDataNotice,
            )
        }
    }

    /** 折叠入口一律「滚到可见 → 点一下 → 静置」，展开的子行随后才在树里。 */
    private fun expand(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    /**
     * AC-R-21 主判据：**恰 4 组、恰 9 行、行行可点**。
     *
     * 「行行可点」在这里的可执行形状：[SettingsGroups] 台账里除「主题」分段卡外的 8 个 tag
     * 全部带 click action；「主题」行由三枚分段按钮承载（每枚都必须可点）；
     * 全页可点节点数 ≥ 9；占位行整族（`settings_pending_*`）与占位文案在树里绝迹。
     */
    @Test
    fun settings_screen_has_four_groups_nine_rows_and_every_row_is_clickable() {
        setScreen()
        composeRule.onNodeWithTag("settings_screen").assertExists()

        assertEquals(4, SettingsGroups.all.size)
        assertEquals(9, SettingsGroups.all.sumOf { it.entries.size })

        SettingsGroups.all.forEach { group ->
            composeRule.onNodeWithTag(group.tag).performScrollTo().assertExists()
            composeRule.onNodeWithText(group.title).assertExists()
        }
        SettingsGroups.all.flatMap { it.entries }.forEach { entry ->
            if (entry.icon != null) {
                composeRule.onNodeWithTag(entry.tag).performScrollTo().assertHasClickAction()
            } else {
                // 「主题」分段卡：卡片本体不是可点行，可点面是三枚分段按钮。
                composeRule.onNodeWithTag(entry.tag).performScrollTo().assertExists()
                ThemeMode.entries.forEach { option ->
                    composeRule.onNodeWithTag("settings_theme_${option.storageKey}")
                        .performScrollTo()
                        .assertHasClickAction()
                }
            }
        }

        val clickable = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        assertTrue(
            "九行加三枚分段应至少 9 个可点节点，实测 $clickable（出现「看起来是行却不可点」的节点时先查这里）",
            clickable >= 9,
        )
        listOf("settings_pending_reading", "settings_pending_profile").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
        listOf("后续版本", "敬请期待", "暂未开放").forEach { wording ->
            assertTrue(
                "占位文案「$wording」不得再出现在设置页（裁决②）",
                composeRule.onAllNodesWithText(wording, substring = true).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    /** 资料卡只报昵称与词书名；今日计划数字按裁决③移出设置页（统计职责归打卡页）。 */
    @Test
    fun profile_card_shows_name_and_wordbook_without_plan_numbers() {
        setScreen(profileName = "Tom", wordBookName = "大学英语四级")
        composeRule.onNodeWithTag("settings_profile_card").assertExists()
        composeRule.onNodeWithText("Tom").assertExists()
        composeRule.onNodeWithText("大学英语四级").assertExists()
        assertTrue(
            "今日计划数字已按裁决③移出设置页，整页不该再出现「今日计划」字样",
            composeRule.onAllNodesWithText("今日计划", substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    /** 未选词书时资料卡要如实说「尚未选择词书」，不拿 null 冒充空词书名。 */
    @Test
    fun profile_card_admits_when_no_word_book_is_chosen() {
        setScreen(wordBookName = null)
        composeRule.onNodeWithText("尚未选择词书").assertExists()
    }

    /** 「学习」组三行 + 「AI 与语音」两行 + 许可行：点下去必须真把回调送到（假入口禁令的行为面）。 */
    @Test
    fun navigation_rows_report_their_callbacks() {
        val opened = mutableMapOf<String, Int>()
        setScreen(
            onOpenSetup = { opened["setup"] = 1 },
            onOpenWorksheet = { opened["worksheet"] = 1 },
            onOpenReminderSettings = { opened["reminder"] = 1 },
            onOpenAiProfiles = { opened["ai"] = 1 },
            onOpenSpeechSettings = { opened["speech"] = 1 },
            onOpenLicenses = { opened["licenses"] = 1 },
        )
        listOf(
            "settings_open_setup" to "setup",
            "settings_open_worksheet" to "worksheet",
            "settings_open_reminder" to "reminder",
            "settings_open_ai_profiles" to "ai",
            "settings_open_speech" to "speech",
            "settings_open_licenses" to "licenses",
        ).forEach { (tag, key) ->
            composeRule.onNodeWithTag(tag).performScrollTo().performClick()
            composeRule.waitForIdle()
            assertEquals("$tag 应恰好触发一次回调", 1, opened[key])
        }
    }

    /** 语音行副标题报真实当前供应商；引擎状态明细行不得回到一级页（旧回归口径保持）。 */
    @Test
    fun speech_row_reports_current_provider_and_status_rows_stay_out() {
        setScreen(speechEngineStatuses = SpeechEngineStatuses(miMo = "已配置", currentProvider = "小米 MiMo"))
        composeRule.onNodeWithText("当前供应商：小米 MiMo").assertExists()
        listOf("settings_speech_mimo", "settings_speech_openai", "settings_speech_zipvoice").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    /** AI 行副标题优先用宿主摘要；读不到时退回固定引导文案。 */
    @Test
    fun ai_profiles_row_prefers_the_host_summary() {
        setScreen(aiProfileSubtitle = "已配置 2 套 · 1 套已设置密钥")
        composeRule.onNodeWithText("已配置 2 套 · 1 套已设置密钥").assertExists()
    }

    @Test
    fun ai_profiles_row_falls_back_to_the_guidance_copy() {
        setScreen()
        composeRule.onNodeWithText("添加多套 OpenAI 兼容服务，管理 Endpoint、模型与密钥").assertExists()
    }

    /**
     * 「词书内容包」收成一行折叠入口（R-B）：默认收起时 SAF 子行不在树里；
     * 展开后导入/导出两行**可达**（只断存在、显示与可点，绝不点击——SAF 口径见类注释）。
     */
    @Test
    fun word_books_row_expands_to_import_and_export_entries() {
        setScreen(importedBookCount = 2)
        composeRule.onNodeWithTag("settings_import_word_book").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_export_word_book").assertDoesNotExist()

        expand("settings_word_books")

        composeRule.onNodeWithTag("settings_import_word_book").performScrollTo().assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("settings_export_word_book").performScrollTo().assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("已导入 2 册，导出为 `.wbpack` 分享给别的设备").assertExists()
    }

    /** 没导过时导出子行要如实说前提；一句「先导入」而不是做成点了没反应的假入口。 */
    @Test
    fun export_row_admits_when_nothing_was_imported_yet() {
        setScreen(importedBookCount = 0)
        expand("settings_word_books")
        composeRule.onNodeWithText("先导入一册词书后才能导出").assertExists()
    }

    /** busy 时两条 SAF 子行的副标题都换成「正在处理」，与迁移前的置灰语义同口径。 */
    @Test
    fun transfer_busy_swaps_both_subtitles_to_the_processing_copy() {
        setScreen(importedBookCount = 2, transferBusy = true)
        expand("settings_word_books")
        assertEquals(
            "导入与导出两行都应显示处理中文案",
            2,
            composeRule.onAllNodesWithText("正在处理，请稍候…").fetchSemanticsNodes().size,
        )
    }

    /** 导入/导出结果提示在展开区以共享提示条呈现（InlineNotice 自带行标签，按文案断言）。 */
    @Test
    fun transfer_message_is_shown_in_the_expanded_area() {
        setScreen(transferMessage = "已导入《ngsl-core-100》，可在「词书与每日目标」中选用。")
        expand("settings_word_books")
        composeRule.onNodeWithText("已导入《ngsl-core-100》，可在「词书与每日目标」中选用。").assertExists()
    }

    /**
     * 「备份与恢复」收成一行折叠入口（R-B / 裁决⑥方向）：展开后四行子入口可达——
     * 留档导出、认证导出、从旧设备恢复、以及下沉进来的「删除全部学习数据」；
     * 子行文案只留用户语言，不再出现内部编号。删除行只断可达，
     * 级联预览 + 二次确认的宿主链路归 `AppScreenOverlayContainerTest` 钉。
     */
    @Test
    fun backup_row_expands_to_all_data_entries_including_the_delete_row() {
        setScreen()
        composeRule.onNodeWithTag("settings_open_backup_export").assertDoesNotExist()

        expand("settings_backup")

        listOf(
            "settings_open_backup_export",
            "settings_open_backup_pairing_export",
            "settings_open_backup_restore",
            "settings_delete_all_learning_data",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertHasClickAction()
        }
        composeRule.onNodeWithText("生成 `.elpbak` 文件（不含密钥，暂不可用于认证恢复）").assertExists()
        composeRule.onNodeWithText("旧设备系统认证后生成配对码与受保护备份").assertExists()
        composeRule.onNodeWithText("扫描旧设备配对码并替换本机数据").assertExists()
        listOf("F4-04", "F4-03").forEach { jargon ->
            assertTrue(
                "设置页可见文案不得出现内部编号「$jargon」（AC-R-21 用户语言）",
                composeRule.onAllNodesWithText(jargon, substring = true).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    /** 删除回执（成功或预览读取失败的一句话）在展开区呈现，不为它保留第二版私有提示条。 */
    @Test
    fun delete_notice_is_shown_in_the_expanded_area() {
        setScreen(deleteAllLearningDataNotice = "学习数据已删除；文章与收藏保留。")
        expand("settings_backup")
        composeRule.onNodeWithText("学习数据已删除；文章与收藏保留。").assertExists()
    }

    /** ThemeModeRow 纯展示件：三档标签固定，点击把选中档原样送出（写偏好由调用方接线）。 */
    @Test
    fun theme_mode_row_is_a_pure_tristate_selector() {
        val picked = mutableListOf<ThemeMode>()
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.System) {
                ThemeModeRow(mode = ThemeMode.System, onSelect = { picked.add(it) })
            }
        }
        listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark).forEach { option ->
            composeRule.onNodeWithText(option.segmentLabelForTest).performClick()
            composeRule.waitForIdle()
        }
        assertEquals(listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark), picked)
    }

    /**
     * 三态接线 + **根底色真像素翻转**（brief Step 1 的 connected 判据）：
     * 起手偏好 = 与平台相反的显式档（首帧页底只能由「偏好压过平台」解释），
     * 在设置页里点另一档显式分段，`AppThemePreferences.write` → 监听 → `rememberAppThemeMode()`
     * → `MintGlassTheme` 换套，整页背景 token 必须翻到另一套。
     *
     * 取样点在 `settings_screen` 节点左侧 2% 宽的竖条、高度中线（左 padding 20dp ≈ 5% 屏宽，
     * 这条带里任何内容都碰不到，量到的必定是页底填充）；容差与自检同 `ThemeModeSwitchTest` 口径。
     */
    @Test
    fun theme_segments_flip_the_whole_settings_background() {
        val first = if (platformInDark) ThemeMode.Light else ThemeMode.Dark
        val second = if (platformInDark) ThemeMode.Dark else ThemeMode.Light
        val firstExpected = backgroundRgb(if (platformInDark) AppColorLight else AppColorDark)
        val secondExpected = backgroundRgb(if (platformInDark) AppColorDark else AppColorLight)
        AppThemePreferences.write(appContext, first)

        composeRule.setContent {
            MintGlassTheme(themeMode = rememberAppThemeMode()) {
                SettingsScreen(
                    profileName = "Tom",
                    wordBookName = "小学",
                    onOpenSetup = {},
                    onOpenWorksheet = {},
                    onOpenAiProfiles = {},
                )
            }
        }

        assertEquals(
            "起手偏好 $first（平台夜间=$platformInDark）时设置页页底必须是该套 background（超出容差 $BACKGROUND_CHANNEL_TOLERANCE）：",
            0,
            withinTolerance(probeSettingsBackground(), firstExpected),
        )

        composeRule.onNodeWithTag("settings_theme_${second.storageKey}").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            withinTolerance(probeSettingsBackground(), secondExpected) == 0
        }
        // 自检（同 ThemeModeSwitchTest 自检①）：容差必须小到另一套页底绝踩不进来，换套判据才有牙齿。
        assertTrue(
            "容差必须远小于浅/深两套 background 的最大逐通道间距，否则本用例失去换套判别力",
            BACKGROUND_CHANNEL_TOLERANCE * 2 < maxChannelDelta(firstExpected, secondExpected),
        )
        assertEquals(
            "从 $first 翻到 $second 后，整页背景必须已是另一套 token（监听或失效重算断了会停在前一套）",
            0,
            withinTolerance(probeSettingsBackground(), secondExpected),
        )
    }

    private fun withinTolerance(actual: Int, expected: Int): Int =
        maxChannelDelta(actual, expected).let { if (it <= BACKGROUND_CHANNEL_TOLERANCE) 0 else it }

    /** 页底取样：`settings_screen` 全幅节点，左侧 2% 宽竖条 × 高度中线 = 纯背景带。 */
    private fun probeSettingsBackground(): Int =
        composeRule.onNodeWithTag("settings_screen").captureToImage().asAndroidBitmap().let { bitmap ->
            bitmap.getPixel(maxOf(1, bitmap.width * 2 / 100), bitmap.height / 2) and 0xFFFFFF
        }

    private fun backgroundRgb(set: AppColorSet): Int = set.background.toArgb() and 0xFFFFFF

    /** 逐通道最大绝对差（与 `ThemeModeSwitchTest` 同一算式口径）。 */
    private fun maxChannelDelta(a: Int, b: Int): Int {
        fun channel(shift: Int) = abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF))
        return maxOf(channel(16), channel(8), channel(0))
    }
}

/** 三档在界面上的固定标签——**本用例自己独立书写一份**，不从生产码取，否则「标签固定」成了自我复读。 */
private val ThemeMode.segmentLabelForTest: String
    get() = when (this) {
        ThemeMode.System -> "跟随系统"
        ThemeMode.Light -> "浅色"
        ThemeMode.Dark -> "深色"
    }

/**
 * 页底换套的逐通道容差：`captureToImage()` 离屏渲染不走显示链色彩管理，
 * 实测页底 token 与取回像素逐通道差为 0，留 2 只抗实现取整（口径同 `ThemeModeSwitchTest` 的
 * `SURFACE_CHANNEL_TOLERANCE`，那份是另一文件的 private，此处按同一数值口径各自钉住）。
 */
private const val BACKGROUND_CHANNEL_TOLERANCE = 2
