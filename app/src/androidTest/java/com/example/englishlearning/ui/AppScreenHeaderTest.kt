package com.example.englishlearning.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
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
 * 口径全部沿用本包既有先例（`createComposeRule()` 组件级宿主；48dp 判定是**单口径 × 三条断言**：
 * `assertHeightIsAtLeast`/`assertWidthIsAtLeast` 内部就走 `withUnclippedBoundsInRoot`，与末尾手写的
 * `getUnclippedBoundsInRoot()` 是同一份测量，两条尺寸断言各钉一轴、末条只把实际值写进失败消息；
 * `assertIsDisplayed()` 才是唯一读**裁剪后**矩形的断言、只钉可见性；
 * 大字号护栏用 `LocalDensity` 换 `fontScale = 2f`，裁切信号读 `TextLayoutResult`；「唯一可点出口」用
 * `onAllNodes(hasClickAction())` 的节点数而不是 `onLast()` 恒真写法）。
 * 组件级宿主没有可滚动祖先，`performScrollTo()` 在这里直接抛
 * `Semantic Node has no parent layout with a Scroll SemanticsAction`（Task 9 首跑实测），故用例不写这一步。
 * 每个方法只调一次 `setContent`，用例之间不复用宿主。
 */
class AppScreenHeaderTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * 钉的破坏：返回位可点区若从 48dp 缩回视觉容器的 40dp（或被 clip 吃掉边缘），
     * 大触控目标契约（F5-03）当场失效。三条断言共用同一份 unclipped 测量、各钉一轴（高 / 宽 /
     * 把实际值写进失败消息），末条 `assertTrue` 不是第二口径；`assertIsDisplayed()` 是唯一读裁剪后
     * 矩形的可见性口径；点击回调用例独立钉「真能点」。断言按顺序短路：尺寸断言先红时末条正常跑法
     * 到不了（评审临时注释掉前两条才量出末条单独能红：40×40 且 `top=4.19`，
     * 恰好证明行 `heightIn(min = 48.dp)` 的居中没坏）。
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
     * 钉的破坏：页头改版把标题压成单行（裁切或省略号），以及标题超出两行容量后 `Clip` 静默丢字。
     * 末条断言读 `TextLayoutResult`，钉的就是这两个形态（「两行换行 vs 单行裁切/省略」与「超容量丢字」）；
     * `>= 48.dp` 那条只钉塌宽（标题列被压成 0 宽的反例形态）；保留的 `> 24.dp` 只能当「不是 0 高」的哨兵，
     * **不得称它为裁切判定**——同 style 同 fontScale 的两处节点高实测 41.90dp（页头标题）与
     * 34.29dp（同宿主参考 Text），dp 门限不可解释，变异 `maxLines = 1` 时它照样不红。
     */
    @Test fun header_survives_font_scale_2_without_clipping_the_title() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                MintGlassTheme { AppScreenHeader("学习记录与复习统计（长标题用）", subtitle = "本周新增 128 词", onBack = {}) }
            }
        }
        val title = composeRule.onNodeWithText("学习记录与复习统计（长标题用）")
        title.assertExists()                                   // 语义节点存在（Clip 与 Ellipsis 都不改语义，此条不足以判裁切）
        val box = title.getUnclippedBoundsInRoot()
        assertTrue("fontScale=2.0 下标题不得塌成 0 宽，实际=$box", box.right - box.left >= 48.dp)
        assertTrue("两行标题应真的长出高度，实际=$box", box.bottom - box.top > 24.dp)
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue(
            "标题必须真的换到两行且无字形裁切：lineCount=${layout.lineCount} " +
                "overflow=${layout.hasVisualOverflow} size=${layout.size}",
            layout.lineCount >= 2 && !layout.hasVisualOverflow,
        )
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

    /**
     * 钉的破坏：R-9.4 补的可选 `title` 层若少渲染一层（只出 `reason`，或把标题拼进 `reason` 里），
     * 两条 `onNodeWithText` 当场红；若为标题另加一个可点容器，末尾「唯一可点出口」的数量断言跟着红。
     * 文案取稿 `.card .empty` 的两层示例（`.t` 15px/700 + `.d` 13px/400）。
     */
    @Test fun screen_unavailable_renders_the_optional_title_layer() {
        var tapped = false
        composeRule.setContent {
            MintGlassTheme {
                ScreenUnavailable(
                    title = "还没有导入的词书",
                    reason = "先从内置的 10 本里挑一本，或导入 .wbpack 词书包",
                    actionLabel = "浏览内置词书",
                    onAction = { tapped = true },
                )
            }
        }
        composeRule.onNodeWithText("还没有导入的词书").assertExists()
        composeRule.onNodeWithText("先从内置的 10 本里挑一本，或导入 .wbpack 词书包").assertExists()
        composeRule.onNodeWithTag("screen_unavailable_action").assertIsDisplayed().performClick()
        assertTrue(tapped)
        assertEquals("补标题层不得长出第二个可点出口",
            1, composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }
}
