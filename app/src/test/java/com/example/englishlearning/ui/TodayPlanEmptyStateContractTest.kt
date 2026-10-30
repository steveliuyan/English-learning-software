package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 批 1 Task 19：今日学习屏的「今天全学完了」那一态对齐原型 `sLearnEmpty`
 * （`assets/app.js:501-518`，路由表 `:1608` `'learn-empty': sLearnEmpty`，屏索引 `:1625`「今日已学完」）。
 *
 * 这一态**不是第六枚新建屏**：原型把它画成「学习」这个 tab 的另一份 `.scr`（页头同名「今日学习」、
 * 底部 `nav('learn-home')` 仍停在第一位），本仓的对应物就是 `TodayPlanScreen` 的 Ready 分支里
 * 「排了任务且每一颗都提交完」的那一态。所以本契约的第一条判据钉「它仍是本屏的一个状态」，
 * 同时钉宿主（`AppScreen.kt`）没有为它长出新的挂载点或新的导航位——本轮获授权的新增屏只有五枚
 * （计划「推进决定」第 2 条），这枚不在名单里。
 *
 * 原型骨架（逐段对着 `app.js` 抄）：
 *  `head({ plain: true, eyebrow: D.today.planDate, title: '今日学习', sub: '连续打卡 N 天' })`（**没有** action 位）
 *   → `emptyState({ icon: 'star', title: '今天的词都学完了', body: '新学 4 个、复习 2 个都已完成。想多学可以调整每日新增，或者读一篇今天生成的文章。',
 *        action: grid(max-width:260px; gap:8px; margin-top:8px) → btn-primary「调整每日新增」→ plan-edit
 *                                                 → btn-secondary「去读今日文章」→ read-article })`
 *   → `section('明天', .card-quiet)`
 *
 * `.empty` 的度量（`assets/app.css:931-940`）：网格居中、`gap: var(--s3)`=12、`padding: var(--s7) var(--s4)`=32/16、
 * 贴纸 84dp / 圆角 26 / 内描边 1dp 5% 墨、`.empty-title` = `--fs-body` 600、`.empty-body` = `--fs-caption` `--ink-3`。
 * 这一族在批 0 已经落进共享 [com.example.englishlearning.ui.components.EmptyState]，
 * 本屏只做调用（本仓此前**零**调用点），不重排它。
 *
 * 与 `sLearnHome` 的分工：`app.js:302` 那一屏是「还没学完」，`app.js:501` 这一屏是「学完了」，
 * 两屏共用同一个 tab 位、不共存。因此本态下强档卡（`PlanNum` 权威数、`PlanProgressBar`、两枚图例键）、
 * 卡外那行 `.caption` 注脚、`SectionHeading`「接下来」与四条裸行**整段不进组合**——
 * 原型的 `.scr-body` 在这里只有 `emptyState` 与 `section('明天')` 两块。
 *
 * ## 三处有意的偏离（都写在这里，不藏在实现里）
 *
 *  1. `section('明天')` 整节不搬。那句 `.meta` 是「明天新学 4 个词，加上今天模糊的 2 个，约 6 个词；
 *     文章会在 0 点后生成」——三个数没有一处读得到：①「明天新学 N 个」要么把今天的 `newTarget` 当成明天的
 *     （AGENTS.md 明写「设置默认从次日生效」，今天改过目标就立刻是假话），要么新开一条
 *     「按次日生效设置预生成明日份额」的读数通路；②「今天模糊的 2 个」要按档聚合今天的 `LearningEvent`，
 *     与 Task 18 台账里 `section('这一轮的反馈')` 整节下线是同一条理由；③「约 6 个词」是前两者的派生数；
 *     ④「文章会在 0 点后生成」在本仓不成立——今日文章是解锁后按需生成，不是 0 点的定时任务。
 *     依据是计划「推进决定」第 3 条：读不到的那枚整枚不显示，新增采集属功能改动、未获授权。
 *  2. 页头按原型的 `.head-plain` 撤掉齿轮动作位，副标题只留「连续打卡 N 天」。
 *     `.head-plain` 在全原型只出现两处（`app.js:503` 与 `app.js:1257` 的「我的」根页），两处的共同点正是
 *     「这一屏不办事、只交代状态」，所以这不是漏画而是画法。设置仍从「我的」tab 进，本态的两枚 CTA
 *     才是这一屏要的出口。`streakDays == 0` 时那一行整行不出现（本仓既有口径：没有连续记录时不摆「连续 0 天」）。
 *  3. `.btn-primary` 的原型底是 `--strong` 深墨（`app.css:583`），本仓主药片走品牌渐变那一档
 *     （`PillStyle.Primary` + 默认域），与 `sLearnHome` 主 CTA 同一取法（那条的色值差登记在批 1 报告）。
 *     本屏任何一态都只许**一枚**渐变，所以这一条不新开强调面；判据是
 *     `the_empty_state_offers_exactly_the_two_exits_the_prototype_draws` 钉的两枚按钮档位
 *     （primary / `PillStyle.Secondary`）加上 connected 侧的渐变枚数用例。
 *
 * 「复习生词本」这一终点随本态下线：原型的空态只有两枚出口，四态主 CTA 里那两枚「做完了才出现」的
 * 文案（去读今日文章 / 复习生词本）本就没有画在学完那一屏上。生词本不是断头路——词库页自己的分段控件
 * （`WordBankScreen.kt` 的四格 tab）是生词段的另一条出口，「我的」页词库格也开这一页。
 * `onReviewWords` 这条回调随终点一起摘掉，不给本屏留一枚无人调用的形参。
 *
 * 读法是源码文本（[uiScreenSource] 已剥注释），不渲染 Compose；像素与可达性由 connected 的
 * `TodayPlanScreenTest` 承担（空态的可点闭合集合、两枚 CTA 各落哪儿、260dp 上限与 48dp 水线）。
 */
