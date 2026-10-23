package com.example.englishlearning.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertHasClickAction
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.AppleMintEnd
import com.example.englishlearning.ui.theme.AppleMintStart
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.DomainDark
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiLearningScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setScreen(
        todayWordCount: Int? = 12,
        dueWordCount: Int? = 5,
        aiConfigured: Boolean = false,
        onOpenFeature: (AiFeature) -> Unit = {},
        onOpenWordList: () -> Unit = {},
    ) {
        composeRule.setContent {
            AiLearningScreen(
                todayWordCount = todayWordCount,
                dueWordCount = dueWordCount,
                aiConfigured = aiConfigured,
                onOpenFeature = onOpenFeature,
                onOpenWordList = onOpenWordList,
            )
        }
    }

    @Test fun renders_the_header_card_and_the_ai_status_badge() {
        setScreen()
        composeRule.onNodeWithTag("ai_learning_screen").assertExists()
        composeRule.onNodeWithTag("ai_learning_header").assertExists()
        composeRule.onNodeWithTag("ai_learning_status_badge").assertExists()
        composeRule.onNodeWithText("AI 学").assertExists()
    }

    /** AI 网关还没接通，页面必须如实说「尚未接通」，不能让人以为已经能用。 */
    @Test fun badge_admits_the_ai_gateway_is_not_connected_yet() {
        setScreen(aiConfigured = false)
        composeRule.onNodeWithText("AI 尚未接通").assertExists()
        composeRule.onNodeWithText("AI 已配置").assertDoesNotExist()
    }

    @Test fun badge_switches_once_a_profile_exists() {
        setScreen(aiConfigured = true)
        composeRule.onNodeWithText("AI 已配置").assertExists()
        composeRule.onNodeWithText("AI 尚未接通").assertDoesNotExist()
    }

    @Test fun unconfigured_badge_is_neutral_while_ai_action_stays_mint() {
        setScreen(aiConfigured = false)
        val badge = composeRule.onNodeWithTag("ai_learning_status_badge").captureToImage().asAndroidBitmap()
        val neutral = AppPalette.TextSecondary.toArgb()
        assertTrue("unconfigured badge should use neutral text", (0 until badge.width).any { x ->
            (0 until badge.height).any { y -> badge.getPixel(x, y) == neutral }
        })
        val action = composeRule.onNodeWithTag("ai_learning_nav_detail").performScrollTo().captureToImage().asAndroidBitmap()
        val mint = AppleMintStart.toArgb()
        assertTrue("AI action should preserve mint gradient", (0 until action.width).any { x ->
            (0 until action.height).any { y -> colorDistance(action.getPixel(x, y), mint) <= 18.0 }
        })
    }

    @Test fun ai_detail_action_uses_brand_gradient_and_dark_text() {
        setScreen(aiConfigured = true)
        val action = composeRule.onNodeWithTag("ai_learning_nav_detail").performScrollTo().captureToImage().asAndroidBitmap()
        val pixels = (0 until action.width).flatMap { x ->
            (0 until action.height).map { y -> action.getPixel(x, y) }
        }
        assertTrue("AI detail action should render mint gradient start", pixels.minOf { colorDistance(it, AppleMintStart.toArgb()) } <= 18.0)
        assertTrue("AI detail action should render softened mint end", pixels.minOf { colorDistance(it, 0xFF65D3B9.toInt()) } <= 18.0)
        assertTrue("AI detail action should use readable teal text", pixels.minOf { colorDistance(it, 0xFF174C44.toInt()) } <= 18.0)
    }

    /**
     * 深色档取证（Task 23 修复轮 R-23.14）：状态胶囊的容器是 `Color.White.copy(alpha = 0.85f)`，
     * **主题无关**的亮容器——两档底色几乎相同，前景单迁主题访问器就是本轮的 AA 回归：
     * 深色档 `colors.text2` 取到 #9FB3AA 亮灰压白胶囊（1.69:1）。回退后未配置档必须仍逐像素
     * 打出登记的静态中性墨 `AppPalette.TextSecondary`（浅档 4.02:1 是 deep-on-white 同形既有债，另行登记）。
     *
     * 深色档走显式 `MintGlassTheme(themeMode = ThemeMode.Dark)` 包装（先例 `ThemeModeSwitchTest`、
     * `glass/GlassPanelThemeInkTest.kt:290`）：本类 `setContent` 不带主题提供者，
     * `cmd uimode night` 翻不动它（`LocalAppColors` 的 `compositionLocalOf` 默认值停在浅套），
     * 应用侧真实路径是设置页主题档——两条路径的差别与读数记录在修复轮报告。
     */
    @Test fun dark_tier_keeps_the_registered_static_neutral_ink_on_the_theme_invariant_pill() {
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Dark) {
                AiLearningScreen(
                    todayWordCount = 12,
                    dueWordCount = 5,
                    aiConfigured = false,
                    onOpenFeature = {},
                    onOpenWordList = {},
                )
            }
        }
        // 前置腿：页底角落 = AppColorDark.background，证明深色档确实到位（不是浅套冒充）。
        val screen = composeRule.onNodeWithTag("ai_learning_screen").captureToImage().asAndroidBitmap()
        assertEquals("dark tier must be actually rendered before the pill leg counts",
            AppColorDark.background.toArgb(), screen.getPixel(0, 0))
        val badge = composeRule.onNodeWithTag("ai_learning_status_badge").captureToImage().asAndroidBitmap()
        val registered = AppPalette.TextSecondary.toArgb()
        assertTrue("the theme-invariant white pill keeps its registered static neutral ink",
            (0 until badge.width).any { x -> (0 until badge.height).any { y -> badge.getPixel(x, y) == registered } })
        // 迁移后的深色值就是回归形状本身：亮灰字身哪怕出现一次都判红。
        val migratedDarkInk = AppColorDark.text2.toArgb()
        assertTrue(
            "the migrated-on-MintTheme text2 must not be painted into a theme-invariant container",
            (0 until badge.width).none { x -> (0 until badge.height).any { y -> badge.getPixel(x, y) == migratedDarkInk } },
        )
    }

    /** 同胶囊的配置档：登记的静态 `DomainColors.AiSpeech.deep`（#188F76）在场，深色域亮值（#35E0AC）禁入。 */
    @Test fun dark_tier_keeps_the_registered_deep_ink_on_the_configured_pill() {
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Dark) {
                AiLearningScreen(
                    todayWordCount = 12,
                    dueWordCount = 5,
                    aiConfigured = true,
                    onOpenFeature = {},
                    onOpenWordList = {},
                )
            }
        }
        val screen = composeRule.onNodeWithTag("ai_learning_screen").captureToImage().asAndroidBitmap()
        assertEquals("dark tier must be actually rendered before the pill leg counts",
            AppColorDark.background.toArgb(), screen.getPixel(0, 0))
        val badge = composeRule.onNodeWithTag("ai_learning_status_badge").captureToImage().asAndroidBitmap()
        val registeredDeep = DomainColors.AiSpeech.deep.toArgb()
        assertTrue("configured pill keeps the registered static deep ink",
            (0 until badge.width).any { x -> (0 until badge.height).any { y -> badge.getPixel(x, y) == registeredDeep } })
        val darkDomainDeep = DomainDark.getValue(AppDomain.AiSpeech).deep.toArgb()
        assertTrue(
            "the bright dark-tier domain deep (2.21:1 on the white pill) must not be painted here",
            (0 until badge.width).none { x -> (0 until badge.height).any { y -> badge.getPixel(x, y) == darkDomainDeep } },
        )
    }

    private fun colorDistance(actual: Int, expected: Int): Double {
        val dr = ((actual shr 16 and 0xFF) - (expected shr 16 and 0xFF)).toDouble()
        val dg = ((actual shr 8 and 0xFF) - (expected shr 8 and 0xFF)).toDouble()
        val db = ((actual and 0xFF) - (expected and 0xFF)).toDouble()
        return Math.sqrt(dr * dr + dg * dg + db * db)
    }

    /**
     * 折叠键只管**未上线**那批：点开逐枚列出、再点收回。
     *
     * 原名 `feature_rows_start_collapsed_and_expand_on_tap`（批 2 口径「功能入口默认收起」）。
     * 裁决④ 把默认态改成「已上线的三枚直接可见」，那条「默认全收起」的断言随之作废——
     * 它不是护栏而是被撤销的旧裁决本身（默认展开正是本批要交付的行为），所以这里改名并改成
     * 判「折叠只管未上线」；@Test 枚数只增不减（本类净 +4）。
     */
    @Test fun feature_rows_expand_and_collapse_on_the_unreleased_line() {
        setScreen()
        val unreleased = AiFeature.entries.filter { !it.implemented }
        unreleased.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}").assertDoesNotExist()
        }
        composeRule.onNodeWithTag("ai_learning_unreleased_toggle").performScrollTo().assertExists()

        expandUnreleased()
        unreleased.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}").assertExists().assertHasClickAction()
            composeRule.onNodeWithText(feature.summary).performScrollTo().assertExists()
        }

        composeRule.onNodeWithTag("ai_learning_unreleased_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        unreleased.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}").assertDoesNotExist()
        }
    }

    /**
     * RED-3（裁决④ / AC-R-23）：默认态就是「现在能用」的那三枚，未上线的四枚压成一条折叠行。
     *
     * 不点任何东西就能断言到已上线的能力；未上线的项**不在**默认树上，但那条折叠行自己标着
     * 「未上线」——用户不必展开也知道下面压着的是没做完的东西。
     * 徽章词在这里按字面量写：`featureStatusBadge` 是本批要新建的符号，测试若直接引用它，
     * 红样只会是一枚编译错误而不是判据红（派工块 §五.3 的「间接路径」要求）；
     * 词与 `implemented` 的绑定由 JVM 侧 `AiFeatureTest` 与下面的
     * `badges_read_the_shared_renderer_word_for_word` 两侧夹住。
     */
    @Test fun implemented_features_are_visible_by_default_and_unreleased_ones_collapse_into_one_line() {
        setScreen()
        AiFeature.entries.filter { it.implemented }.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}").performScrollTo().assertExists()
            composeRule.onNodeWithTag("ai_feature_status_badge_${feature.key}", useUnmergedTree = true)
                .performScrollTo()
                .assertTextEquals(AVAILABLE_BADGE)
        }
        AiFeature.entries.filter { !it.implemented }.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}").assertDoesNotExist()
        }
        composeRule.onNodeWithTag("ai_learning_unreleased_toggle").performScrollTo().assertExists().assertHasClickAction()
        composeRule.onNodeWithText(UNRELEASED_BADGE, useUnmergedTree = true).performScrollTo().assertExists()
        // 折叠标题不再拿「7 个」这种混算总数当默认可见面的口径（R-23.8 ③）。
        composeRule.onNodeWithText("AI 功能（${AiFeature.entries.size} 个）展开 ˅").assertDoesNotExist()
    }

    /** 折叠行点开后才逐枚列出未上线项，再点收回。 */
    @Test fun the_unreleased_line_expands_into_the_unreleased_rows_and_collapses_again() {
        setScreen()
        val unreleased = AiFeature.entries.filter { !it.implemented }
        composeRule.onNodeWithTag("ai_learning_unreleased_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        unreleased.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}").performScrollTo().assertExists().assertHasClickAction()
            composeRule.onNodeWithTag("ai_feature_status_badge_${feature.key}", useUnmergedTree = true)
                .performScrollTo()
                .assertTextEquals(UNRELEASED_BADGE)
        }
        composeRule.onNodeWithTag("ai_learning_unreleased_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        unreleased.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}").assertDoesNotExist()
        }
    }

    /**
     * 徽章词必须来自那一个共享产出（R-23.9）：屏上出现的词与 `featureStatusBadge(implemented)`
     * 逐枚相等，防止屏侧再长出第二套「可用 / 未实现」措辞。
     */
    @Test fun badges_read_the_shared_renderer_word_for_word() {
        setScreen()
        expandUnreleased()
        AiFeature.entries.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_status_badge_${feature.key}", useUnmergedTree = true)
                .performScrollTo()
                .assertTextEquals(featureStatusBadge(feature.implemented))
        }
    }

    /** TalkBack 读一行必须同时读到能力名与状态，不能只念名字。 */
    @Test fun each_feature_row_is_announced_with_its_own_status() {
        setScreen()
        AiFeature.entries.filter { it.implemented }.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}")
                .performScrollTo()
                .assertContentDescriptionEquals("${feature.title}，状态：${featureStatusBadge(feature.implemented)}")
        }
    }

    /** 折叠入口默认收起未上线那批，需要先点开再断言那四枚行。 */
    private fun expandUnreleased() {
        composeRule.onNodeWithTag("ai_learning_unreleased_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test fun renders_all_feature_rows_after_expanding() {
        setScreen()
        expandUnreleased()
        AiFeature.entries.forEach { feature ->
            composeRule
                .onNodeWithTag("ai_feature_${feature.key}")
                .assertExists()
                .assertHasClickAction()
            composeRule.onNodeWithText(feature.summary).performScrollTo().assertExists()
        }
    }

    /**
     * 用户明确抱怨过「点了没反应」。每个入口都必须真的把回调打出去。
     */
    @Test fun every_feature_row_reports_its_own_feature() {
        val received = mutableListOf<AiFeature>()
        setScreen(onOpenFeature = { received += it })
        expandUnreleased()
        AiFeature.entries.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}").performScrollTo().performClick()
        }
        composeRule.waitForIdle()
        assertEquals(AiFeature.entries.toList(), received)
    }

    @Test fun navigation_card_shows_the_real_word_counts() {
        setScreen(todayWordCount = 12, dueWordCount = 5)
        composeRule.onNodeWithTag("ai_learning_nav_card").assertExists()
        composeRule.onNodeWithTag("ai_learning_word_list_count").performScrollTo().assertExists()
    }

    /** 今日计划不可读时必须说不可读，不能拿 0 冒充。 */
    @Test fun navigation_card_does_not_fake_zero_when_the_plan_is_unavailable() {
        setScreen(todayWordCount = null, dueWordCount = null)
        composeRule.onNodeWithTag("ai_learning_nav_card").assertExists().performScrollTo()
        composeRule.onNodeWithText("今日计划还没读出来，稍后重试。").assertExists()
    }

    @Test fun detail_button_opens_the_word_list() {
        var opened = 0
        setScreen(onOpenWordList = { opened++ })
        composeRule.onNodeWithTag("ai_learning_nav_detail").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
    }

    @Test fun explainer_starts_collapsed_and_expands_on_tap() {
        setScreen()
        val toggle = composeRule.onNodeWithTag("ai_learning_explainer_toggle")
        toggle.performScrollTo().assertExists()
        composeRule.onNodeWithContentDescription("AI 学如何起作用说明").assertExists()
        composeRule.onNodeWithText("会用到的数据").assertDoesNotExist()
        toggle.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("会用到的数据").assertExists()
        composeRule.onNodeWithText("数据发往哪里").assertExists()
    }

    private companion object {
        const val AVAILABLE_BADGE = "现在能用"
        const val UNRELEASED_BADGE = "未上线"
    }
}
