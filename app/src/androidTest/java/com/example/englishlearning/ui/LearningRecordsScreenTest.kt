package com.example.englishlearning.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.LearningRecord
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
    fun twoTabsAreRendered() {
        composeRule.setContent { LearningRecordsScreen(LearningRecordsUiState(), {}, {}, {}, {}) }
        composeRule.onNodeWithTag("learning_records_screen").assertIsDisplayed()
        composeRule.onNodeWithText("今日已学").assertIsDisplayed()
        composeRule.onNodeWithText("历史记录").assertIsDisplayed()
        composeRule.onNodeWithText("全部单词").assertDoesNotExist()
    }

    @Test
    fun missingRecordIsVisibleButNotClickable() {
        val row = LearningRecord.missing("book:parent", CardFeedback.Fuzzy, Instant.EPOCH, LocalDate.of(2026, 9, 30), "plan-1")
        composeRule.setContent { LearningRecordsScreen(LearningRecordsUiState(today = listOf(row)), {}, {}, {}, {}) }
        composeRule.onNodeWithTag("learning_record_missing_parent").assertIsDisplayed()
        composeRule.onAllNodesWithTag("learning_record_row_parent").assertCountEquals(0)
    }
}
