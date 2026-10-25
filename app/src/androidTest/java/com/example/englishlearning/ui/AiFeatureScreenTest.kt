package com.example.englishlearning.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.DomainDark
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiFeatureScreenTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * `setContent` 每个测试只能调一次，所以四个功能的轮询走一个可变状态切换，
     * 而不是在循环里反复 setContent。
     */
    private fun setScreen(
        onBack: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onOpenLearning: () -> Unit = {},
    ): MutableState<AiFeature> {
        val current = mutableStateOf(AiFeature.WORD_PASSAGE)
        composeRule.setContent {
            AiFeatureScreen(
                feature = current.value,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                onOpenLearning = onOpenLearning,
            )
        }
        return current
    }

    /**
     * 骨架页只属于**未实现**的功能（已实现的渲染各自的真实屏幕）。
     * 骨架保留标题、进度、动作；2026-10-25 简化批删掉的「还差什么」「会用到的数据」两张卡
     * 在这里反向钉住（见 `docs/decisions/2026-10-25-ui-simplification-deletions.md`）。
     */
    @Test fun every_unimplemented_feature_renders_a_complete_skeleton() {
        val current = setScreen()
        AiFeature.entries.filter { !it.implemented }.forEach { feature ->
            current.value = feature
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("ai_feature_screen").assertExists()
            composeRule.onNodeWithTag("ai_feature_title").assertExists()
            composeRule.onNodeWithText(feature.title).assertExists()
            composeRule.onNodeWithTag("ai_feature_status_card").assertExists()
            composeRule.onNodeWithTag("ai_feature_status").assertExists()
            composeRule.onNodeWithTag("ai_feature_dependencies_card").assertDoesNotExist()
            composeRule.onNodeWithTag("ai_feature_data_card").assertDoesNotExist()
            composeRule.onNodeWithTag("ai_feature_open_settings").assertExists()
        }
    }

    /** 骨架页面上必须出现该功能自己的状态文案，不能被泛化成一句万金油。 */
    @Test fun status_block_repeats_the_catalogue_wording() {
        val current = setScreen()
        AiFeature.entries.filter { !it.implemented }.forEach { feature ->
            current.value = feature
            composeRule.waitForIdle()
            composeRule.onNodeWithText(feature.status).assertExists()
        }
    }

    /**
     * 徽章面（裁决④）：骨架页属于「未上线」那批，徽章必须说「未上线」；
     * 同一屏的状态正文仍照目录原文说「未实现：…」（R-23.4 两面分开，徽章改词不动目录）。
     * 词一律从 `featureStatusBadge` 取，测试不抄徽章字面量——徽章词改了这里跟着走。
     */
    @Test fun status_pill_admits_the_feature_is_not_implemented() {
        val current = setScreen()
        composeRule.onNodeWithTag("ai_feature_status_pill").assertExists()
        composeRule.onNodeWithText(featureStatusBadge(false)).assertExists()
        composeRule.onNodeWithText(featureStatusBadge(true)).assertDoesNotExist()
        composeRule.onNodeWithText(current.value.status).assertExists()

        current.value = AiFeature.SENTENCE_ANALYSIS
        composeRule.waitForIdle()
        composeRule.onNodeWithText(featureStatusBadge(true)).assertExists()
        composeRule.onNodeWithText(featureStatusBadge(false)).assertDoesNotExist()
    }

    /**
     * 2026-10-25 简化批：原先的两条 `data_block_*` 用例随「会用到的数据」卡一并删除
     * （卡片下线，不是把断言改松）。本机词数仍在 AI 主页的导航卡上如实汇报，
     * 口径由 `AiLearningScreenTest` 的 wordListLine 用例继续钉住。
     */
    @Test fun the_usable_actions_report_their_callbacks() {
        var toSettings = 0
        var toLearning = 0
        setScreen(onOpenSettings = { toSettings++ }, onOpenLearning = { toLearning++ })
        composeRule.onNodeWithTag("ai_feature_open_settings").performScrollTo().assertHasClickAction().performClick()
        composeRule.onNodeWithTag("ai_feature_open_learning").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, toSettings)
        assertEquals(1, toLearning)
    }

    /** 这些按钮同时要有 testTag（测试用）与 contentDescription（无障碍 + adb 走查用）。 */
    @Test fun every_action_is_reachable_by_its_content_description() {
        setScreen()
        composeRule.onNodeWithContentDescription("返回 AI 学").assertExists()
        composeRule.onNodeWithContentDescription("去配置 AI 服务").performScrollTo().assertExists()
        composeRule.onNodeWithContentDescription("回到今日学习").performScrollTo().assertExists()
    }

    @Test fun back_button_reports_its_callback() {
        var backed = 0
        setScreen(onBack = { backed++ })
        composeRule.onNodeWithTag("ai_feature_back").assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(1, backed)
    }

    /**
     * 深色档取证（Task 23 修复轮 R-23.13）：按钮容器 `MintPrimary` 两档都是亮薄荷
     * （容器主题化归 Task 26/27），前景必须按 `LocalIsDark` 分档——深色取 `onBrand`（#04241C 压 #35E0AC ≈9.74:1，
     * 与 `AiBadgeContrastFixTest` 的活 token 亮度腿同一把尺）。
     * 上一席把前景单迁成裸 `colors.onMint`，深色档渲出亮套 `onMint`（#DFF6EC）压亮薄荷（1.49:1），
     * 本例的前置腿（容器亮薄荷在场）判绿、墨色腿当场判红——正是 B1 回归的设备侧镜像。
     *
     * 深色档走显式 `MintGlassTheme(themeMode = ThemeMode.Dark)` 包装（先例
     * `glass/GlassPanelThemeInkTest.kt:290`、`ThemeModeSwitchTest`）：本类 `setContent` 不带主题提供者，
     * `cmd uimode night` 翻不动它（`compositionLocalOf` 停在浅套默认值），应用真实路径是设置页主题档。
     */
    @Test fun dark_tier_button_inks_the_mint_container_with_on_brand_foreground() {
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Dark) {
                AiFeatureScreen(
                    feature = AiFeature.CLOZE,
                    onBack = {},
                    onOpenSettings = {},
                    onOpenLearning = {},
                )
            }
        }
        val pixels = pixelsOf("ai_feature_open_settings")
        val container = DomainDark.getValue(AppDomain.AiSpeech).base.toArgb()
        assertTrue(
            "prerequisite leg: the dark tier really renders the bright mint container",
            pixels.minOf { colorDistance(it, container) } <= 18.0,
        )
        val ink = AppColorDark.onBrand.toArgb()
        assertTrue(
            "the mint container's dark-tier ink must be on-brand; bare onMint is light ink on light mint (1.49:1)",
            pixels.minOf { colorDistance(it, ink) } <= 18.0,
        )
    }

    /**
     * 深色档取证（R-23.13 第二站点）：StatusPill「现在能用」档容器同是 `MintPrimary`，
     * 墨色同规则分档。取样用 `SENTENCE_ANALYSIS`（implemented=true），未上线档走浅 MintTint，
     * 不在本腿判据内。
     */
    @Test fun dark_tier_status_pill_inks_the_mint_container_with_on_brand_foreground() {
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Dark) {
                AiFeatureScreen(
                    feature = AiFeature.SENTENCE_ANALYSIS,
                    onBack = {},
                    onOpenSettings = {},
                    onOpenLearning = {},
                )
            }
        }
        val pixels = pixelsOf("ai_feature_status_pill")
        val container = DomainDark.getValue(AppDomain.AiSpeech).base.toArgb()
        assertTrue(
            "prerequisite leg: the dark tier really renders the bright mint pill",
            pixels.minOf { colorDistance(it, container) } <= 18.0,
        )
        val ink = AppColorDark.onBrand.toArgb()
        assertTrue(
            "the pill's dark-tier ink must be on-brand, split by theme like the button",
            pixels.minOf { colorDistance(it, ink) } <= 18.0,
        )
    }

    /** 取一枚节点的全体不透明像素（节点外形外的透明区一并滤掉，免得 0x00000000 冒充颜色）。 */
    private fun pixelsOf(tag: String): List<Int> {
        val bitmap = composeRule.onNodeWithTag(tag).performScrollTo().captureToImage().asAndroidBitmap()
        return (0 until bitmap.width).flatMap { x ->
            (0 until bitmap.height).map { y -> bitmap.getPixel(x, y) }
        }.filter { it ushr 24 == 0xFF }
    }

    /**
     * 逐通道距离，容差 18 沿用 `ThemeModeSwitchTest` / 本包 `AiLearningScreenTest.colorDistance`
     * 的取证口径（同仓已有一份实现，androidTest 侧无共享夹具，这里是指针式复写，注释指明出处）。
     */
    private fun colorDistance(actual: Int, expected: Int): Double {
        val dr = ((actual shr 16 and 0xFF) - (expected shr 16 and 0xFF)).toDouble()
        val dg = ((actual shr 8 and 0xFF) - (expected shr 8 and 0xFF)).toDouble()
        val db = ((actual and 0xFF) - (expected and 0xFF)).toDouble()
        return Math.sqrt(dr * dr + dg * dg + db * db)
    }
}
