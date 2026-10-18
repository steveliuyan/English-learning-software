package com.example.englishlearning.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.ConfirmDestructiveDialog
import com.example.englishlearning.ui.components.destructiveDialogBody
import com.example.englishlearning.ui.theme.MintGlassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 批 1 Task 11 的组件级 connected 护栏：[ConfirmDestructiveDialog]
 * （spec §3「删除/清空确认：级联条数 + 不受影响的保留面 + 不可撤销」）。
 *
 * 分工（先写清楚，否则后人会来这里找「按钮容器色 == error」那条断言）：**connected 读不到颜色**
 * （Task 9 实测），裁决 R-11.1 的三档按钮墨（破坏性确认 = `error` 容器 + `surface` 前景）由 JVM 纯表
 * `dialogButtonInk(role, set)` 钉死（见 `ConfirmDestructiveInkTest`）；措辞四要素齐备由
 * `ConfirmDestructiveWordingTest` 钉死。本类只钉**渲染面**：正文各行真的在树上、
 * 两枚按钮各恰一枚可点节点、48dp 触控下限、回调真的接上、`state = false` 时一个节点都不组合。
 *
 * 正文期望值一律由纯函数 [destructiveDialogBody] 现算，不在用例里重抄一遍文案
 * （抄两遍 = 措辞改一次要改两处，且用例与生产可能各漂一边）。
 *
 * 测量口径沿用本包先例（`EntryRowTest` / `InlineNoticeTest`）：组件级 `createComposeRule()` 宿主
 * （对话框内容按 tag 可达，先例见 `SentenceAnalysisScreenTest` 的 `sentence_analysis_decline_outbound`）；
 * 不写 `performScrollTo()`；**每个方法只调一次 `setContent`**（同方法内第二次真机必崩，Task 9 实测）。
 */
class ConfirmDestructiveDialogTest {
    @get:Rule val composeRule = createComposeRule()

    private val cascade = listOf("会同时删除 12 条复习记录", "会删除 3 张卡片配图")
    private val keep = listOf("其他词书不受影响", "导出过的备份不受影响")

    /**
     * 钉的破坏：三件事少说一件（级联条数 / 保留面 / 不可撤销）、按钮丢标签或回调、
     * 或面板本体长出第三枚可点源。
     *
     * 可点计数钉**恰两枚**（确认 + 取消），两棵树各钉一次：合并树那条抓「按钮丢了 click」，
     * 未合并树那条抓「面板或正文里长出第二处可点源」（口径同 `EntryRowTest` R-10.7②）。
     */
    @Test
    fun dialog_states_cascade_keep_and_irreversibility_with_two_exits() {
        var confirmed = false
        var dismissed = false
        composeRule.setContent {
            MintGlassTheme {
                ConfirmDestructiveDialog(
                    state = true,
                    title = "删除词书《核心 1000》",
                    cascadeLines = cascade,
                    keepLines = keep,
                    irreversible = true,
                    confirmLabel = "删除词书",
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeRule.onNodeWithText("删除词书《核心 1000》").assertExists()
        val body = destructiveDialogBody(cascade, keep, irreversible = true)
        assertTrue("正文必须非空（空正文 = 三件事一件都没说）：$body", body.isNotEmpty())
        assertTrue("不可撤销必须明说（AGENTS.md：删除级联确认）：$body", body.any { it.contains("不可撤销") })
        body.forEach { line -> composeRule.onNodeWithText(line).assertExists() }

        val confirm = composeRule.onNodeWithTag("confirm_destructive_confirm")
        confirm.assertHasClickAction()
        confirm.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        composeRule.onNodeWithText("删除词书").assertExists()
        val dismiss = composeRule.onNodeWithTag("confirm_destructive_dismiss")
        dismiss.assertHasClickAction()
        dismiss.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        assertEquals(
            "对话框恰两枚可点目标（确认 + 取消）：面板本体与正文都不是触控目标",
            2,
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals(
            "未合并树里也只能有两枚可点节点（口径同 EntryRowTest R-10.7②）",
            2,
            composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().size,
        )

        confirm.performClick()
        assertTrue("确认按钮必须回调 onConfirm", confirmed)
        dismiss.performClick()
        assertTrue("取消按钮必须回调 onDismiss", dismissed)
    }

    /**
     * 钉的破坏：可逆的破坏性入口也谎称不可撤销。
     *
     * 这条与 `ConfirmDestructiveWordingTest.reversible_confirmation_does_not_claim_irreversibility`
     * 是一对：JVM 那条钉纯函数真的不产出那一行，本条钉**渲染面**也没把它画出来
     * （纯函数对了、组件却另外硬写一行，JVM 侧看不出来）。
     */
    @Test
    fun reversible_confirmation_renders_no_irreversible_line() {
        val reversibleCascade = listOf("会重排今天的新词队列")
        composeRule.setContent {
            MintGlassTheme {
                ConfirmDestructiveDialog(
                    state = true,
                    title = "重建今日计划",
                    cascadeLines = reversibleCascade,
                    keepLines = emptyList(),
                    irreversible = false,
                    confirmLabel = "重建",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("重建今日计划").assertExists()
        destructiveDialogBody(reversibleCascade, emptyList(), irreversible = false)
            .forEach { line -> composeRule.onNodeWithText(line).assertExists() }
        composeRule.onNodeWithText("此操作不可撤销。").assertDoesNotExist()
        assertEquals(
            "可逆档同样只有两枚可点目标",
            2,
            composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    /**
     * 钉的破坏：`state = false` 仍然组合出对话框（现网 4 份 `AlertDialog` 都是 `if (show) { … }`，
     * 收进组件后这条开关必须真的关掉整棵树，否则会凭空多出一枚模态窗口挡住底下的屏）。
     */
    @Test
    fun hidden_state_composes_nothing() {
        composeRule.setContent {
            MintGlassTheme {
                ConfirmDestructiveDialog(
                    state = false,
                    title = "删除词书《核心 1000》",
                    cascadeLines = cascade,
                    keepLines = keep,
                    confirmLabel = "删除词书",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("删除词书《核心 1000》").assertDoesNotExist()
        composeRule.onNodeWithTag("confirm_destructive_confirm").assertDoesNotExist()
        composeRule.onNodeWithTag("confirm_destructive_dismiss").assertDoesNotExist()
        assertEquals(
            "关闭态不得留下任何可点节点",
            0,
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size,
        )
    }

    /**
     * 钉的破坏：两枚按钮被压回 F5-03 之下（对话框按钮是模态窗口里唯一的出路，
     * 触控面不够就是「看得见点不着」）。
     *
     * 按 R-10.7① 打在**可点节点**上：`onAllNodes(hasClickAction(), useUnmergedTree = true)` 逐枚量，
     * 而不是量标签层（标签与 clickable 同节点时量标签层是自证）。
     */
    @Test
    fun both_buttons_clear_the_48dp_touch_floor() {
        composeRule.setContent {
            MintGlassTheme {
                ConfirmDestructiveDialog(
                    state = true,
                    title = "清空学习记录",
                    cascadeLines = listOf("会删除 248 条复习记录"),
                    keepLines = listOf("词书与收藏不受影响"),
                    confirmLabel = "清空",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        val clickables = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true)
        assertEquals("恰两枚可点按钮", 2, clickables.fetchSemanticsNodes().size)
        clickables[0].assertHeightIsAtLeast(48.dp)
        clickables[1].assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("confirm_destructive_confirm").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("confirm_destructive_dismiss").assertHeightIsAtLeast(48.dp)
    }
}
