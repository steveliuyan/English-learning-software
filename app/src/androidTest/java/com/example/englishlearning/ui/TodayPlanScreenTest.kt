package com.example.englishlearning.ui

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import com.example.englishlearning.learning.WordBookProgress
import com.example.englishlearning.ui.theme.DomainColors
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        composeRule.onNodeWithTag("today_plan_progress_summary").assertExists()
        composeRule.onNodeWithTag("today_plan_total_progress").assertExists()
        composeRule.onNodeWithTag("today_plan_unlock_status").assertExists()
        composeRule.onAllNodesWithText("今日计划共 5 项").assertCountEquals(0)
    }

    /**
     * 首页词书卡上的整册进度：左边百分比、右边「已学/总数 词」、下面一条进度条和「未学」。
     * 2680/3039 取截断后是 88.1%（四舍五入会给 88.2%，所以这条断言同时钉住了取整方式）。
     */
    @Test fun ready_summary_card_shows_word_book_progress() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready(
                    "高等职业教育专科英语", "2026-10-03", 10, 12, 22,
                    bookProgress = WordBookProgress(learned = 2680, total = 3039),
                ),
            )
        }
        composeRule.onNodeWithTag("today_plan_learned_percent").assertTextEquals("已学 88.1%")
        composeRule.onNodeWithTag("today_plan_book_words").assertTextEquals("2680/3039 词")
        composeRule.onNodeWithTag("today_plan_book_unlearned").assertTextEquals("未学 359 词")
        composeRule.onNodeWithTag("today_plan_book_progress").assertExists()
    }

    /** 还没算出行时不能画成 0%：占位行整体不出现，而不是给一个假进度。 */
    @Test fun ready_without_progress_hides_the_progress_row() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        composeRule.onNodeWithTag("today_plan_learned_percent").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_book_progress").assertDoesNotExist()
    }

    @Test fun locked_reading_explains_state_without_looking_like_primary_action() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready(
                    "小学", "2026-09-19", 2, 3, 5,
                    isUnlocked = false,
                    unlockReason = "完成新词与复习后解锁文章",
                ),
            )
        }
        composeRule.onNodeWithTag("today_plan_reading_status").assertExists()
        composeRule.onAllNodesWithText("完成新词与复习后解锁文章").assertCountEquals(1)
        composeRule.onNodeWithTag("today_plan_open_reading").assertIsNotEnabled()
    }

    /** 同级按钮都有 contentDescription；这一个是本轮改名后新加的，不能漏掉无障碍标注。 */
    @Test fun ready_offers_a_labelled_learning_tools_entry() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        composeRule.onNodeWithTag("today_plan_learning_tools").assertExists().assertHasClickAction()
        composeRule.onNodeWithContentDescription("学习工具与设置").assertExists()
    }

    @Test fun reading_is_after_primary_learning_action() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        val primaryBottom = composeRule.onNodeWithTag("today_plan_start_learning").fetchSemanticsNode().boundsInRoot.bottom
        val readingTop = composeRule.onNodeWithTag("today_plan_open_reading").fetchSemanticsNode().boundsInRoot.top
        assertTrue("reading should follow the primary learning action", readingTop > primaryBottom)
    }

    @Test fun ready_offers_check_in_entry() {
        var opened = 0
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5), onOpenCheckIn = { opened++ }) }
        composeRule.onNodeWithTag("today_plan_open_check_in").assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
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

    @Test fun task_cards_use_neutral_surfaces_with_quiet_distinct_markers() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        listOf("today_plan_new_count", "today_plan_due_count").forEach { tag ->
            assertRenderedColor(tag, 0xFFFFFFFF.toInt())
            assertRenderedColor(tag, 0xFF1C1C1E.toInt())
        }
        assertRenderedColor("today_plan_new_count", 0xFF68BDA6.toInt())
        assertRenderedColor("today_plan_due_count", 0xFFC7A47D.toInt())
        assertTrue("neutral text needs 4.5:1", contrastRatio(0xFF1C1C1E.toInt(), 0xFFFFFFFF.toInt()) >= 4.5)
    }

    @Test fun available_actions_keep_one_mint_gradient_and_quiet_supporting_actions() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5, isUnlocked = true)) }
        assertRenderedColor("today_plan_start_learning", 0xFFA8F3C8.toInt())
        assertRenderedColor("today_plan_start_learning", 0xFF65D3B9.toInt())
        assertRenderedColor("today_plan_start_learning", 0xFF174C44.toInt())
        assertTrue("mint gradient needs 4.5:1 at both ends", listOf(0xFFA8F3C8.toInt(), 0xFF65D3B9.toInt()).all { contrastRatio(it, 0xFF174C44.toInt()) >= 4.5 })
        assertRenderedColor("today_plan_open_reading", 0xFFFFFFFF.toInt())
        assertRenderedColor("today_plan_open_reading", 0xFFC81E46.toInt())
        assertRenderedColor("today_plan_open_check_in", 0xFFFFFFFF.toInt())
        assertRenderedColor("today_plan_open_check_in", 0xFF795B36.toInt())
    }

    @Test fun locked_reading_action_is_neutral_and_not_clickable() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5, isUnlocked = false)) }
        composeRule.onNodeWithTag("today_plan_open_reading").assertIsNotEnabled()
        assertRenderedColor("today_plan_open_reading", 0xFFE5E5EA.toInt())
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

    private fun assertRenderedColor(tag: String, expectedColor: Int) {
        val bitmap = composeRule.onNodeWithTag(tag).performScrollTo().captureToImage().asAndroidBitmap()
        val nearest = (0 until bitmap.width).flatMap { x ->
            (0 until bitmap.height).map { y -> colorDistance(bitmap.getPixel(x, y), expectedColor) }
        }.minOrNull() ?: Double.POSITIVE_INFINITY
        assertTrue(
            "$tag should render near ${AndroidColor.red(expectedColor)},${AndroidColor.green(expectedColor)},${AndroidColor.blue(expectedColor)}; nearest distance=$nearest",
            nearest <= 18.0,
        )
    }

    private fun colorDistance(actual: Int, expected: Int): Double {
        val dr = (AndroidColor.red(actual) - AndroidColor.red(expected)).toDouble()
        val dg = (AndroidColor.green(actual) - AndroidColor.green(expected)).toDouble()
        val db = (AndroidColor.blue(actual) - AndroidColor.blue(expected)).toDouble()
        return Math.sqrt(dr * dr + dg * dg + db * db)
    }
}
