package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 1 Task 16：新建「今日词单」屏对齐原型 `sLearnQueue`（`assets/app.js:357-390`）。
 *
 * 这一屏在本轮之前**没有承载点**（`docs/superpowers/plans/2026-10-29-ui-prototype-alignment.md`
 * 全 36 屏清单第 2 行）：背词与复习都从今日学习那枚 CTA 直接进词卡，中间没有「今天到底排了哪些词」
 * 这一层。用户 2026-10-29 拍板「三屏全部新建」，本屏是其中之一。
 *
 * 原型这一屏的骨架（逐段对着 `app.js` 抄，行号写在每条判据的消息里）：
 *  `barTop({title:'今日词单', action: .bar-action「调整」→ plan-edit})`
 *   → `.meta` 一句「{planDate} 的词单由「当前词书 + 每日新增 + 到期复习」共同决定。」
 *   → 三枚 `chip()`（全部 / 新学 / 复习，`aria-pressed` 就是本屏的过滤器）
 *   → `section('新学 · N 个词', .rows)` + `section('复习 · N 个词', .rows, '按遗忘曲线排序')`
 *   → `.foot` 一枚主按钮「开始第 N 个词」
 *
 * 两条**有意的偏离**（台账同条登记在下面用例的断言消息里，报告从这里抄）：
 *  1. 原型 `.notice` 那句走向文案整条不搬——它的三个分句在本仓要么由三枚真开关驱动（不写死才不假），
 *     要么与 AGENTS.md「今日计划是不可变快照」直接对冲（「本轮末尾再出现一次」）。同一句话在学习计划屏
 *     已有按真开关动态给出的版本（`PlanEstimate.kt` 的 `revealCaptionText`，Task 12 台账第 3 行同口径）。
 *  2. 原型给到期词一律标「到期」；本仓今日已完成的复习项标「已学」，因为标「到期」是假话
 *     （快照里那一项已经记为完成）。
 *
 * 读法是源码文本（[uiScreenSource] 已剥注释），不渲染 Compose：枚数、具名实参、文案逐字都是文本问题；
 * 像素与可达性侧由 connected 的 `TodayWordListScreenTest` 承担。
 */
class TodayWordListLayoutContractTest {

    private val screen: String by lazy { uiScreenSource("TodayWordListScreen.kt") }

    /** 本屏不得再吃的静态色桥与 M3 排印读点（批 1 逐屏迁移口径，与 `LearningSetupLayoutContractTest` 同表）。 */
    private val forbiddenReads = listOf(
        "MintBackground", "MintSurface", "MintOutline", "MintPrimaryDark", "MintPrimary",
        "MintTextMuted", "MintTint", "MaterialTheme.typography",
    )

    @Test
    fun `the screen takes its header from the shared component and names the prototype page`() {
        assertEquals(
            1,
            countMatches(screen, Regex("""AppScreenHeader\(""")),
            "页头必须恰一处共享 `AppScreenHeader`（原型 `barTop`，`app.js:374`）",
        )
        assertTrue("title = \"今日词单\"" in screen, "页名逐字取原型 `barTop({title:'今日词单'})`")
        assertTrue("onBack = onBack" in screen, "返回位交给共享页头（原型 `.bar` 的 ←）")
        assertEquals(0, countMatches(screen, Regex("← 返回")), "自写「← 返回」字形必须为 0：页头返回位由组件承载")
        assertTrue("text = \"调整\"" in screen, "页头动作逐字取原型 `.bar-action「调整」`（`app.js:374`）")
        assertTrue("onOpenSetup" in screen, "「调整」的目的地是学习计划屏（原型 data-to=plan-edit）")
    }

    @Test
    fun `the meta line states what the list is built from`() {
        assertTrue(
            "的词单由「当前词书 + 每日新增 + 到期复习」共同决定。" in screen,
            "`.meta` 句逐字照原型（`app.js:376`），只把 `D.today.planDate` 换成计划快照的真实日期",
        )
        assertEquals(
            2,
            countMatches(screen, Regex("""AppType\.Meta\b""")),
            "`.meta` 档恰两枚：页头下面那一行次级说明（`app.css:1020` = 13px `--ink-2`）＋页头动作" +
                "`.bar-action`（`:274-286` 逐字 `font-size: var(--fs-meta)` + `font-weight: 600`）。" +
                "第三枚就是本屏多出一行原型没有的次级说明；第四枚即页头动作漂到别的档",
        )
    }

    @Test
    fun `the three prototype chips become the screen filter`() {
        assertEquals(
            3,
            countMatches(screen, Regex("""AppChip\(""")),
            "三枚 chip 恰三枚（`app.js:377-380` 的 全部 / 新学 / 复习），第四枚就是原型没有的过滤器",
        )
        listOf("全部", "新学", "复习").forEach { prefix ->
            assertTrue("""label = "$prefix """ in screen, "chip 文案以原型前缀「$prefix」开头，后面跟真实计数")
        }
        assertEquals(
            3,
            countMatches(screen, Regex("""selected = filter ==""")),
            "三枚 chip 的选中态都绑同一个过滤器状态（原型 `aria-pressed`），不许有一枚绑别的",
        )
        assertTrue("FlowRow(" in screen, "chip 行必须是 FlowRow（原型 `display:flex` 会换行），2× 字号下三枚挤破一行")
    }

