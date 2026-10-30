package com.example.englishlearning.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.WordBookProgress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 词书详情（批 1 Task 15，原型 `sBookDetail`）的 connected 判据。
 *
 * 源码形状（页头归壳、操作组只剩两行、mock 行目不搬）由 `BookDetailScreenContractTest` 钉；
 * 这里只判 JVM 问不到的那一半：真渲染出来的文案、进度环在场、未知计数整行不画、两枚入口各落哪儿、
 * 以及可点节点的闭合集合。
 */
@RunWith(AndroidJUnit4::class)
class BookDetailScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun screen(
        progress: WordBookProgress? = WordBookProgress(learned = 268, total = 3039),
        learningCount: Int? = 312,
        dueCount: Int? = 5,
        onOpenWordList: () -> Unit = {},
        onOpenPicker: () -> Unit = {},
    ) {
        composeRule.setContent {
            BookDetailScreen(
                bookName = "高等职业教育专科英语",
                progress = progress,
                learningCount = learningCount,
                dueCount = dueCount,
                onOpenWordList = onOpenWordList,
                onOpenPicker = onOpenPicker,
            )
        }
    }

    @Test fun summary_carries_the_book_name_the_learned_count_and_the_percentage() {
        screen()
        composeRule.onNodeWithTag("book_detail_screen").assertExists()
        composeRule.onAllNodesWithText("正在使用").assertCountEquals(1)
        composeRule.onAllNodesWithText("高等职业教育专科英语").assertCountEquals(1)
        composeRule.onNodeWithTag("book_detail_learned").performScrollTo()
        composeRule.onAllNodesWithText("已学 268 / 3039 词").assertCountEquals(1)
        composeRule.onNodeWithTag("book_detail_ring").assertExists()
        // 268 / 3039 ≈ 8.8% → 环心取整 9（原型 `Math.round(p*100)`）。
        composeRule.onAllNodesWithText("9").assertCountEquals(1)
    }

    @Test fun facts_show_the_counts_this_app_can_read() {
        screen()
        composeRule.onAllNodesWithText("已学").assertCountEquals(1)
        composeRule.onAllNodesWithText("未学").assertCountEquals(1)
        composeRule.onAllNodesWithText("学习中").assertCountEquals(1)
        composeRule.onAllNodesWithText("待复习").assertCountEquals(1)
        composeRule.onAllNodesWithText("312").assertCountEquals(1)
        composeRule.onAllNodesWithText("5").assertCountEquals(1)
        // 原型 mock 行目不搬。
        composeRule.onAllNodesWithText("已掌握").assertCountEquals(0)
        composeRule.onAllNodesWithText("词书版本").assertCountEquals(0)
    }

    @Test fun unknown_counts_disappear_instead_of_showing_a_fake_zero() {
        screen(learningCount = null, dueCount = null)
        composeRule.onAllNodesWithText("学习中").assertCountEquals(0)
        composeRule.onAllNodesWithText("待复习").assertCountEquals(0)
        // 进度本身仍是真的那份。
        composeRule.onNodeWithTag("book_detail_learned").performScrollTo()
        composeRule.onAllNodesWithText("已学 268 / 3039 词").assertCountEquals(1)
    }

    @Test fun without_progress_the_learned_rows_stay_off() {
        screen(progress = null, learningCount = null, dueCount = null)
        composeRule.onNodeWithTag("book_detail_learned").assertDoesNotExist()
        composeRule.onAllNodesWithText("已学").assertCountEquals(0)
        composeRule.onAllNodesWithText("未学").assertCountEquals(0)
        composeRule.onAllNodesWithText("正在使用").assertCountEquals(1)
    }

    @Test fun the_two_action_rows_open_the_word_list_and_the_picker() {
        var openedList = 0
        var openedPicker = 0
        screen(
            onOpenWordList = { openedList++ },
            onOpenPicker = { openedPicker++ },
        )
        composeRule.onNodeWithTag("book_detail_open_word_list")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, openedList)
        assertEquals(0, openedPicker)

        composeRule.onNodeWithTag("book_detail_open_picker")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, openedPicker)
        // 闭合集合：本屏只有这两枚可点节点（原型另外两行与页头 more 都没有能力承载）。
        assertEquals(
            "词书详情可点节点必须恰两枚（查看完整词单 / 更换词书）",
            2,
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size,
        )
    }
}
