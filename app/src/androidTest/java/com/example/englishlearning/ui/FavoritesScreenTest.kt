package com.example.englishlearning.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.learning.FavoriteWord
import com.example.englishlearning.ui.theme.MintGlassTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 20-D（计划 Step 4c / R-20D.1）：收藏页**独立屏**的 connected 面。
 *
 * 本类补的是 R-20D.1② 登记的两处缺口之一——重算命令
 * `grep -rln "FavoritesScreen\|WrongWordsScreen" app/src/androidTest` 在本批前只命中
 * `MyWordsScreenTest.kt` 的注释（`:160`/`:214`，描述缺陷形状而非挂屏），
 * 即收藏页独立屏**零渲染级用例**。收藏页在 `AppScreen.kt:1125` 仍挂在导航树上
 * （打卡页 `onOpenFavorites` → `showFavorites`），所以按 brief §二.2 走「保留则补冒烟」这一支，
 * 不是「已由合并页取证覆盖」。
 *
 * 两枚用例分工：
 *  ① `ready_rows_render_and_reach_the_host` —— 渲染级冒烟（行、词头、时间戳、两枚出口回调都真的接线）；
 *  ② `favoriteMetaTimestampIsLaidOutInTwoAllowedLinesInsideTheColumnCap` —— `FavoritesScreen.kt:144`
 *     那枚 `meta = 收藏于 yyyy-MM-dd` 的**布局级不截断判据**（R-20D.1③ 定的形状，形同
 *     `b76f133` 的 `disabledRowShowsTheUnavailableNoticeInFull`）。
 *
 * 为什么判据必须读 `textLayout()` 而不是 `onNodeWithText(...).assertExists()`：语义文本给的是
 * **全文**，节点被 `Ellipsis` 截断时文本层判据照样绿（合并页 `favorite_timestamp_shows_the_same_date_tier_as_the_favorites_page`
 * 就是这个形状——它绿着，而右列当时仍是 `maxLines = 1`）。截断只在布局层暴露。
 *
 * 字号一律在**组合级**钉死（`LocalDensity provides Density(density, fontScale = …)`，本仓先例
 * `EntryRowTest`/`WordRowTest`/`CheckInScreenTest`）：这样同一枚判据不随设备「字体大小」滑档漂移，
 * 设备级 `font_scale 2.0` 轮里整类仍跑同一口径。墨色不在此断言（Task 9 口径，取色归
 * `WordRowSpeakerContractTest` 的纯表复算）。
 */
