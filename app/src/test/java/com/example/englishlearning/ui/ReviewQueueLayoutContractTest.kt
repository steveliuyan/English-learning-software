package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 1 Task 17：新建「复习队列」屏对齐原型 `sReview`（`assets/app.js:472-498`）。
 *
 * 与「今日词单」同族：这一屏在本轮之前**没有承载点**（计划文档全 36 屏清单第 4 行）——复习直接从
 * 今日学习那枚「开始复习」CTA 进词卡，中间没有「到底哪些词到期、为什么现在复习」这一层。
 * 用户 2026-10-29 拍板「三屏全部新建」，本屏是第二枚，入口是今日学习软卡裸行组的第二行
 * （`today_plan_open_review_queue`，原型 `app.js:343` 的 `rowItem({sticker:'clock', …, to:'learn-review'})`）。
 *
 * 原型骨架（逐段对着 `app.js` 抄）：
 *  `barTop({title:'复习队列'})`（**没有** `.bar-action`）
 *   → `.strong-card.on-strong`：`.strong-label「到期复习」` + `.strong-title「N 个词在等你」`
 *     + `.strong-meta「最久的一个已经 9 天没见了…」` + `.meter` 70% + `.strong-foot「…6 天后才会再来」`
 *   → `section('到期词', .rows)`（行尾 `9 天前`）
 *   → `section('复习方式', .card-quiet 两行)`：`看英文，想中文 / 默认方式 / .dot` 与 `听发音，选释义 / 练听力时用`
 *   → `.foot` 主按钮「开始复习」
 *
 * 三条**有意的偏离**（原型写死数据，本仓没有对应的采集，一律整块不搬，不换成同义改写、也不造新指标）：
 *  1. `.strong-meta`「最久的一个已经 9 天没见了」与行尾「9 天前」——两者都要 `lastReviewedAtEpochMillis`
 *     按卡参与界面读取；本屏只吃已经备好的计划快照状态（`TodayPlanUiState.Ready`），不为这两句新开一条
 *     仓库→界面的读数通路。
 *  2. `.meter` 70% 与 `.strong-foot`「今天复习过的词 6 天后才会再来」——前者那 70% 在原型的 mock 里
 *     没有任何一句说明它量的是什么，后者是把 FSRS 的**逐卡**下次间隔写死成一句全局话。同屏同族的
 *     「不可核验的进度块整块删除」先例是批 1 Task 8 打卡页的完成度环。
 *  3. 「听发音，选释义」整行不搬——本仓没有"播放音频让用户从若干释义里选"这一模式（词卡流程只有
 *     看英文想中文 + 三档反馈）。按「无后端就隐藏」口径处理（用户 2026-10-29 拍板）。
 *     留下的那一行「看英文，想中文 / 默认方式」是本仓真实默认模式，`.dot` 就是原型那枚选中点。
 *
 * 读法是源码文本（[uiScreenSource] 已剥注释），不渲染 Compose；像素与可达性由 connected 的
 * `ReviewQueueScreenTest` 承担。
 */
class ReviewQueueLayoutContractTest {

    private val screen: String by lazy { uiScreenSource("ReviewQueueScreen.kt") }

    /** 本屏不得再吃的静态色桥与 M3 排印读点（批 1 逐屏迁移口径，与 `TodayWordListLayoutContractTest` 同表）。 */
    private val forbiddenReads = listOf(
        "MintBackground", "MintSurface", "MintOutline", "MintPrimaryDark", "MintPrimary",
        "MintTextMuted", "MintTint", "MaterialTheme.typography",
    )

    @Test
    fun `the header is the prototype bar top with no action slot`() {
        assertEquals(
            1,
            countMatches(screen, Regex("""AppScreenHeader\(""")),
            "页头恰一处共享 `AppScreenHeader`（原型 `barTop`，`app.js:475`）",
        )
        assertTrue("title = \"复习队列\"" in screen, "页名逐字取原型 `barTop({title:'复习队列'})`")
        assertTrue("onBack = onBack" in screen, "返回位交给共享页头（原型 `.bar` 的 ←）")
        assertEquals(
            0,
            countMatches(screen, Regex("""\baction = \{""")),
            "原型这一屏的 `barTop` **没有** `.bar-action`（对比 `sLearnQueue` 的「调整」），" +
                "动作位塞任何东西都是原型没画的入口。判据写 `action = {` 而不是 `action`，" +
                "因为 WordRow 的 `actions = {`（那枚选中点）是合法读点",
        )
        assertEquals(0, countMatches(screen, Regex("← 返回")), "自写「← 返回」字形必须为 0：页头返回位由组件承载")
    }

    @Test
    fun `the strong card carries the label and the queue count only`() {
        assertEquals(1, countMatches(screen, Regex("""StrongCard\(""")), "一屏一枚强档卡（原型 `.strong-card.on-strong`，`app.js:477`）")
        assertEquals(
            1,
            countMatches(screen, Regex("""AppType\.StrongTitle\b""")),
            "`.strong-title`（`app.css:410` = serif 27px）恰一处，就是「N 个词在等你」那一行；" +
                "本屏是这份档位仓内第一处真实调用点",
        )
        assertTrue("\"到期复习\"" in screen, "`.strong-label` 逐字（`app.js:478`）")
        assertTrue(" 个词在等你" in screen, "`.strong-title` 后半句逐字（`app.js:479`），前半是真实计数")
        assertTrue(
            "state.dueQueue.size" in screen,
            "计数取**列出行数**（原型 `due.length`），与今日学习入口行取的快照目标数分权——同一口径见 Task 16 台账第 9 行",
        )
    }

