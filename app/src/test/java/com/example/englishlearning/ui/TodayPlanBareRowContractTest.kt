package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 学习页（批 1 §3.2）的落点判据：入口不再平铺在主页。
 *
 * 批 2 时这里钉的是「入口组同组同色 + 组内字形互异」（Task 27 / 裁决 22-2）。批 1 把入口本身搬走了：
 * 词书单词与我的词并入**词库**页，学习记录与打卡并入**我的**页，调整词书与目标回到设置的词书组。
 * 学习页只剩两件事——「今天学什么」（强档卡）与「接下来去哪」（今日文章 / 词库两条裸行）。
 * 于是旧判据的对象（入口组）整体消失，判据换成三条：
 *  1. 只剩两条裸行、恰一枚强档卡、零枚 EntryRow；
 *  2. 主 CTA 四种状态同锚点（`today_plan_start_learning`），文案随状态换；
 *  3. 两条裸行各取自己那一域的域色，同屏不许第三枚域色散读。
 *
 * 第三条是旧用例 `TodayPlanNumberAuthorityTest.today_markers_and_workload_segments_read_the_same_domain_colors`
 * 的**具名替代**（那份工作量分段条随本页重排下线，`WorkloadKind` 三个私有扩展一并删除）：
 * 判据的内核不变——「同一概念在本屏只许一套颜色，且颜色必须来自域色表」，只是对象从
 * 「三档工作量段」换成「两条裸行」。
 *
 * 读法是源码文本（`uiScreenSource` 已剥注释），不渲染 Compose：枚数与具名实参是文本问题；
 * 像素侧（实际渲染色、48dp 命中区、可点节点闭合集合）由 connected 的
 * `TodayPlanScreenTest.ready_groups_entry_actions_and_keeps_one_primary_cta` 承担。
 */
class TodayPlanBareRowContractTest {
    private val screen: String by lazy { uiScreenSource("TodayPlanScreen.kt") }

    @Test
    fun `the learning home keeps only two bare rows`() {
        assertEquals(2, countMatches(screen, Regex("""\bBareRow\(""")), "学习页只留两条裸行（今日文章 / 词库）")
        assertEquals(1, countMatches(screen, Regex("""\bStrongCard\(""")), "学习页恰一枚强档卡（§3.2 主区）")
        assertEquals(0, countMatches(screen, Regex("""\bEntryRow\(""")), "五个入口行必须离场：两个去词库、三个去我的")
        listOf("today_plan_reading_status", "today_plan_open_word_bank").forEach { tag ->
            assertEquals(1, countMatches(screen, Regex(Regex.escape(tag))), "裸行锚「$tag」必须恰一处")
        }
        assertTrue("SoftCard(" in screen, "两条裸行同住一枚软档卡（§4.1 同组分隔线）")
        listOf(
            "today_plan_open_wordbook_words",
            "today_plan_open_my_words",
            "today_plan_open_learning_records",
            "today_plan_open_check_in",
            "today_plan_open_setup_entry",
        ).forEach { gone ->
            assertTrue(gone !in screen, "入口锚「$gone」已迁出学习页（spec §3.2 移出清单），不得残留")
        }
    }

    @Test
    fun `the main cta keeps one anchor across the two states`() {
        assertEquals(1, countMatches(screen, Regex("""\btoday_plan_start_learning""")), "主 CTA 两态必须同锚点，恰一处")
        listOf("开始学习", "开始复习", "去读今日文章", "复习生词本").forEach { label ->
            assertTrue(label in screen, "完成态/进行态文案「$label」缺一枚：四种状态都要有落点")
        }
        // 主 CTA 的**唯一性只在 Ready 分支体内**判定：缺配置与不可用两个分支各自合法地拥有一枚
        // PillButton（「去设置词书」/「重试」），全文件计数会把它们算成「第二枚主 CTA」而红。
        // 取 Ready 分支体（花括号配平）再数，判据比全文件计数更紧：「Ready 里多一枚整宽按钮」照旧红。
        val readyBody = readyBranchBody()
        assertEquals(
            1,
            countMatches(readyBody, Regex("""\bPillButton\(""")),
            "Ready 分支体里只能有一枚 PillButton（主 CTA）；缺配置/不可用分支的按钮不在本次计数内",
        )
        assertTrue(
            "today_plan_start_learning" in readyBody,
            "主 CTA 的锚点必须长在 Ready 分支体内，而不是被抽到分支之外另写的私有组合件里",
        )
    }

    /**
     * 取 `is TodayPlanUiState.Ready -> {` 那一段的花括号体。
     *
     * 与 `TodayPlanNumberAuthorityTest.bodyOf` 同一口径（显式失败、不静默返回空串）：找不到锚点或
     * 括号不配平都必须当场红，否则「取到空串 → 计数 0 → 断言意外通过」会让判据空转。
     */
    private fun readyBranchBody(): String {
        val anchor = "is TodayPlanUiState.Ready ->"
        val anchorAt = screen.indexOf(anchor)
        assertTrue(anchorAt >= 0, "本屏源码里找不到 \"$anchor\" 锚点，无法取样 Ready 分支体")
        val open = screen.indexOf('{', anchorAt)
        assertTrue(open > anchorAt, "Ready 分支锚点之后没有开出花括号体")
        var depth = 0
        for (i in open until screen.length) {
            when (screen[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return screen.substring(open + 1, i)
                }
            }
        }
        throw AssertionError("Ready 分支体花括号未配平：从偏移 $open 到末尾都没闭合")
    }

    /**
     * 域色对位：今日文章 → `Reading`、词库 → `Library`，且**只此两枚**。
     *
     * 同屏第三枚 `appDomain(...)` 读点就是「同一概念两套颜色」的入口（旧判据的立法目的），
     * 主 CTA 的强调底由 `PillButton` 内部按 `AiSpeech` 取，不经过本屏的域色读点。
     *
     * 枚数按 **Ready 分支体**判而不是全文件：`Loading` 分支的转圈取 `AiSpeech.deep` 是加载件的
     * 单一墨色（三态共用的那个指示器只有这一处取色点），它不参与「今日计划呈现里的同一概念两套颜色」
     * 这条口径。取样范围收窄不是放宽——Ready 态是本条全部立法目的所在，且比全文件计数更紧：
     * 之前靠「全文件恰 2」顺带罩住 Ready，任何一处在别处新增域色都会把这条判红，与它无关的分支也一票否决；
     * 现在别处（如 Loading）新增域色不会被误判，而 Ready 体里出现第三枚照样红。
     */
    @Test
    fun `the two bare rows carry their own domain ink and nothing else`() {
        assertEquals(1, countMatches(screen, Regex("""appDomain\(AppDomain\.Reading\)""")), "今日文章裸行取 Reading 域色")
        assertEquals(1, countMatches(screen, Regex("""appDomain\(AppDomain\.Library\)""")), "词库裸行取 Library 域色")
        assertEquals(
            2,
            countMatches(readyBranchBody(), Regex("""appDomain\(AppDomain\.[A-Za-z]+\)""")),
            "Ready 分支体里的域色读点只此两枚（文章 / 词库）：第三枚即同一概念两套颜色的入口",
        )
    }
}
