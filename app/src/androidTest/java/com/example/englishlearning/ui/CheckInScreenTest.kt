package com.example.englishlearning.ui

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.DomainColors
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.domain.DailyLearningStats
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckInScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun ready_renders_statistics_and_calendar() {
        val today = LocalDate.of(2026, 9, 25)
        val stats = { date: LocalDate -> DailyLearningStats(date, 3, 1, 2, 4) }
        composeRule.setContent { CheckInScreen(CheckInUiState.Ready(stats(today), (0..6).map { stats(today.minusDays(it.toLong())) }, (1..today.lengthOfMonth()).map { stats(today.withDayOfMonth(it)) }, true), onBack = {}) }
        composeRule.onNodeWithTag("check_in_screen").assertExists()
        composeRule.onNodeWithText("完成任务 2/4", substring = true).assertExists()
        composeRule.onNodeWithTag("check_in_week_chart").assertExists()
        (0..6).forEach { index -> composeRule.onNodeWithTag("check_in_week_bar_$index").assertExists() }
        composeRule.onNodeWithTag("check_in_completion_ratio").assertExists()
        composeRule.onNodeWithTag("check_in_completion_percent").assertTextContains("50%")
        composeRule.onNodeWithTag("check_in_calendar").assertExists()
        composeRule.onNodeWithContentDescription("2026-09-25 已完成").assertExists()
        composeRule.onNodeWithContentDescription("2026-09-01 已完成").assertExists()
    }

    @Test fun review_progress_and_completed_dates_use_review_orange() {
        val date = LocalDate.of(2026, 9, 25)
        val stats = DailyLearningStats(date, 3, 1, 2, 4)
        composeRule.setContent { CheckInScreen(CheckInUiState.Ready(stats, List(7) { stats }, listOf(stats), true), onBack = {}) }
        val orange = DomainColors.Review.deep.toArgb()
        assertTrue("progress graphics need 3:1 against white", contrastRatio(orange, AndroidColor.WHITE) >= 3.0)
        assertContainsColor("check_in_week_bar_0", orange)
        assertArcColor(orange)
        assertContainsColor("check_in_completion_percent", orange)
        assertContainsColor("check_in_calendar", DomainColors.Review.deep.toArgb())
    }

    private fun contrastRatio(a: Int, b: Int): Double {
        fun luminance(color: Int): Double {
            val channels = listOf(AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color))
                .map { it / 255.0 }
                .map { if (it <= 0.04045) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
            return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
        }
        val (lighter, darker) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertArcColor(color: Int) {
        val bitmap = composeRule.onNodeWithTag("check_in_completion_arc").performScrollTo().captureToImage().asAndroidBitmap()
        val y = bitmap.height / 2
        val xRange = (bitmap.width * 11 / 12) until bitmap.width
        assertTrue("completion arc should render accessible review orange", xRange.any { x -> bitmap.getPixel(x, y) == color })
    }

    private fun assertContainsColor(tag: String, color: Int) {
        val bitmap = composeRule.onNodeWithTag(tag).performScrollTo().captureToImage().asAndroidBitmap()
        assertTrue("$tag should show review accent", (0 until bitmap.width).any { x ->
            (0 until bitmap.height).any { y -> bitmap.getPixel(x, y) == color }
        })
    }

    @Test fun unavailable_offers_retry() {
        var retried = 0
        composeRule.setContent { CheckInScreen(CheckInUiState.Unavailable, onBack = {}, onRetry = { retried++ }) }
        composeRule.onNodeWithTag("check_in_unavailable").assertExists()
        composeRule.onNodeWithContentDescription("重试").assertHasClickAction().performClick()
        assertEquals(1, retried)
    }

    @Test fun back_invokes_callback() {
        var backs = 0
        composeRule.setContent { CheckInScreen(CheckInUiState.Loading, onBack = { backs++ }) }
        composeRule.onNodeWithTag("check_in_back").assertHasClickAction().performClick()
        assertEquals(1, backs)
    }

    /**
     * F5-03 / Task 10（B2 文字链）：打卡页头部三条文字链（我的收藏 / 错词本 / 返回）
     * 的触控目标必须 ≥48dp 见方——它们只有 `padding(10.dp)`，emulator-5574 RED 原文实测 36.190475dp
     * 高（原先记录的「约 40dp」为目测值、已废弃），不达标准。
     *
     * 双口径（dispatch-notes §B）：brief 的 `assertHeightIsAtLeast/assertWidthIsAtLeast`（读裁剪后矩形，
     * 故先 `performScrollTo()` + `assertIsDisplayed()` 确认在视口内）与既有先例的
     * `getUnclippedBoundsInRoot`（断真实尺寸）各跑一遍。
     */
    @Test fun headerTextLinksMeetTheMinimumTouchTarget() {
        composeRule.setContent {
            CheckInScreen(
                state = CheckInUiState.Loading,
                onBack = {},
                onOpenFavorites = {},
                onOpenWrongWords = {},
            )
        }
        listOf("check_in_open_favorites", "check_in_open_wrong_words", "check_in_back").forEach { tag ->
            assertMinTouchTarget(tag)
        }
    }

    private fun assertMinTouchTarget(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        composeRule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp)

        val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue("$tag 的触控目标应至少 48dp 高，实际=$bounds", bounds.bottom - bounds.top >= 48.dp)
        assertTrue("$tag 的触控目标应至少 48dp 宽，实际=$bounds", bounds.right - bounds.left >= 48.dp)
    }

    @Test fun zero_target_shows_zero_percent() {
        val date = LocalDate.of(2026, 9, 25)
        val zero = DailyLearningStats(date, 0, 0, 0, 0)
        composeRule.setContent { CheckInScreen(CheckInUiState.Ready(zero, List(7) { zero }, listOf(zero), false), onBack = {}) }
        composeRule.onNodeWithTag("check_in_completion_percent").assertTextContains("0%")
        composeRule.onNodeWithText("目标 0 项，已完成 0 项", substring = true).assertExists()
    }

    /**
     * F5-03 / Task 11（§E.2，Task 10 评审 I-4 的护栏）：打卡页头部「标题 + 三条文字链」所在 Row 已补
     * `horizontalScroll`，且修饰符顺序必须是 **`fillMaxWidth()` 在前、`horizontalScroll` 在后**
     * （两种顺序的设备实测差异记在 `CheckInScreen.kt` 头部注释）。本用例钉两条可观测性质：
     *  1. `fontScale = 2.0`（Android「字体大小」上限档）下三条文字链仍能 `performScrollTo()` 后进入视口
     *     ——口径照本文件既有 `assertMinTouchTarget`，钉住「链不因头部改版而变得不可达」；
     *  2. 标题 `weight(1f)` 不得塌成 0 宽：阈值 ≥48dp（正序实测 78.48dp；反序
     *     `horizontalScroll().fillMaxWidth()` 在 emulator-5574 塌成 `DpRect(left = right = 20.190475.dp)`
     *     即 0dp → 本条当场红，RED 原文见 task-11-report.md §2.1）。**这条才是护栏的证伪力来源**：
     *     头部改版成「先滚动后填充」的写法会立刻被钉住。
     *
     * 如实登记（评审 I-4 的前提未复现）：在 2.0 与 3.0 两档、加 / 不加 `horizontalScroll` 的组合下，
     * 头部几何与文字链可达性没有差别（摘掉 `horizontalScroll` 后本用例仍绿），即「尾部链被挤出屏外不可达」
     * 在 411dp 宽设备 + 28sp 级链字 + 48dp 下限 + 10dp padding 的参数组合下不会发生；大字号下的真实症状是
     * 标题被 `weight(1f)` 压到 78.48dp 后换行成 4 行（高 99.81dp，无字形裁切，`hasVisualOverflow` 为 false），
     * 而 `horizontalScroll` 修不了它 → 归 UI 重设计轮，见报告 §2.1 与 §5 遗留 L-T11-1。
     */
    @Test
    fun headerKeepsLinksReachableAndTitleReadableAtMaximumLargeFontScale() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 2f)) {
                CheckInScreen(
                    state = CheckInUiState.Loading,
                    onBack = {},
                    onOpenFavorites = {},
                    onOpenWrongWords = {},
                )
            }
        }
        listOf("check_in_open_favorites", "check_in_open_wrong_words", "check_in_back").forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo()
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
        val title = composeRule.onNodeWithText("学习打卡").getUnclippedBoundsInRoot()
        assertTrue(
            "fontScale=2.0 时头部标题不应塌成 0 宽（实测正序 78.48dp、反序 0dp），实际边界=$title",
            title.right - title.left >= 48.dp,
        )
    }
}
