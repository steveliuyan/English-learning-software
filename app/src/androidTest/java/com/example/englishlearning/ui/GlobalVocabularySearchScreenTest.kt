package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
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

    /**
     * 空输入不是死路：页面提示写着「空输入会显示最近搜索」，那这个动作就必须真的能触发。
     *
     * 旧实现把按钮 `enabled` 绑在「输入非空」上，于是用户清空输入后回不到最近搜索，
     * 只能退出页面从主页入口重进——文案承诺的能力在界面上够不着。
     *
     * 批 3 Task 5 起文案不再随输入切换（原先空输入读作「查看最近搜索」）：那一套把一枚按钮
     * 当成两个动作，用户读完按钮上的字再点、字已经变了。现在恒称「搜索」，能力不变——
     * 「空输入落回最近搜索」由宿主的 `onSubmit` 判定，按钮只负责提交。
     */
    @Test
    fun blank_query_keeps_the_submit_action_available_and_the_label_never_changes() {
        var submits = 0
        composeRule.setContent {
            GlobalVocabularySearchScreen(
                state = GlobalVocabularySearchUiState.Idle,
                query = "",
                onQueryChange = {},
                onSubmit = { submits++ },
                onRetry = {},
                onClearHistory = {},
                onOpenHistory = {},
                onSelect = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("global_search_submit").assertIsEnabled()
        composeRule.onNodeWithText("搜索").assertExists()
        composeRule.onNodeWithTag("global_search_submit").performClick()

        assertEquals(1, submits)
    }

    /** 非空输入不再是另一枚按钮：同一句「搜索」，空与非空都点得到同一个动作。 */
    @Test
    fun non_blank_query_keeps_the_same_submit_label() {
        composeRule.setContent {
            GlobalVocabularySearchScreen(
                state = GlobalVocabularySearchUiState.Idle,
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

        composeRule.onNodeWithText("搜索").assertExists()
    }

    /**
     * 旧的两套按钮文案一条不留：删旧文案必须配「旧文案必须为 0」的正向断言，
     * 否则「新文案后面又拼一句旧的」这种形状会静默通过。
     *
     * 单独成一条：和上一条写在同一个用例里时，前一条断言失败会先行中断，这条哨兵根本不会执行
     * （2026-10-05 变异验证实证）。substring 匹配防「新文案后面拼旧文案」。
     */
    @Test
    fun the_old_two_state_submit_labels_are_gone() {
        composeRule.setContent {
            GlobalVocabularySearchScreen(
                state = GlobalVocabularySearchUiState.Idle,
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

        composeRule.onNodeWithText("查看最近搜索", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("全量搜索", substring = true).assertDoesNotExist()
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
     * 这个页面有两个入口：主页查词、阅读页点未知词。返回位必须真有得点（`BackHandler` 之外还留一枚
     * 可点节点，否则平板/鼠标用户只能靠手势），且走的是全 App 统一的共享页头。
     *
     * 批 3 Task 5 起返回位不再是自写 `TextButton("← 返回上一层")`：共享页头的返回位固定
     * testTag `app_screen_header` + 可及名「返回」（`AppScreenHeader` 无参数化返回文案），
     * 所以这里按内容描述点，不再按「返回上一层」。
     */
    @Test
    fun backSlotIsTheSharedHeaderAndStillWorks() {
        var backs = 0
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
                onBack = { backs++ },
            )
        }

        composeRule.onNodeWithTag("app_screen_header").assertExists()
        composeRule.onNodeWithContentDescription("返回").performClick()

        assertEquals(1, backs)
    }

    /**
     * 旧返回文案一条不留：「← 返回上一层」这行字与它的可及名都随自写返回按钮退役；
     * 指错方向的「返回首页」一并留在这个哨兵里。
     *
     * 「旧文案不再出现」单独成一条，**不和上面那条放在一起**：
     * 变异验证（2026-10-05）实证过，把两条放进同一个测试时，前一条断言会先中断用例，
     * 后面这条哨兵根本不会执行——看着有断言，实际从没跑过。
     *
     * 用 substring 匹配：全等断言对「在新文案后面又拼一句旧的」恰好免疫。
     */
    @Test
    fun theOldBackCopyIsGone() {
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

        composeRule.onNodeWithText("← 返回上一层", substring = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("返回上一层").assertDoesNotExist()
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