class TodayPlanEmptyStateContractTest {

    private val screen: String by lazy { uiScreenSource("TodayPlanScreen.kt") }

    /** 按花括号配对取 `needle` 之后第一枚 `{` 的体内文本；锚缺失或未闭合都当场响亮红。 */
    private fun bodyOf(needle: String, site: String): String {
        val at = screen.indexOf(needle)
        assertTrue(at >= 0, "$site：取样锚 \"$needle\" 不在位（被改名/删除/换形），本契约拒绝回退成整份原文")
        val open = screen.indexOf('{', at)
        assertTrue(open > at, "$site：锚 \"$needle\" 之后找不到体起始花括号")
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
        fail("$site：从下标 $open 起花括号未闭合，区间无法界定")
    }

    /** Ready 分支体（与 `TodayPlanBareRowContractTest.readyBranchBody` 同一锚、同一配平口径）。 */
    private fun readyBranchBody(): String = bodyOf("is TodayPlanUiState.Ready ->", "Ready 分支体取样")

    /** 空态那一支的体（`if (todayAllDone) {`）。 */
    private fun emptyBranchBody(): String = bodyOf("if (todayAllDone) {", "空态分支体取样")

    @Test
    fun `the all done state is a state of this screen rendered with the shared empty state`() {
        assertEquals(
            1,
            countMatches(screen, Regex("""\bEmptyState\(""")),
            "空态走批 0 的共享 `EmptyState`（原型 `.empty`，`app.css:931-940`），恰一处调用；" +
                "屏侧不自铺 84dp 贴纸 + 标题 + 说明那一套",
        )
        assertTrue("Sticker.Star" in screen, "贴纸逐字取原型 `emptyState({icon:'star'})`（`app.js:506`）")
        assertTrue("今天的词都学完了" in screen, "空态标题逐字（`app.js:507`）")
        assertTrue(
            "\"today_plan_all_done_state\"" in screen,
            "空态整块有自己的锚点，connected 侧按它取节点（不与既有 `today_plan_empty`——那是「今天一个任务都没排」那一态——混用）",
        )

        // 「学完了」这件事只许有一个判定式：页头（`.head-plain` + 副标题）与 Ready 分支都吃同一个值。
        // 两处各写一遍 `totalDone >= totalTarget` 就是第二份权威，将来 coerce 口径改一处漏一处即分叉。
        val predicateAt = screen.indexOf("val todayAllDone =")
        val whenAt = screen.indexOf("when (state)")
        assertTrue(predicateAt >= 0, "本屏找不到 `val todayAllDone =`——完成判定没有算在页头之前")
        assertTrue(whenAt >= 0, "本屏找不到 `when (state)` 锚点，无法判定完成判定的位置")
        assertTrue(
            predicateAt < whenAt,
            "完成判定必须在 `when (state)` **之前**算一次（页头在它上面）：实际 判定=$predicateAt / `when`=$whenAt",
        )
        assertEquals(
            1,
            countMatches(screen, Regex("""val todayAllDone =""")),
            "完成判定式只许一枚定义：两处各写一遍 `totalDone >= totalTarget` 就是第二份权威，" +
                "coerce 口径将来改一处漏一处即分叉",
        )

        // 这一态仍是本屏的一个状态：宿主只有一处挂载，也没有为它新增导航位。
        val host = uiScreenSource("AppScreen.kt")
        assertEquals(1, countMatches(host, Regex("""(?<![A-Za-z0-9_])TodayPlanScreen\(""")), "宿主挂载今日学习屏恰一处：空态不是第六枚新建屏")
        assertEquals(
            0,
            countMatches(host, Regex("""showLearnEmpty|showTodayEmpty|LearnEmptyScreen""")),
            "不得为空态新增导航位或新屏（计划「推进决定」第 2 条只授权五屏）",
        )
    }

