package com.example.englishlearning.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * 「复习队列」（批 1 Task 17，新建屏，原型 `sReview`）的 connected 判据。
 *
 * 源码形状（组件枚数、页头无动作位、禁读的三块 mock 指标、静态墨）归
 * `ReviewQueueLayoutContractTest`，本文件只管**渲染出来才看得见**的四件事：
 *  1. 到期词按快照队列逐行落地，点哪一行就打开哪个词（行与卡的对应关系只有渲染侧能证）；
 *  2. 「复习方式」那一行是**说明不是控件**——原型只画了一行加一个选中点，没有 radio/switch，
 *     所以带这枚 tag 的节点自己不许有 click 动作（`onClick` 一旦补上，本条红）；
 *  3. 吸底「开始复习」恰一次回调，且队列空时整条下线（原型是写死数据，没画 0 个到期词那一态）；
 *  4. 2× 字号下到期词行、复习方式卡与吸底按钮都还在（F5-03 / 系统字体放大）。
 *
 * 沿用本仓既有约束：一次用例只 `setContent` 一次。
 */
@RunWith(AndroidJUnit4::class)
class ReviewQueueScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun card(id: String, lemma: String, pos: String, meaning: String) = WordCard(
        cardId = id,
        wordBookId = "primary-school",
        lemma = lemma,
        ipa = "",
        partOfSpeech = pos,
        meaningZh = meaning,
    )

    /** 两个到期词的快照——本文件多数用例共用。 */
    private fun readyWithDueWords() = TodayPlanUiState.Ready(
        wordBookName = "小学",
        localDateLabel = "2026-09-19",
        newTarget = 2,
        dueTarget = 2,
        totalTasks = 4,
        newDone = 2,
        dueDone = 0,
        newQueue = listOf(card("n1", "apple", "n.", "苹果"), card("n2", "run", "v.", "跑")),
        dueQueue = listOf(card("d1", "bright", "adj.", "明亮的"), card("d2", "cloud", "n.", "云")),
        doneCardIds = setOf("n1", "n2"),
    )

    @Test fun header_and_strong_card_carry_the_prototype_copy() {
        var backRequests = 0
        composeRule.setContent {
            ReviewQueueScreen(
                state = readyWithDueWords(),
                onBack = { backRequests++ },
                onStartReview = {},
                onOpenWord = {},
            )
        }
        composeRule.onNodeWithTag("review_queue_screen").assertExists()
        composeRule.onNodeWithText("复习队列").assertIsDisplayed()
        composeRule.onNodeWithTag("review_queue_summary").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("到期复习").assertIsDisplayed()
        // 卡上的唯一权威数是**队列长度**（本屏列出来的就是这些行）。
        composeRule.onNodeWithText("2 个词在等你").assertIsDisplayed()
        // 页头只有返回，没有原型给 `sLearnQueue` 的那枚「调整」动作位（源码侧钉 `action = {` 为 0，
        // 这里钉渲染侧：屏上任何地方都不该出现「调整」两个字）。
        composeRule.onNodeWithText("调整").assertDoesNotExist()
        composeRule.onNodeWithText("到期词").assertIsDisplayed()
        composeRule.onNodeWithText("复习方式").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").assertHasClickAction().performClick()
        assertEquals(1, backRequests)
    }

    @Test fun due_rows_list_the_queue_and_open_that_word() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            ReviewQueueScreen(
                state = readyWithDueWords(),
                onBack = {},
                onStartReview = {},
                onOpenWord = { opened += it.cardId },
            )
        }
        composeRule.onNodeWithTag("review_queue_row_d1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("bright").assertIsDisplayed()
        composeRule.onNodeWithText("adj. 明亮的").assertIsDisplayed()
        composeRule.onNodeWithTag("review_queue_row_d2").performScrollTo().assertHasClickAction().performClick()
        assertEquals(listOf("d2"), opened)
    }

    /**
     * 「复习方式」行是说明，不是控件。
     *
     * 原型 `sReview` 的 `.card-quiet` 里只有一行 `rowItem` 文本加一枚 `.dot`（`app.js:492`），
     * 没有 radio / switch / 可点态；本仓也确实只有这一种复习模式。判据取「带这枚 tag 的节点自身
     * 不许有 click 动作」，而不是「点它没反应」——后者注入的点击会落到外层滚动列上，证明不了什么。
     */
    @Test fun the_review_mode_row_is_a_label_not_a_control() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            ReviewQueueScreen(
                state = readyWithDueWords(),
                onBack = {},
                onStartReview = {},
                onOpenWord = { opened += it.cardId },
            )
        }
        composeRule.onNodeWithTag("review_queue_mode_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("review_queue_mode_row").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("看英文，想中文").assertIsDisplayed()
        composeRule.onNodeWithText("默认方式").assertIsDisplayed()
        composeRule.onNode(hasTestTag("review_queue_mode_row") and hasClickAction()).assertDoesNotExist()
        composeRule.onNodeWithText("听发音，选释义").assertDoesNotExist()
        assertEquals("说明行不许把点击透传给词行", emptyList<String>(), opened)
    }

    @Test fun the_sticky_foot_starts_review_exactly_once() {
        var startRequests = 0
        composeRule.setContent {
            ReviewQueueScreen(
                state = readyWithDueWords(),
                onBack = {},
                onStartReview = { startRequests++ },
                onOpenWord = {},
            )
        }
        composeRule.onNodeWithTag("review_queue_start").assertIsDisplayed().assertHasClickAction()
        // 按可及名点（与 `AppScreenTest` 点主 CTA 同一口径：药片的可点节点带 contentDescription）。
        composeRule.onNodeWithContentDescription("开始复习到期词").assertExists().performClick()
        assertEquals(1, startRequests)
    }

    /**
     * 0 个到期词：整节与吸底都下线，强卡照旧说真话。
     *
     * 「复习方式」那一节**照旧渲染**——它讲的是模式，与队列有没有词无关；原型没画「0 个」那一态，
     * 所以这里不自创「今天没有到期的词」那种文案（类 KDoc 同口径）。
     */
    @Test fun an_empty_queue_hides_the_section_and_the_footer() {
        composeRule.setContent {
            ReviewQueueScreen(
                state = readyWithDueWords().copy(dueQueue = emptyList(), dueTarget = 0, totalTasks = 2),
                onBack = {},
                onStartReview = {},
                onOpenWord = {},
            )
        }
        composeRule.onNodeWithText("0 个词在等你").assertIsDisplayed()
        composeRule.onNodeWithText("到期词").assertDoesNotExist()
        composeRule.onNodeWithTag("review_queue_row_d1").assertDoesNotExist()
        composeRule.onNodeWithTag("review_queue_start").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("开始复习到期词").assertDoesNotExist()
        composeRule.onNodeWithText("复习方式").assertIsDisplayed()
    }

    /** 2× 字号（F5-03 / 系统字体放大）：两行到期词可达、说明卡与吸底按钮都还在。 */
    @Test fun large_font_scale_keeps_rows_mode_card_and_footer() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                ReviewQueueScreen(
                    state = readyWithDueWords(),
                    onBack = {},
                    onStartReview = {},
                    onOpenWord = {},
                )
            }
        }
        composeRule.onNodeWithTag("review_queue_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("review_queue_row_d2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("review_queue_mode_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("开始复习到期词").assertIsDisplayed()
    }
}
