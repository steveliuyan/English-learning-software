package com.example.englishlearning.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 两列词书色块（批 5 Task 9，对账 4 判据 4 改判落地后）的 connected 判据。
 *
 * 前身是 `WordBookSwipeRowTest`：那版钉的是「静止的可滑动词书卡不得露出红色删除底」。
 * 滑删随两列布局一起退役（横滑与列内横向手势、页面滚动互相抢，且滑删没有视觉出口），
 * 本文件接住它原有的两条护栏，并补上新的手势口径：
 *
 *  1. 色块**永远不画红色错误底**（原红底护栏的等价判据：色块自己的捕获图里不许有 error 色像素）；
 *  2. 「删除词书」自定义动作必须挂在**被 TalkBack 聚焦的那个可点击节点**上（I-A2 原样保留）；
 *  3. 删除入口是**长按**，且长按只「请求删除」（弹确认框），点按只「选中」——两条手势不许串线；
 *  4. 内置册（`canDelete` 为 false）连长按入口都不存在：结构上没有被删的可能。
 */
@RunWith(AndroidJUnit4::class)
class WordBookTileTest {
    @get:Rule val composeRule = createComposeRule()

    private val importedBook = WordBook("imported-book", "导入的词书", "自定义", 100, "v1", "user-provided-xlsx")

    @Test
    fun the_tile_never_paints_the_error_red_backdrop() {
        var errorColor = 0
        composeRule.setContent {
            errorColor = MaterialTheme.colorScheme.error.toArgb()
            MintGlassTheme(themeMode = ThemeMode.Light) {
                WordBookPicker(
                    wordBooks = listOf(importedBook),
                    loading = false,
                    selectedId = null,
                    progressByBook = emptyMap(),
                    progressLoading = false,
                    canDelete = { true },
                    onSelect = {},
                    onRequestDelete = {},
                )
            }
        }
        val bitmap = composeRule.onNodeWithTag("wordbook_tile_imported-book")
            .captureToImage().asAndroidBitmap()

        var backdropPixels = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (isClose(bitmap.getPixel(x, y), errorColor)) backdropPixels++
            }
        }
        // 滑删时代那层红底是滑动容器**始终**铺在内容之下的，卡片一缩就露出一圈 #B3261E。
        // 现在没有滑动容器，色块自己也不许把 error 色当装饰：允许极少数抗锯齿混色（真机实测 0）。
        assertTrue(
            "词书色块露出了 error 色像素：$backdropPixels 个近似 error 色像素（上限 64）——" +
                "红色是删除/危险专用档，不能落在常驻色块上",
            backdropPixels <= 64,
        )
    }

    /**
     * I-A2（终审 A §2）：「删除词书」自定义动作必须挂在**被 TalkBack 聚焦的那个可点击节点**上。
     *
     * 长按是视觉手势，读屏用户念不到也做不出来——动作与长按必须走同一个 `onRequestDelete`
     * （只弹确认框，两步确认红线不变）。动作声明若挪到外层不可点击节点，真实无障碍树里就没有
     * 可聚焦目标，本条按「同节点」钉死。
     */
    @Test
    fun deleteWordBookCustomActionIsRegisteredOnTheClickableNode() {
        var deleted = 0
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Light) {
                WordBookPicker(
                    wordBooks = listOf(importedBook),
                    loading = false,
                    selectedId = null,
                    progressByBook = emptyMap(),
                    progressLoading = false,
                    canDelete = { true },
                    onSelect = {},
                    onRequestDelete = { deleted++ },
                )
            }
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
        assertEquals(true, action.action())
        assertEquals("自定义动作必须走与长按同一个 onRequestDelete（只请求、不直接删）", 1, deleted)
    }

    /**
     * 两条手势不许串线：点按只选中，长按只请求删除。
     *
     * 变异证据：把 `combinedClickable` 的 `onLongClick` 换成 `onClick` 的别名（或把长按接到删除之外的地方），
     * 长按后的 `requested` 归 0，本条立刻红。
     */
    @Test
    fun tap_selects_while_long_press_only_asks_for_confirmation() {
        var selected = 0
        var requested = 0
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Light) {
                WordBookPicker(
                    wordBooks = listOf(importedBook),
                    loading = false,
                    selectedId = null,
                    progressByBook = emptyMap(),
                    progressLoading = false,
                    canDelete = { true },
                    onSelect = { selected++ },
                    onRequestDelete = { requested++ },
                )
            }
        }

        val tile = composeRule.onNodeWithTag("wordbook_tile_imported-book")
        tile.performClick()
        assertEquals("点按必须只触发选中", 1, selected)
        assertEquals("点按不得触发删除请求", 0, requested)

        tile.performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertEquals("长按只请求删除（弹确认框），不得跳过确认直接删", 1, requested)
        assertEquals("长按不得同时把册选中", 1, selected)
    }

    /**
     * 内置册（`canDelete` 为 false）：长按入口与「删除词书」动作**都不存在**，从结构上保证没人能删它。
     */
    @Test
    fun the_bundled_book_has_no_long_press_delete_entry() {
        val bundled = WordBook("cet4", "大学英语四级", "CET-4", 100, "v1", "ngsl-nawl-1.2")
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Light) {
                WordBookPicker(
                    wordBooks = listOf(bundled),
                    loading = false,
                    selectedId = null,
                    progressByBook = emptyMap(),
                    progressLoading = false,
                    canDelete = { false },
                    onSelect = {},
                    onRequestDelete = {},
                )
            }
        }
        val node = composeRule.onNodeWithTag("wordbook_tile_cet4").fetchSemanticsNode()
        assertNull(
            "内置册不得登记长按手势：结构上就没有被删的可能，而不是靠运行时拦一下",
            node.config.getOrNull(SemanticsActions.OnLongClick),
        )
        assertNull(
            "内置册不得有「删除词书」自定义动作",
            node.config.getOrNull(SemanticsActions.CustomActions),
        )
        // 选中态仍在语义里（换手势不许把 F5-03 的选中播报一起撕掉）。
        assertEquals(
            "色块的选中态必须进 stateDescription",
            "未选",
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.StateDescription),
        )
    }

    private fun isClose(actual: Int, expected: Int): Boolean {
        fun channel(shift: Int) = ((actual shr shift) and 0xFF) - ((expected shr shift) and 0xFF)
        return kotlin.math.abs(channel(16)) <= 10 &&
            kotlin.math.abs(channel(8)) <= 10 &&
            kotlin.math.abs(channel(0)) <= 10
    }

    private fun clickableWithDeleteAction() = SemanticsMatcher("可点击且带「删除词书」自定义动作") { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null &&
            node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == "删除词书" } == true
    }
}
