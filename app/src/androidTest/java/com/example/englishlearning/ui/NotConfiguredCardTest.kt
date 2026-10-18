package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.NotConfiguredCard
import com.example.englishlearning.ui.components.notConfiguredStatusLine
import com.example.englishlearning.ui.theme.MintGlassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 批 1 Task 11 Step 2 的组件级 connected 护栏：[NotConfiguredCard]
 * （spec §3「AI 未配置：**一句现状 + 一个『去配置 ›』出口**，绝不再做只给文字的死屏」）。
 *
 * 口径与 `EntryRowTest` / `InlineNoticeTest` 同一条：**connected 读不到颜色**（Task 9 实测），
 * 四枚槽位墨由 JVM 纯表 `notConfiguredCardInks(set)` 钉死（见 `NotConfiguredCardContractTest`），
 * 本类只钉几何、语义、可点数量、裁切。测量口径同样沿用本包先例：`assertHeightIsAtLeast` 与
 * `getUnclippedBoundsInRoot` 同属一个 `withUnclippedBoundsInRoot`；组件级宿主无可滚动祖先，
 * 不写 `performScrollTo()`；**每个方法只调一次 `setContent`**（同方法内第二次真机必崩，Task 9 实测）。
 *
 * **死屏禁令的另一半不在这里**：「组件不允许渲染一张没有出口的卡」是签名形状问题
 * （`onConfigure` 必填、不可空、无默认值），由 `NotConfiguredCardContractTest` 的源码腿钉住；
 * 本类只能证明「这一处渲染出了出口」。屏侧「四个消费屏重排后必须调用 `NotConfiguredCard(`」
 * 那条按 brief Step 2 归批 3 收口（本任务先测组件自身）。
 */
class NotConfiguredCardTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * 钉的破坏：现状句漂词/丢功能名、出口丢标签或回调、出口缩回 F5-03 之下、
     * 卡本体或行内徽标长出第二个可点源。
     *
     * 出口是这张卡**唯一**的触控目标，`not_configured_goto` 与 `clickable` 必须在同一枚语义节点上
     * （组件把两者并在 `mergeDescendants` 的那一层）；否则标签在父、click 在子，
     * `onNodeWithTag("not_configured_goto").performClick()` 会打在没有 click action 的节点上
     * （`InlineNotice` 动作槽的同一处置、同一实测依据）。
     *
     * 两条可点计数的分工同 `EntryRowTest`（R-10.7②）：合并树那条抓「出口丢了 click（计数变 0）」，
     * 未合并树那条把「这张卡里只有一枚可点节点」钉成契约，并且是非合并型 click 源
     * （`Modifier.semantics { onClick = … }`）的唯一防线。
     */
    @Test
    fun card_gives_one_status_line_and_the_only_configure_exit() {
        var configured = false
        composeRule.setContent {
            MintGlassTheme {
                NotConfiguredCard(featureLabel = "长难句分析", onConfigure = { configured = true })
            }
        }
        composeRule.onNodeWithText(notConfiguredStatusLine("长难句分析")).assertExists()
        composeRule.onNodeWithText("去配置").assertExists()

        val exit = composeRule.onNodeWithTag("not_configured_goto")
        exit.assertHasClickAction()
        exit.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        exit.assertHeightIsAtLeast(48.dp)
        // R-10.7①：48dp 还要打在**可点节点**上（标签层量出来恒真是自证，可点面才是 F5-03 的约束对象）。
        composeRule.onNode(hasClickAction(), useUnmergedTree = true).assertHeightIsAtLeast(48.dp)

        assertEquals(
            "出口是这张卡唯一的可点目标：卡本体与行内徽标都不得成为第二可点源",
            1,
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals(
            "未合并树里也只能有一枚可点节点（合并型 clickable 两棵树都数得出，" +
                "这条守的是非合并型 `semantics { onClick = … }` 那一类，口径见 EntryRowTest 变异 ⑩）",
            1,
            composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        exit.performClick()
        assertTrue("出口点击必须回调 onConfigure（死屏禁令的执行面）", configured)
    }

    /**
     * 钉的破坏：补充说明那一行顶掉了出口（「给了 detail 就不给去配置」正是 spec §3 点名的死屏变体），
     * 或补充说明自己长成一枚可点目标。
     *
     * [detail] 是可选的第二行，出口始终在：传与不传都必须只有一枚可点节点、都必须点得到。
     */
    @Test
    fun optional_detail_never_replaces_the_configure_exit() {
        var configured = false
        val detail = "去「设置 · AI」添加一套服务并设为默认"
        composeRule.setContent {
            MintGlassTheme {
                Column {
                    NotConfiguredCard(
                        featureLabel = "拍照翻译",
                        onConfigure = { configured = true },
                        detail = detail,
                    )
                }
            }
        }
        composeRule.onNodeWithText(notConfiguredStatusLine("拍照翻译")).assertExists()
        composeRule.onNodeWithText(detail).assertExists()
        composeRule.onNodeWithTag("not_configured_goto").assertHasClickAction()
        assertEquals(
            "补一行说明不许多出可点目标（补充说明是内容不是操作）",
            1,
            composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithTag("not_configured_goto").performClick()
        assertTrue("带 detail 时出口仍必须回调 onConfigure", configured)
    }

    /**
     * 钉的破坏：现状句/补充说明被加上 `maxLines` 而裁掉（spec §3 的 fontScale 2.0 口径：
     * 这张卡承载的就是「为什么用不了 + 去哪儿解决」，裁掉任何一句都是丢信息）。
     *
     * 与 `EntryRow` 的标题/副标题**相反**口径（那边是提示性内容、允许省略号），
     * 所以这里 `hasVisualOverflow == false` 才是期望结果。判定方向由本条钉住，
     * 源码腿（`maxLines` 0 处）在 `NotConfiguredCardContractTest`。
     */
    @Test
    fun status_and_detail_wrap_instead_of_clipping_at_font_scale_2() {
        val longLabel = "长难句分析（含从句拆解、成分标注与逐段译文对照）"
        val longDetail = "去「设置 · AI」添加一套服务并设为默认；若已添加，请检查该服务的 Endpoint 与密钥是否仍然有效"
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                MintGlassTheme {
                    NotConfiguredCard(
                        featureLabel = longLabel,
                        onConfigure = {},
                        detail = longDetail,
                    )
                }
            }
        }
        listOf(notConfiguredStatusLine(longLabel), longDetail).forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(text)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertTrue(
                "文案必须整段可读（不得有视觉裁切）：「$text」lineCount=${layout.lineCount} " +
                    "overflow=${layout.hasVisualOverflow} width=${layout.didOverflowWidth}",
                !layout.hasVisualOverflow,
            )
            assertTrue("长文案应当真的换行（不给 maxLines）：「$text」lineCount=${layout.lineCount}", layout.lineCount >= 2)
        }
    }
}
