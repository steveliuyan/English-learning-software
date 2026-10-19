package com.example.englishlearning.ui

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import com.example.englishlearning.learning.WordBookProgress
import com.example.englishlearning.ui.theme.DomainColors
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
     * F3（修复轮 1 / 评审 I-1）：查词入口必须**四态常驻**。14B 把它收进了只在 Ready 分支渲染的
     * `TodayPlanEntryGroup`，于是缺配置 / 不可用两态下学习 tab 内没有任何查词出口
     * （`AppTab` 四枚里没有查词 tab），与 `AGENTS.md`「无网络或无 AI Key 时……离线查词仍应可用」相抵。
     *
     * 拆成两条 `@Test` 而不是在一条里渲染两个态：本仓 Compose connected 测试一台设备一个用例只
     * `setContent` 一次（同一次测试里第二次 `setContent` 会让测试宿主崩），这是既有约束。
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
        composeRule.onNodeWithTag("today_plan_new_count").assertExists()
        composeRule.onNodeWithContentDescription("今日新增 2 词").assertExists()
        composeRule.onNodeWithTag("today_plan_due_count").assertExists()
        composeRule.onNodeWithContentDescription("今日复习 3 词").assertExists()
        composeRule.onNodeWithTag("today_plan_task_total").assertExists()
        composeRule.onNodeWithTag("today_plan_start_learning").assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(1, started)
    }

    @Test fun ready_with_no_tasks_renders_empty_state() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 0, 0, 0)) }
        composeRule.onNodeWithTag("today_plan_empty").assertExists()
    }

    @Test fun ready_renders_total_progress_and_unlock_reason() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready(
                    "小学", "2026-09-19", 2, 3, 5,
                    newDone = 1, dueDone = 2, isUnlocked = false,
                    unlockReason = "完成新词与复习后解锁文章",
                ),
            )
        }
        composeRule.onNodeWithTag("today_plan_progress_summary").assertExists()
        composeRule.onNodeWithTag("today_plan_total_progress").assertExists()
        composeRule.onNodeWithTag("today_plan_unlock_status").assertExists()
        composeRule.onAllNodesWithText("今日计划共 5 项").assertCountEquals(0)
    }

    /**
     * 首页词书卡上的整册进度：左边百分比、右边「已学/总数 词」、下面一条进度条和「未学」。
     * 2680/3039 取截断后是 88.1%（四舍五入会给 88.2%，所以这条断言同时钉住了取整方式）。
     */
    @Test fun ready_summary_card_shows_word_book_progress() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready(
                    "高等职业教育专科英语", "2026-10-03", 10, 12, 22,
                    bookProgress = WordBookProgress(learned = 2680, total = 3039),
                ),
            )
        }
        composeRule.onNodeWithTag("today_plan_learned_percent").assertTextEquals("已学 88.1%")
        composeRule.onNodeWithTag("today_plan_book_words").assertTextEquals("2680/3039 词")
        composeRule.onNodeWithTag("today_plan_book_unlearned").assertTextEquals("未学 359 词")
        composeRule.onNodeWithTag("today_plan_book_progress").assertExists()
    }

    /** 还没算出行时不能画成 0%：占位行整体不出现，而不是给一个假进度。 */
    @Test fun ready_without_progress_hides_the_progress_row() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        composeRule.onNodeWithTag("today_plan_learned_percent").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_book_progress").assertDoesNotExist()
    }

    @Test fun locked_reading_explains_state_without_looking_like_primary_action() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready(
                    "小学", "2026-09-19", 2, 3, 5,
                    isUnlocked = false,
                    unlockReason = "完成新词与复习后解锁文章",
                ),
            )
        }
        composeRule.onNodeWithTag("today_plan_reading_status").assertExists()
        composeRule.onAllNodesWithText("完成新词与复习后解锁文章").assertCountEquals(1)
        composeRule.onNodeWithTag("today_plan_open_reading").assertIsNotEnabled()
    }

    /** 同级按钮都有 contentDescription；这一个是本轮改名后新加的，不能漏掉无障碍标注。 */
    @Test fun ready_offers_a_labelled_learning_tools_entry() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        composeRule.onNodeWithTag("today_plan_learning_tools").assertExists().assertHasClickAction()
        composeRule.onNodeWithContentDescription("学习工具与设置").assertExists()
    }

    @Test fun reading_is_after_primary_learning_action() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        val primaryBottom = composeRule.onNodeWithTag("today_plan_start_learning").fetchSemanticsNode().boundsInRoot.bottom
        val readingTop = composeRule.onNodeWithTag("today_plan_open_reading").fetchSemanticsNode().boundsInRoot.top
        assertTrue("reading should follow the primary learning action", readingTop > primaryBottom)
    }

    @Test fun ready_offers_check_in_entry() {
        var opened = 0
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5), onOpenCheckIn = { opened++ }) }
        composeRule.onNodeWithTag("today_plan_open_check_in").performScrollTo().assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
    }

    /** D2：词书单词从学习记录拆出为独立入口，与「学习记录」并排。 */
    @Test fun ready_offers_a_labelled_word_book_words_entry() {
        var opened = 0
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5), onOpenWordBookWords = { opened++ }) }
        composeRule.onNodeWithTag("today_plan_open_wordbook_words").performScrollTo().assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
        composeRule.onNodeWithContentDescription("词书单词").assertExists()
    }

    @Test fun ready_offers_setup_entry_to_reopen_word_book_settings() {
        var opened = 0
        composeRule.setContent {
            TodayPlanScreen(TodayPlanUiState.Ready("大学英语四级", "2026-09-19", 10, 0, 10), onOpenSetup = { opened++ })
        }
        // Task 14B：AC-R-20 把动作收成「1 主 CTA + 入口行组」后，这一行落在滚动区更下方，
        // 不先滚到屏内 `performClick()` 会把点打在被裁剪的坐标上、回调不触发（与本文件
        // `ready_offers_check_in_entry` / `ready_offers_a_labelled_word_book_words_entry` 同法）。
        // 判据本身不放宽：仍然是「点这一行 → `onOpenSetup` 恰被调用一次」。
        composeRule.onNodeWithTag("today_plan_open_setup_entry").performScrollTo().assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
    }

    @Test fun task_cards_use_neutral_surfaces_with_quiet_distinct_markers() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5)) }
        listOf("today_plan_new_count", "today_plan_due_count").forEach { tag ->
            assertRenderedColor(tag, 0xFFFFFFFF.toInt())
            assertRenderedColor(tag, 0xFF1C1C1E.toInt())
        }
        // Task 14B：两枚色点从两枚裸 hex 改读域色 deep（浅套值）——
        // 「今日新增」= 学习域 #075EB4（原 #68BDA6），「今日复习」= 复习域 #AB5700（原 #C7A47D）。
        // 浅套像素值因此**本来就该变**：这不是回归，而是 AC-R-20 要求的新语义，
        // 且与 `TodayWorkloadBar` 的分段条同源（同一概念在本屏只允许一套颜色）。
        assertRenderedColor("today_plan_new_count", 0xFF075EB4.toInt())
        assertRenderedColor("today_plan_due_count", 0xFFAB5700.toInt())
        assertTrue("neutral text needs 4.5:1", contrastRatio(0xFF1C1C1E.toInt(), 0xFFFFFFFF.toInt()) >= 4.5)
    }

    @Test fun available_actions_keep_one_mint_gradient_and_quiet_supporting_actions() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5, isUnlocked = true)) }
        assertRenderedColor("today_plan_start_learning", 0xFFA8F3C8.toInt())
        assertRenderedColor("today_plan_start_learning", 0xFF65D3B9.toInt())
        assertRenderedColor("today_plan_start_learning", 0xFF174C44.toInt())
        assertTrue("mint gradient needs 4.5:1 at both ends", listOf(0xFFA8F3C8.toInt(), 0xFF65D3B9.toInt()).all { contrastRatio(it, 0xFF174C44.toInt()) >= 4.5 })
        assertRenderedColor("today_plan_open_reading", 0xFFFFFFFF.toInt())
        assertRenderedColor("today_plan_open_reading", 0xFFC81E46.toInt())
        // Task 14B：「打卡与成就」从整宽按钮（那枚无对应令牌的棕 #795B36）收成入口行，
        // 域色对位是 `Review`（稿「连续与记录」组用的就是 --d-review），字形墨色 = deep 浅套值 #AB5700。
        // 棕值 #795B36 因此退役，浅套像素改变是裁决本身（登记优于蒙混：不凑一枚近似令牌，
        // 而是给出语义对位与理由），不是回归。白底仍然来自组容器 `surface`。
        assertRenderedColor("today_plan_open_check_in", 0xFFFFFFFF.toInt())
        assertRenderedColor("today_plan_open_check_in", 0xFFAB5700.toInt())
    }

    @Test fun locked_reading_action_is_neutral_and_not_clickable() {
        composeRule.setContent { TodayPlanScreen(TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5, isUnlocked = false)) }
        composeRule.onNodeWithTag("today_plan_open_reading").assertIsNotEnabled()
        assertRenderedColor("today_plan_open_reading", 0xFFE5E5EA.toInt())
    }

    /**
     * AC-R-20 的像素侧：今日数字只留**一处**权威尺寸。
     *
     * 「今天完成了几项」那一格用 `AppType.Display`（34sp），其余数字（小标题、整册百分比、整册词数、
     * 未学、明细、工作量图例）只能是它的注脚。compose-ui 1.10 的语义节点里**没有** FontSize 属性
     * （`SemanticsProperties.FontSize` 在这个 BOM 上不存在），所以这里按「渲染高度」复算尺寸。
     *
     * **阈值为什么是 0.45（修复轮 1 的 F2，`emulator-5574` @ 420dpi 实测）**：
     *  | 档 | sp | 本用例实测渲染高度 | 对 Display 的行高比 |
     *  |---|---|---|---|
     *  | `Display`（今日完成数） | 34 | 105.0px | 1.000 |
     *  | `Footnote`（本用例采样的 7 行注脚） | 13 | 40.0px | **0.381** |
     *  | `Label`（`today_plan_task_total` 小标题） | 12 | 37.0px | 0.352 |
     *  | `Title`（词书名那一处，实测探针） | 17 | 52.0px | **0.495** |
     *  | `Headline`（推算：行高随字号线性） | 24 | ≈74.1px | ≈0.706 |
     *
     * 旧判据写作 `h * 2f < displayHeight`，即阈值 0.5——代入实测：`52 × 2 = 104 < 105` **成立**，
     * 也就是说「把注脚升回 `Title`」这一形在旧判据下是绿的（余量只有 1px，评审 C-1 的代数边界分析被实测证实）。
     * 改成显式双阈值后：`0.45` 相对实测注脚上限 `0.381` 留 0.069 余量（字体度量/动态字体缩放的一般抖动），
     * 又相对 `Title` 的 `0.495` 留出 0.045 的**判红**空间；`Headline` 及以上更是远超。
     * 覆盖声明也要订正（旧 KDoc 自称「不会放过升回 Title/Headline」，实测对 Title 是失守的）：
     * **本条兜住 ≥ `Title`（17sp）的所有升档**，`AppType.Title` 只许出现在词书名那一处由
     * `TodayPlanNumberAuthorityTest.display_is_used_exactly_once_and_anchors_the_total_progress_slot`
     * 的全文件读数钉死；「屏内新增第二枚大字号数字」这一形同样由该条源码判据负责（本条只采样固定标签，
     * 新标签天然不在名单里）。
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
        val display = composeRule.onNodeWithTag("today_plan_total_progress")
        display.performScrollTo().assertTextEquals("2/5")
        val displayHeight = display.fetchSemanticsNode().boundsInRoot.height
        assertTrue("权威数字自身高度为 $displayHeight px，判据失去参照（本用例空转）", displayHeight > 0f)
        listOf(
            "today_plan_task_total",
            "today_plan_learned_percent",
            "today_plan_book_words",
            "today_plan_book_unlearned",
            "today_plan_progress",
            "today_plan_workload_learned_label",
            "today_plan_workload_review_label",
            "today_plan_workload_new_label",
        ).forEach { tag ->
            val node = composeRule.onNodeWithTag(tag).performScrollTo().fetchSemanticsNode()
            val ratio = node.boundsInRoot.height / displayHeight
            assertTrue(
                "$tag 的渲染高度 ${node.boundsInRoot.height}px 对权威数字 ${displayHeight}px 的比值=$ratio，" +
                    "超过注脚上限阈值 $MaxFootnoteToDisplayRatio——它没降到注脚档" +
                    "（实测：Footnote 40.0/105.0=0.381，Label 37.0/105.0=0.352，Title 52.0/105.0=0.495）",
                ratio <= MaxFootnoteToDisplayRatio,
            )
        }
    }

    /**
     * AC-R-20 的版面侧：就绪态收成「1 枚主 CTA + 一组入口行」+ 一条常驻查词。
     *
     * 判据四条：① 整屏可点目标恰为 **9** 枚——1 主 CTA（`today_plan_start_learning`）+ 1 受解锁态约束的
     * 阅读文章 + 1 **常驻**查词 + 6 条组内入口行。这个 9 是 AC-R-20 的**闭合集合**，多一枚少一枚都红；
     * 批 3（Task 19–26「其余屏与入口分组重排」）若增删入口，必须**同时**改这条判据，并在
     * `docs/superpowers/plans/2026-10-17-ui-redesign.md` 里留名说明改动理由——红因与 AC-R-20 无关时，
     * 计划里那一行就是免责凭据（评审 I-6/I-7 预告的「删两条切 tab 入口」正落在这条上）。
     * ② 组内 6 行都在同一个组容器里、都可点、都不低于 48dp 触控下限（F5-03），且组高 = 6 行之和
     * （分组是真的，不是「贴了个 tag 的空壳」）；
     * ③ 查词那一行**必须落在组容器之外**（F3：它四态常驻，跟着组走就只在 Ready 态出现）；
     * ④ 组内不得出现品牌渐变的两个端点色——渐变归主 CTA 一处，入口行是弱档 tint 底。
     */
    @Test fun ready_groups_entry_actions_and_keeps_one_primary_cta() {
        composeRule.setContent {
            TodayPlanScreen(
                TodayPlanUiState.Ready("小学", "2026-09-19", 2, 3, 5, isUnlocked = true),
                vocabularyCount = 12,
            )
        }
        val rowTags = listOf(
            "today_plan_open_wordbook_words",
            "today_plan_open_vocabulary",
            "today_plan_open_learning_records",
            "today_plan_open_check_in",
            "today_plan_learning_tools",
            "today_plan_open_setup_entry",
        )
        val persistentSearchTag = "today_plan_search_entry"
        // ① 闭合集合 = 1 主 CTA + 1 阅读文章 + 1 常驻查词 + 6 组内行 = 9。
        //    用 fetchSemanticsNodes().size + assertEquals 而不是 assertCountEquals，是为了把「为什么是 9」
        //    与改法流程写进失败消息（compose 1.10 的 assertCountEquals 没有自定义消息形参）。
        val clickable = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertEquals(
            "本屏可点节点的**闭合集合**必须是 9 = 1 主 CTA(today_plan_start_learning) + 1 受解锁态约束的" +
                "阅读文章(today_plan_open_reading) + 1 常驻查词($persistentSearchTag) + 6 条入口组内行。" +
                "这是 AC-R-20 的收成结果，多一枚（新的整宽按钮 / 第二枚主 CTA）少一枚（把查词又收进组里 / " +
                "删掉某条出口）都红。批 3（Task 19–26「其余屏与入口分组重排」，含内容审计预告的" +
                "「删两条切 tab 入口」）若增删本屏入口，必须**同时**改这条判据，并在 " +
                "docs/superpowers/plans/2026-10-17-ui-redesign.md 的批 3 段落里留名说明改动理由；" +
                "静态口径（EntryRow 份数、PillButton 唯一、标签白名单）由 " +
                "TodayPlanNumberAuthorityTest.the_screen_keeps_one_primary_cta_and_entry_rows_for_every_other_action 负责。",
            1 + 1 + 1 + rowTags.size,
            clickable.size,
        )

        val group = composeRule.onNodeWithTag("today_plan_entry_group")
        group.performScrollTo()
        val groupBox = group.fetchSemanticsNode().boundsInRoot
        val minTouchPx = with(composeRule.density) { 48.dp.toPx() }
        var rowsHeight = 0f
        rowTags.forEach { tag ->
            val row = composeRule.onNodeWithTag(tag)
            row.performScrollTo().assertHasClickAction()
            val bounds = row.fetchSemanticsNode().boundsInRoot
            rowsHeight += bounds.height
            assertTrue("$tag 的行高 ${bounds.height}px 低于 48dp 触控下限（${minTouchPx}px）", bounds.height >= minTouchPx)
            assertTrue(
                "$tag 越出入口组容器：行 $bounds / 组 $groupBox",
                bounds.left >= groupBox.left - 1f && bounds.right <= groupBox.right + 1f &&
                    bounds.top >= groupBox.top - 1f && bounds.bottom <= groupBox.bottom + 1f,
            )
        }
        assertTrue(
            "入口组高 ${groupBox.height}px 与 6 条行之和 ${rowsHeight}px 对不上——分组不是逐条相接的行",
            kotlin.math.abs(groupBox.height - rowsHeight) <= with(composeRule.density) { 2.dp.toPx() },
        )
        // ③ 常驻查词在组**外**：它跟着 `when (state)` 走，四个态都要能看见（F3 / 评审 I-1）。
        //    写进组里 = 只在 Ready 态存在，本条判据正是用来拦住那次回退的。
        val searchRow = composeRule.onNodeWithTag(persistentSearchTag)
        searchRow.performScrollTo().assertHasClickAction()
        val searchBounds = searchRow.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "$persistentSearchTag 被收进了入口组容器（组 $groupBox / 行 $searchBounds）——" +
                "查词入口必须四态常驻，收进组里等于只在就绪态提供查词出口",
            !(searchBounds.top >= groupBox.top - 1f && searchBounds.bottom <= groupBox.bottom + 1f &&
                searchBounds.left >= groupBox.left - 1f && searchBounds.right <= groupBox.right + 1f),
        )
        assertTrue(
            "$persistentSearchTag 的行高 ${searchBounds.height}px 低于 48dp 触控下限（${minTouchPx}px）",
            searchBounds.height >= minTouchPx,
        )
        // ④ 品牌渐变只许留在主 CTA 一处：组容器**可见部分**内不得出现渐变两端色
        // （#A8F3C8 / #65D3B9，见 GlassButton.kt:81 与 MintPalette.kt:78）。
        // 组比视口高，captureToImage 只能拿到可见区，所以这条是「可见部分」判据，
        // 静态全量判据由 TodayPlanNumberAuthorityTest 的源码口径负责。
        assertNoRenderedColor("today_plan_entry_group", 0xFFA8F3C8.toInt())
        assertNoRenderedColor("today_plan_entry_group", 0xFF65D3B9.toInt())
    }

    private companion object {
        /**
         * 注脚行高对权威数字（`AppType.Display`）的上限阈值。
         *
         * 选取理由与实测数据见 [ready_presents_todays_numbers_with_one_display_authority] 的 KDoc：
         * `emulator-5574` @ 420dpi 实测 Footnote/Display = 40.0/105.0 = 0.381，
         * Title/Display = 52.0/105.0 = 0.495，故 0.45 既放过注脚与 `Label`（0.352），
         * 又能判红「升回 Title」这一档（旧判据 `h*2 < H` 的 0.5 阈值在这一档只有 1px 余量，实测放过）。
         */
        const val MaxFootnoteToDisplayRatio = 0.45f
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

    private fun colorDistance(actual: Int, expected: Int): Double {
        val dr = (AndroidColor.red(actual) - AndroidColor.red(expected)).toDouble()
        val dg = (AndroidColor.green(actual) - AndroidColor.green(expected)).toDouble()
        val db = (AndroidColor.blue(actual) - AndroidColor.blue(expected)).toDouble()
        return Math.sqrt(dr * dr + dg * dg + db * db)
    }
}
