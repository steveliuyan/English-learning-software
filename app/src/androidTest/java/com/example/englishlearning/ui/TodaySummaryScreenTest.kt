package com.example.englishlearning.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「今日完成」（批 1 Task 18，新建屏，原型 `sSummary`）的 connected 判据。
 *
 * 源码形状（组件枚数、禁读的 mock 指标、静态墨桥、吸底线）归 `TodaySummaryLayoutContractTest`，
 * 本文件只管**渲染出来才看得见**的五件事：
 *  1. 页名 / 日期眉行 / hero 那句 serif / 统计数字按传进来的真数据落地；
 *  2. 原型那三块 mock（`用时`、`一次答对`、`这一轮的反馈`）与「8 分钟后再看 1 个词」在屏上
 *     **一个节点都不许出现**——源码侧钉的是「不写」，这里钉「不显示」：用户 2026-10-29 拍板
 *     「读不到的那枚整枚不显示」，任何同义改写（`耗时`/`正确率`）都会在这里红；
 *  3. 三个出口（页头 ← / 页头「完成」/ 吸底「回到今日」）各恰好回调一次，且「读今天的文章」
 *     把点击交给宿主而不是自己翻页；
 *  4. 文章已读与否驱动行尾那一格的两值（本仓有真读数，`未读` 不是装饰）；
 *  5. 大字号（fontScale 2.0，spec §3 钉住的验收档）下 hero、统计卡、行、吸底都仍可达。
 *
 * 沿用本仓既有约束：一次用例只 `setContent` 一次；异步取数归 JVM 侧，本屏只吃喂进来的快照。
 */
@RunWith(AndroidJUnit4::class)
class TodaySummaryScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun summary(
        completedCount: Int = 8,
        total: Int = 8,
        dateLabel: String? = "2026-09-19",
        articleRead: Boolean = false,
        onBack: () -> Unit = {},
        onDone: () -> Unit = {},
        onOpenReading: () -> Unit = {},
    ) {
        composeRule.setContent {
            TodaySummaryScreen(
                state = WordCardUiState.AllDone(total = total, completedCount = completedCount),
                dateLabel = dateLabel,
                articleRead = articleRead,
                onBack = onBack,
                onDone = onDone,
                onOpenReading = onOpenReading,
            )
        }
    }

    @Test fun header_hero_and_stat_carry_the_real_numbers() {
        summary(completedCount = 6, total = 8)
        composeRule.onNodeWithTag("today_summary_screen").assertExists()
        composeRule.onNodeWithText("今日完成").assertIsDisplayed()
        composeRule.onNodeWithTag("today_summary_date").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2026-09-19").assertIsDisplayed()
        composeRule.onNodeWithText("今天的词都过了一遍").assertIsDisplayed()
        // 统计数字取**真提交完**的 6 而非计划目标 8（原型 `total` 是 mock 的目标数，两数在此分权）。
        composeRule.onNodeWithTag("today_summary_stat_words").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("6").assertIsDisplayed()
        composeRule.onNodeWithText("个词").assertIsDisplayed()
        composeRule.onNodeWithText("8").assertDoesNotExist()
        // 原型 `badge-lg` 那一格是 64dp（`app.css:560`）；只钉下界，上界由组件的档位表负责。
        composeRule.onNodeWithTag("today_summary_badge").assertHeightIsAtLeast(60.dp)
    }

    /**
     * 原型的三块 mock 指标整块不显示（拍板 #3）。
     *
     * 判据取「屏上没有这些节点」，而不只是「源码里没有这些字」：宿主若把同一份数据接到别处渲染，
     * 或有人把「用时」换成「耗时」同义回填，源码侧的正则判据可能照旧绿，这里会红。
     */
    @Test fun the_mock_metrics_from_the_prototype_stay_off_the_screen() {
        summary()
        listOf(
            "用时", "一次答对", "6:42", "82", "这一轮的反馈",
            "下次间隔", "明天再来", "今天晚些时候", "8 分钟后再看", "最佳时点",
        ).forEach { token ->
            composeRule.onNodeWithText(token, substring = true).assertDoesNotExist()
        }
        composeRule.onNodeWithText("4 段").assertDoesNotExist()
        composeRule.onNodeWithText("覆盖 4 个今日词").assertDoesNotExist()
    }

    @Test fun the_three_exits_each_fire_once_and_the_reading_row_hands_off() {
        var back = 0
        var done = 0
        var reading = 0
        summary(onBack = { back++ }, onDone = { done++ }, onOpenReading = { reading++ })

        composeRule.onNodeWithContentDescription("返回").assertHasClickAction().performClick()
        assertEquals("页头返回只该转调宿主一次", 1, back)
        assertEquals(0, done)

        composeRule.onNodeWithContentDescription("完成，回到今日学习").assertHasClickAction().performClick()
        assertEquals(1, done)

        composeRule.onNodeWithTag("today_summary_open_reading").performScrollTo().assertHasClickAction().performClick()
        assertEquals("读今天的文章交给宿主导航，本屏不自己翻页", 1, reading)

        composeRule.onNodeWithTag("today_summary_back_to_today").assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals("吸底「回到今日」与页头「完成」是同一条退出通路，各自恰好一次", 2, done)
    }

    /** 行尾那一格绑真实阅读读数：今日文章读过就写「已读」，没读过就写「未读」，两值都不是装饰。 */
    @Test fun the_reading_row_reports_the_actual_read_state() {
        summary(articleRead = true)
        composeRule.onNodeWithTag("today_summary_open_reading").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("读今天的文章").assertIsDisplayed()
        composeRule.onNodeWithText("已读").assertIsDisplayed()
        composeRule.onNodeWithText("未读").assertDoesNotExist()
    }

    @Test fun the_reading_row_reports_unread_when_today_is_not_read() {
        summary(articleRead = false)
        composeRule.onNodeWithText("未读").assertIsDisplayed()
        composeRule.onNodeWithText("已读").assertDoesNotExist()
    }

    /**
     * 计划快照没就绪（宿主传 `dateLabel = null`）时眉行整行不进组合。
     *
     * 这条钉的是「没有日期就宁可不摆那一行」，而不是拿空串或「今天」占位——
     * 占位会让读屏用户在一个并不存在的日期上听到一句编出来的话。
     */
    @Test fun a_missing_plan_date_hides_the_eyebrow_row_only() {
        summary(dateLabel = null)
        composeRule.onNodeWithTag("today_summary_date").assertDoesNotExist()
        composeRule.onNodeWithText("今日完成").assertIsDisplayed()
        composeRule.onNodeWithText("今天的词都过了一遍").assertIsDisplayed()
        composeRule.onNodeWithTag("today_summary_stat_words").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("today_summary_back_to_today").assertIsDisplayed()
    }

    /** fontScale 2.0（spec §3 钉住的验收档）：hero、统计、行、吸底都仍可达。 */
    @Test fun large_font_scale_keeps_every_block() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                TodaySummaryScreen(
                    state = WordCardUiState.AllDone(total = 8, completedCount = 8),
                    dateLabel = "2026-09-19",
                    articleRead = false,
                    onBack = {},
                    onDone = {},
                    onOpenReading = {},
                )
            }
        }
        composeRule.onNodeWithTag("today_summary_hero").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("today_summary_stat_words").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("today_summary_open_reading").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("today_summary_back_to_today").assertIsDisplayed()
    }
}