@RunWith(AndroidJUnit4::class)
class FavoritesScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val baseInstant: Instant = Instant.parse("2026-10-19T00:30:00Z")

    private fun favorite(lemma: String, index: Int, addedAt: Instant = baseInstant) = FavoriteWord(
        profileId = "p1",
        cardId = "fav-card-$index",
        wordBookId = "cet4",
        lemma = lemma,
        addedAt = addedAt,
    )

    /**
     * 渲染级冒烟（R-20D.1② 缺口的第一半）：`Ready` 两行真被组合出来、两枚出口真回交宿主。
     *
     * 判据是「回调收到的实参与次数」，不是「点击没崩」。夹具把时刻钉死（UTC + 同一枚 Instant），
     * 时间戳整句可复算。
     */
    @Test
    fun ready_rows_render_and_reach_the_host() {
        val opened = mutableListOf<String>()
        val unfavorited = mutableListOf<String>()
        composeRule.setContent {
            MintGlassTheme {
                FavoritesScreen(
                    state = FavoritesUiState.Ready(
                        words = listOf(
                            favorite("delicious", 0),
                            favorite("beacon", 1, addedAt = Instant.parse("2026-10-18T08:15:00Z")),
                        ),
                    ),
                    onBack = {},
                    onOpenWord = { opened += it.lemma },
                    onUnfavorite = { unfavorited += it.lemma },
                    zoneId = ZoneOffset.UTC,
                )
            }
        }
        composeRule.onNodeWithTag("favorites_screen").assertExists()
        composeRule.onNodeWithTag("favorites_word_0").assertExists()
        composeRule.onNodeWithTag("favorites_word_1").assertExists()
        // 两行的时刻刻意错开（夹具注释见 `favorite(...)`）：同文两行会让「这一行的时间戳」这类
        // 逐行判据变成歧义命中，逐行断言必须有逐行可辨的数据。
        composeRule.onNodeWithText("delicious").assertExists()
        composeRule.onNodeWithText("收藏于 2026-10-19").assertExists()
        composeRule.onNodeWithText("beacon").assertExists()
        composeRule.onNodeWithText("收藏于 2026-10-18").assertExists()
        composeRule.onNodeWithTag("favorites_empty").assertDoesNotExist()

        composeRule.onNodeWithTag("favorites_word_0").performClick()
        composeRule.onNodeWithTag("favorites_unfavorite_1").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("delicious"), opened)
        assertEquals(listOf("beacon"), unfavorited)
    }

    /**
     * 站点 `FavoritesScreen.kt:144` 的布局级判据（R-20D.1③；策略口径见裁定 R-20D.2）。
     *
     * 四条腿各钉一种破坏：
     *  ① `maxLines ≥ 2`：`meta` 是自由文本承载位（时间戳/出处），R-20D.2 把它从 1 改为 2；
     *     现盘（`WordRow.kt:189` 的 `maxLines = 1`）在这一条天然红。
     *  ② 宽度落在右列预期区间 `≤ 120dp`（容 0.1dp 换算余量）且 `≥ 48dp`：R-20D.2 **不加宽**右列，
     *     加宽即挤压左列阅读主体；下限挡的是反方向破坏（右列塌成 0dp 时文本层同样读得到全文）。
     *  ③ 整句被布局：`getLineEnd(末行) == 文本长度`（`AppScreenOverlayContainerTest` 17B 修复轮的
     *     「纵向腿」口径；`hasVisualOverflow` 判不出省略号与裁切的区别，不用它交差）。
     *  ④ 逐行不伸出文本盒 +1dp（横向腿，挡真裁切）。
     */
    @Test
    fun favoriteMetaTimestampIsLaidOutInTwoAllowedLinesInsideTheColumnCap() {
        val meta = "收藏于 2026-10-19"
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1f)) {
                MintGlassTheme {
                    FavoritesScreen(
                        state = FavoritesUiState.Ready(words = listOf(favorite("delicious", 0))),
                        onBack = {},
                        onOpenWord = {},
                        onUnfavorite = {},
                        zoneId = ZoneOffset.UTC,
                    )
                }
            }
        }
        assertMetaSlotSurvives(meta, expectMinLines = 1)
    }

    /**
     * 本类布局判据的公共实现（与 `MyWordsScreenTest` 同口径、各自私有：跨测试文件抽公共工具会让
     * 一侧改动波及另一侧的取证，本仓 connected 面按类自足）。
     *
     * 未合并树取节点：收藏行外层 `Card` 带 `clickable`，合并后一次 `GetTextLayoutResult` 回调会把
     * 行内多枚文本并成一包（`layouts.single()` 当场炸），逐槽判定必须走未合并树
     * （先例见 `EntryRowTest.entry_row_truncates_title_and_subtitle_with_ellipsis_not_clipping`）。
     */
    private fun assertMetaSlotSurvives(meta: String, expectMinLines: Int) {
        val node: SemanticsNodeInteraction = composeRule.onNodeWithText(meta, useUnmergedTree = true)
        node.performScrollTo()
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val widthDp = layout.size.width / density
        val maxLines = layout.layoutInput.maxLines
        val boxWidth = node.fetchSemanticsNode().boundsInRoot.width
        val tolerancePx = with(Density(density, fontScale = 1f)) { 1.dp.toPx() }
        assertTrue(
            "「$meta」所在槽必须允许两行（R-20D.2：meta 是自由文本承载位，截断即用户可见的信息丢失）；" +
                "实测 maxLines=$maxLines 说明它还在单行右列槽里",
            maxLines >= 2,
        )
        assertTrue(
            "「$meta」必须待在右列 `widthIn(max = 120.dp)` 的上限内（R-20D.2 明确不加宽，" +
                "加宽等于挤压左列 lemma/gloss 阅读主体），同时不得塌成 0dp：" +
                "实测宽度 ${widthDp}dp（判定区间 48dp–120.1dp）、lineCount=${layout.lineCount} size=${layout.size}",
            widthDp in 48.0..120.1,
        )
        assertTrue(
            "「$meta」至少要占 ${expectMinLines} 行（两行换行策略真的被用上，而不是靠加宽糊过去）：" +
                "实测 lineCount=${layout.lineCount} maxLines=$maxLines widthDp=${widthDp}",
            layout.lineCount >= expectMinLines,
        )
        assertEquals(
            "「$meta」必须整句被布局（末行没到文本末尾 = 有字被省略号吃掉）：" +
                "lineCount=${layout.lineCount} maxLines=$maxLines widthDp=${widthDp} size=${layout.size} " +
                "lineEnd=${layout.getLineEnd(layout.lineCount - 1)}",
            meta.length,
            layout.getLineEnd(layout.lineCount - 1),
        )
        for (line in 0 until layout.lineCount) {
            assertTrue(
                "「$meta」第 $line 行不得伸出文本盒 +1dp（逐行真裁切信号）：" +
                    "lineRight=${layout.getLineRight(line)} boxWidth=$boxWidth tolerancePx=$tolerancePx bounds=" +
                    node.fetchSemanticsNode().boundsInRoot,
                layout.getLineRight(line) <= boxWidth + tolerancePx,
            )
        }
    }
}
