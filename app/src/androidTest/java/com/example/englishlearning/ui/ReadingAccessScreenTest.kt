package com.example.englishlearning.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.reading.FeedItem
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
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

    @Test
    fun readyStateShowsThreeSourceEntriesAndTheCostHint() {
        composeRule.setContent { readyScreen() }

        composeRule.onNodeWithTag("reading_source_ai").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("reading_source_feed").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("reading_source_import").assertExists().assertHasClickAction()
        // 费用提示必须逐字可读；绝不能再出现「本阶段不会发起网络生成」这类假话。
        composeRule.onNodeWithText("每次生成都会真实调用你配置的 AI 服务并可能产生费用", substring = true).assertExists()
        composeRule.onNodeWithText("本阶段不会发起网络生成", substring = true).assertDoesNotExist()
    }

    @Test
    fun generateFailureShowsConstantMessageAndConfigureAction() {
        var settingsOpened = 0
        composeRule.setContent {
            readyScreen(
                generation = GenerationUiState.Failed(AiFailure.Unauthorized),
                onOpenAiSettings = { settingsOpened++ },
            )
        }

        composeRule.onNodeWithTag("generation_failure").assertExists()
        // 泄露哨兵用子串匹配：拼接出来的 endpoint/Key 片段必须被常量文案挡住。
        composeRule.onNodeWithText("api.test", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("密钥被拒绝", substring = true).assertExists()
        composeRule.onNodeWithTag("generation_failure_action").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(1, settingsOpened)
    }

    @Test
    fun needsConfirmationDialogNamesTheHostAndWiresBothAnswers() {
        var answered: Boolean? = null
        composeRule.setContent {
            readyScreen(
                generation = GenerationUiState.NeedsConfirmation("api.test"),
                onConfirmOutbound = { answered = it },
            )
        }

        composeRule.onNodeWithTag("outbound_confirmation_text").assertExists()
        composeRule.onNodeWithText("api.test", substring = true).assertExists()
        composeRule.onNodeWithText("密钥将发送给", substring = true).assertExists()
        composeRule.onNodeWithTag("outbound_cancel").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(false, answered)
        composeRule.onNodeWithTag("outbound_confirm").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(true, answered)
    }

    @Test
    fun todayArticleExposesReadAndRegenerateButtons() {
        var opened = 0
        var regenerated = 0
        composeRule.setContent {
            readyScreen(
                todayArticle = todayArticle(),
                onOpenTodayArticle = { opened++ },
                onRegenerate = { regenerated++ },
            )
        }

        composeRule.onNodeWithTag("reading_open_today").assertExists().performClick()
        composeRule.onNodeWithTag("reading_regenerate").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
        assertEquals(1, regenerated)
    }

    @Test
    fun feedEmptyStateIsExplicit() {
        composeRule.setContent { readyScreen(feed = FeedUiState.Empty) }
        composeRule.onNodeWithTag("feed_empty").assertExists()
    }

    @Test
    fun feedUnreachableStateIsExplicit() {
        composeRule.setContent { readyScreen(feed = FeedUiState.Unreachable) }
        composeRule.onNodeWithTag("feed_error").assertExists()
    }

    @Test
    fun feedReadyListsItemsWithSourceName() {
        composeRule.setContent {
            readyScreen(
                feed = FeedUiState.Ready(
                    items = listOf(FeedItem("A Robot Teacher", "https://learningenglish.voanews.com/a/x", 0L, "")),
                    sourceDisplayName = "VOA Learning English",
                ),
            )
        }
        composeRule.onNodeWithTag("feed_source").assertExists()
        composeRule.onNodeWithText("VOA Learning English", substring = true).assertExists()
        composeRule.onNodeWithTag("feed_item_0").assertExists().assertHasClickAction()
    }

    @Test
    fun readyStateHistoryEntryIsReachableByScrolling() {
        // 真机走查发现：Ready 态内容高于屏幕后，根 Column 没有 verticalScroll，
        // 历史入口被折叠在折叠线以下且无法滚动到——在这台设备上不可达。
        // 本测试要求根容器可滚动，历史入口必须能滚到并保持可点击。
        composeRule.setContent { readyScreen(todayArticle = todayArticle()) }
        composeRule.onNodeWithTag("reading_access_screen")
            .performScrollToNode(hasTestTag("reading_history_entry"))
        composeRule.onNodeWithTag("reading_history_entry").assertExists().assertHasClickAction()
    }

    @Composable
    private fun readyScreen(
        generation: GenerationUiState = GenerationUiState.Idle,
        feed: FeedUiState = FeedUiState.Idle,
        todayArticle: com.example.englishlearning.reading.domain.Article? = null,
        onGenerate: () -> Unit = {},
        onRegenerate: () -> Unit = {},
        onOpenTodayArticle: () -> Unit = {},
        onConfirmOutbound: (Boolean) -> Unit = {},
        onOpenAiSettings: () -> Unit = {},
        onOpenFeed: () -> Unit = {},
        onFetchFeedItem: (FeedItem) -> Unit = {},
    ) = ReadingAccessScreen(
        state = ReadingAccessUiState.Ready(
            preference = ReadingPreference("p1", ArticleType.STORY, null),
            history = emptyList(),
            todayArticle = todayArticle,
            generation = generation,
            feed = feed,
        ),
        onSelectType = {},
        onOpenHistory = {},
        onGenerate = onGenerate,
        onRegenerate = onRegenerate,
        onOpenTodayArticle = onOpenTodayArticle,
        onConfirmOutbound = onConfirmOutbound,
        onOpenAiSettings = onOpenAiSettings,
        onOpenFeed = onOpenFeed,
        onFetchFeedItem = onFetchFeedItem,
    )

    private fun todayArticle() = com.example.englishlearning.reading.domain.Article(
        articleId = "today-1", profileId = "p1", localDate = "2026-09-25", activeWordBookId = "cet4",
        articleType = ArticleType.STORY, lengthTier = ArticleLengthTier.STANDARD, version = 1,
        title = "Today", englishText = List(200) { "word" }.joinToString(" "), chineseText = "译文",
        generatedAtEpochMillis = 1, coveredLemmas = emptyList(),
        source = ArticleSource.AiGenerated(modelName = "gpt-x", parameterSummary = ""),
    )
}