    @Test
    fun `the empty branch draws no plan card no progress bar and no bare rows`() {
        val body = emptyBranchBody()
        assertTrue("EmptyState(" in body, "空态那一支渲染的就是 `EmptyState`")
        listOf(
            "StrongCard(",
            "SoftCard(",
            "BareRow(",
            "PlanProgressBar",
            "SectionHeading(",
            "AppType.PlanNum",
            "LinearProgressIndicator",
            "CircularProgressIndicator",
        ).forEach { token ->
            assertEquals(
                0,
                countMatches(body, Regex(Regex.escape(token))),
                "原型空态的 `.scr-body` 里没有「$token」这一件（`app.js:501-518` 只有 emptyState + section('明天')）：" +
                    "强卡、进度条、图例与四条裸行整段不进这一态的组合",
            )
        }
        // 反向：那几件在「还没学完」那一支里照旧在场，本条不是把它们整屏删掉。
        val ready = readyBranchBody()
        assertEquals(1, countMatches(ready, Regex("""\bStrongCard\(""")), "未学完那一支仍恰一枚强档卡（`sLearnHome` 的 `.strong-card`）")
        assertEquals(4, countMatches(ready, Regex("""\bBareRow\(""")), "未学完那一支仍恰四条裸行（`app.js:342-343` + 文章 + 词库）")
    }

