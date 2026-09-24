package com.example.englishlearning.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingHistoryScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun offlineHistoryListsStoredArticles() {
        composeRule.setContent { ReadingHistoryScreen(history = listOf(article("Local story")), onBack = {}) }

        composeRule.onNodeWithTag("reading_history_screen").assertExists()
        composeRule.onNodeWithTag("reading_history_item_a1").assertExists()
    }

    @Test
    fun emptyHistoryHasExplicitOfflineEmptyState() {
        composeRule.setContent { ReadingHistoryScreen(history = emptyList(), onBack = {}) }

        composeRule.onNodeWithTag("reading_history_empty").assertExists()
    }

    private fun article(title: String) = Article(
        articleId = "a1", profileId = "p1", localDate = "2026-09-22", activeWordBookId = "cet4",
        articleType = ArticleType.STORY, lengthTier = ArticleLengthTier.STANDARD, version = 1,
        title = title, englishText = "text", chineseText = "译文", generatedAtEpochMillis = 1L, modelName = null,
        coveredLemmas = listOf("title"), parameterSummary = "",
    )
}
