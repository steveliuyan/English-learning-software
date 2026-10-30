package com.example.englishlearning.ui

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.WordBookProgress
import com.example.englishlearning.ui.components.IllustratedIconTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 学习页（2026-10-25 版式重做 §3.2）的 connected 判据。
 *
 * 本文件随「搜索 / 强档卡 / 今日文章 / 词库」四段式重写：旧版把八枚入口平铺在 Ready 分支里，
 * 本次只留裸行，其余入口迁去词库页与我的页。批 1 Task 16 按原型 `app.js:342` 在裸行组首位
 * 补进「今日词单」（新建今日词单屏在本屏的唯一入口），本文件的裸行枚数与可点闭合集合随
 * `rowTags` 现算，不抄绝对数。因此这里**下线**的用例（`ready_offers_check_in_entry`
 * / `ready_offers_a_labelled_word_book_words_entry` / `ready_offers_setup_entry_to_reopen_word_book_settings`
 * / `progress_card_uses_neutral_surface_with_quiet_ink` / `available_actions_keep_one_mint_gradient_and_quiet_supporting_actions`
 * / `locked_reading_action_is_neutral_and_not_clickable` / `ready_groups_entry_actions_and_keeps_one_primary_cta`）
 * 都是**功能与锚点一起离场**，不是把断言改松：入口的存在性由 `TodayPlanBareRowContractTest` 反向钉住，
 * 迁走的目的地由各屏自己的用例接管。
 *
 * 沿用本仓既有约束：一台设备一个用例只 `setContent` 一次（同一次测试里第二次 `setContent`
 * 会让测试宿主崩），所以「缺配置 / 不可用」两态各拆一条用例。
 */
