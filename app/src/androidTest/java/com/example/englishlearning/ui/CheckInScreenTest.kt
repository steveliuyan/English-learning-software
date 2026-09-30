package com.example.englishlearning.ui

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertHasClickAction
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

    @Test fun zero_target_shows_zero_percent() {
        val date = LocalDate.of(2026, 9, 25)
        val zero = DailyLearningStats(date, 0, 0, 0, 0)
        composeRule.setContent { CheckInScreen(CheckInUiState.Ready(zero, List(7) { zero }, listOf(zero), false), onBack = {}) }
        composeRule.onNodeWithTag("check_in_completion_percent").assertTextContains("0%")
        composeRule.onNodeWithText("目标 0 项，已完成 0 项", substring = true).assertExists()
    }
}
