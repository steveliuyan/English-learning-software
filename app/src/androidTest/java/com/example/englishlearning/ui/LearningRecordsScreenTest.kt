package com.example.englishlearning.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.LearningRecord
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import com.example.englishlearning.learning.domain.CardFeedback
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningRecordsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun threeTabsAndFiltersAreRendered() {
        composeRule.setContent { LearningRecordsScreen(LearningRecordsUiState(), {}, {}, {}, {}, {}, {}) }
        composeRule.onNodeWithContentDescription("learning_records_screen").assertIsDisplayed()
        composeRule.onNodeWithText("今日已学").assertIsDisplayed()
        composeRule.onNodeWithText("历史记录").assertIsDisplayed()
        composeRule.onNodeWithText("全部单词").assertIsDisplayed()
    }

    @Test
    fun missingRecordIsVisibleButNotClickable() {
        val row = LearningRecord.missing("book:parent", CardFeedback.Fuzzy, Instant.EPOCH, LocalDate.of(2026, 9, 30), "plan-1")
        composeRule.setContent { LearningRecordsScreen(LearningRecordsUiState(today = listOf(row)), {}, {}, {}, {}, {}, {}) }
        composeRule.onNodeWithContentDescription("learning_record_missing_parent").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("learning_record_row_parent").assertCountEquals(0)
    }

    @Test
    fun allWordFilterLabelsAreShown() {
        val card = com.example.englishlearning.learning.domain.WordCard("book:a", "book", "apple", "", "n.", "苹果")
        composeRule.setContent { LearningRecordsScreen(LearningRecordsUiState(selectedTab = LearningRecordsTab.ALL_WORDS, allWords = listOf(WordBookRecord(card, card.cardId, card.lemma, WordBookRecordStatus.Unlearned))), {}, {}, {}, {}, {}, {}) }
        composeRule.onNodeWithText("未学 1", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("学习中 0", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("待复习 0", substring = false).assertIsDisplayed()
    }
}