    @Test
    fun `the empty state offers exactly the two exits the prototype draws`() {
        val body = emptyBranchBody()
        assertEquals(2, countMatches(body, Regex("""\bPillButton\(""")), "空态恰两枚药片按钮（原型那枚 260px 网格里的一枚 primary + 一枚 secondary）")
        assertEquals(0, countMatches(body, Regex("""(?<![A-Za-z0-9_])Button\(""")), "屏侧不出现裸 M3 `Button(`：两枚出口都走共享 `PillButton`")
        assertTrue("text = \"调整每日新增\"" in body, "主出口文案逐字（`app.js:510`）")
        assertTrue("text = \"去读今日文章\"" in body, "次出口文案逐字（`app.js:511`）")
        assertTrue("onClick = onOpenSetup" in body, "「调整每日新增」落既有设置通路（原型 `data-to=\"plan-edit\"`）")
        assertTrue("onClick = onOpenReading" in body, "「去读今日文章」落既有阅读通路（原型 `data-to=\"read-article\"`）")
        assertTrue("style = PillStyle.Secondary" in body, "次出口按原型 `.btn-secondary`（`app.css:585` surface 底 + `--line` 描边）取 `PillStyle.Secondary`")
        listOf("today_plan_adjust_goal", "today_plan_read_article").forEach { tag ->
            assertEquals(1, countMatches(screen, Regex(Regex.escape("\"$tag\""))), "空态出口锚「$tag」全文件恰一处")
        }
        // 原型那枚 grid：`width:100%; max-width:260px; gap:var(--s2); margin-top:var(--s2)`（`app.js:509`）。
        assertTrue("widthIn(max = 260.dp)" in body, "两枚出口收在 260dp 上限的列里（原型 `.empty` 里那枚 grid 的 max-width）")
        assertTrue("Arrangement.spacedBy(8.dp)" in body, "两枚出口之间 8dp（原型 `gap: var(--s2)`）")
        assertTrue(".padding(top = 8.dp)" in body, "出口块自带 8dp 上边（原型同一段的 `margin-top: var(--s2)`）")

        // 「复习生词本」随四态主 CTA 一起离场：原型这一屏没有第三枚出口，也不把次出口换成它。
        listOf("复习生词本", "onReviewWords").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "「$token」在本屏归零：空态两枚出口逐字取原型，本屏不留一枚原型没画的第三出口，" +
                    "也不留无人调用的形参（生词本走词库页自己的分段控件）",
            )
        }
    }

    @Test
    fun `the mock tomorrow section stays off the screen`() {
        listOf("明天", "约 6 个词", "0 点后生成", "模糊的", "QuietCard(").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "「明天」那一节整节不搬（`app.js:515`）：三个数与「0 点生成」本仓都读不到，" +
                    "新增采集属功能改动、未获授权（计划「推进决定」第 3 条）",
            )
        }
    }

    @Test
    fun `the body sentence names only the counts the plan snapshot actually has`() {
        assertTrue("都已完成" in screen, "空态说明的前半句逐字（`app.js:508`「新学 4 个、复习 2 个都已完成」）")
        assertTrue(
            "新学 \${state.newTarget} 个" in screen,
            "「新学 N 个」取计划快照的目标数（与未学完那一支的入口行、强卡图例同一权威），不是原型 mock 的 4",
        )
        assertTrue(
            "复习 \${state.dueTarget} 个" in screen,
            "「复习 N 个」同样取快照到期目标，不是原型 mock 的 2",
        )
        listOf("新学 4 个", "复习 2 个").forEach { token ->
            assertEquals(0, countMatches(screen, Regex(Regex.escape(token))), "原型 mock 数「$token」不得写死进文案")
        }
        // 某一档为 0 时那一枚数量分句整段不出现：「新学 0 个、复习 2 个都已完成」是把「今天没排新词」
        // 说成「排了 0 个并且学完了」。两档都为 0 时整块空态也进不来（`todayAllDone` 要求 totalTasks > 0）。
        assertTrue("if (state.newTarget > 0)" in screen, "新学分句按快照目标数是否 >0 决定拼不拼")
        assertTrue("if (state.dueTarget > 0)" in screen, "复习分句同理")
        assertTrue(
            "想多学可以调整每日新增，或者读一篇今天生成的文章。" in screen,
            "空态说明的后半句逐字（`app.js:508`），两枚出口正是这一句说的两条路",
        )
    }

    @Test
    fun `the plain head drops the gear and keeps only the streak in the subtitle`() {
        assertEquals(1, countMatches(screen, Regex("""AppScreenHeader\(""")), "页头仍恰一处共享 `AppScreenHeader`，空态不另起一份页头")
        assertTrue(
            "if (!todayAllDone) SettingsAction(" in screen,
            "原型空态的 `head({plain:true})` 没有 action 位（全原型 `.head-plain` 只两处，`app.js:503`/`:1257`）：" +
                "学完那一态不挂齿轮，未学完那一态照旧挂",
        )
        assertEquals(
            1,
            countMatches(screen, Regex("""if \(!todayAllDone\) SettingsAction\(""")),
            "齿轮只有这一处渲染点，且由完成判定管着；复制第二枚 `SettingsAction(` 调用即多一枚可点节点",
        )
        assertEquals(
            1,
            countMatches(screen, Regex("""今日目标""")),
            "「今日目标 N 个词」只留在未学完那一档副标题里：原型空态的 `.head-sub` 只有「连续打卡 N 天」（`app.js:503`）",
        )
        assertTrue("连续打卡 \$streakDays 天" in screen, "空态副标题逐字取原型 `.head-sub`「连续打卡 N 天」（`app.js:503`）")
        assertEquals(
            1,
            countMatches(screen, Regex("""AppType\.Eyebrow\b""")),
            "眉标档位仍恰一枚（日期那行是本屏唯一的 `.eyebrow`；空态不自带第二枚眉标）",
        )
    }

    @Test
    fun `the empty state adds no color read of its own`() {
        val body = emptyBranchBody()
        assertEquals(
            0,
            countMatches(body, Regex("""appColors\(\)|colors\.(?:tint|surface|brand|onStrong)""")),
            "空态那一支不自己取色：两枚出口的墨色由 `PillButton` 内部按纯表取，" +
                "贴纸与三行文字由共享 `EmptyState` 承载（本屏的取色入口总数由 `TodayPlanNumberAuthorityTest` 管）",
        )
        assertEquals(
            0,
            countMatches(body, Regex("""\bMint[A-Z]\w*|MaterialTheme\.typography\.|FontWeight\.|fontSize|lineHeight|TextStyle\(|Color\(0x""")),
            "批 0 令牌纪律照旧：空态这块也不引入静态桥、库内排印档或自造字号/色值",
        )
        assertEquals(
            1,
            countMatches(body, Regex("""\bEmptyState\(""")),
            "空态那一支的呈现件只有一枚共享 `EmptyState`：不在分支里另铺贴纸、标题与说明",
        )
    }
}
