package com.example.englishlearning.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.domain.WordCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「今日词单」（批 1 Task 16，原型 `sLearnQueue`）的 connected 判据。
 *
 * 本屏是新建屏，所以这里只钉**它自己**的四件事；源码形状（组件枚数、档位、禁读点）归
 * `TodayWordListLayoutContractTest`，两侧不重叠：
 *  1. 三枚筛选真的换版面（筛选是本屏唯一的交互分支，两节恒显就等于没有筛选）；
 *  2. 行尾状态**逐 id** 落地——今日已完成的复习项显示「已学」而不是「到期」（AGENTS.md：计划是
 *     不可变快照，已完成的那一项再说「到期」是假话）；
 *  3. 吸底主按钮的序号跟着真实完成数走，做完就整条消失（原型没画「没有下一个词」那一态）；
 *  4. 行可点、可点节点有可及名，2× 字号下三枚筛选与两节词行都还在（F5-03 / 系统字体放大）。
 *
 * 沿用本仓既有约束：一次用例只 `setContent` 一次。
 */
@RunWith(AndroidJUnit4::class)
class TodayWordListScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun card(id: String, lemma: String, pos: String, meaning: String) = WordCard(
        cardId = id,
        wordBookId = "primary-school",
        lemma = lemma,
        ipa = "",
        partOfSpeech = pos,
        meaningZh = meaning,
    )

    /** 两节都有内容、且 n1 与 d1 已完成的那份状态——本文件多数用例共用。 */
    private fun readyWithQueues() = TodayPlanUiState.Ready(
        wordBookName = "小学",
        localDateLabel = "2026-09-19",
        newTarget = 2,
        dueTarget = 2,
        totalTasks = 4,
        newDone = 1,
        dueDone = 1,
        newQueue = listOf(card("n1", "apple", "n.", "苹果"), card("n2", "run", "v.", "跑")),
        dueQueue = listOf(card("d1", "bright", "adj.", "明亮的"), card("d2", "cloud", "n.", "云")),
        doneCardIds = setOf("n1", "d1"),
    )

    @Test fun header_meta_and_chips_carry_the_prototype_copy() {
        var setupRequests = 0
        composeRule.setContent {
            TodayWordListScreen(
                state = readyWithQueues(),
                onBack = {},
                onOpenSetup = { setupRequests++ },
                onStartLearning = {},
                onOpenWord = {},
            )
        }
        composeRule.onNodeWithTag("today_word_list_screen").assertExists()
        composeRule.onNodeWithText("今日词单").assertIsDisplayed()
        composeRule.onNodeWithText("的词单由", substring = true).assertIsDisplayed()
        // 三枚筛选的计数取**队列长度**（本屏列出来的就是这些行），不是计划目标数。
        composeRule.onNodeWithText("全部 4").assertIsDisplayed()
        composeRule.onNodeWithText("新学 2").assertIsDisplayed()
        composeRule.onNodeWithText("复习 2").assertIsDisplayed()
        composeRule.onNodeWithText("新学 · 2 个词").assertIsDisplayed()
        composeRule.onNodeWithText("复习 · 2 个词").assertIsDisplayed()
        composeRule.onNodeWithText("按遗忘曲线排序").assertIsDisplayed()
        // 页头动作按可及名点（与 `AppScreenTest` 找「保存学习设置」同一口径）。
        composeRule.onNodeWithContentDescription("调整今日计划").assertExists().performClick()
        assertEquals("「调整」是本页唯一的计划入口，一次点击必须恰一次回调", 1, setupRequests)
    }

    @Test fun the_three_filter_chips_switch_which_sections_show() {
        composeRule.setContent {
            TodayWordListScreen(
                state = readyWithQueues(),
                onBack = {},
                onOpenSetup = {},
                onStartLearning = {},
                onOpenWord = {},
            )
        }
        composeRule.onNodeWithTag("today_word_list_filter_new").performClick()
        composeRule.onNodeWithText("新学 · 2 个词").assertIsDisplayed()
        composeRule.onNodeWithText("复习 · 2 个词").assertDoesNotExist()
        composeRule.onNodeWithTag("today_word_list_row_due_d1").assertDoesNotExist()

        composeRule.onNodeWithTag("today_word_list_filter_due").performClick()
        composeRule.onNodeWithText("复习 · 2 个词").assertIsDisplayed()
        composeRule.onNodeWithText("新学 · 2 个词").assertDoesNotExist()
        composeRule.onNodeWithTag("today_word_list_row_new_n1").assertDoesNotExist()

        composeRule.onNodeWithTag("today_word_list_filter_all").performClick()
        composeRule.onNodeWithText("新学 · 2 个词").assertIsDisplayed()
        composeRule.onNodeWithText("复习 · 2 个词").assertIsDisplayed()
    }

    /**
     * 行内容：词头 + 「词性 释义」那一档 + **逐 id** 的行尾状态。
     *
     * 「已学」两枚（n1 与 d1）、「待学」「到期」各一枚——按 id 判定而不是按计数：
     * `newDone == 1 && dueDone == 1` 说不出**哪一张**卡做完，两张都标「待学」才是假话。
     */
    @Test fun rows_carry_the_gloss_tier_and_per_card_status() {
        composeRule.setContent {
            TodayWordListScreen(
                state = readyWithQueues(),
                onBack = {},
                onOpenSetup = {},
                onStartLearning = {},
                onOpenWord = {},
            )
        }
        composeRule.onNodeWithTag("today_word_list_row_new_n1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        composeRule.onNodeWithText("n. 苹果").assertIsDisplayed()
        composeRule.onNodeWithTag("today_word_list_row_due_d1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("bright").assertIsDisplayed()
        composeRule.onNodeWithText("adj. 明亮的").assertIsDisplayed()
        assertEquals(2, composeRule.onAllNodesWithText("已学").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("待学").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("到期").fetchSemanticsNodes().size)
    }

    @Test fun tapping_a_row_opens_that_word_and_the_footer_starts_the_next_one() {
        val opened = mutableListOf<String>()
        var startRequests = 0
        composeRule.setContent {
            TodayWordListScreen(
                state = readyWithQueues(),
                onBack = {},
                onOpenSetup = {},
                onStartLearning = { startRequests++ },
                onOpenWord = { opened += it.cardId },
            )
        }
        composeRule.onNodeWithTag("today_word_list_row_due_d2").performScrollTo().performClick()
        assertEquals(listOf("d2"), opened)

        // 已完成 2 个 → 按钮写「开始第 3 个词」（今日完成数 + 1，与词卡流程「从第一张未完成卡续读」同口径）。
        composeRule.onNodeWithText("开始第 3 个词").assertIsDisplayed()
        // 点击按可及名点（`AppScreenTest` 点主 CTA 的既有口径：药片的可点节点带 contentDescription）。
        composeRule.onNodeWithContentDescription("开始学习今日词单").assertExists().performClick()
        assertEquals(1, startRequests)
    }

    /** 全部做完：四行都是「已学」，吸底按钮整条下线（原型没画「没有下一个词」那一态）。 */
    @Test fun the_footer_disappears_once_today_is_fully_done() {
        composeRule.setContent {
            TodayWordListScreen(
                state = readyWithQueues().copy(
                    newDone = 2,
                    dueDone = 2,
                    doneCardIds = setOf("n1", "n2", "d1", "d2"),
                ),
                onBack = {},
                onOpenSetup = {},
                onStartLearning = {},
                onOpenWord = {},
            )
        }
        composeRule.onNodeWithTag("today_word_list_row_due_d2").performScrollTo().assertIsDisplayed()
        assertEquals(4, composeRule.onAllNodesWithText("已学").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("到期").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("today_word_list_start").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("开始学习今日词单").assertDoesNotExist()
    }

    /** 某一节没有词就整节不渲染（原型是写死数据，没画过「0 个词」那一节）。 */
    @Test fun an_empty_section_is_not_rendered_at_all() {
        composeRule.setContent {
            TodayWordListScreen(
                state = readyWithQueues().copy(
                    newQueue = emptyList(),
                    newDone = 0,
                    newTarget = 0,
                    totalTasks = 2,
                    doneCardIds = setOf("d1"),
                ),
                onBack = {},
                onOpenSetup = {},
                onStartLearning = {},
                onOpenWord = {},
            )
        }
        composeRule.onNodeWithText("新学 · 0 个词").assertDoesNotExist()
        composeRule.onNodeWithTag("today_word_list_row_new_n1").assertDoesNotExist()
        composeRule.onNodeWithText("复习 · 2 个词").assertIsDisplayed()
        composeRule.onNodeWithText("全部 2").assertIsDisplayed()
        composeRule.onNodeWithText("新学 0").assertIsDisplayed()
    }

    /**
     * 2× 字号（F5-03 / 系统字体放大）：三枚筛选换行后仍在，词行靠滚动可达，吸底按钮不被裁掉。
     *
     * 筛选行用 `FlowRow` 而不是 `Row` 就是为了这一档——`Row` 会把第三枚挤出屏幕（原型是
     * `display:flex` + `gap`，本就允许换行）。
     */
    @Test fun large_font_scale_keeps_chips_rows_and_the_footer() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                TodayWordListScreen(
                    state = readyWithQueues(),
                    onBack = {},
                    onOpenSetup = {},
                    onStartLearning = {},
                    onOpenWord = {},
                )
            }
        }
        listOf("today_word_list_filter_all", "today_word_list_filter_new", "today_word_list_filter_due")
            .forEach { tag -> composeRule.onNodeWithTag(tag).assertExists() }
        composeRule.onNodeWithTag("today_word_list_row_due_d2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("开始学习今日词单").assertIsDisplayed()
    }
}
