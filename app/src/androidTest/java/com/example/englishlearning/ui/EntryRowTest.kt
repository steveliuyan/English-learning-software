package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.EntryIconTone
import com.example.englishlearning.ui.components.EntryRow
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.MintGlassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 批 1 Task 10 的组件级 connected 护栏：[EntryRow]（稿 §入口行 `mockups.html:150-160`）。
 *
 * **口径（先写清楚，否则后人会照计划原文找「chevron 颜色 == text2」那条断言）**：connected 读不到颜色
 * （Task 9 实测：`AppGlyph`/`Text` 的着色不是语义节点能取的东西），所以入口行的五枚墨色一律由 JVM 纯表
 * `entryRowInks(set)` 钉死（见 `EntryRowInkTest`），本类只钉**几何、语义、可点数量、裁切**四件事。
 * 计划原文那句「connected 断言 chevron 颜色 == `text2`」不可执行，已作废。
 *
 * 测量口径沿用本包既有先例（`AppScreenHeaderTest`）：组件级 `createComposeRule()` 宿主；
 * `assertHeightIsAtLeast` 与 `getUnclippedBoundsInRoot` 走同一个 `withUnclippedBoundsInRoot`（单口径）；
 * 组件级宿主没有可滚动祖先，`performScrollTo()` 会直接抛，故本类不写；每个方法只调一次 `setContent`
 * （同方法内第二次 `setContent` 真机必崩，Task 9 实测）。
 *
 * 徽标位靠组件内部的 `entry_row_icon` 标签定位（`AppGlyph` 是装饰层、无语义节点，否则 40dp 容器
 * 在真机上根本没有可测量的对象）；行是 `clickable` 的合并节点，子节点不进合并树，故一律
 * `useUnmergedTree = true`（先例见 `GlobalVocabularySearchScreenTest` 的同一口径注释）。
 */
class EntryRowTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * 钉的破坏：入口行的可点性被拆成多枚（徽标或 chevron 各自 `clickable`）、或整行丢了
     * `Role.Button`（TalkBack 只念「组」不念「按钮」）。数量断言用节点数而不是 `onLast()` 恒真写法。
     *
     * **两条计数各有分工，不是重复（R-10.7②，修复轮 2 的 I-2 补口）**：合并树那条抓得住「行丢了 click
     * （计数变 0）」；未合并树那条抓的是「行内长出第二枚可点源」，并且是**非合并型 click 源**
     * （`Modifier.semantics { onClick = … }` 这类不给自己开 `mergeDescendants` 的写法）的唯一防线——
     * 那种源会被父级并进整行、合并树计数不加。
     * 实测口径（变异自证 ⑩：给 chevron 的 `AppGlyph` 加一枚 `Modifier.clickable { }`）要写清，别照二手
     * 推断读：本行 42dp、chevron 16dp，**两棵树当时都数到了 2**（`Modifier.clickable` 自己会写
     * `MergeDescendants = true`，所以它作为子节点不会被并进父节点、合并树也数得出）。故本轮证据是
     * 「合并树 `expected:<1> but was:<2>` + 未合并树 `Expected exactly '1' node but found '2'`」两条同时红，
     * 不是「只有未合并树红」。未合并树这条仍按 R-10.7② 保留：它把「未合并树里也只有这一枚可点节点」
     * 变成契约，覆盖上面那类合并型源以外的写法。
     * 与 Task 9 `AppScreenHeaderTest`（`:106/131`）的口径差别：那边根节点不是 clickable，子级可点源的
     * 合并行为与这里不同，**同一写法不等于同一承重**，照抄前先按本段实测复核。
     */
    @Test
    fun entry_row_is_one_click_target_whole_row_with_button_role() {
        var clicked = false
        composeRule.setContent {
            MintGlassTheme {
                EntryRow(
                    icon = AppIcon.Book,
                    domain = AppDomain.Library,
                    title = "词书库",
                    subtitle = "10 本内置词书",
                    trailing = "10",
                    modifier = Modifier.testTag("entry_row"),
                    onClick = { clicked = true },
                )
            }
        }
        val row = composeRule.onNodeWithTag("entry_row")
        row.assertHasClickAction()
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals(
            "整行是唯一可点目标：徽标与 chevron 都不得成为第二可点源",
            1,
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals(
            "未合并树里也只能有一枚可点节点：徽标/chevron/尾部值都不得长出第二处可点源" +
                "（合并型 clickable 两棵树都数得出，这条守的是非合并型 `semantics { onClick = … }` 那一类，见本用例 KDoc 的实测口径）",
            1,
            composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        clicked = false
        row.performClick()
        assertTrue("整行点击必须回调 onClick", clicked)
    }

    /**
     * 钉的破坏：入口行被压回 F5-03 之下的触控高度。
     *
     * **故意钉 48 而不是 56**：48 是无障碍硬下限（回归会立刻响），56 只是本组件的设计目标值
     * （稿 `.entry { min-height:56px }`）——把 56 钉死会让任何合法的字体缩放或内容增高变成假红。
     *
     * **上面那两条量的是「整行外框」，它证明不了可点面（R-10.7①，修复轮 2 的 C-1 补口）**：
     * `Modifier.testTag("entry_row")` 由调用方传在链首 = 修饰符链的**最外层**语义节点，
     * 只要 `heightIn(min = 56.dp)` 还在链里，它量出来就恒 ≥56dp；而 F5-03 的 48dp 约束的是
     * **能点的那枚节点**（`clickable` 所在层）。`padding` 是 LayoutModifier 会新开一层布局节点，
     * 所以批 2 有人按稿面顺序把 `clickable` 挪回 `padding` **内侧**时（`EntryRow.kt` 的「可点面的两条共同交付」
     * 段明令禁止的那个写法；`heightIn` 排在 `clickable` 之前或之后都合法，它不影响可点面尺寸），
     * 可点面缩成 40dp、外层盒子一动不动 → 只靠上面两条会**静默放行**。
     * 故第三条必须打在带 click action 的那枚节点上（变异自证 ⑧：调回旧顺序 → 红的就是这条，
     * 且报的是可点节点 40dp 而不是外层 56dp）。
     */
    @Test
    fun entry_row_clears_the_48dp_touch_floor() {
        composeRule.setContent {
            MintGlassTheme {
                EntryRow(
                    icon = AppIcon.List,
                    domain = AppDomain.Learn,
                    title = "今日计划",
                    modifier = Modifier.testTag("entry_row"),
                ) {}
            }
        }
        val row = composeRule.onNodeWithTag("entry_row")
        row.assertHeightIsAtLeast(48.dp)
        val box = row.getUnclippedBoundsInRoot()
        assertTrue("整行高度应 ≥48dp（F5-03 硬下限），实际=$box", box.bottom - box.top >= 48.dp)
        composeRule.onNode(hasClickAction(), useUnmergedTree = true).assertHeightIsAtLeast(48.dp)
    }

    /**
     * 钉的破坏（R-10.2）：弱档另起一份容器、两档容器尺寸不同 = 「同族两档」变成两种组件。
     * 两档只有底色与字形墨不同，容器必须同宽同高且 ≥40dp。
     *
     * **等宽等高是严格相等，不是容差**（R-10.6，Task 10 评审轮收紧）：两档共用同一枚
     * `Box(Modifier.size(40.dp))`（`IconBadge.kt` 里只写一遍，几何契约见 `SharedComponentGeometryContractTest`），
     * 实测必然相同；真机若不相等就是 R-10.2 真违规（弱档另起了容器），不许把本条放宽回 0.6dp 容差。
     * `≥40dp` 下限保留——那是 F5-03 侧的闸，与等宽等高分属两件事。
     */
    @Test
    fun entry_row_keeps_a_40dp_icon_container_in_both_tones() {
        composeRule.setContent {
            MintGlassTheme {
                Column {
                    EntryRow(
                        icon = AppIcon.Camera,
                        domain = AppDomain.Reading,
                        title = "图片导入",
                        iconTone = EntryIconTone.Gradient,
                        modifier = Modifier.testTag("entry_row_solid"),
                    ) {}
                    EntryRow(
                        icon = AppIcon.Camera,
                        domain = AppDomain.Reading,
                        title = "图片导入",
                        iconTone = EntryIconTone.Tint,
                        modifier = Modifier.testTag("entry_row_tint"),
                    ) {}
                }
            }
        }
        val badges = composeRule.onAllNodesWithTag("entry_row_icon", useUnmergedTree = true)
        assertEquals("两档各一枚徽标节点，共 2 处（少于 2 = 有一档没渲染容器）", 2, badges.fetchSemanticsNodes().size)
        val solid = badges[0].getUnclippedBoundsInRoot()
        val tinted = badges[1].getUnclippedBoundsInRoot()
        val solidW = solid.right - solid.left
        val solidH = solid.bottom - solid.top
        val tintW = tinted.right - tinted.left
        val tintH = tinted.bottom - tinted.top
        assertTrue("两档容器都必须 ≥40dp：实底档 ${solidW}x${solidH}、弱档 ${tintW}x${tintH}",
            solidW >= 40.dp && solidH >= 40.dp && tintW >= 40.dp && tintH >= 40.dp)
        assertEquals(
            "R-10.2「同族两档」= 同一枚容器（只有底色/字形墨不同）：实测宽度差 $solidW - $tintW，" +
                "两档共用 `Box(size(40.dp))`，宽度差必须为 0（严格相等，不许放宽回 0.6dp 容差）",
            0.dp,
            solidW - tintW,
        )
        assertEquals(
            "R-10.2「同族两档」= 同一枚容器（只有底色/字形墨不同）：实测高度差 $solidH - $tintH，" +
                "两档共用 `Box(size(40.dp))`，高度差必须为 0（严格相等，不许放宽回 0.6dp 容差）",
            0.dp,
            solidH - tintH,
        )
    }

    /**
     * 钉的破坏：入口行的标题/副标题被改成两行（挤掉同行其它入口）或被裁成没有省略号的半截词。
     *
     * **判定方向与页头用例相反，别照抄**：入口行的标题/副标题是提示性内容（点进去才读得到全量），
     * 组件给 `maxLines = 1` + `Ellipsis`，所以这里 `hasVisualOverflow == true` 是**期望结果**
     * （省略号生效 = 提示性内容被合法裁掉）；页头 `AppScreenHeaderTest` 钉的是 `!hasVisualOverflow`
     * （页标题裁掉即丢信息）。把本用例「改成」`!hasVisualOverflow` 就等于把两个相反口径合成一个。
     *
     * **为什么单靠 `hasVisualOverflow` 不够（R-10.6，Task 10 评审轮补）**：`Ellipsis` 与 `Clip` 两种
     * 溢出写法都让 `hasVisualOverflow == true`，它判不出「省略号收尾 vs 字形直接裁掉」——而用例名
     * （not_clipping）与 KDoc 声称钉的正是后者。`TextLayoutResult.isLineEllipsized(0)` 只在省略号
     * 真的加到该行时返回 true：`Clip` 下它为 false，本条当场红（变异自证 ⑤：标题 `Ellipsis` → `Clip`，
     * 红的必须是这条而不是 `lineCount`/`hasVisualOverflow`）。
     */
    @Test
    fun entry_row_truncates_title_and_subtitle_with_ellipsis_not_clipping() {
        val longTitle = "超长入口标题用来验证省略号生效而不是把字形直接裁掉 Mixed Length Title Goes Further"
        val longSubtitle = "超长副标题同样只给一行，超出部分必须以省略号收尾而不是裁掉内容 abcdefghijklmnopqrstuvwxyz"
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                MintGlassTheme {
                    EntryRow(
                        icon = AppIcon.BookOpen,
                        domain = AppDomain.Reading,
                        title = longTitle,
                        subtitle = longSubtitle,
                        modifier = Modifier.testTag("entry_row"),
                    ) {}
                }
            }
        }
        listOf(longTitle, longSubtitle).forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            // 未合并树：行是 clickable 的合并节点，合并后只剩一个节点、两条文本的 TextLayoutResult
            // 会被并成一次回调（layouts.single() 当场炸），逐条判定必须走未合并树。
            composeRule.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals(
                "入口行的「$text」必须只占一行（maxLines = 1）：lineCount=${layout.lineCount} size=${layout.size}",
                1,
                layout.lineCount,
            )
            assertTrue(
                "省略号必须真的生效（本用例判定方向与页头用例相反：hasVisualOverflow = true 才是合规，" +
                    "false 说明超长内容根本没被省略号处理）：overflow=${layout.hasVisualOverflow} " +
                    "width=${layout.didOverflowWidth} size=${layout.size}",
                layout.hasVisualOverflow,
            )
            assertTrue(
                "「$text」必须真的以省略号收尾而不是把字形裁掉（Clip 也报 hasVisualOverflow=true，" +
                    "判不出裁切 vs 省略号，只有本条判得出，R-10.6）：isLineEllipsized(0)=false " +
                    "overflow=${layout.hasVisualOverflow} lineCount=${layout.lineCount}",
                layout.isLineEllipsized(0),
            )
        }
    }

    /**
     * 钉的破坏（R-10.5）：发丝线被画进内容里（把 56dp 最小内容高吃成 55/57）、或整条线挤掉了行高。
     * 两条 `topDivider` 相反的行同宿主竖排：带线的那条只应比不带的**高出那条线**（≥1dp），
     * 且两条都仍在 48dp 触控下限以上（内容高没被线吃掉）。
     */
    @Test
    fun entry_row_top_divider_is_outside_the_content_height() {
        composeRule.setContent {
            MintGlassTheme {
                Column {
                    EntryRow(
                        icon = AppIcon.Star,
                        domain = AppDomain.Review,
                        title = "收藏",
                        topDivider = false,
                        modifier = Modifier.testTag("entry_row_plain"),
                    ) {}
                    EntryRow(
                        icon = AppIcon.Star,
                        domain = AppDomain.Review,
                        title = "收藏",
                        topDivider = true,
                        modifier = Modifier.testTag("entry_row_divided"),
                    ) {}
                }
            }
        }
        val plain = composeRule.onNodeWithTag("entry_row_plain").getUnclippedBoundsInRoot()
        val divided = composeRule.onNodeWithTag("entry_row_divided").getUnclippedBoundsInRoot()
        val plainH = plain.bottom - plain.top
        val dividedH = divided.bottom - divided.top
        val extra = dividedH - plainH
        assertTrue("顶线必须画在内容外侧：带线行应比不带线的同行至少高 1dp，实际差 $extra（$plain vs $divided）",
            extra >= 1.dp)
        assertTrue("两条行都不得被线吃掉最小高（都应 ≥48dp）：plain=$plainH divided=$dividedH",
            plainH >= 48.dp && dividedH >= 48.dp)
        // 线的上限也钉住：多出来的高度只能是一条发丝线（超过 4dp 说明线被做成分隔块、把内容顶跑了）。
        assertTrue("顶线只能是一条 1dp 发丝线，实际多出了 $extra", extra <= 4.dp)
    }
}
