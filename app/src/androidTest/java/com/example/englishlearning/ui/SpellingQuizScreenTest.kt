package com.example.englishlearning.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.SubmitFeedbackResult
import com.example.englishlearning.learning.spelling.RecordSpellingFeedbackUseCase
import com.example.englishlearning.learning.spelling.SpellingPrompt
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「拼写测验」的 connected 判据（2026-11-09 新建屏）。
 *
 * 源码形状归 `SpellingQuizScreen` 的类 KDoc 与 JVM 侧用例（`SpellingQuizTest` /
 * `RecordSpellingFeedbackUseCaseTest` / `SpellingQuizViewModelTest`），本文件只管**渲染出来才看得见**的：
 *  1. 未作答时只给释义与词性/音标，按钮不可点（空输入不许交卷）；
 *  2. 三档判分各念出一句真话，且都把正确拼写念出来（这一屏的用处就是纠错）；
 *  3. 结算卡说清「每题已按三档记入复习排期」，并给出本轮准确率；
 *  4. 接了 VM 时每题下方补一句调度器**真正写入**的时间；写库失败时明说没记上、排期没动；
 *  5. 2× 字号下题面与作答仍在。
 *
 * 输入框：tag 挂在 [com.example.englishlearning.ui.components.AppField] 的外层 Column 上，
 * 真正的 `SetText` 语义在内部的 BasicTextField 上，所以按动作取节点（`hasSetTextAction`），
 * 不按 tag 取——照 tag 取会拿到没有输入动作的那一层。
 */
@RunWith(AndroidJUnit4::class)
class SpellingQuizScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val now = Instant.parse("2026-11-09T10:00:00Z")

    private fun prompt(
        answer: String,
        meaning: String,
        partOfSpeech: String = "n.",
    ) = SpellingPrompt(
        cardId = "c-$answer",
        wordBookId = "primary-school",
        answer = answer,
        partOfSpeech = partOfSpeech,
        meaningZh = meaning,
        ipa = "/x/",
        example = null,
    )

    private fun typeAnswer(text: String) {
        composeRule.onNode(hasSetTextAction()).performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(text)
    }

    private fun submit() {
        composeRule.onNodeWithTag("spelling_quiz_submit").performScrollTo().performClick()
    }

    private fun viewModel(result: SubmitFeedbackResult): SpellingQuizViewModel =
        SpellingQuizViewModel(
            recordSpellingFeedback = RecordSpellingFeedbackUseCase(
                eventIds = EventIdFactory { "evt-1" },
            ) { result },
            clock = FixedClockProvider(now, ZoneOffset.UTC),
        )

    @Test fun unanswered_question_shows_meaning_and_refuses_an_empty_submission() {
        composeRule.setContent {
            SpellingQuizScreen(
                prompts = listOf(prompt("confidence", "自信")),
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("spelling_quiz_progress").assertIsDisplayed()
        composeRule.onNodeWithText("第 1 / 1 题").assertExists()
        composeRule.onNodeWithTag("spelling_quiz_prompt").assertExists()
        composeRule.onNodeWithText("自信").assertExists()
        composeRule.onNodeWithTag("spelling_quiz_submit").assertIsNotEnabled()
    }

    @Test fun correct_spelling_is_praised_and_the_round_can_advance() {
        composeRule.setContent {
            SpellingQuizScreen(
                prompts = listOf(prompt("confidence", "自信"), prompt("bright", "明亮的")),
                onBack = {},
            )
        }

        typeAnswer("confidence")
        submit()

        composeRule.onNodeWithText("拼对了：confidence").assertExists()
        composeRule.onNodeWithTag("spelling_quiz_next").performScrollTo().performClick()
        composeRule.onNodeWithText("第 2 / 2 题").assertExists()
        composeRule.onNodeWithText("已拼对 1 个").assertExists()
    }

    @Test fun a_close_miss_still_reads_out_the_correct_spelling() {
        composeRule.setContent {
            SpellingQuizScreen(
                prompts = listOf(prompt("confidence", "自信")),
                onBack = {},
            )
        }

        typeAnswer("confidense")
        submit()

        composeRule.onNodeWithText("差一点：正确拼写是 confidence").assertExists()
    }

    @Test fun a_wrong_answer_reads_out_the_correct_spelling() {
        composeRule.setContent {
            SpellingQuizScreen(
                prompts = listOf(prompt("dish", "盘子")),
                onBack = {},
            )
        }

        typeAnswer("dishes")
        submit()

        composeRule.onNodeWithText("正确拼写是 dish").assertExists()
    }

    @Test fun summary_reports_accuracy_and_says_the_verdicts_reach_the_schedule() {
        composeRule.setContent {
            SpellingQuizScreen(
                prompts = listOf(prompt("progressive", "渐进的", "adj.")),
                onBack = {},
            )
        }

        typeAnswer("progressive")
        submit()
        composeRule.onNodeWithTag("spelling_quiz_next").performScrollTo().performClick()

        composeRule.onNodeWithTag("spelling_quiz_summary").assertExists()
        composeRule.onNodeWithText("1 / 1 个拼对").assertExists()
        composeRule.onNodeWithText("准确率 100%").assertExists()
        composeRule.onNodeWithText("每题结果已按三档记入复习排期", substring = true).assertExists()
    }

    @Test fun a_recorded_verdict_reports_the_interval_the_scheduler_wrote() {
        val vm = viewModel(SubmitFeedbackResult.Recorded(now.plusSeconds(10 * 60)))
        composeRule.setContent {
            SpellingQuizScreen(
                prompts = listOf(prompt("confidence", "自信")),
                onBack = {},
                viewModel = vm,
                profileId = "p-1",
            )
        }

        typeAnswer("confidence")
        submit()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("spelling_quiz_schedule").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("已记入复习排期", substring = true).assertExists()
        composeRule.onNodeWithText("约 10 分钟后再问一次", substring = true).assertExists()
    }

    @Test fun a_storage_failure_says_the_schedule_did_not_move() {
        val vm = viewModel(SubmitFeedbackResult.StorageUnavailable)
        composeRule.setContent {
            SpellingQuizScreen(
                prompts = listOf(prompt("confidence", "自信")),
                onBack = {},
                viewModel = vm,
                profileId = "p-1",
            )
        }

        typeAnswer("confidence")
        submit()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("spelling_quiz_schedule_failed").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("这题没记上", substring = true).assertExists()
    }

    @Test fun a_bare_quiz_without_a_view_model_records_nothing() {
        composeRule.setContent {
            SpellingQuizScreen(
                prompts = listOf(prompt("confidence", "自信")),
                onBack = {},
            )
        }

        typeAnswer("confidence")
        submit()

        composeRule.onNodeWithText("拼对了：confidence").assertExists()
        composeRule.onAllNodesWithTag("spelling_quiz_schedule").assertCountEquals(0)
        composeRule.onAllNodesWithTag("spelling_quiz_schedule_failed").assertCountEquals(0)
    }

    @Test fun the_question_and_its_field_survive_a_two_x_font_scale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                SpellingQuizScreen(
                    prompts = listOf(prompt("confidence", "自信")),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("spelling_quiz_prompt").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("spelling_quiz_answer").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("spelling_quiz_submit").performScrollTo().assertIsDisplayed()
    }
}
