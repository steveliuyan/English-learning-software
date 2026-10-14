package com.example.englishlearning.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
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
    fun tappingAHistoryItemOpensThatArticle() {
        // F3-01A：历史页此前只展示不可点，旧版全文无从读起（真机走查发现）。
        // 单版本组保持「点卡直开」——同一天只有一版时不必多一次展开。
        var opened: Article? = null
        composeRule.setContent {
            ReadingHistoryScreen(
                history = listOf(article("Local story")),
                onBack = {},
                onOpenArticle = { opened = it },
            )
        }

        composeRule.onNodeWithTag("reading_history_item_a1").assertHasClickAction().performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals("Local story", opened?.title)
    }

    @Test
    fun emptyHistoryHasExplicitOfflineEmptyState() {
        composeRule.setContent { ReadingHistoryScreen(history = emptyList(), onBack = {}) }

        composeRule.onNodeWithTag("reading_history_empty").assertExists()
    }

    // ---- F4-01 决策 §8：多版本组「看得见、删得掉、删不空」（UI 侧） ----

    @Test
    fun multiVersionGroupExpandsToPerVersionRows() {
        composeRule.setContent {
            ReadingHistoryScreen(
                history = listOf(article("Evening story", articleId = "a2", version = 2), article("Morning story", articleId = "a1", version = 1)),
                onBack = {},
            )
        }

        composeRule.onNodeWithTag(groupTag).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("reading_version_row_a2").assertExists()
        composeRule.onNodeWithTag("reading_version_row_a1").assertExists()
        composeRule.onNodeWithText("第 2 版 · 2026-09-22").assertExists()
    }

    @Test
    fun tappingRereadOpensTheExactVersionNotJustTheNewest() {
        var opened: Article? = null
        composeRule.setContent {
            ReadingHistoryScreen(
                history = listOf(article("Evening story", articleId = "a2", version = 2), article("Morning story", articleId = "a1", version = 1)),
                onBack = {},
                onOpenArticle = { opened = it },
            )
        }

        composeRule.onNodeWithTag(groupTag).performClick()
        composeRule.onNodeWithTag("reading_version_reread_a1").performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals(1, opened?.version)
    }

    @Test
    fun tappingDeleteVersionRequestsDeletionForThatExactVersion() {
        var requested: Article? = null
        composeRule.setContent {
            ReadingHistoryScreen(
                history = listOf(article("Evening story", articleId = "a2", version = 2), article("Morning story", articleId = "a1", version = 1)),
                onBack = {},
                onRequestDeleteVersion = { requested = it },
            )
        }

        composeRule.onNodeWithTag(groupTag).performClick()
        composeRule.onNodeWithTag("reading_version_delete_a1").performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals("a1", requested?.articleId)
    }

    @Test
    fun singleVersionGroupHasNoDeleteEntry() {
        // 「删不空」的入口侧表达：唯一版本连删除按钮都不给，误触无从谈起。
        composeRule.setContent {
            ReadingHistoryScreen(
                history = listOf(article("Local story", articleId = "a1", version = 1)),
                onBack = {},
                onRequestDeleteVersion = { org.junit.Assert.fail("唯一版本不该发起删除请求") },
            )
        }

        composeRule.onNodeWithTag("reading_history_item_a1").assertExists()
        composeRule.onNodeWithTag("reading_version_delete_a1").assertDoesNotExist()
    }

    @Test
    fun deletionNoticeIsDisplayedAfterAnOperation() {
        composeRule.setContent {
            ReadingHistoryScreen(
                history = listOf(article("Local story")),
                onBack = {},
                notice = "已删除第 2 版（连带 1 条阅读完成记录），同组剩余 1 个版本。",
            )
        }

        composeRule.onNodeWithTag("reading_version_notice").assertExists()
        composeRule.onNodeWithText("已删除第 2 版（连带 1 条阅读完成记录），同组剩余 1 个版本。").assertIsDisplayed()
    }

    private companion object {
        // 分组键 = localDate_activeWordBookId_articleType_lengthTier（与实现约定一致）
        val groupTag = "reading_history_group_2026-09-22_cet4_STORY_STANDARD"
    }

    private fun article(
        title: String,
        articleId: String = "a1",
        version: Int = 1,
    ) = Article(
        articleId = articleId, profileId = "p1", localDate = "2026-09-22", activeWordBookId = "cet4",
        articleType = ArticleType.STORY, lengthTier = ArticleLengthTier.STANDARD, version = version,
        title = title, englishText = "text", chineseText = "译文", generatedAtEpochMillis = 1L,
        coveredLemmas = listOf("title"),
        source = ArticleSource.AiGenerated(modelName = "", parameterSummary = ""),
    )
}
