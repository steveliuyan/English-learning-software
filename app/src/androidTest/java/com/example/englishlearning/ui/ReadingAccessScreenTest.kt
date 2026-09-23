package com.example.englishlearning.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingAccessScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun lockedStateShowsReasonAndDisablesArticleChoices() {
        composeRule.setContent {
            ReadingAccessScreen(
                state = ReadingAccessUiState.Locked("还差 2 个新词、1 个复习词"),
                onSelectType = {},
                onOpenHistory = {},
            )
        }

        composeRule.onNodeWithTag("reading_access_locked_reason").assertExists()
        composeRule.onNodeWithTag("reading_type_story").assertExists().assertIsNotEnabled()
        composeRule.onNodeWithTag("reading_history_entry").assertExists().assertHasClickAction()
    }

    @Test
    fun unlockedStateShowsFourTypesPreferenceAndOfflineHistoryEntry() {
        var selected: ArticleType? = null
        var historyOpened = 0
        composeRule.setContent {
            ReadingAccessScreen(
                state = ReadingAccessUiState.Ready(
                    preference = ReadingPreference("p1", ArticleType.SCIENCE, ArticleLengthTier.LONG),
                    history = emptyList(),
                ),
                onSelectType = { selected = it },
                onOpenHistory = { historyOpened++ },
            )
        }

        composeRule.onNodeWithTag("reading_type_news").assertExists()
        composeRule.onNodeWithTag("reading_type_story").assertExists()
        composeRule.onNodeWithTag("reading_type_science").assertExists().performClick()
        composeRule.onNodeWithTag("reading_type_workplace").assertExists()
        composeRule.onNodeWithTag("reading_preference").assertExists()
        composeRule.onNodeWithTag("reading_history_entry").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(ArticleType.SCIENCE, selected)
        assertEquals(1, historyOpened)
    }
}