    @Test
    fun `the three mock metric blocks stay off the strong card`() {
        listOf("最久", "没见", "才会再来", "8 分钟").forEach { token ->
            assertEquals(0, countMatches(screen, Regex(Regex.escape(token))), "原型 mock 句「$token」不得出现在屏侧（没有对应采集）")
        }
        listOf("LinearProgressIndicator", "PlanProgressBar", "ProgressRing", "ThinProgress").forEach { token ->
            assertEquals(0, countMatches(screen, Regex(Regex.escape(token) + "\\(")), "`.meter` 70% 无口径说明，整块删除（先例：批 1 Task 8 打卡页完成度环）：$token")
        }
    }

    @Test
    fun `the due section lists the queue through the shared word row`() {
        assertEquals(2, countMatches(screen, Regex("""SectionHeading\(""")), "两节标题 = 恰两枚共享 SectionHeading（原型 `section('到期词'…)` + `section('复习方式'…)`）")
        assertTrue("\"到期词\"" in screen, "第一节标题逐字（`app.js:484`）")
        assertTrue("\"复习方式\"" in screen, "第二节标题逐字（`app.js:491`）")
        assertEquals(
            2,
            countMatches(screen, Regex("""WordRow\(""")),
            "到期词一节与复习方式一节各调一处共享 WordRow；私有行就是留第二份版式（棘轮族 4）",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""(?m)^private fun \w*Row\w*\(""")),
            "本屏不得有私有「*Row*」函数：那是绕开共享词行的后门",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""\bstatus\s*=""")),
            "到期词行尾不写状态文案：原型那一格是「9 天前」（要逐卡上次复习时间，本屏没有这条通路），" +
                "空着比编一枚诚实度存疑的词更贴近原型",
        )
        assertTrue("wordListGloss(" in screen, "行副文案走 WordListFormat 那一处口径（词性 + 释义），不在屏侧拼第二份")
        assertTrue("onOpenWord(" in screen, "行本体可点开词义详情（与今日词单同一载体）")
        assertEquals(
            1,
            countMatches(screen, Regex("""topDivider = index > 0""")),
            "裸行列表的分线只由行号给一次（首行无线）；两处就是两节各拼一份缝",
        )
    }

    @Test
    fun `the review mode section keeps only the mode this app can do`() {
        assertEquals(1, countMatches(screen, Regex("""QuietCard\(""")), "`.card-quiet`（`app.css:375`）= 共享 QuietCard，恰一处")
        assertTrue("\"看英文，想中文\"" in screen, "默认模式行标题逐字（`app.js:492`）")
        assertTrue("\"默认方式\"" in screen, "默认模式行副案逐字（`app.js:492`）")
        assertEquals(0, countMatches(screen, Regex("听发音")), "「听发音，选释义」整行不搬：本仓没有这一模式（用户 2026-10-29 拍板「无能力就隐藏」）")
        assertEquals(0, countMatches(screen, Regex("练听力")), "副案「练听力时用」随行一起不搬")
        assertEquals(
            1,
            countMatches(screen, Regex("""AppIcon\.Document\b""")),
            "默认模式行的前导贴纸取原型 `st('document', 44)` 那一枚（`app.js:492`），经共享 InlineIconBadge 给",
        )
        assertTrue("colors.brandDeep" in screen, "`.dot` 的墨 = `--brand-deep`（`app.css:646`），选中点由调用点显式取色")
    }

    @Test
    fun `the sticky foot carries the one primary action and nothing else`() {
        assertEquals(
            1,
            countMatches(screen, Regex("""review_queue_start""")),
            "吸底主按钮锚点恰一处（原型 `.foot` 的 `btn btn-primary btn-block`，`app.js:496`）",
        )
        assertTrue("text = \"开始复习\"" in screen, "CTA 文案逐字取原型「开始复习」（`app.js:496`）")
        assertTrue("onStartReview" in screen, "CTA 的目的地是词卡流程（原型 data-to=learn-session，与今日学习的「开始复习」同一条路）")
        assertTrue("hairlineTop(" in screen, "吸底条与滚动区之间那条线由共享 hairlineTop 承载")
        assertEquals(0, countMatches(screen, Regex("""\bButton\(""")), "主按钮走共享 PillButton：屏侧不出现裸 M3 `Button(`")
    }

    @Test
    fun `an empty queue renders neither the section nor the foot`() {
        assertTrue(
            "if (dueCount > 0)" in screen,
            "0 个到期词时「到期词」整节不渲染、吸底整条不出现（原型写死数据，没画过空队列；" +
                "与 Task 16「空节不渲染 / 剩余 0 时吸底不出现」同口径）",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""今日没有到期的词|没有需要复习""")),
            "空态文案不得自创：原型没画这一态，写一句就是编造文案",
        )
    }

    @Test
    fun `the screen holds no static ink bridges and one color entry`() {
        forbiddenReads.forEach { token ->
            assertEquals(0, countMatches(screen, Regex(Regex.escape(token))), "本屏不得读静态桥 / M3 排印：$token")
        }
        assertEquals(
            1,
            countMatches(screen, Regex("""appColors\(\)""")),
            "取色入口恰一处 `appColors()`：屏侧不各自抓 CompositionLocal",
        )
        assertEquals(0, countMatches(screen, Regex("""FontWeight\.""")), "字重由 AppType 档位给，屏侧不另拧 FontWeight")
    }
}
