package com.example.englishlearning.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.ScreenUnavailable
import com.example.englishlearning.ui.theme.MintGlassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 批 1 Task 9 的组件级 connected 护栏：`AppScreenHeader` 与 `ScreenUnavailable`。
 *
 * 口径全部沿用本包既有先例（`createComposeRule()` 组件级宿主；48dp 双口径 =
 * `assertHeightIsAtLeast`/`assertWidthIsAtLeast` 读裁剪后矩形 + `getUnclippedBoundsInRoot` 断真实尺寸；
 * 大字号护栏用 `LocalDensity` 换 `fontScale = 2f`；「唯一可点出口」用
 * `onAllNodes(hasClickAction())` 的节点数而不是 `onLast()` 恒真写法）。
 * 每个方法只调一次 `setContent`，用例之间不复用宿主。
 */
class AppScreenHeaderTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * 钉的破坏：返回位可点区若从 48dp 缩回视觉容器的 40dp（或被 clip 吃掉边缘），
     * 大触控目标契约（F5-03）当场失效——两条口径 + 点击回调用例各自都能红。
     */
    @Test fun header_back_is_a_48dp_target_with_a_readable_description() {
        var backed = false
        composeRule.setContent { MintGlassTheme { AppScreenHeader("学习记录", onBack = { backed = true }) } }
        val back = composeRule.onNodeWithContentDescription("返回")
        back.assertHasClickAction()
        back.assertIsDisplayed()
        back.assertHeightIsAtLeast(48.dp); back.assertWidthIsAtLeast(48.dp)
        val b = back.getUnclippedBoundsInRoot()
        assertTrue("返回可点区应 ≥48dp，实际=$b", b.bottom - b.top >= 48.dp && b.right - b.left >= 48.dp)
        backed = false; back.performClick(); assertTrue(backed)
    }

    /**
     * 钉的破坏：页头改版把标题压成单行省略号或 `weight` 塌宽（F5-03 移交账点过的反例形态）时，
     * fontScale=2.0 下标题语义节点会缺文本或宽度归零——两条断言分别拦截。
     */
    @Test fun header_survives_font_scale_2_without_clipping_the_title() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                MintGlassTheme { AppScreenHeader("学习记录与复习统计（长标题用）", subtitle = "本周新增 128 词", onBack = {}) }
            }
        }
        val title = composeRule.onNodeWithText("学习记录与复习统计（长标题用）")
        title.assertExists()                                   // maxLines=2 且不省略号 → 文本节点完整存在
        val box = title.getUnclippedBoundsInRoot()
        assertTrue("fontScale=2.0 下标题不得塌成 0 宽，实际=$box", box.right - box.left >= 48.dp)
        assertTrue("两行标题应真的长出高度，实际=$box", box.bottom - box.top > 24.dp)
    }

    /**
     * 钉的破坏：整屏不可用态若长出第二个可点出口（多余的链接/按钮），或动作按钮丢了
     * `screen_unavailable_action` 标签与回调，本用例的数量断言与点击断言当场红。
     */
    @Test fun screen_unavailable_offers_exactly_one_action() {
        var tapped = false
        composeRule.setContent {
            MintGlassTheme { ScreenUnavailable(reason = "AI 尚未配置", actionLabel = "去配置", onAction = { tapped = true }) }
        }
        composeRule.onNodeWithTag("screen_unavailable_action").assertIsDisplayed().performClick()
        assertTrue(tapped)
        assertEquals("整屏不可用态只能有一个可点出口",
            1, composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }
}
