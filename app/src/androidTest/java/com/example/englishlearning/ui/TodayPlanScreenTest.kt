package com.example.englishlearning.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayPlanScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun loading_has_stable_tag() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Loading) }
        composeRule.onNodeWithTag("today_plan_screen").assertExists()
        composeRule.onNodeWithTag("today_plan_loading").assertExists()
    }

    @Test fun missing_setup_offers_clickable_setup() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.MissingSetup) }
        composeRule.onNodeWithTag("today_plan_missing_setup").assertExists()
        composeRule.onNodeWithTag("today_plan_open_setup").assertHasClickAction()
        composeRule.onNodeWithContentDescription("去设置词书").assertExists()
    }

    @Test fun unavailable_offers_clickable_retry() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Unavailable) }
        composeRule.onNodeWithTag("today_plan_unavailable").assertExists()
        composeRule.onNodeWithTag("today_plan_retry").assertHasClickAction()
        composeRule.onNodeWithContentDescription("重试").assertExists()
    }

    @Test fun ready_renders_task_counts_and_start_learning_entry() {
        var started = 0
        composeRule.setContent {
            TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5), onStartLearning = { started++ })
        }
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
        composeRule.onNodeWithTag("today_plan_new_count").assertExists()
        composeRule.onNodeWithContentDescription("今日新增 2 词").assertExists()
        composeRule.onNodeWithTag("today_plan_due_count").assertExists()
        composeRule.onNodeWithContentDescription("今日复习 3 词").assertExists()
        composeRule.onNodeWithTag("today_plan_task_total").assertExists()
        composeRule.onNodeWithTag("today_plan_start_learning").assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(1, started)
    }

    @Test fun ready_with_no_tasks_renders_empty_state() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 0, 0, 0)) }
        composeRule.onNodeWithTag("today_plan_empty").assertExists()
    }

    @Test fun ready_renders_total_progress_and_unlock_reason() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready(
                    "小学", "2026-09-19", 2, 3, 5,
                    newDone = 1, dueDone = 2, isUnlocked = false,
                    unlockReason = "完成新词与复习后解锁文章",
                ),
            )
        }
        composeRule.onNodeWithTag("today_plan_total_progress").assertExists()
        composeRule.onNodeWithTag("today_plan_unlock_status").assertExists()
    }

    /** 同级按钮都有 contentDescription；这一个是本轮改名后新加的，不能漏掉无障碍标注。 */
    @Test fun ready_offers_a_labelled_learning_tools_entry() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        composeRule.onNodeWithTag("today_plan_learning_tools").assertExists().assertHasClickAction()
        composeRule.onNodeWithContentDescription("学习工具与设置").assertExists()
    }

    @Test fun ready_offers_setup_entry_to_reopen_word_book_settings() {
        var opened = 0
        composeRule.setContent {
            TodayPlanScreen(TodayPlanUiState.Ready("大学英语四级", "2026-09-19", 10, 0, 10), onOpenSetup = { opened++ })
        }
        composeRule.onNodeWithTag("today_plan_open_setup_entry").assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
    }
}
