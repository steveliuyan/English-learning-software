package com.example.englishlearning.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * 「设置」**子页** connected 回归（决策 §3，2026-11-02）。
 *
 * 这一屏从「4 组 9 行的设置根页」缩成「外观一段 + 一句范围说明」：原九行分发到「我的」hub 的 13 行
 * （行为判据在 `ProfileScreenTest` / `ProfileHubLedgerTest`），发音与提醒两行先经
 * 「我的 → 提醒与发音」导航页（`ReminderSpeechScreenTest`），词书内容包整条 SAF 通路搬进
 * 独立子页（可达性与禁用态由 `AppScreenOverlayContainerTest` 的路 B 腿在宿主层承重）。
 * 本文件因此只管三件事：
 *
 *  ① **形状**：页头带返回位（且真的触发一次回调）、「外观」段标题、主题三枚分段、页末说明逐字在场；
 *     旧的一级行 tag 与「关于 / 许可 / 备份 / 发音 / AI」出口一律缺席——出现即第二份入口回潮。
 *  ② **三态语义**：`ThemeModeRow` 是纯选择器；当前档 `Selected = true`、另两档 false、角色 RadioButton
 *     （Task 26 Step 4 / AC-R-12 走查补牙，口径一字未动）。
 *  ③ **根底色真像素翻转**：偏好压过平台起手，点另一档整页 background token 换套
 *     （容差与自检同 `ThemeModeSwitchTest`）。
 *
 * 三条口径约束（本包既有纪律）：一个 `@Test` 只 `setContent` 一次；设置直测会真写
 * `ui_display_prefs`（显示偏好，可丢项、不进备份），`@Before`/`@After` 双道复位 `System`，
 * 不把人为档留给下一枚用例；像素断言取 `AppColorLight/AppColorDark` 的 `background` token，不重抄 hex。
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

    private fun setScreen(onBack: () -> Unit = {}) {
        composeRule.setContent {
            MintGlassTheme(themeMode = rememberAppThemeMode()) {
                SettingsScreen(onBack = onBack)
            }
        }
    }

    /** 全页可点节点计数（合并树）。 */
    private fun clickableNodeCount(): Int =
        composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size

    /**
     * ① 子页形状：页头 +「外观」+ 三枚分段 + 页末说明。
     *
     * [CLICKABLE_ON_APPEARANCE_PAGE] 是现盘读数：页头返回位 1 + 主题三档 1×3 = 4。
     * 「主题」卡本体与说明条不可点；多一枚 = 这一页又长出别的入口，少一枚 = 分段或返回位坏了。
     */
    @Test
    fun theSubpageIsTheAppearanceSwitchPlusTheScopeNoticeAndNothingElse() {
        var backRequested = 0
        setScreen(onBack = { backRequested++ })
        composeRule.onNodeWithTag("settings_screen").assertExists()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_group_appearance").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("外观").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_theme_mode").assertIsDisplayed()
        listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark).forEach { option ->
            composeRule.onNodeWithTag("settings_theme_${option.storageKey}")
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
        }
        // 页末说明按参考逐字：这一句现在是真话（提醒与发音、计划、词书、备份与许可都在「我的」里）。
        composeRule.onNodeWithText(SCOPE_NOTICE).performScrollTo().assertIsDisplayed()

        // 页头返回位：`AppScreenHeader` 的固定 `contentDescription = "返回"`（全站同一枚，无专属 tag）。
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()
        assertEquals("页头返回位必须恰好触发一次回调", 1, backRequested)

        assertEquals(
            "子页可点节点必须恰 $CLICKABLE_ON_APPEARANCE_PAGE（返回位 1 + 主题三档 3）",
            CLICKABLE_ON_APPEARANCE_PAGE,
            clickableNodeCount(),
        )
        listOf("后续版本", "敬请期待", "暂未开放").forEach { wording ->
            assertTrue(
                "占位文案「$wording」不得再出现在设置页（裁决②）",
                composeRule.onAllNodesWithText(wording, substring = true).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    /**
     * ① 的反向腿：旧的九行分发与说明类出口一律不得回到这一页。
     *
     * 每一条 `assertDoesNotExist` 都对应一个真实存在过的入口：它们现在各住在 hub 或自己的子页，
     * 在这一页再挂一枚就是同一条通路的第二份入口（决策 §3 立这条的目的）。
     */
    @Test
    fun theOldDistributionRowsStayOutOfThisPage() {
        setScreen()
        listOf(
            "settings_open_setup",
            "settings_open_worksheet",
            "settings_open_ai_profiles",
            "settings_open_speech",
            "settings_open_reminder",
            "settings_word_books",
            "settings_backup",
            "settings_open_about",
            "settings_open_licenses",
            "settings_import_word_book",
            "settings_export_word_book",
            "profile_open_settings",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
        listOf("提醒与发音", "词书内容包", "关于", "数据与备份", "学习计划").forEach { title ->
            composeRule.onNodeWithText(title).assertDoesNotExist()
        }
        // 发音摘要随发音行搬到「提醒与发音」导航页（`ReminderSpeechScreenTest` 承重），这里不该有它。
        composeRule.onNodeWithText("当前供应商：系统 TTS").assertDoesNotExist()
    }

    /** ② ThemeModeRow 纯展示件：三档标签固定，点击把选中档原样送出（写偏好由调用方接线）。 */
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
     * ② TalkBack 走查补牙（Task 26 Step 4）：主题三态是**单选**控件。
     * 当前档 `Selected = true`、另两档 false、角色 RadioButton，三档都保留点击动作。
     */
    @Test
    fun the_theme_segments_expose_their_selected_state_to_talkback() {
        val mode = if (platformInDark) ThemeMode.Dark else ThemeMode.Light
        AppThemePreferences.write(appContext, mode)
        setScreen()

        ThemeMode.entries.forEach { option ->
            val node = composeRule.onNodeWithTag("settings_theme_${option.storageKey}").performScrollTo()
            node.assertHasClickAction()
            if (option == mode) {
                node.assertIsSelected()
            } else {
                node.assertIsNotSelected()
            }
        }
        composeRule.onNodeWithTag("settings_theme_${mode.storageKey}")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNodeWithTag("settings_theme_${mode.storageKey}")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Expand))
    }

    /**
     * ③ 三态接线 + **根底色真像素翻转**（brief Step 1 的 connected 判据）：
     * 起手偏好 = 与平台相反的显式档（首帧页底只能由「偏好压过平台」解释），
     * 在这一页点另一档显式分段，`AppThemePreferences.write` → 监听 → `rememberAppThemeMode()`
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
        setScreen()

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

/** 页末范围说明（参考 `sMeSettings` 的 notice，与 `SettingsPrototypeAlignmentContractTest` 同一逐字判据）。 */
private const val SCOPE_NOTICE =
    "这一页只放全局开关。提醒与发音、计划、词书、备份与许可都在「我的」里，不在这里重复出现。"

/**
 * 现盘可点节点数：页头返回位 1 + 主题三档 3 = 4。
 * 「主题」卡本体不可点，说明条与段标题也不参与点击；版面一动这个数就必须一起动。
 */
private const val CLICKABLE_ON_APPEARANCE_PAGE = 4

/**
 * 页底换套的逐通道容差：`captureToImage()` 离屏渲染不走显示链色彩管理，
 * 实测页底 token 与取回像素逐通道差为 0，留 2 只抗实现取整（口径同 `ThemeModeSwitchTest` 的
 * `SURFACE_CHANNEL_TOLERANCE`，那份是另一文件的 private，此处按同一数值口径各自钉住）。
 */
private const val BACKGROUND_CHANNEL_TOLERANCE = 2
