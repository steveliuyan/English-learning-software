package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.BundledWordBookIdSource
import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 词书选取屏接入统一页头与骨架（批 2 Task 13）的 connected 像素/语义判据。
 *
 * 三条各一个 `@Test`、每个只 `setContent` 一次（本仓真机怪癖：同一测试内两次 setContent 必崩）。
 * 期望像素一律取 [AppColorLight] 的 token，不重抄 hex（R-13.1 / m-1：浅套 `outlineSoft` 的权威值是
 * `AppColors.kt` 里的 `0xFFE4EFE9`，brief 抄成了 `#E5EFE9`——取 token 才不会被这种抄录漂移带偏）；
 * 本主题显式锁 `ThemeMode.Light`，
 * 与 `ThemeModeSwitchTest` 同套取样口径（`captureToImage()` 离屏、alpha ≥ 250 过滤、逐通道容差 2）。
 */
@RunWith(AndroidJUnit4::class)
class WordbookPickerHeaderTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * ①页头由 `AppScreenHeader` 承载：testTag 存在、标题与副标题文案逐字命中、
     * 返回入口落在页头的返回位上。
     *
     * 批 2 Task 6 改期望值（不是放宽）：旧版本屏另有一枚 `← 返回上一层` 的 `TextButton` 排在页头**之前**，
     * 页头自己又传 `onBack = null`——「标题下面还有一枚返回」是两个并排的返回入口。现在删掉那枚
     * 文字按钮、把 `onCancel` 接到页头 `onBack`，朗读文案随之变成页头固定的「返回」。判据因此拆成
     * 一正一反：正 = 页头返回位在；反 = 旧文案「返回上一层」必须不存在（返回位只剩一处）。
     * 比原来只断言「有一枚返回」更严：谁再把第二枚返回加回来，本条就红。
     */
    @Test
    fun wordbook_picker_header_is_carried_by_the_shared_app_screen_header() {
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Light) {
                LearningSetupScreen(
                    profileId = "probe-profile",
                    viewModel = probeSetupViewModel(),
                    onSaved = {},
                    onCancel = {},
                )
            }
        }
        composeRule.onNodeWithTag("app_screen_header").assertExists()
        composeRule.onNodeWithText("选好词书，开始今天的积累").assertExists()
        composeRule.onNodeWithText("每天一点点，学习会自然变成习惯。").assertExists()
        composeRule.onNodeWithContentDescription("返回").assertExists().assertHasClickAction()
        composeRule.onNodeWithContentDescription("返回上一层").assertDoesNotExist()
    }

    /**
     * ②首帧骨架：`wordbook_tile_skeleton_0..<WORD_BOOK_PLACEHOLDER_ROWS × WORD_BOOK_TILE_COLUMNS>`
     * 逐格齐全，每格实测高度 ≥ 生产的 `WordBookTileMinHeight`，且同一行的两格**并排**（等高、左块右缘
     * 与右块左缘之间正好是 `WordBookTileGap`）。
     *
     * 行数、列数与下限高度都读**生产真值**（R-13.1 / m-4）：测试里另抄一份 `3` / `2` / `132f` 的话，
     * 生产把行数改成 4、列数改成 1、或把最小高度改掉时本例照样绿，判据与被测对象只剩巧合对齐。
     *
     * 批 5 Task 9 改判留因（对账 4）：旧版是「三行单列骨架 + 册间分隔线」，两列色块下分隔线不存在，
     * 骨架也随之变 3 行 × 2 列 6 格；判据从「逐行高度」换成「逐格高度 + 同行并排」，强度不降。
     */
    @Test
    fun first_frame_reserves_two_column_placeholder_tiles_at_the_tile_minimum_height() {
        val minHeightPx = WordBookTileMinHeight.value * composeRule.density.density
        val gapPx = WordBookTileGap.value * composeRule.density.density
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Light) {
                WordBookPicker(
                    wordBooks = emptyList(),
                    loading = true,
                    selectedId = null,
                    progressByBook = emptyMap(),
                    progressLoading = true,
                    canDelete = { false },
                    onSelect = {},
                    onRequestDelete = {},
                )
            }
        }
        val tileCount = WORD_BOOK_PLACEHOLDER_ROWS * WORD_BOOK_TILE_COLUMNS
        assertTrue("首帧骨架格数必须 > 0，否则下面的循环会空转", tileCount > 0)
        val bounds = (0 until tileCount).map { index ->
            val node = composeRule.onNodeWithTag("wordbook_tile_skeleton_$index")
            node.assertExists()
            val b = node.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "骨架第 $index 格高度不足 ${WordBookTileMinHeight.value} dp（实测 ${b.height / composeRule.density.density}dp）",
                b.height >= minHeightPx - 1f,
            )
            b
        }
        // 同一行两格并排：等高、顶边对齐、缝宽 = WordBookTileGap。
        for (row in 0 until WORD_BOOK_PLACEHOLDER_ROWS) {
            val left = bounds[row * WORD_BOOK_TILE_COLUMNS]
            val right = bounds[row * WORD_BOOK_TILE_COLUMNS + 1]
            assertTrue("第 $row 行两格顶边必须对齐（左 ${left.top} / 右 ${right.top}）", abs(left.top - right.top) <= 1f)
            assertTrue("第 $row 行两格必须等高（左 ${left.height} / 右 ${right.height}）", abs(left.height - right.height) <= 1f)
            assertTrue("第 $row 行两格必须等宽（左 ${left.width} / 右 ${right.width}）", abs(left.width - right.width) <= 1f)
            assertTrue(
                "第 $row 行两格之间的缝必须正好是 ${WordBookTileGap.value}dp（实测 ${(right.left - left.right) / composeRule.density.density}dp）",
                abs((right.left - left.right) - gapPx) <= 1f,
            )
            assertTrue("第 $row 行右格必须落在左格右侧（否则不是两列）", right.left > left.right)
        }
    }

    /**
     * ③选词书是**两列色块**：两册同排等宽并排，第三册（行内只剩一格）保持半宽而不被撑成通栏。
     *
     * 变异证据：把 `Row` 里的 `Spacer(Modifier.weight(1f))` 删掉，第三册会独占整行、宽度翻倍，本条变红；
     * 把 `chunked(WORD_BOOK_TILE_COLUMNS)` 换成 `forEach`（每册一行），同排断言立刻不成立。
     *
     * 缝里必须是页底而不是分隔线：色块间的留白是 grid 的格缝（`AppColorLight.background` 透出来），
     * 旧版那条 `outlineSoft` 册间分隔线随单列一起退役。取样窗只覆盖缝本身——色块自带 1dp `outlineSoft`
     * 描边，取样点离描边至少 6dp，不会把描边误当分隔线。
     */
    @Test
    fun the_picker_lays_word_books_out_in_two_equal_columns() {
        val first = WordBook("wb-first", "第一册", "L1", 100, "v1", "user-provided-xlsx")
        val second = WordBook("wb-second", "第二册", "L2", 100, "v1", "user-provided-xlsx")
        val third = WordBook("wb-third", "第三册", "L3", 100, "v1", "user-provided-xlsx")
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Light) {
                Column(Modifier.background(AppColorLight.background)) {
                    WordBookPicker(
                        wordBooks = listOf(first, second, third),
                        loading = false,
                        selectedId = null,
                        progressByBook = emptyMap(),
                        progressLoading = false,
                        canDelete = { true },
                        onSelect = {},
                        onRequestDelete = {},
                        modifier = Modifier.testTag(LIST_TAG),
                    )
                }
            }
        }

        val listBounds = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().boundsInRoot
        fun tileBounds(id: String) = composeRule.onNodeWithTag("wordbook_tile_$id").fetchSemanticsNode().boundsInRoot
        val b1 = tileBounds(first.id)
        val b2 = tileBounds(second.id)
        val b3 = tileBounds(third.id)

        val gapPx = WordBookTileGap.value * composeRule.density.density
        assertTrue("第一册必须从列表左缘起（实测 ${b1.left - listBounds.left}）", abs(b1.left - listBounds.left) <= 1f)
        assertTrue("同排两册必须等高（${b1.height} / ${b2.height}）", abs(b1.height - b2.height) <= 1f)
        assertTrue("同排两册必须等宽（${b1.width} / ${b2.width}）", abs(b1.width - b2.width) <= 1f)
        assertTrue(
            "同排两册之间的缝必须正好是 ${WordBookTileGap.value}dp（实测 ${(b2.left - b1.right) / composeRule.density.density}dp）",
            abs((b2.left - b1.right) - gapPx) <= 1f,
        )
        assertTrue("第二册必须落在第一册右侧（否则不是两列）", b2.left > b1.right)
        assertTrue("第二册必须换行到下一排（否则三册挤成一行）", b3.top > b1.top + b1.height - 1f)
        assertTrue(
            "第三册独占一排时仍须保持半宽（实测 ${b3.width} / 同排格宽 ${b1.width}）——少一个占位 Spacer 就会被撑成通栏",
            abs(b3.width - b1.width) <= 1f,
        )

        // 缝里透出的必须是页底 background，而不是某条实心分隔线。
        val bitmap = composeRule.onNodeWithTag(LIST_TAG).captureToImage().asAndroidBitmap()
        val seamX = ((b1.right + b2.left) / 2f - listBounds.left).roundToInt()
        val seamY = ((b1.top + b1.bottom) / 2f - listBounds.top).roundToInt()
        val expectedBackground = rgb(AppColorLight.background)
        val expectedSurface = rgb(AppColorLight.surface)
        assertTrue(
            "background 与 surface 太接近，缝取样失去判别力",
            maxChannelDelta(expectedBackground, expectedSurface) > CHANNEL_TOLERANCE * 2,
        )
        val argb = bitmap.getPixel(seamX, seamY)
        assertTrue(
            "两列之间的缝里必须透出页底（期望 $expectedBackground，实测 ${argb and 0xFFFFFF}）——" +
                "缝被画成实心色块就等于两列粘成一块",
            maxChannelDelta(argb and 0xFFFFFF, expectedBackground) <= CHANNEL_TOLERANCE,
        )
    }

    private fun rgb(color: Color): Int = color.toArgb() and 0xFFFFFF

    private fun maxChannelDelta(a: Int, b: Int): Int {
        fun channel(shift: Int) = abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF))
        return maxOf(channel(16), channel(8), channel(0))
    }

    private fun probeSetupViewModel(): LearningSetupViewModel {
        val repository = FakeLearningProfileRepository()
        val seedWordBooks = SeedWordBooksUseCase(
            { "[{\"id\":\"cet4\",\"displayName\":\"大学英语四级\",\"level\":\"CET-4\",\"totalWords\":1,\"dataVersion\":\"v1\",\"sourceId\":\"ngsl-nawl-1.2\",\"sourcePolicy\":\"应用内学习分组，不是官方考试大纲词表。\"}]" },
            repository,
        )
        val settingsRepo = FakeLearningSettingsRepository()
        return LearningSetupViewModel(
            repository = repository,
            seedWordBooks = seedWordBooks,
            selectWordBook = SelectWordBookAndSetDailyTargetUseCase(repository),
            getSettings = GetLearningSettingsUseCase(settingsRepo),
            saveSettings = SaveLearningSettingsUseCase(settingsRepo),
            bundledIds = BundledWordBookIdSource { setOf("cet4") },
        )
    }
}

private const val LIST_TAG = "wordbook_list_probe"
private const val CHANNEL_TOLERANCE = 2
