package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.SearchVocabularyResult
import com.example.englishlearning.learning.VocabularySearchHistory
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 主页全量搜索页的 Compose 断言。
 *
 * 这里盯住两件事：一是「用户看得见的语义」都真的画出来了（历史 / 结果 / 词书上下文 / 次数 /
 * 失败重试），二是主页查词里**不再出现「浏览词书」**——用户明确反馈过这个入口没有意义，
 * 而它原本就在旧词书搜索页里，所以必须有一条断言把它挡在主页之外。
 */
@RunWith(AndroidJUnit4::class)
class GlobalVocabularySearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-10-04T09:00:00Z")

    @Test
    fun emptyInputShowsRecentHistoryAndNoBrowseEntry() {
        composeRule.setContent {
            GlobalVocabularySearchScreen(
                state = GlobalVocabularySearchUiState.Ready(
                    history = listOf(
                        GlobalVocabularySearchHistoryItem(history("ability", 3), searchCountForDisplay = 3),
                    ),
                    results = emptyList(),
                ),
                query = "",
                onQueryChange = {},
                onSubmit = {},
                onRetry = {},
                onClearHistory = {},
                onOpenHistory = {},
                onSelect = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("最近搜索").assertExists()
        // 行本身带 testTag（行是 clickable 的合并节点，能在合并树里找到）。
        composeRule.onNodeWithTag("global_search_history_ability").assertExists()
        // 行内的 tag 必须走未合并树：clickable 会 mergeDescendants，子节点不进合并树。
        // 这一条同时是下一条「关闭时不存在」的正向对照——先证明它在显示时确实找得到。
        composeRule.onNodeWithTag("global_search_history_count_ability", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("浏览词书", substring = true).assertDoesNotExist()
    }

    @Test
    fun historyCountIsHiddenWhenTheSettingIsOff() {
        composeRule.setContent {
            GlobalVocabularySearchScreen(
                state = GlobalVocabularySearchUiState.Ready(
                    history = listOf(
                        GlobalVocabularySearchHistoryItem(history("ability", 3), searchCountForDisplay = null),
                    ),
                    results = emptyList(),
                ),
                query = "",
                onQueryChange = {},
                onSubmit = {},
                onRetry = {},
                onClearHistory = {},
                onOpenHistory = {},
                onSelect = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("global_search_history_ability").assertExists()
        // 未合并树 + 断言「不存在」：必须显式指定 useUnmergedTree，否则这个断言是**空转**的——
        // 节点本来就被 clickable 合并掉了，用合并树查什么都查不到，关不关设置都会绿。
        composeRule.onNodeWithTag("global_search_history_count_ability", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun resultsKeepOneRowPerWordBookAndShowContext() {
        composeRule.setContent {
            GlobalVocabularySearchScreen(
                state = GlobalVocabularySearchUiState.Ready(
                    history = emptyList(),
                    results = listOf(
                        result("cet4", "四级词书", "ability", "能力", count = 4),
                        result("ielts", "雅思词书", "ability", "能力，才能", count = 4),
                    ),
                ),
                query = "ability",
                onQueryChange = {},
                onSubmit = {},
                onRetry = {},
                onClearHistory = {},
                onOpenHistory = {},
                onSelect = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("找到 2 个结果").assertExists()
        composeRule.onNodeWithTag("global_search_result_book_cet4:cet4:ability", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("global_search_result_book_ielts:ielts:ability", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("来源：四级词书").assertExists()
        composeRule.onNodeWithText("来源：雅思词书").assertExists()
        composeRule.onNodeWithTag("global_search_result_count_cet4:cet4:ability", useUnmergedTree = true).assertExists()
    }

    @Test
    fun failureOffersRetry() {
        var retries = 0
        composeRule.setContent {
            GlobalVocabularySearchScreen(
                state = GlobalVocabularySearchUiState.Failure,
                query = "ability",
                onQueryChange = {},
                onSubmit = {},
                onRetry = { retries++ },
                onClearHistory = {},
                onOpenHistory = {},
                onSelect = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("搜索暂时不可用，请重试").assertExists()
        composeRule.onNodeWithTag("global_search_retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun submittingUsesTheTypedQuery() {
        var submitted: String? = null
        composeRule.setContent {
            var query by remember { mutableStateOf("") }
            GlobalVocabularySearchScreen(
                state = GlobalVocabularySearchUiState.Idle,
                query = query,
                onQueryChange = { query = it },
                onSubmit = { submitted = query },
                onRetry = {},
                onClearHistory = {},
                onOpenHistory = {},
                onSelect = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("global_search_input").performTextInput("ability")
        composeRule.onNodeWithTag("global_search_submit").performClick()

        assertEquals("ability", submitted)
    }

    @Test
    fun tappingHistoryItemReopensThatQuery() {
        var opened: String? = null
        composeRule.setContent {
            GlobalVocabularySearchScreen(
                state = GlobalVocabularySearchUiState.Ready(
                    history = listOf(GlobalVocabularySearchHistoryItem(history("ability", 3), null)),
                    results = emptyList(),
                ),
                query = "",
                onQueryChange = {},
                onSubmit = {},
                onRetry = {},
                onClearHistory = {},
                onOpenHistory = { opened = it },
                onSelect = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("global_search_history_ability").performClick()

        assertEquals("ability", opened)
    }

    @Test
    fun tappingResultOpensThatCardWithItsWordBook() {
        var selected: SearchVocabularyResult? = null
        composeRule.setContent {
            GlobalVocabularySearchScreen(
                state = GlobalVocabularySearchUiState.Ready(
                    history = emptyList(),
                    results = listOf(result("cet4", "四级词书", "ability", "能力", count = 1)),
                ),
                query = "ability",
                onQueryChange = {},
                onSubmit = {},
                onRetry = {},
                onClearHistory = {},
                onOpenHistory = {},
                onSelect = { selected = it },
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("global_search_result_cet4:cet4:ability").performClick()

        assertEquals("cet4:ability", selected?.cardId)
        assertEquals("四级词书", selected?.wordBookName)
    }

    /**
     * 点开的那条结果读不到词卡（索引残留了已删除词书 / 词书包损坏）时：
     * 只加一条提示，**不能**把整份结果列表换成失败态——搜索本身是好的，
     * 因为一条点不开就丢掉全部结果，比不提示更糟。
     */
    @Test
    fun openFailureIsAnnouncedWithoutDroppingTheResults() {
        composeRule.setContent {
            GlobalVocabularySearchScreen(
                state = GlobalVocabularySearchUiState.Ready(
                    history = emptyList(),
                    results = listOf(result("cet4", "四级词书", "ability", "能力", count = 1)),
                    openFailed = true,
                ),
                query = "ability",
                onQueryChange = {},
                onSubmit = {},
                onRetry = {},
                onClearHistory = {},
                onOpenHistory = {},
                onSelect = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("global_search_open_failed").assertExists()
        // 结果还在：正向对照，证明提示不是「用失败态换掉了列表」。
        composeRule.onNodeWithTag("global_search_result_cet4:cet4:ability").assertExists()
        composeRule.onNodeWithText("找到 1 个结果").assertExists()
    }

    /**
     * 这个页面有两个入口：主页查词、阅读页点未知词。从阅读页进来时，返回要回到**文章**，
     * 所以按钮必须说「返回上一层」——说「返回首页」就是在指错方向。
     */
    @Test
    fun backPointerSaysUpOneLevel() {
        composeRule.setContent {
            GlobalVocabularySearchScreen(
                state = GlobalVocabularySearchUiState.Idle,
                query = "",
                onQueryChange = {},
                onSubmit = {},
                onRetry = {},
                onClearHistory = {},
                onOpenHistory = {},
                onSelect = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("← 返回上一层").assertExists()
        composeRule.onNodeWithContentDescription("返回上一层").assertExists()
    }

    /**
     * 「旧文案不再出现」单独成一条，**不和上面那条放在一起**：
     * 变异验证（2026-10-05）实证过，把两条放进同一个测试时，前一条断言会先中断用例，
     * 后面这条哨兵根本不会执行——看着有断言，实际从没跑过。
     *
     * 用 substring 匹配：全等断言对「在新文案后面又拼一句旧的」恰好免疫。
     */
    @Test
    fun backPointerNeverPointsAtTheAppHome() {
        composeRule.setContent {
            GlobalVocabularySearchScreen(
                state = GlobalVocabularySearchUiState.Idle,
                query = "",
                onQueryChange = {},
                onSubmit = {},
                onRetry = {},
                onClearHistory = {},
                onOpenHistory = {},
                onSelect = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("返回首页", substring = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("返回首页").assertDoesNotExist()
    }

    private fun result(bookId: String, bookName: String, lemma: String, meaning: String, count: Int) =
        GlobalVocabularySearchResultItem(
            result = SearchVocabularyResult(
                wordBookId = bookId,
                cardId = "$bookId:$lemma",
                wordBookName = bookName,
                lemma = lemma,
                ipa = "/əˈbɪləti/",
                meaningZh = meaning,
            ),
            searchCount = count,
            searchCountForDisplay = count,
        )

    private fun history(query: String, count: Int) = VocabularySearchHistory(
        profileId = "default",
        normalizedQuery = query,
        displayQuery = query,
        searchCount = count,
        firstSearchedAt = now,
        lastSearchedAt = now,
        representative = null,
    )
}
