package com.example.englishlearning.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.WordBook
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
}
