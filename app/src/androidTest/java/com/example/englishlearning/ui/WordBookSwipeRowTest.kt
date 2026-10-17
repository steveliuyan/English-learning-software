package com.example.englishlearning.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.WordBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 可滑动词书卡在**静止**状态下不得露出底下的红色删除底。
 *
 * 回归背景（真机像素取证）：这张卡一度带 0.985 的「未选中」缩放动画，卡片于是比整行窄，
 * 而那层红色删除底是滑动容器**始终**铺在内容之下的，结果红色从卡片四周露出一圈
 * `#B3261E`（M3 `colorScheme.error`）描边，看上去像报错态。
 *
 * 这个缺陷靠「是否正在拖动」也拦不住：`SwipeToDismissBoxState.progress` 在锚点相同的
 * 静止态返回非 0 值（实测门控无效）。既然静止时唯一能挡住红底的就是「内容铺满整行」，
 * 这里就直接对渲染结果做像素断言，把「别再加缩放」钉死。
 */
@RunWith(AndroidJUnit4::class)
class WordBookSwipeRowTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun settled_swipe_row_does_not_reveal_the_delete_backdrop() {
        val book = WordBook("imported-book", "导入的词书", "自定义", 100, "v1", "user-provided-xlsx")
        var errorColor = 0
        composeRule.setContent {
            errorColor = MaterialTheme.colorScheme.error.toArgb()
            SwipableWordBookCard(
                book = book,
                selected = false,
                progress = null,
                progressLoading = false,
                onSelect = {},
                onDelete = {},
            )
        }
        val bitmap = composeRule.onNodeWithTag("wordbook_swipe_imported-book")
            .captureToImage().asAndroidBitmap()

        var backdropPixels = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (isClose(bitmap.getPixel(x, y), errorColor)) backdropPixels++
            }
        }
        // 允许极少数抗锯齿边缘像素：卡片与红底都用 18dp 圆角、边界完全重合，
        // 圆角处可能留下几个次像素混色。真机实测这个数是 0；变红时会到数千个。
        assertTrue(
            "静止的可滑动卡片露出了红色删除底：$backdropPixels 个近似 error 色像素（上限 64）",
            backdropPixels <= 64,
        )
    }

    private fun isClose(actual: Int, expected: Int): Boolean {
        fun channel(shift: Int) = ((actual shr shift) and 0xFF) - ((expected shr shift) and 0xFF)
        return kotlin.math.abs(channel(16)) <= 10 &&
            kotlin.math.abs(channel(8)) <= 10 &&
            kotlin.math.abs(channel(0)) <= 10
    }

    /**
     * I-A2（终审 A §2）：「删除词书」自定义动作必须挂在**被 TalkBack 聚焦的那个可点击节点**上。
     *
     * 原形状把 `customActions` 声明在 `SwipeToDismissBox` 的外层 modifier 上（那一层只有 testTag、
     * 不可点击）；语义合并方向是「子 → 承载 merged 节点的父」，父节点属性不会流入子节点，
     * 而焦点落在内层 `WordBookCard` 的 `clickable(onSelect)` 合并节点上——动作在真实无障碍树里
     * 并不伴随任何可聚焦目标。connected 语义树能查到 ≠ 真机焦点序列可达，这里按「同节点」钉死。
     *
     * 外层 `wordbook_swipe_*` 标签必须原地保留：上面那条红底护栏按它 `captureToImage()`，
     * 标签挪走等于把护栏改成捕获错误区域。
     */
    @Test
    fun deleteWordBookCustomActionIsRegisteredOnTheClickableNode() {
        val book = WordBook("imported-book", "导入的词书", "自定义", 100, "v1", "user-provided-xlsx")
        var deleted = 0
        composeRule.setContent {
            SwipableWordBookCard(
                book = book,
                selected = false,
                progress = null,
                progressLoading = false,
                onSelect = {},
                onDelete = { deleted++ },
            )
        }

        val nodes = composeRule.onAllNodes(clickableWithDeleteAction()).fetchSemanticsNodes()
        assertEquals(
            "「删除词书」自定义动作必须与 click 动作挂在同一个可聚焦节点上，命中节点数=${nodes.size}",
            1,
            nodes.size,
        )

        val action = nodes.single().config.getOrNull(SemanticsActions.CustomActions)
            ?.single { it.label == "删除词书" }
            ?: error("可点击节点上没有「删除词书」自定义动作")
        // 动作执行走与滑动 confirmValueChange 同一个 onDelete（「滑不删、弹确认」两步确认红线不变）。
        assertEquals(true, action.action())
        assertEquals(1, deleted)

        // 红底护栏依赖的外层标签不得被挪走。
        composeRule.onNodeWithTag("wordbook_swipe_imported-book").assertExists()
    }

    private fun clickableWithDeleteAction() = SemanticsMatcher("可点击且带「删除词书」自定义动作") { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null &&
            node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == "删除词书" } == true
    }
}
