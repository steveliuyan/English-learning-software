package com.example.englishlearning.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.WordBookProgress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「开始学习」上方那条今日工作量分段条。
 *
 * 它替代了原先的两句文字提示（「优先复习 N 个到期词，再学习新增词」），所以这里守的是三件事：
 * 三节各自标什么数、数字是剩余量还是目标量、以及计数为 0 的小节有没有偷偷占宽度。
 */
@RunWith(AndroidJUnit4::class)
class TodayWorkloadBarTest {
    @get:Rule val composeRule = createComposeRule()

    /** 三段：已学 / 今日复习 / 今日新学；数字写在条下图例里，条上每一节用同色圆点对应。 */
    @Test fun workload_bar_labels_learned_review_and_new_sections() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready(
                    "高等职业教育专科英语", "2026-10-03", 10, 12, 22,
                    bookProgress = WordBookProgress(learned = 2680, total = 3039),
                ),
            )
        }
        composeRule.onNodeWithTag("today_plan_workload_learned_label").assertTextEquals("已学 2680")
        composeRule.onNodeWithTag("today_plan_workload_review_label").assertTextEquals("今日复习 12")
        composeRule.onNodeWithTag("today_plan_workload_new_label").assertTextEquals("今日新学 10")
        composeRule.onNodeWithTag("today_plan_workload_learned_bar").assertExists()
        composeRule.onNodeWithTag("today_plan_workload_review_bar").assertExists()
        composeRule.onNodeWithTag("today_plan_workload_new_bar").assertExists()
    }

    /** 条子回答的是「还要干多少」：今天已经复习掉的 5 个、新学掉的 3 个不再占宽度。 */
    @Test fun workload_bar_counts_the_remaining_work_only() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready(
                    "小学", "2026-10-03", 10, 12, 22,
                    newDone = 3, dueDone = 5,
                    bookProgress = WordBookProgress(learned = 100, total = 1000),
                ),
            )
        }
        composeRule.onNodeWithTag("today_plan_workload_learned_label").assertTextEquals("已学 100")
        composeRule.onNodeWithTag("today_plan_workload_review_label").assertTextEquals("今日复习 7")
        composeRule.onNodeWithTag("today_plan_workload_new_label").assertTextEquals("今日新学 7")
    }

    /** 计数为 0 的小节彻底不画：「不用复习」和「要复习一点点」在界面上必须能区分。 */
    @Test fun workload_bar_omits_sections_with_nothing_to_do() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready(
                    "小学", "2026-10-03", 10, 0, 10,
                    bookProgress = WordBookProgress(learned = 0, total = 4308),
                ),
            )
        }
        composeRule.onNodeWithTag("today_plan_workload").assertExists()
        composeRule.onNodeWithTag("today_plan_workload_learned_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_workload_review_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_workload_new_bar").assertExists()
        composeRule.onNodeWithTag("today_plan_workload_learned_label").assertTextEquals("已学 0")
        composeRule.onNodeWithTag("today_plan_workload_review_label").assertTextEquals("今日复习 0")
    }

    /** 还没算整册进度时连条子一起不画，而不是拿 0 当分母画一条假的。 */
    @Test fun workload_bar_is_hidden_without_book_progress() {
        composeRule.setContent {
            TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-10-03", 10, 12, 22))
        }
        composeRule.onNodeWithTag("today_plan_workload").assertDoesNotExist()
    }

    /** 三项计数全为 0（例如计划里没有任务）时不画一条空条子占地方。 */
    @Test fun workload_bar_is_hidden_when_everything_is_zero() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready("小学", "2026-10-03", 0, 0, 0, bookProgress = WordBookProgress(0, 100)),
            )
        }
        composeRule.onNodeWithTag("today_plan_workload").assertDoesNotExist()
    }
}
