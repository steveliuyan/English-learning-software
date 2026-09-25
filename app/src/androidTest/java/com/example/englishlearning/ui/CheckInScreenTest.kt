package com.example.englishlearning.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
        composeRule.onNodeWithTag("check_in_today").assertTextContains("完成任务")
        composeRule.onNodeWithTag("check_in_week_chart").assertExists()
        (0..6).forEach { index -> composeRule.onNodeWithTag("check_in_week_bar_$index").assertExists() }
        composeRule.onNodeWithTag("check_in_completion_ratio").assertExists()
        composeRule.onNodeWithTag("check_in_completion_percent").assertTextContains("50%")
        composeRule.onNodeWithTag("check_in_calendar").assertExists()
        composeRule.onNodeWithContentDescription("2026-09-25 已完成").assertExists()
        composeRule.onNodeWithContentDescription("2026-09-01 已完成").assertExists()
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
        composeRule.onNodeWithTag("check_in_completion_ratio").assertTextContains("目标 0 项，已完成 0 项")
    }
}