    @Test
    fun `the two sections carry the prototype headings and the forgetting-curve note`() {
        assertTrue("新学 · " in screen, "第一节标题前缀逐字（`app.js:363`）")
        assertTrue("复习 · " in screen, "第二节标题前缀逐字（`app.js:369`）")
        assertTrue(" 个词\"" in screen, "两节标题都以「个词」收尾（原型 `' 个词'`）")
        assertEquals(
            1,
            countMatches(screen, Regex("trailing = \"按遗忘曲线排序\"")),
            "`.sec-note`「按遗忘曲线排序」恰一处且挂在复习节（`app.js:371`）——本仓的到期队列正是 " +
                "`dueCardIds` 的 `ORDER BY nextReviewAtEpochMillis ASC`（InternalLearningEventDao:105-111），这句话是真的",
        )
        assertEquals(2, countMatches(screen, Regex("""SectionHeading\(""")), "两节标题 = 恰两枚共享 SectionHeading")
    }

    @Test
    fun `word rows go through the shared component and carry the prototype status words`() {
        assertEquals(
            2,
            countMatches(screen, Regex("""WordRow\(""")),
            "新学节与复习节各调一处共享 WordRow；私有词行就是留第二份版式（棘轮族 4）",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""(?m)^private fun \w*Row\w*\(""")),
            "本屏不得有私有「*Row*」函数：那是绕开共享词行的后门",
        )
        // 原型 `wordRow` 行前那枚 44dp 贴纸取自 `D.words[key].icon`（每词一张插画）；本仓 `WordCard`
        // 没有词级图标字段。留空是**诚实**的形态，拿域徽标填进去就是把「这个域」说成「这个词的贴纸」。
        assertEquals(
            0,
            countMatches(screen, Regex("""leading\s*=""")),
            "词行不得给前导槽塞一枚域徽标冒充原型的词级贴纸（`WordCard` 没有词级图标字段）",
        )
        listOf("已学", "待学", "到期").forEach { status ->
            assertTrue("\"$status\"" in screen, "行尾状态文案「$status」缺一枚：原型 `wordRow(k, 已学/待学/到期)`")
        }
        assertTrue(
            "wordListGloss(" in screen,
            "行副文案走 WordListFormat 那一处口径（词性 + 释义），不在屏侧拼第二份",
        )
        assertTrue("onOpenWord(" in screen, "行本体可点开词义详情（原型 `data-to=word-detail`）")
    }

    @Test
    fun `the sticky foot carries the one primary action and nothing else`() {
        assertEquals(
            1,
            countMatches(screen, Regex("""today_word_list_start""")),
            "吸底主按钮锚点恰一处（原型 `.foot` 的 `btn btn-primary btn-block`，`app.js:388`）",
        )
        assertTrue("开始第 " in screen, "CTA 文案取原型「开始第 N 个词」那一串的前半")
        assertTrue(" 个词\"" in screen, "CTA 文案后半「 个词」逐字")
        assertTrue("onStartLearning" in screen, "CTA 的目的地是词卡流程（原型 data-to=learn-session）")
        assertTrue("hairlineTop(" in screen, "吸底条与滚动区之间那条线由共享 hairlineTop 承载（`.foot` 只有顶部分界）")
        assertEquals(
            0,
            countMatches(screen, Regex("""\bButton\(""")),
            "主按钮走共享 PillButton：屏侧不出现裸 M3 `Button(`（本仓的吸底条另有形态，见 SetupSaveBar，" +
                "本屏不复制那份）",
        )
    }

    @Test
    fun `the prototype notice line stays off this screen`() {
        assertEquals(
            0,
            countMatches(screen, Regex("""InlineNotice\(""")),
            "原型那句走向文案（`app.js:385`）整条不搬：三个分句在本仓要么由三枚真开关驱动、" +
                "要么与「今日计划是不可变快照」对冲（「本轮末尾再出现一次」）。动态版本住在学习计划屏" +
                "（`PlanEstimate.kt`），台账见计划文档批 1 偏离表 Task 16 行",
        )
        assertEquals(0, countMatches(screen, Regex("""本轮末尾""")), "「本轮末尾再出现一次」不得在任何屏出现")
    }

    @Test
    fun `the screen holds no second strong surface and no static ink bridges`() {
        forbiddenReads.forEach { token ->
            assertEquals(0, countMatches(screen, Regex(Regex.escape(token))), "本屏不得读静态桥 / M3 排印：$token")
        }
        assertEquals(0, countMatches(screen, Regex("""StrongCard\(""")), "深色强卡属于今日学习（原型一屏一枚），本屏零枚")
        assertEquals(
            1,
            countMatches(screen, Regex("""appColors\(\)""")),
            "取色入口恰一处 `appColors()`：屏侧不各自抓 CompositionLocal",
        )
    }
}
