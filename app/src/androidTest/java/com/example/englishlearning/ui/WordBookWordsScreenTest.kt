package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import com.example.englishlearning.learning.domain.WordCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordBookWordsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    /** D2 验收：词书单词页按字母分组，组头可见、行可见。 */
    @Test
    fun letterGroupsRenderWithStickyHeadersAndRows() {
        composeRule.setContent {
            WordBookWordsScreen(
                state = WordBookWordsUiState(
                    words = listOf(
                        word("book:avocado", "avocado"),
                        word("book:apple", "apple"),
                        word("book:banana", "banana", WordBookRecordStatus.Due),
                        word("book:苹果", "苹果"),
                    ),
                ),
                onBack = {}, onFilter = {}, onRetry = {}, onOpenWord = {},
            )
        }
        composeRule.onNodeWithContentDescription("wordbook_words_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_letter_A").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_letter_B").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_letter_#").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("wordbook_words_row_apple").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("wordbook_words_row_banana").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("wordbook_words_row_苹果").assertIsDisplayed()
    }

    /** D2 验收：右侧 A–Z 快速导航栏 26 个字母常驻，每个都可点击。 */
    @Test
    fun alphabetRailIsRenderedFromAToZ() {
        composeRule.setContent {
            WordBookWordsScreen(state = WordBookWordsUiState(words = listOf(word("book:a", "apple"))), onBack = {}, onFilter = {}, onRetry = {}, onOpenWord = {})
        }
        composeRule.onNodeWithTag("wordbook_words_rail").assertIsDisplayed()
        ('A'..'Z').forEach { letter ->
            composeRule.onNodeWithTag("wordbook_words_rail_$letter").assertExists()
        }
    }

    /**
     * D2 验收：点导航字母直接跳到对应组。断言钉用户契约——
     * 起点在 A（aword1 可见），点 Z 后离开 A（aword1 不可见）且 Z 组头可见。
     * stickyHeader 是 overlay 钉顶实现，不钉具体某一行是否进入可视区。
     */
    @Test
    fun tappingRailLetterScrollsToItsGroup() {
        val words = ('A'..'Z').flatMap { letter ->
            (1..3).map { index -> word("book:${letter.lowercaseChar()}$index", "${letter.lowercase()}word$index") }
        }
        composeRule.setContent {
            WordBookWordsScreen(state = WordBookWordsUiState(words = words), onBack = {}, onFilter = {}, onRetry = {}, onOpenWord = {})
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("wordbook_words_row_a1").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_rail_Z").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_letter_Z").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("wordbook_words_row_z1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("wordbook_words_row_a1").assertDoesNotExist()
    }

    /** 状态筛选沿用原「全部单词」的语义，只是换到了独立页：计数 + 本地过滤。 */
    @Test
    fun filterChipsShowCountsAndSelectingNarrowsRows() {
        var filter by mutableStateOf<WordBookRecordStatus?>(null)
        composeRule.setContent {
            WordBookWordsScreen(
                state = WordBookWordsUiState(
                    words = listOf(
                        word("book:apple", "apple", WordBookRecordStatus.Unlearned),
                        word("book:avocado", "avocado", WordBookRecordStatus.Unlearned),
                        word("book:banana", "banana", WordBookRecordStatus.Due),
                        word("book:苹果", "苹果", WordBookRecordStatus.Learning),
                    ),
                    filter = filter,
                ),
                onBack = {},
                onFilter = { filter = it },
                onRetry = {},
                onOpenWord = {},
            )
        }
        composeRule.onNodeWithContentDescription("全部 4").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("未学 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("待复习 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("wordbook_words_row_banana").assertIsDisplayed()
        composeRule.onNodeWithTag("wordbook_words_filter_unlearned").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("wordbook_words_row_banana").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("wordbook_words_row_apple").assertIsDisplayed()
    }

    private fun word(
        cardId: String,
        lemma: String,
        status: WordBookRecordStatus = WordBookRecordStatus.Unlearned,
    ): WordBookRecord {
        val card = WordCard(cardId, "book", lemma, "", "", "释义")
        return WordBookRecord(card, cardId, lemma, status)
    }
}
