package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeAction
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.theme.MintGlassTheme
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
 * 用例断高度。**R-11.0 起动作本体一律走 [InlineNoticeAction]**（不再是本文件里手抄的调用方样张），
 * 于是那条 48dp 按 R-10.7① 只打在**可点节点**上（`onNode(hasClickAction(), useUnmergedTree = true)`）：
 * 打在包裹层标签上那条是自证（那个 `Box` 自己写着 `heightIn(min = 48.dp)`，除非整条删掉否则恒真），
 * 真正承重的口径是「48dp 交到了能点的那一枚」——删掉包裹层的 `propagateMinConstraints` 时，
 * 包裹层仍 48dp、槽内退回 13sp 行高（真机实测 15.24dp，变异自证 ⑨），只有可点节点这条会红。
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
     * `mergeDescendants` 的容器里，槽内容由 [InlineNoticeAction] 渲染成稿 `.act` 的形状
     * （`Text` + `clickable` + `FontWeight.Bold` + `noticeInk(...).action` 的墨色，四件套只在
     * `InlineNotice.kt` 里写一遍，源码契约见 `InlineNoticeActionContractTest`）。
     * **R-11.0 起本用例就是「调用方该怎么写」的样张**：`action = { InlineNoticeAction(kind, "标签", onClick = { … }) }`，
     * 不再自排 `Text` + `Modifier.clickable` + 自取动作墨（那是被禁形状，全域 0 处 `color = noticeInk(`）。
     * 回调必须具名传 `onClick =`：裁决把签名末位钉成 `modifier`（不是函数类型），尾随 lambda 写法编译不过。
     *
     * **48dp 只打在可点节点上（R-10.7①，本任务起）**：包裹层那条自证断言已退役——
     * `inline_notice_action` 落在组件那个自己写着 `heightIn(min = 48.dp)` 的 `Box` 上，除非整条删掉否则恒真；
     * 把 48dp 真正**交给槽内那枚 `clickable`** 的是包裹层的 `propagateMinConstraints = true`，
     * 删掉它以后 `Box` 仍是 48dp、而 [InlineNoticeAction] 的 `fillMaxHeight()` 只吃到 13sp 行高
     * （真机实测 15.24dp），提示条的动作就成了「看着像链接、点不着」——F5-03 已破而恒真那条继续绿。
     * 所以承重的是下面这条打在 `hasClickAction()` 节点上的断言（变异自证 ⑨：删 `propagateMinConstraints`
     * → 红的正是这条，报 `Actual height is 15.238096.dp`）。
     *
     * **同一条断言还钉了「恰好 48dp」这个上限（本棒实测新增）**：48dp 是触控**下限**，不是「越大越好」——
     * 裁决 R-11.0 原文让槽内加 `fillMaxHeight()` 承接，实测把可点面撑到 635.05dp（有界宿主里
     * `fillMaxHeight()` 吃的是最大高约束），整条提示被拉成半屏空白。故这里按 `assertEquals(48.dp, …)` 钉死：
     * 撑高（`fillMaxHeight()` 回潮）与缩回（`propagateMinConstraints` 被删）两个方向都当场红。
     * 无界宿主（`LazyColumn` 一类）里 `fillMaxHeight()` 是空操作，所以这条只能在有界的组件级宿主里钉。
     *
     * **两条可点计数的分工同 `EntryRowTest`（R-10.7②，I-2）**：合并树那条抓「动作丢了 click（计数变 0）」；
     * 未合并树那条把「这条提示里只能有一枚可点节点」钉成契约，并且是**非合并型 click 源**
     * （`Modifier.semantics { onClick = … }` 不给自己开 `mergeDescendants`，会被并进动作节点）的唯一防线。
     * 合并型源（`Modifier.clickable`）在两棵树里都数得出——实测口径见 `EntryRowTest` 变异 ⑩ 那段，别照
     * 「合并树一定数不出」的二手推断读。与 Task 9 `AppScreenHeaderTest`（`:106/131`）的差别：那边根节点
     * 不是 clickable，子级可点源的合并行为不同，照抄写法不等于照抄承重。
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
                        // R-11.0 起调用方的唯一写法：动作本体（Text + clickable + Bold + 动作墨）
                        // 全部交给同文件的 InlineNoticeAction；kind 必须与外层提示条一致。
                        // 回调**必须具名传** `onClick =`：签名按裁决钉成 (kind, label, onClick, modifier = Modifier)，
                        // 末位是 modifier（不是函数类型），所以尾随 lambda 写法编译不过
                        // （实测报错 `No value passed for parameter 'onClick'`）——已在报告里点名为顾虑。
                        InlineNoticeAction(InlineNoticeKind.Error, "重试", onClick = { clicks += 1 })
                    },
                )
            }
        }
        val action = composeRule.onNodeWithTag("inline_notice_action")
        action.assertExists()
        composeRule.onNode(hasClickAction(), useUnmergedTree = true).assertHeightIsAtLeast(48.dp)
        // 48dp 是**下限也是这里的实际上限**：动作槽只把触控面抬到 F5-03 的硬下限，不许把整条提示撑高
        // （槽内 `fillMaxHeight()` 在有界宿主里会吃满宿主高度，提示条就变成半屏空白 + 一行字）。
        val clickableBounds = composeRule.onNode(hasClickAction(), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val clickableHeight = clickableBounds.bottom - clickableBounds.top
        assertEquals(
            "动作槽的可点面必须恰好是 48dp 下限（$clickableBounds）：撑高就是 fillMaxHeight 吃满了宿主高度，" +
                "提示条会被拉成半屏；缩回去就是 propagateMinConstraints 没把下限传进槽内",
            48.dp,
            clickableHeight,
        )
        assertEquals(
            "动作是这条提示里唯一的可点目标",
            1,
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals(
            "未合并树里也只能有一枚可点节点：提示条本体与槽内装饰都不得长出第二处可点源" +
                "（这条把「原始语义树里只有一枚可点」钉成契约；合并树能否看见第二枚按 `EntryRowTest` 用例 ⑩ 的实测口径处理，别照机制推断写死，R-10.7②）",
            1,
            composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().size,
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
