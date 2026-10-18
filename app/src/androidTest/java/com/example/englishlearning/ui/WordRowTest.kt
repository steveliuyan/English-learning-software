package com.example.englishlearning.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.WordRow
import com.example.englishlearning.ui.theme.MintGlassTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 批 1 终审 R2（= F3 的 connected 半边）：词行右列宽度上限的大字号实测。
 *
 * 钉的破坏：`WordRow` 右侧「状态/时间」列不带 `widthIn(max = 120.dp)` 时，`Row` 先量**无权重**的孩子，
 * 右列按内容无限撑宽，带 `weight(1f)` 的 lemma 列被挤到只剩一点宽度——在 fontScale 2.0 下 lemma
 * 直接塌成几像素宽，用户读不到词头。JVM 侧的 `WordRowSpeakerContractTest` 只能钉**源码形状**
 * （那一行 `Modifier.widthIn(max = 120.dp)` 在不在），量不到真实布局；这条用例补的就是实测那一面。
 *
 * 口径全部沿用本包既有先例（`AppScreenHeaderTest.kt` 的大字号用例）：
 *  - 组件级宿主 `createComposeRule()` + `MintGlassTheme`，一个 `@Test` 恰一次 `setContent`，用例之间不复用宿主；
 *  - 大字号用 `LocalDensity` 换 `Density(base.density, fontScale = 2f)`，不动密度本身；
 *  - 宽度取 `getUnclippedBoundsInRoot()`（`assertWidthIsAtLeast` 内部同一口径，这里手写一份是为了把实际值写进失败消息）；
 *  - **不断言颜色**（Task 9 实测口径：connected 读不到 `inks.lemma` 的实际取色，颜色归 JVM 对比契约管）。
 *
 * `gloss = null` 是简报给的调用形状；释义列有无止不了右列挤压（挤压发生在 `Row` 量孩子阶段，
 * 与左列内容行数无关），所以这里不加噪声文本。
 */
class WordRowTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * 钉的破坏：移除 `WordRow` 右列的 `widthIn(max = 120.dp)`（或把它放宽到整屏宽）后，
     * fontScale 2.0 下 lemma 列会被「2026-10-19 08:30」这类时间串挤瘪。
     *
     * 两条判据一起才咬得住，各钉机制的一端：
     *  1. **meta 列实宽 ≤ 120dp**：直接钉「上限真的在生效」——摘掉 `widthIn` 后这一列按内容撑宽，当场红；
     *  2. **lemma 实宽 ≥ 48dp**（简报口径）：钉挤压的极端形态（词头被挤成 0 宽/塌行），
     *     同时作为「行还没塌」的哨兵。两条判据共用同一份 unclipped 测量，实际值一律写进失败消息。
     */
    @Test
    fun lemma_keeps_readable_width_at_font_scale_2_when_meta_is_long() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                MintGlassTheme {
                    WordRow(
                        lemma = "unprecedented",
                        gloss = null,
                        status = "待复习",
                        meta = "2026-10-19 08:30",
                    )
                }
            }
        }
        val lemmaBox = composeRule.onNodeWithText("unprecedented").getUnclippedBoundsInRoot()
        val metaBox = composeRule.onNodeWithText("2026-10-19 08:30").getUnclippedBoundsInRoot()
        val lemmaWidth = lemmaBox.right - lemmaBox.left
        val metaWidth = metaBox.right - metaBox.left
        assertTrue(
            "fontScale=2.0 下右列必须被 WordRow 的 widthIn(max = 120.dp) 收住，且 lemma 列不得被挤瘪：" +
                "meta 实际宽度=$metaWidth（判定线上限 120dp）、lemma 实际宽度=$lemmaWidth（判定线下限 48dp）；" +
                "meta bounds=$metaBox，lemma bounds=$lemmaBox",
            metaWidth <= 120.dp && lemmaWidth >= 48.dp,
        )
    }
}
