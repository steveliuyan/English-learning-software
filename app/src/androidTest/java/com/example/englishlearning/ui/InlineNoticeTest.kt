package com.example.englishlearning.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.noticeInk
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.appColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 批 1 Task 10 的组件级 connected 护栏：[InlineNotice]（稿 §Banner `mockups.html:178-186`）。
 *
 * 口径与 `EntryRowTest` 同一条：**connected 读不到颜色**（Task 9 实测），三态的「前景 / 底 / 描边 /
 * 动作墨」四枚取值由 JVM 纯表 `noticeInk(kind, set)` 钉死（见 `InlineNoticeKindTest`），本类只钉
 * 几何、语义、可点数量、裁切。测量口径同样沿用本包先例：`assertHeightIsAtLeast` 与
 * `getUnclippedBoundsInRoot` 是同一个 `withUnclippedBoundsInRoot` 口径；组件级宿主无可滚动祖先，
 * 不写 `performScrollTo()`；每个方法只调一次 `setContent`。
 *
 * 48dp 只属于**动作节点**：无动作的 `.banner` 约 42dp（12dp 上下 padding + 13sp 行高），它是内容
 * 不是触控目标，把它钉成 ≥48dp 会造出一条稿里没有、F5-03 也没要求的水线——所以本类只有带 action 的
 * 用例断高度，且断的是 `inline_notice_action` 那枚节点。
 */
class InlineNoticeTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * 钉的破坏：提示条自己变成可点（稿 `.banner` 没有整条点击行为），或三态里某一态没渲染、
     * 行标签漂移（`inline_notice_error` / `_info` / `_success` 是批 2 各屏用例的定位锚点）。
     */
    @Test
    fun notice_without_action_has_no_click_target() {
        composeRule.setContent {
            MintGlassTheme {
                Column {
                    InlineNotice(text = "保存失败，请重试", kind = InlineNoticeKind.Error)
                    InlineNotice(text = "词书已同步到本地", kind = InlineNoticeKind.Info)
                    InlineNotice(text = "打卡完成", kind = InlineNoticeKind.Success)
                }
            }
        }
        InlineNoticeKind.entries.forEach { kind ->
            composeRule.onNodeWithTag("inline_notice_${kind.name.lowercase()}").assertExists()
        }
        assertEquals(
            "无动作的提示条不得有任何可点节点（整条不是操作）",
            0,
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size,
        )
    }

    /**
     * 钉的破坏：动作丢了标签/回调、动作节点缩回 F5-03 之下、或提示条本体长出第二个可点目标。
     *
     * `inline_notice_action` 这个标签与 `clickable` 必须在**同一枚语义节点**上：组件把动作槽包在
     * `mergeDescendants` 的容器里，槽内容由调用方渲染成稿 `.act` 的形状（`Text` + `clickable` +
     * `FontWeight.Bold` + `noticeInk(...).action` 的墨色）。用例这里就是「调用方该怎么写」的样张。
     */
    @Test
    fun notice_action_is_the_only_click_target_and_meets_the_48dp_floor() {
        var clicks = 0
        composeRule.setContent {
            MintGlassTheme {
                InlineNotice(
                    text = "语音评测暂时不可用",
                    kind = InlineNoticeKind.Error,
                    action = {
                        // 调用方的写法样张：墨色问纯表（与 JVM 侧 `InlineNoticeKindTest` 同一张表），
                        // 字重按稿 `.act` 的 700 走 FontWeight.Bold；标签与 48dp 触控面由组件负责。
                        val ink = noticeInk(InlineNoticeKind.Error, appColors())
                        Text(
                            text = "重试",
                            style = AppType.Footnote,
                            color = ink.action,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { clicks += 1 },
                        )
                    },
                )
            }
        }
        val action = composeRule.onNodeWithTag("inline_notice_action")
        action.assertHeightIsAtLeast(48.dp)
        assertEquals(
            "动作是这条提示里唯一的可点目标",
            1,
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size,
        )
        action.performClick()
        assertEquals(1, clicks)
    }

    /**
     * 钉的破坏：提示文案被加上 `maxLines` 而裁掉（提示文案裁掉就是丢信息，与入口行标题/副标题
     * 的「提示性内容允许省略号」是**相反**口径，见 `EntryRowTest` 同族用例的判定方向）。
     *
     * 本用例**不断高度**：无动作的提示条约 42dp 是合规的（内容不是触控目标）。
     */
    @Test
    fun notice_text_wraps_instead_of_clipping_at_font_scale_2() {
        val longText = "这是一条需要换行才能读完的长提示：网络在同步词书时中断，稍后会自动重试，" +
            "如果反复失败请先检查本机存储空间与登录状态后再继续（Network unavailable）"
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                MintGlassTheme {
                    InlineNotice(text = longText, kind = InlineNoticeKind.Info)
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(longText)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue(
            "提示文案必须整段可读：不得有视觉裁切（裁掉的是信息）。lineCount=${layout.lineCount} " +
                "overflow=${layout.hasVisualOverflow} size=${layout.size} width=${layout.didOverflowWidth}",
            !layout.hasVisualOverflow,
        )
        assertTrue("长文案应当真的换行（不给 maxLines），实际 lineCount=${layout.lineCount}", layout.lineCount >= 2)
    }
}