@RunWith(AndroidJUnit4::class)
class TodayPlanScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun loading_has_stable_tag() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Loading) }
        composeRule.onNodeWithTag("today_plan_screen").assertExists()
        composeRule.onNodeWithTag("today_plan_loading").assertExists()
    }

    @Test fun missing_setup_offers_clickable_setup() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.MissingSetup) }
        composeRule.onNodeWithTag("today_plan_missing_setup").assertExists()
        composeRule.onNodeWithTag("today_plan_open_setup").assertHasClickAction()
        composeRule.onNodeWithContentDescription("去设置词书").assertExists()
    }

    @Test fun unavailable_offers_clickable_retry() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Unavailable) }
        composeRule.onNodeWithTag("today_plan_unavailable").assertExists()
        composeRule.onNodeWithTag("today_plan_retry").assertHasClickAction()
        composeRule.onNodeWithContentDescription("重试").assertExists()
    }

    /**
     * F3（修复轮 1 / 评审 I-1）：查词入口必须**四态常驻**。它现在长在 `when (state)` 之前，
     * 所以缺配置 / 不可用两态下也在——`AppTab` 四枚里没有查词 tab，收进 Ready 分支就等于
     * 计划读不出来时全应用没有查词出口，与 `AGENTS.md`「无网络或无 AI Key 时……离线查词仍应可用」相抵。
     */
    @Test fun search_entry_is_reachable_in_non_ready_state_missing_setup() {
        var opened = 0
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.MissingSetup, onOpenSearch = { opened++ }) }
        composeRule.onNodeWithTag("today_plan_search_entry")
            .assertExists()
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
        composeRule.onNodeWithContentDescription("搜索单词或短语").assertExists()
    }

    /** F3 的另一半：计划读取失败（`Unavailable`）时「查一个词」这条出路仍然在。 */
    @Test fun search_entry_is_reachable_in_non_ready_state_unavailable() {
        var opened = 0
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Unavailable, onOpenSearch = { opened++ }) }
        composeRule.onNodeWithTag("today_plan_search_entry")
            .assertExists()
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
        composeRule.onNodeWithContentDescription("搜索单词或短语").assertExists()
    }

    @Test fun ready_renders_task_counts_and_start_learning_entry() {
        var started = 0
        composeRule.setContent {
            TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5), onStartLearning = { started++ })
        }
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
        // 原型对齐批 1：两个计数从「一行新学 x/y · 复习 x/y」换成原型 `.plan-legend` 的两枚键
        // （`assets/app.js:328-331`），权威数仍是 `today_plan_total_progress` 那一枚 PlanNum。
        composeRule.onNodeWithTag("today_plan_new_legend").assertTextEquals("新学 0 / 2")
        composeRule.onNodeWithTag("today_plan_due_legend").assertTextEquals("复习 0 / 3")
        composeRule.onNodeWithTag("today_plan_total_progress").assertTextEquals("0")
        composeRule.onNodeWithTag("today_plan_remaining").assertTextEquals("还剩 5 个词")
        composeRule.onNodeWithTag("today_plan_new_count").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_due_count").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("今日新增 2 词").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("今日复习 3 词").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_progress").assertExists()
        composeRule.onNodeWithTag("today_plan_start_learning")
            .performScrollTo()
            .assertTextEquals("开始学习")
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, started)
    }

    /** 页头行尾的齿轮（原型 `.icon-btn` + `data-to="me-settings"`）：可点、带无障碍名、落到「我的」。 */
    @Test fun header_gear_opens_settings() {
        var opened = 0
        composeRule.setContent {
            TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5), onOpenSettings = { opened++ })
        }
        composeRule.onNodeWithTag("today_plan_settings_action")
            .assertExists()
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
        composeRule.onNodeWithContentDescription("设置").assertExists()
    }

    /** 「计划」次按钮（原型 `.plan-head` 下那枚 secondary）与主 CTA 同屏，各走各的回调。 */
    @Test fun secondary_plan_button_opens_the_plan_editor() {
        var planned = 0
        var started = 0
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5),
                onOpenSetup = { planned++ },
                onStartLearning = { started++ },
            )
        }
        composeRule.onNodeWithTag("today_plan_open_plan")
            .performScrollTo()
            .assertTextEquals("计划")
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, planned)
        assertEquals(0, started)
    }

    @Test fun ready_with_no_tasks_renders_empty_state() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 0, 0, 0)) }
        composeRule.onNodeWithTag("today_plan_empty").assertExists()
        composeRule.onNodeWithTag("today_plan_plan_footnote").assertDoesNotExist()
    }

    /**
     * 未解锁：说明落在**裸行的副标题 + 强档卡那一行注脚**，不再是一枚禁用的整宽按钮
     * （旧的 `today_plan_open_reading` M3 `Button` 与它那枚 `#E5E5EA` 灰一并退役）。
     * 行本身仍可点——点它去阅读 tab，由阅读页自己解释解锁条件，这比一枚点不动的死按钮更有出路。
     */
    @Test fun locked_reading_row_says_what_is_missing() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready(
                    "小学", "2026-09-19", 2, 3, 5,
                    newDone = 1, dueDone = 2, isUnlocked = false,
                    unlockReason = "完成新词与复习后解锁文章",
                ),
            )
        }
        composeRule.onNodeWithTag("today_plan_reading_status")
            .performScrollTo()
            .assertExists()
            .assertHasClickAction()
        composeRule.onAllNodesWithText("今日文章 · 未解锁").assertCountEquals(1)
        composeRule.onAllNodesWithText("完成今日计划后解锁").assertCountEquals(1)
        composeRule.onAllNodesWithText("完成新词与复习后解锁文章").assertCountEquals(1)
        // 旧锚点整体离场：同一条信息不再出现第二遍。
        composeRule.onNodeWithTag("today_plan_open_reading").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_unlock_status").assertDoesNotExist()
    }

    @Test fun unlocked_reading_row_offers_the_article() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready(
                    "小学", "2026-09-19", 2, 3, 5,
                    newDone = 1, dueDone = 2, isUnlocked = true,
                ),
            )
        }
        composeRule.onAllNodesWithText("今日文章 · 可阅读").assertCountEquals(1)
        composeRule.onAllNodesWithText("去读今天生成的短文").assertCountEquals(1)
        composeRule.onAllNodesWithText("今日已解锁文章").assertCountEquals(1)
    }

    /** D2 + 重做：整册进度从旧的三段式（百分比 / 词数 / 未学）收进词库行的标题一句话。 */
    @Test fun ready_offers_a_word_bank_row_with_whole_book_progress() {
        var opened = 0
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready(
                    "高等职业教育专科英语", "2026-10-03", 10, 12, 22,
                    bookProgress = WordBookProgress(learned = 2680, total = 3039),
                ),
                onOpenWordBank = { opened++ },
            )
        }
        composeRule.onAllNodesWithText("高等职业教育专科英语 · 已学 2680 / 3039").assertCountEquals(1)
        composeRule.onNodeWithTag("today_plan_open_word_bank")
            .performScrollTo()
            .assertExists()
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
        // 旧的三段式进度件退役，不许以「百分比 + 进度条」的第二形回来。
        composeRule.onNodeWithTag("today_plan_learned_percent").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_book_progress").assertDoesNotExist()
    }

    /** 还没算出行时不能画成 0%：整句「已学 …」整体不出现，而不是给一个假进度。 */
    @Test fun word_bank_row_hides_progress_when_it_is_unknown() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        composeRule.onAllNodesWithText("已学", substring = true).assertCountEquals(0)
        // 改期望值（批 1 Task 4）：旧判据是「全屏『小学』恰 1 处」。四段式重排后词书名在
        // **两处**合法出现——强档卡的词书标题 + 词库行的行标题（`TodayPlanScreen.kt` 的
        // `today_plan_open_word_bank`），所以全屏计数会误红。判据强度没降：
        // 「已学」零处这半条原样保留（假进度仍当场红），并改为**点名词库行节点**必须带出词书名。
        composeRule.onNodeWithTag("today_plan_open_word_bank")
            .performScrollTo()
            .assertTextContains("小学")
    }

    /** 主 CTA 两态同锚点：新词学完但仍欠复习 →「开始复习」，仍走学习入口。 */
    @Test fun cta_switches_to_review_when_new_words_are_done() {
        var started = 0
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5, newDone = 2),
                onStartLearning = { started++ },
            )
        }
        composeRule.onNodeWithTag("today_plan_start_learning")
            .performScrollTo()
            .assertTextEquals("开始复习")
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, started)
    }

    /**
     * 计划全做完 → 换成原型的空态（`sLearnEmpty`，`assets/app.js:501-518`）。
     *
     * 这是本屏的**换态**，不是新增一屏：强档卡、进度条、权威数字、「接下来」节标题与四条裸行
     * 在那一态整段不进组合，`.scr-body` 只剩 `emptyState`。两枚出口的落点也在这里钉：
     * 「调整每日新增」→ 既有设置通路、「去读今日文章」→ 既有阅读通路。
     */
    @Test fun all_done_state_swaps_in_the_prototype_empty_state_with_two_exits() {
        var openedReading = 0
        var openedSetup = 0
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5, newDone = 2, dueDone = 3),
                onOpenReading = { openedReading++ },
                onOpenSetup = { openedSetup++ },
            )
        }
        // 换态：空态整块在，未学完那一支的那几件都不在。
        composeRule.onNodeWithTag("today_plan_all_done_state").assertExists()
        composeRule.onNodeWithTag("today_plan_summary").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_plan_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_total_progress").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_secondary_card").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_start_learning").assertDoesNotExist()
        listOf(
            "today_plan_open_word_list",
            "today_plan_open_review_queue",
            "today_plan_reading_status",
            "today_plan_open_word_bank",
        ).forEach { composeRule.onNodeWithTag(it).assertDoesNotExist() }
        composeRule.onAllNodesWithText("今天的词都学完了").assertCountEquals(1)
        // 说明句的两枚数量取**快照目标数**（新学 2 / 复习 3），不是原型 mock 的「4 个 / 2 个」。
        composeRule.onAllNodesWithText(
            "新学 2 个、复习 3 个都已完成。想多学可以调整每日新增，或者读一篇今天生成的文章。",
        ).assertCountEquals(1)
        // 原型那一屏没有第三枚出口：做了才出现的「复习生词本」不复存在。
        composeRule.onAllNodesWithText("复习生词本").assertCountEquals(0)
        // 空态只许一枚品牌渐变：主出口是 primary（`.btn-primary`），次出口走 `PillStyle.Secondary` 的
        // surface 底 + 描边（`.btn-secondary`）。两枚都长成渐变就等于同屏两个「主操作」。
        assertRenderedColor("today_plan_adjust_goal", 0xFFA8F3C8.toInt())
        assertNoRenderedColor("today_plan_read_article", 0xFFA8F3C8.toInt())

        composeRule.onNodeWithTag("today_plan_adjust_goal")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, openedSetup)
        assertEquals(0, openedReading)

        composeRule.onNodeWithTag("today_plan_read_article")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, openedReading)
    }

    /**
     * 空态的可点节点**闭合集合**：常驻查词 + 两枚出口 = 3，且页头齿轮在空态撤掉
     * （原型 `head({plain:true})` 没有 action 位，`assets/app.js:503`）。
     */
    @Test fun all_done_state_keeps_a_closed_clickable_set_and_no_gear() {
        composeRule.setContent {
            TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5, newDone = 2, dueDone = 3))
        }
        composeRule.onNodeWithTag("today_plan_settings_action").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_search_entry").assertHasClickAction()
        composeRule.onNodeWithTag("today_plan_adjust_goal").assertHasClickAction()
        composeRule.onNodeWithTag("today_plan_read_article").assertHasClickAction()
        assertEquals(
            "空态可点节点必须是 1 常驻查词 + 2 出口 = 3 枚：多一枚（齿轮回来了 / 新长出第三枚出口）" +
                "少一枚（某条出口丢了）都红",
            3,
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size,
        )
    }

    /** 空态页头按原型 `.head-plain`：副标题只剩「连续打卡 N 天」，「今日目标」那一句随强卡一起离场。 */
    @Test fun all_done_state_keeps_only_the_streak_in_the_plain_head() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5, newDone = 2, dueDone = 3),
                streakDays = 12,
            )
        }
        composeRule.onAllNodesWithText("连续打卡 12 天").assertCountEquals(1)
        composeRule.onAllNodesWithText("今日目标", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("today_plan_settings_action").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_all_done_state").assertExists()
        composeRule.onNodeWithTag("today_plan_empty").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_progress").assertDoesNotExist()
    }

    /** 没有连续天数记录时不摆「连续 0 天」：空态页头连副标题一起不出现。 */
    @Test fun all_done_state_without_streak_hides_the_subtitle() {
        composeRule.setContent {
            TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5, newDone = 2, dueDone = 3))
        }
        composeRule.onAllNodesWithText("连续打卡", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("today_plan_all_done_state").assertExists()
        composeRule.onNodeWithTag("today_plan_settings_action").assertDoesNotExist()
    }

    /** 未学完那一态仍挂齿轮、仍说「今日目标」：空态撤齿轮不是把页头整体改掉。 */
    @Test fun not_done_state_still_shows_the_gear_and_the_goal() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5, newDone = 1, dueDone = 1),
                streakDays = 12,
            )
        }
        composeRule.onNodeWithTag("today_plan_settings_action").assertExists()
        composeRule.onAllNodesWithText("连续打卡 12 天 · 今日目标 5 个词").assertCountEquals(1)
    }

    /** 原型图例两段各带自己的分子分母（`.plan-key` 的「新学 a / b」「复习 a / b」）。 */
    @Test fun ready_renders_the_two_legend_keys_with_their_own_counts() {
        composeRule.setContent {
            TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5, newDone = 1, dueDone = 2))
        }
        composeRule.onNodeWithTag("today_plan_new_legend").performScrollTo().assertTextEquals("新学 1 / 2")
        composeRule.onNodeWithTag("today_plan_due_legend").performScrollTo().assertTextEquals("复习 2 / 3")
        composeRule.onNodeWithTag("today_plan_plan_bar").performScrollTo().assertExists()
        // 旧的两条 `ThinProgress` 随原型「一屏一根进度条」下线，不许以第二根细条回来。
        composeRule.onNodeWithTag("today_plan_new_progress").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_review_progress").assertDoesNotExist()
    }

    /**
     * 入口组的离场：`today_plan_entry_group` 连同「学习工具与设置」（目的地与底部导航重复）
     * 以及五枚迁走的入口锚一起下线。判决见 `docs/decisions/2026-10-25-ui-simplification-deletions.md`
     * 与本轮版式重做 §3.2；源码侧由 `TodayPlanBareRowContractTest` 双钉，这里是界面侧的同一口径。
     */
    @Test fun ready_no_longer_offers_a_learning_tools_entry() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        composeRule.onNodeWithTag("today_plan_entry_group").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_learning_tools").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_open_check_in").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_open_setup_entry").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("学习工具与设置").assertDoesNotExist()
    }

    /**
     * 阅读状态行排在主 CTA 之后（信息架构：先「学什么」，再「顺带读」）。
     *
     * 为什么用 `positionInRoot` + `size` 而不是 `boundsInRoot.bottom/top`：`boundsInRoot` 是**裁剪后**的矩形，
     * 这个测试宿主的根窗口只有 `2250×946`px（≈818×344dp，真机像素密度的 946px 高），今日计划屏整卷比它高，
     * 因此卡片里的 CTA 与它下面那行都落在视口外，两个 `boundsInRoot` 都被裁成 `Rect(0,0,0,0)`——
     * 于是 `0 > 0` 恒假，判据测的成了窗口高度而不是文档顺序。`positionInRoot` 报的是布局位置（不裁剪），
     * 用它算「前一块的下沿」与「后一块的上沿」才是这条判据本来要量的东西，且仍要求**严格**先后（`>`，不是 `>=`）。
     */
    @Test fun reading_is_after_primary_learning_action() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        val primary = composeRule.onNodeWithTag("today_plan_start_learning").fetchSemanticsNode()
        val reading = composeRule.onNodeWithTag("today_plan_reading_status").fetchSemanticsNode()
        val primaryBottom = primary.positionInRoot.y + primary.size.height
        val readingTop = reading.positionInRoot.y
        assertTrue(
            "reading should follow the primary learning action（CTA 下沿=$primaryBottom，" +
                "阅读行上沿=$readingTop）",
            readingTop > primaryBottom,
        )
    }

    /**
     * 强档卡的分套层次（2026-10-31 用户评审 ③：「四级词汇 · 开始学习」那块深墨实底太重、喧宾夺主）。
     *
     * 浅套底从 `strongSurface`（`#1F4739`）改成 `tint2`（浅薄荷一档），前景随之从 `onStrong`
     * 换成 `text1`；深套一字不动（`#22332C` 压深背景只差一档明度，深底浅字本来就是对的）。
     * 判据仍是三条合取、温度不变：底色在、前景色在、这一对过 4.5——外加一条反向对偶：
     * 这张卡的**底**不许再是旧深墨 `#1F4739`（只判前三条会被「上下两块底色都画了」这种半迁移放过）。
     *
     * 两枚期望字面量取的是 **token 逐值**而不是文档里的抄录：浅套 `tint2 = #DCEBE1`
     * （`AppColors.kt:72`）与 `text1 = #15211C`（`:76`）。旧版写的是 `#E4EFE9` / `#1F2B26`
     * ——`WordbookPickerHeaderTest.kt:38` 已经登记过同一类漂移（「brief 与旧注释里抄成 `#E5EFE9` /
     * `0xFFE4EFE9` 都是抄录漂移，取 token 才不会被带偏」），它们靠 12.0 与 17.3 的欧氏距离勉强落在阈值 18
     * 之内，等于把判据泡在余量里。改成逐值后同样的阈值只给渲染取整留位置。
     *
     * 反向对偶为什么**不能**用 [assertNoRenderedColor] 整卡扫：卡片里嵌着主 CTA（`today_plan_start_learning`），
     * 它的墨是 `onBrand` `#04241C`（品牌渐变键的深墨），文字抗锯齿把 `#04241C` 与薄荷渐变混出 `#19483A` 一档，
     * 这枚**合成色**与 `#1F4739` 的欧氏距离只有 6.2（阈值 18.0）——整卡禁色会把合法的 CTA 字形边缘
     * 判成「旧底色回退」，量的是渲染伪影而不是版式。所以反向对偶改成 [assertCardFillBands]：
     * 只在只由底色构成、且落在原型装饰光晕盘外的三点上取色（理由见该函数 KDoc）。
     */
    @Test fun ready_strong_card_paints_the_light_tone_and_the_container_ink() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        assertRenderedColor("today_plan_summary", 0xFFDCEBE1.toInt())
        assertRenderedColor("today_plan_total_progress", 0xFF15211C.toInt())
        assertTrue(
            "text1 压 tint2 必须过 4.5:1（浅套强档卡前景的分套墨）",
            contrastRatio(0xFF15211C.toInt(), 0xFFDCEBE1.toInt()) >= 4.5,
        )
        assertCardFillBands("today_plan_summary", 0xFFDCEBE1.toInt(), 0xFF1F4739.toInt())
    }

    /**
     * 品牌渐变只剩主 CTA 一处。
     *
     * 旧值 `#174C44`（`onMint`）随 M3 `Button` 退役：主 CTA 现在是 `PillButton` 的 Primary + AiSpeech 档，
     * 墨是主题无关的 `onBrand` = `#04241C`（纯表见 `GlassButton.kt` 的 `pillButtonInk`，两套同值）。
     * 三端渐变 = `#A8F3C8 → #5DDFB4 → #65D3B9`（`MintPalette.kt`）。这是**期望值随形改**，不是放宽：
     * 判据仍是「两端色都在、墨都在、对比度都过线」，并追加「软档卡里一个端点色都不许有」。
     */
    @Test fun the_primary_cta_keeps_the_only_brand_gradient() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        listOf(0xFFA8F3C8, 0xFF5DDFB4, 0xFF65D3B9).forEach { stop ->
            assertRenderedColor("today_plan_start_learning", stop.toInt())
            assertTrue(
                "品牌渐变端 $stop 压 onBrand 必须过 4.5:1",
                contrastRatio(stop.toInt(), 0xFF04241C.toInt()) >= 4.5,
            )
        }
        assertRenderedColor("today_plan_start_learning", 0xFF04241C.toInt())
        // 软档卡（词单 / 复习队列 / 今日文章 / 词库四条裸行）里不得出现品牌渐变的两个可辨认端点。
        assertNoRenderedColor("today_plan_secondary_card", 0xFFA8F3C8.toInt())
        assertNoRenderedColor("today_plan_secondary_card", 0xFF65D3B9.toInt())
    }

    /**
     * 四条裸行各自带一枚**插画**徽标，外卡是原型 `.card-soft` 的 `tint` 薄荷底（判据随形改，不放宽）。
     *
     * 旧断言取「行内徽标那层域 `deep` 矢量墨」（今日文章 = `Reading #9C3B4F`、词库 = `Library #65478A`）。
     * 插画档上线后，行内徽标走 `InlineIconBadge` 的插画分支：颜色写在母版位图里（**母版定主域**，
     * 例如词库那枚是蓝皮合书，不是 Library 的紫），域色只留给兜底的 5 枚豁免字形，旧断言必然判红
     * （实测最近距离 49.6）。设备侧因此改判两件可执行的事：每行都必须真的挂着插画位图（证明插画分支
     * 走通、没有静默退回 24×24 线框），且外层软卡仍是**中性冷底**（「域色只出现在字形上、不铺成色块」不变）。
     *
     * 批 1 Task 16 起名单多一枚 `today_plan_open_word_list`（原型 `app.js:342` 的贴纸 `list`，
     * 插画在 `AppIllustrations.kt:56` → `R.drawable.app_icon_list`）；Task 17 再起一枚
     * `today_plan_open_review_queue`（原型 `app.js:343` 的贴纸 `clock` → `R.drawable.app_icon_clock`，
     * `AppIllustrations.kt:45`）。
     *
     * **外卡期望色在批 0 之后从白改成 `tint`**（本轮统一验收前发现的两处设备侧陈旧期望之一）：
     * `39e826a`（共享组件与令牌对齐冻结原型）把 `SoftCard` 的填充按原型
     * `.card-soft { background: var(--tint) }`（`app.css:369-374`）从 `surface` 改成 `tint`
     * （浅套 `#E8F2EA`），2026-10-30 位图档那句「外卡仍是中性白」量的是改令牌之前的版面。
     * 这条改判不是放宽：它原来只能证「白」，现在只能证「原型那一档薄荷」，两者都是**单点期望**；
     * 「域色不铺成色块」那层立法由 `the_primary_cta_keeps_the_only_brand_gradient` 的反向扫禁
     * （软卡里禁 `#A8F3C8` / `#65D3B9` 两枚渐变端点）继续承担。
     */
    @Test fun the_four_bare_rows_carry_illustrated_badges_on_the_prototype_soft_tint_card() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        listOf(
            "today_plan_open_word_list",
            "today_plan_open_review_queue",
            "today_plan_reading_status",
            "today_plan_open_word_bank",
        ).forEach { tag ->
            composeRule.onNode(
                hasTestTag(tag) and hasAnyDescendant(hasTestTag(IllustratedIconTag)),
                useUnmergedTree = true,
            ).assertExists()
        }
        assertRenderedColor("today_plan_secondary_card", 0xFFE8F2EA.toInt())
    }

    /**
     * AC-R-20 的像素侧：今日数字只留**一处**权威尺寸。
     *
     * 原型对齐批 1 起权威数是 `AppType.PlanNum`（32sp serif，原型 `.plan-count b`），
     * 其余数字（还剩 N 个词、卡外注脚）只能是它的注脚。语义节点里没有 FontSize 属性，
     * 所以改从 `SemanticsActions.GetTextLayoutResult` 取**解析后的字号**复算：
     *  | 档 | sp | 对 PlanNum（32sp）的字号比 |
     *  |---|---|---|
     *  | `PlanNum`（今日完成数） | 32 | 1.000（参照） |
     *  | `Footnote`（还剩 / 注脚 / 图例） | 13 | 0.406 |
     *  | `Body` | 15 | 0.469 |
     *  | `Title` | 17 | 0.531 |
     *  | `Display` | 46 | 1.438 |
     *
     * 阈值取 **0.43**：相对 Footnote 的 0.406 留 0.024 余量（字体度量 / 取整抖动），
     * 又相对 Body 的 0.469 留 0.039 的判红空间——「把注脚升到 Body/Title/Headline」都会当场红。
     * 覆盖声明：本条兜住 ≥ Body（15sp）的所有升档；`AppType.PlanNum` 全屏恰一枚、其余大档归零
     * 由 `TodayPlanNumberAuthorityTest` 的源码读数负责（本条只采样固定标签）。
     *
     * ## 为什么不再按「渲染高度」算（2026-10-26 真机回归）
     *
     * 旧版用 `boundsInRoot.height` 当字号比的代理，前提是「两档行高与字号成正比（同一字体）」。
     * 这个前提在真机上不成立：参照文本是数字（Roboto 数字，垂直度量约 1.17×字号），
     * 被采样的注脚是 CJK 混排（约 1.43×字号），两者字体族不同。
     * 改为直接读解析字号后，比值回到纯字号比，判据比原来更严（不再有字体度量抖动余量可吃）。
     */
    @Test fun ready_presents_todays_numbers_with_one_display_authority() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready(
                    "高等职业教育专科英语", "2026-10-03", 2, 3, 5,
                    newDone = 1, dueDone = 1,
                    bookProgress = WordBookProgress(learned = 2680, total = 3039),
                ),
            )
        }
        composeRule.onNodeWithTag("today_plan_total_progress").performScrollTo().assertTextEquals("2")
        val displaySize = resolvedFontSizeSp("today_plan_total_progress")
        assertTrue("权威数字解析字号为 ${displaySize}sp，判据失去参照（本用例空转）", displaySize > 0f)
        listOf(
            "today_plan_remaining",
            "today_plan_progress",
        ).forEach { tag ->
            val size = resolvedFontSizeSp(tag)
            val ratio = size / displaySize
            assertTrue(
                "$tag 的解析字号 ${size}sp 对权威数字 ${displaySize}sp 的比值=$ratio，" +
                    "超过注脚上限阈值 $MaxFootnoteToDisplayRatio——它没降到注脚档" +
                    "（字号比：Footnote 13/32=0.406、Body 15/32=0.469、Title 17/32=0.531）",
                ratio <= MaxFootnoteToDisplayRatio,
            )
        }
    }

    /**
     * 版面侧：可点目标的**闭合集合** + 四条裸行的触控水线。
     *
     * ① 闭合集合 = 1 枚主 CTA + 1 条常驻查词 + `rowTags.size` 条裸行 + 卡内次按钮与页头齿轮。
     *    枚数由 `rowTags` 现算，注释不抄绝对数；增删入口必须**同时**改这条判据，并在本轮计划里留名说明理由。
     * ② 每条裸行 ≥ 48dp（F5-03），且软档卡高 = 各行之和（分组是真的，不是贴了个 tag 的空壳）。
     * ③ 查词条落在软档卡**之外**（F3：它四态常驻，跟着卡走就只在 Ready 态出现）。
     */
    @Test fun ready_keeps_a_closed_clickable_set_and_touch_sized_bare_rows() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        // 批 1 Task 17 起四条：词单 / 复习队列（原型 `app.js:343` 的 `to:'learn-review'`）/ 文章 / 词库。
        val rowTags = listOf(
            "today_plan_open_word_list",
            "today_plan_open_review_queue",
            "today_plan_reading_status",
            "today_plan_open_word_bank",
        )
        // 原型批 1：强卡底部是一对按钮（主 CTA「继续学习族」+ 次按钮「计划」），加上页头齿轮。
        val ctaTags = listOf("today_plan_start_learning", "today_plan_open_plan")
        val headerActionTag = "today_plan_settings_action"
        val persistentSearchTag = "today_plan_search_entry"
        val clickable = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertEquals(
            "本屏可点节点的闭合集合必须是 ${ctaTags.size + 2 + rowTags.size} = " +
                "${ctaTags.size} 枚卡内按钮 + 1 枚页头齿轮($headerActionTag) + 1 常驻查词($persistentSearchTag) + " +
                "${rowTags.size} 条裸行。这是原型对齐后的收成结果，多一枚（新的整宽按钮 / 第二枚主 CTA）少一枚" +
                "（把查词又收进卡里 / 删掉某条出口）都红；增删入口须同时改本条与计划文档。",
            ctaTags.size + 2 + rowTags.size,
            clickable.size,
        )

        val card = composeRule.onNodeWithTag("today_plan_secondary_card").performScrollTo()
        val cardBox = card.fetchSemanticsNode().boundsInRoot
        val minTouchPx = with(composeRule.density) { 48.dp.toPx() }
        var rowsHeight = 0f
        rowTags.forEach { tag ->
            val row = composeRule.onNodeWithTag(tag)
            row.performScrollTo().assertHasClickAction()
            val bounds = row.fetchSemanticsNode().boundsInRoot
            rowsHeight += bounds.height
            assertTrue("$tag 的行高 ${bounds.height}px 低于 48dp 触控下限（${minTouchPx}px）", bounds.height >= minTouchPx)
            assertTrue(
                "$tag 越出软档卡容器：行 $bounds / 卡 $cardBox",
                bounds.left >= cardBox.left - 1f && bounds.right <= cardBox.right + 1f &&
                    bounds.top >= cardBox.top - 1f && bounds.bottom <= cardBox.bottom + 1f,
            )
        }
        assertTrue(
            "软档卡高 ${cardBox.height}px 与 ${rowTags.size} 条行之和 ${rowsHeight}px 对不上——" +
                "分组不是逐条相接的行（内边距 / 行间间距不得再叠一层）",
            kotlin.math.abs(cardBox.height - rowsHeight) <= with(composeRule.density) { 2.dp.toPx() },
        )

        val searchRow = composeRule.onNodeWithTag(persistentSearchTag)
        searchRow.performScrollTo().assertHasClickAction()
        val searchBounds = searchRow.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "$persistentSearchTag 被收进了软档卡（卡 $cardBox / 行 $searchBounds）——" +
                "查词入口必须四态常驻，收进卡里等于只在就绪态提供查词出口",
            !(searchBounds.top >= cardBox.top - 1f && searchBounds.bottom <= cardBox.bottom + 1f &&
                searchBounds.left >= cardBox.left - 1f && searchBounds.right <= cardBox.right + 1f),
        )
        assertTrue(
            "$persistentSearchTag 的行高 ${searchBounds.height}px 低于 48dp 触控下限（${minTouchPx}px）",
            searchBounds.height >= minTouchPx,
        )
    }

    /**
     * 读某个文本节点**解析后**的字号（单位 sp），不依赖渲染边界高度。
     *
     * 走 `SemanticsActions.GetTextLayoutResult`（compose-ui 1.10 的语义树里没有 FontSize 属性，
     * 但每个 `Text` 都会挂这条 action）。用它而不是 `boundsInRoot.height` 的原因见
     * [ready_presents_todays_numbers_with_one_display_authority] 的「为什么不再按渲染高度算」：
     * 不同字体族（Roboto 数字 vs CJK 混排）的垂直度量不同，渲染高度比不能当字号比用。
     */
    private fun resolvedFontSizeSp(tag: String): Float {
        val node = composeRule.onNodeWithTag(tag).performScrollTo()
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(
            "$tag 的 GetTextLayoutResult 产出 ${results.size} 份布局（期望恰 1 份），取不到解析字号",
            1,
            results.size,
        )
        return results.single().layoutInput.style.fontSize.value
    }

    private companion object {
        /**
         * 注脚**解析字号**对权威数字（`AppType.PlanNum` 32sp）的上限阈值。
         *
         * 字号比：Footnote 13/32=0.406（放行）、Body 15/32=0.469（判红）、Title 17/32=0.531（判红）。
         * 0.43 处在 Footnote 与 Body 之间，两侧各留约 0.03–0.04。
         * 取值理由与采样清单见 [ready_presents_todays_numbers_with_one_display_authority] 的 KDoc。
         */
        const val MaxFootnoteToDisplayRatio = 0.43f
    }

    private fun contrastRatio(a: Int, b: Int): Double {
        fun luminance(color: Int): Double {
            val channels = listOf(AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color))
                .map { it / 255.0 }
                .map { if (it <= 0.04045) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
            return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
        }
        val (lighter, darker) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertRenderedColor(tag: String, expectedColor: Int) {
        val bitmap = composeRule.onNodeWithTag(tag).performScrollTo().captureToImage().asAndroidBitmap()
        val nearest = (0 until bitmap.width).flatMap { x ->
            (0 until bitmap.height).map { y -> colorDistance(bitmap.getPixel(x, y), expectedColor) }
        }.minOrNull() ?: Double.POSITIVE_INFINITY
        assertTrue(
            "$tag should render near ${AndroidColor.red(expectedColor)},${AndroidColor.green(expectedColor)},${AndroidColor.blue(expectedColor)}; nearest distance=$nearest",
            nearest <= 18.0,
        )
    }

    /**
     * [assertRenderedColor] 的反向对偶：该颜色**不该**出现在这个节点的可见像素里。
     *
     * 与前者共用同一个 18.0 的欧氏距离阈值（判据必须对称，否则「缺席」会因为阈值不一致而恒真），
     * 报的是最近距离，方便改色时一眼看出差多远。
     */
    private fun assertNoRenderedColor(tag: String, forbiddenColor: Int) {
        val bitmap = composeRule.onNodeWithTag(tag).performScrollTo().captureToImage().asAndroidBitmap()
        val nearest = (0 until bitmap.width).flatMap { x ->
            (0 until bitmap.height).map { y -> colorDistance(bitmap.getPixel(x, y), forbiddenColor) }
        }.minOrNull() ?: Double.POSITIVE_INFINITY
        assertTrue(
            "$tag 里出现了 ${AndroidColor.red(forbiddenColor)},${AndroidColor.green(forbiddenColor)},${AndroidColor.blue(forbiddenColor)}" +
                "（最近距离=$nearest，阈值 18.0）",
            nearest > 18.0,
        )
    }

    /**
     * 强档卡「底」的三点取样判据：三点都必须是 [expectedFill]，且都不许接近 [forbiddenFill]。
     *
     * 三个点位都刻意落在**只由底色构成**的地方，避开一切文字、进度条、主 CTA 及其抗锯齿：
     *  - 上沿居中、距顶 5dp（在 20dp 圆角之内、18dp 内边距之内）；
     *  - 左内边带、垂直居中、距左 5dp；
     *  - 右内边带、垂直居中、距右 5dp。
     *
     * 阈值沿用全局 18.0 欧氏距离，与 [assertRenderedColor] 同温；报的是点位坐标与实际色，
     * 改色时一眼能看出哪条边带先漏。为什么要「取样点」而不是整卡扫，见
     * [ready_strong_card_paints_the_light_tone_and_the_container_ink] 的 KDoc。
     *
     * **点位必须落在光晕之外**（本轮统一验收前发现的第二处设备侧陈旧期望）：原型
     * `.strong-card::after` 是一枚 `radial-gradient(rgba(108,203,171,.3) → transparent 68%)`
     * 的装饰光斑（`app.css:392-402`），`StrongCard` 按它画了 `drawBehind` 的
     * `brand(30%)` 径向渐变（`Containers.kt:87-99`，圆心 `(宽-149dp, 3dp)`、半径 114dp）。
     * 旧点位里的「上沿居中」正落在亮核覆盖区（圆心 `宽-149dp, 3dp`、半径 114dp 的盘罩住整条上沿中段），
     * 实测取到的是底色与光晕合成后的 (197,228,214)，离当时那枚期望底 38.0（阈值 18.0）——那枚**合法的装饰层**
     * 被读成了「底色漏画」。三条点位因此全部搬到光晕盘外（左右内边带各自离圆心远超 114dp，
     * 判据的「底必须是这一档」一字未松）：左内边带上段 / 左内边带下段 / 右内边带中段，
     * 且都避开了 26dp 圆角（角弧只咬住两轴同时 5dp 的角落点）。三点的色一起报，任何一点偏了都能看见是哪条边带先漏。
     */
    private fun assertCardFillBands(tag: String, expectedFill: Int, forbiddenFill: Int) {
        val bitmap = composeRule.onNodeWithTag(tag).performScrollTo().captureToImage().asAndroidBitmap()
        val insetPx = with(composeRule.density) { 5.dp.roundToPx() }
        val midY = bitmap.height / 2
        val probes = listOf(
            "左内边带上段" to (insetPx to bitmap.height / 4),
            "左内边带下段" to (insetPx to bitmap.height * 3 / 4),
            "右内边带中段" to (bitmap.width - 1 - insetPx to midY),
        )
        val readings = probes.map { (label, point) ->
            val pixel = bitmap.getPixel(point.first, point.second)
            Triple(label, point, pixel)
        }
        val report = readings.joinToString("\n") { (label, point, pixel) ->
            "  $label x=${point.first} y=${point.second} 实际 (${AndroidColor.red(pixel)}," +
                "${AndroidColor.green(pixel)},${AndroidColor.blue(pixel)})" +
                " / 距底色=${colorDistance(pixel, expectedFill)}" +
                " / 距旧深墨=${colorDistance(pixel, forbiddenFill)}"
        }
        readings.forEach { (label, point, pixel) ->
            assertTrue(
                "$tag 的$label x=${point.first} y=${point.second} 应是底色" +
                    "(${AndroidColor.red(expectedFill)},${AndroidColor.green(expectedFill)},${AndroidColor.blue(expectedFill)})，" +
                    "阈值 18.0。三点读数：\n$report",
                colorDistance(pixel, expectedFill) <= 18.0,
            )
            assertTrue(
                "$tag 的$label 不许是旧深墨底" +
                    "(${AndroidColor.red(forbiddenFill)},${AndroidColor.green(forbiddenFill)},${AndroidColor.blue(forbiddenFill)})，" +
                    "阈值 18.0。三点读数：\n$report",
                colorDistance(pixel, forbiddenFill) > 18.0,
            )
        }
    }

    private fun colorDistance(actual: Int, expected: Int): Double {
        val dr = (AndroidColor.red(actual) - AndroidColor.red(expected)).toDouble()
        val dg = (AndroidColor.green(actual) - AndroidColor.green(expected)).toDouble()
        val db = (AndroidColor.blue(actual) - AndroidColor.blue(expected)).toDouble()
        return Math.sqrt(dr * dr + dg * dg + db * db)
    }
}
