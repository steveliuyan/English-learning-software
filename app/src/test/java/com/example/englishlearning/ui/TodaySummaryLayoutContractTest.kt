package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 1 Task 18：新建「今日完成」屏对齐原型 `sSummary`（`assets/app.js:440-469`）。
 *
 * 这一屏在本轮之前没有承载点（计划文档全 36 屏清单第 5 行）：词卡流程走完最后一张就停在
 * `WordCardScreen` 的 `AllDone` 分段里（「今天的词卡都提交完了」+ 一行计数 + 一枚「返回今日计划」），
 * 收尾没有「今天过了多少词、还差哪一步」这一层。用户 2026-10-29 拍板「三屏全部新建」，本屏是第三枚，
 * 入口就是原型的入口（`app.js:1688`、`:1701`：末张卡推进 → `go('learn-summary')`），宿主在
 * `showLearning` 分支里按 `WordCardUiState.AllDone` 挂本屏、不再挂词卡屏。
 *
 * 原型骨架（逐段对着 `app.js` 抄）：
 *  `barTop({title:'今日完成', action: .bar-action「完成」→ learn-home})`
 *   → `.mt4` hero：`badge-lg`（64dp / 圆角 20）里一枚 `star` 贴纸 + `.eyebrow`（计划日期）+ `.serif` 26px「今天的词都过了一遍」
 *   → `.card.mt5` 三列统计格：`个词`（`p.newTarget + p.dueTarget`）/ `用时 6:42` / `一次答对 82%`
 *   → `section('这一轮的反馈', .card-quiet 三行)`：`认识 / 模糊 / 忘记` + 各自 `row-sub` + `row-value` 计数
 *   → `section('还差一步', .rows)`：`读今天的文章`（`4 段 · 覆盖 4 个今日词`，value `未读`）+ `8 分钟后再看 1 个词`
 *   → `.foot` 主按钮「回到今日」
 *
 * 三处**有意的偏离**（原型是 mock 数据，本仓没有对应的采集，整块不搬、不换成同义改写、也不造新指标；
 * 依据是计划「推进决定」第 3 条：读到不到的那枚整枚不显示，新增采集属功能改动、未获授权）：
 *  1. `用时` 与 `一次答对`：本仓不记单轮用时，也不记「一次答对率」（`LearningEvent` 只有逐卡 feedback，
 *     没有尝试次数）。三列统计格只剩 `个词` 一枚有真数，所以那一格按「有几枚真数占几列」铺，
 *     不是照抄三列再挖两个空位。
 *  2. `这一轮的反馈` 整节：三枚计数要按档聚合今天的 `LearningEvent`，是一条新的仓库→界面读数通路
 *     （本屏只吃已经备好的 `WordCardUiState.AllDone`）；三行 `row-sub`「下次间隔 6 天 / 明天再来 /
 *     今天晚些时候再问一次」又与 `V1ReviewScheduler` 的真实档位（Again 10 分钟 / Hard 1 天 / Good 3 天）
 *     和「一次反馈即完成、本轮不重排」对冲。同口径先例：批 1 Task 17 的三块 mock 指标、Task 8 的完成度环。
 *  3. `还差一步` 的第二行 `8 分钟后再看 1 个词 / 忘记的词会在最佳时点回来`：本仓「忘记」的词不会在本轮
 *     再出现（AGENTS.md：今日计划是首次进入时生成的不可变快照；`WordCardViewModel` 末张判据就是这条），
 *     那枚 `1` 也没有备好的数。留下的「读今天的文章」是本仓真有的下一步，`未读 / 已读` 取宿主的
 *     `checkInState.today.completedReadingCount`（今日学习那条裸行同一份权威）。
 *
 * 读法是源码文本（[uiScreenSource] 已剥注释），不渲染 Compose；像素与可达性由 connected 的
 * `TodaySummaryScreenTest` 承担，挂载与返回键由 `AppScreenTest` 承担。
 */
class TodaySummaryLayoutContractTest {

    private val screen: String by lazy { uiScreenSource("TodaySummaryScreen.kt") }

    /** 本屏不得再吃的静态色桥与 M3 排印读点（批 1 逐屏迁移口径，与 `ReviewQueueLayoutContractTest` 同表）。 */
    private val forbiddenReads = listOf(
        "MintBackground", "MintSurface", "MintOutline", "MintPrimaryDark", "MintPrimary",
        "MintTextMuted", "MintTint", "MaterialTheme.typography",
    )

    @Test
    fun `the header is the prototype bar top with the 完成 action`() {
        assertEquals(
            1,
            countMatches(screen, Regex("""AppScreenHeader\(""")),
            "页头恰一处共享 `AppScreenHeader`（原型 `barTop`，`app.js:444`）",
        )
        assertTrue("title = \"今日完成\"" in screen, "页名逐字取原型 `barTop({title:'今日完成'})`（`app.js:444`）")
        assertTrue("onBack = onBack" in screen, "返回位交给共享页头（原型 `.bar` 的 ←）")
        assertTrue("text = \"完成\"" in screen, "页头动作逐字取原型 `.bar-action「完成」`（`app.js:444`）")
        assertEquals(0, countMatches(screen, Regex("← 返回")), "自写「← 返回」字形必须为 0：页头返回位由组件承载")
        assertEquals(
            1,
            countMatches(screen, Regex("""AppType\.Meta\b""")),
            "`AppType.Meta` 恰一枚 = 页头动作 `.bar-action`（`app.css:274-286` 逐字 `font-size: var(--fs-meta)` + " +
                "`font-weight: 600`）。本屏原型没有 `.meta` 说明行（那是 `sLearnQueue` 的），多一枚就是自己加一层",
        )
    }

    @Test
    fun `the hero is the 64dp star badge with the date eyebrow and the serif line`() {
        assertEquals(
            1,
            countMatches(screen, Regex("""StickerBadge\(""")),
            "hero 那枚徽标走共享 `StickerBadge`（原型 `.badge`，`app.css:550-560`），屏侧不自铺底与内描边",
        )
        assertTrue("Sticker.Star" in screen, "贴纸逐字取原型 `star.png`（`app.js:448`）")
        assertTrue(
            "StickerSize.Large" in screen,
            "档位取 `StickerSize.Large`（64dp / 圆角 20），逐字对应原型 `.badge-lg`（`app.css:560`）",
        )
        assertTrue("dateLabel" in screen, "`.eyebrow` 那格是本仓真实计划日期（宿主的 `localDateLabel`），不是原型的 `D.today.planDate`")
        assertEquals(
            1,
            countMatches(screen, Regex("""AppType\.Eyebrow\b""")),
            "日期那行是原型 `.eyebrow`（`app.css` 11px / 600 / `--ink-3`）= `AppType.Eyebrow`，恰一枚",
        )
        assertTrue("今天的词都过了一遍" in screen, "hero 主句逐字（`app.js:450`）")
        assertEquals(
            2,
            countMatches(screen, Regex("""AppType\.StatNum\b""")),
            "`AppType.StatNum`（26sp serif，逐字等于原型 `.stat-num`，`app.css:696`）恰两枚：hero 那句 `.serif` 26px" +
                "（`app.js:450` 的行内字号）与统计格的数字位。第三枚就是本屏多开一处 26px serif 位",
        )
    }

    @Test
    fun `the stat card carries only the count this app actually knows`() {
        assertEquals(
            1,
            countMatches(screen, Regex("""AppCard\(""")),
            "统计格外壳是原型 `.card`（`app.css:363`：`surface` 底 + 1dp `line` 描边）= 共享 `AppCard`，恰一枚",
        )
        assertTrue("state.completedCount" in screen, "`个词` 取本轮**真提交完**的卡数（`AllDone.completedCount`），与今日学习那张强卡的计划目标数分权（Task 16 台账第 7 行同口径）")
        assertTrue("\"个词\"" in screen, "统计档名逐字（`app.js:453`）")
        listOf("用时", "一次答对", "6:42", "82").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "原型 mock 指标「$token」整枚不显示：本仓不采集它，新增采集属功能改动、未获授权" +
                    "（计划「推进决定」第 3 条；Task 12 台账第 6 行同口径）",
            )
        }
        assertEquals(
            0,
            countMatches(screen, Regex("""(?m)^private fun \w*Cell\w*\(""")),
            "统计格现在只有一枚真数，屏侧不预先给「以后可能有几枚」留私有排版函数（那是第二份版式的入口）",
        )
    }

    @Test
    fun `the per-round feedback section stays off the screen`() {
        listOf("这一轮的反馈", "下次间隔", "明天再来", "今天晚些时候", "忘记的词").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "反馈节整节不搬：三枚计数要新开按档聚合的读数通路，三行副案又与 V1 真实档位对冲",
            )
        }
        assertEquals(
            0,
            countMatches(screen, Regex("""QuietCard\(""")),
            "反馈节的 `.card-quiet`（`app.js:457`）随整节一起不出现；本屏没有安静卡这一档",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""LinearProgressIndicator|PlanProgressBar|ProgressRing|ThinProgress""")),
            "本屏不许长出任何进度件：原型这一屏没有进度条，统计格是三格数字",
        )
    }

    @Test
    fun `the next step section keeps only the row this app can hand over`() {
        assertEquals(1, countMatches(screen, Regex("""SectionHeading\(""")), "整屏只剩「还差一步」一节，节标题恰一枚共享 `SectionHeading`")
        assertTrue("\"还差一步\"" in screen, "节标题逐字（`app.js:462`）")
        assertTrue("读今天的文章" in screen, "行标题逐字（`app.js:463`）")
        assertEquals(
            1,
            countMatches(screen, Regex("""BareRow\(""")),
            "「还差一步」是卡片汤之外的裸行列表（原型 `.rows`），走共享 `BareRow`，恰一行",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""(?m)^private fun \w*Row\w*\(""")),
            "本屏不得有私有「*Row*」函数：那是绕开共享行的后门（棘轮族 4）",
        )
        assertTrue("去读今天生成的短文" in screen, "副案取今日学习那条裸行已在用的真话（`TodayPlanScreen` 解锁态同句），不搬原型 `4 段 · 覆盖 4 个今日词`")
        assertEquals(
            0,
            countMatches(screen, Regex("""4 段|覆盖""")),
            "段数与覆盖词是原型 mock：文章还没生成就说不出这两个数",
        )
        assertTrue("if (articleRead)" in screen, "`未读 / 已读` 绑宿主的 `checkInState` 真读数，与今日学习那条行同一份权威")
        assertTrue("\"未读\"" in screen && "\"已读\"" in screen, "行尾取值逐字（原型 `value: '未读'`，本仓两态都说得出）")
        assertEquals(
            0,
            countMatches(screen, Regex("""后再看|最佳时点""")),
            "「8 分钟后再看 1 个词」整行不搬：本仓「忘记」的词不在本轮重排（Task 16 台账第 2 行同一条对冲）",
        )
        assertEquals(1, countMatches(screen, Regex("""AppIcon\.Newspaper\b""")), "行前导取原型 `sticker:'newspaper'` 那一枚（`app.js:463`）")
        assertTrue("onOpenReading" in screen, "行的目的地是阅读（原型 `data-to=\"read-article\"`），由宿主接线")
    }

    @Test
    fun `the sticky foot carries the one primary action and nothing else`() {
        assertEquals(
            1,
            countMatches(screen, Regex("""today_summary_back_to_today""")),
            "吸底主按钮锚点恰一处（原型 `.foot` 的 `btn btn-primary btn-block`，`app.js:467`）",
        )
        assertTrue("text = \"回到今日\"" in screen, "CTA 文案逐字取原型「回到今日」（`app.js:467`）")
        assertTrue("hairlineTop(" in screen, "吸底条与滚动区之间那条线由共享 `hairlineTop` 承载（本仓吸底槽统一这一形，见 Task 16 台账第 1 行）")
        assertEquals(0, countMatches(screen, Regex("""\bButton\(""")), "主按钮走共享 `PillButton`：屏侧不出现裸 M3 `Button(`")
    }

    @Test
    fun `the screen holds no static ink bridges and one color entry`() {
        forbiddenReads.forEach { token ->
            assertEquals(0, countMatches(screen, Regex(Regex.escape(token))), "本屏不得读静态桥 / M3 排印：$token")
        }
        assertEquals(
            1,
            countMatches(screen, Regex("""appColors\(\)""")),
            "取色入口恰一处 `appColors()`：屏侧不各自抓 CompositionLocal，也不给统计格另开一枚私有排版函数",
        )
    }

    /**
     * 宿主接线（台账第 10 行）：收尾屏由 `AppScreen` 的 `showLearning` 分支按 `AllDone` 挂，
     * **不给本屏新置导航状态位**，三个出口同一条 `exitLearning`。
     *
     * 为什么值得在源码侧钉一条（connected 那两条 `AppScreenTest` 已证行为）：这一屏与前三枚新建屏的
     * 接线形状**不同族**——词单 / 复习队列各有一枚 `show*` 状态位、`overlayOpen` 项与专属 `BackHandler`，
     * 本屏一样都不许有。若有人照那一族补一枚 `showTodaySummary`，返回键就要在两处各让一次位、
     * `overlayOpen` 聚合也会多一项，而原型三个出口与系统返回键本来就同构（全回 learn-home 并重算计划），
     * 多出来的那一位只会让「收尾屏压住底 tab」这类判断多一个漏写点。`showLearning` 与
     * `BackHandler(enabled = showLearning)` 各仍恰一处命中，就是这条漏写点的反向保险。
     */
    @Test
    fun `the host mounts it on the AllDone state without a new navigation bit`() {
        val host = uiScreenSource("AppScreen.kt")
        assertEquals(1, countMatches(host, Regex("""TodaySummaryScreen\(""")), "宿主挂载本屏恰一处")
        assertTrue("cardState is WordCardUiState.AllDone" in host, "分派条件是词卡状态的 `AllDone`，不是新的状态位")
        assertTrue("dateLabel = todayReady?.localDateLabel" in host, "日期取计划快照（未就绪就是 null，屏侧整行不进组合）")
        assertTrue("articleRead = todayArticleRead" in host, "「未读 / 已读」取打卡快照那份权威（今日学习那条行同一份）")
        assertEquals(
            1,
            countMatches(host, Regex("""onBack = exitLearning,""")),
            "页头 ← 接的是宿主那条唯一 `exitLearning`（回今日学习**且重算计划**，F1-04）；" +
                "写成只关浮层的半边退出，今日学习的数字会停在进流程之前的那份快照上",
        )
        assertEquals(
            1,
            countMatches(host, Regex("""onDone = exitLearning,""")),
            "页头「完成」与吸底「回到今日」共用同一条退出通路（原型两个出口都是 `data-to=\"learn-home\"`），" +
                "全仓只此一处 `onDone = exitLearning`——另写一份就是两条退出通路",
        )
        assertEquals(
            0,
            countMatches(host, Regex("""showTodaySummary|showSummary""")),
            "不得为收尾屏新置导航状态位（本屏由状态分派，`overlayOpen` 与 `BackHandler` 沿用 `showLearning`）",
        )
        assertEquals(
            1,
            countMatches(host, Regex("""BackHandler\(enabled = showLearning\)""")),
            "`BackHandler(enabled = showLearning)` 仍只有一条：返回键由它接管，不给本屏另加一条",
        )
    }

    /**
     * 屏侧红线：收尾屏绝不碰播放器（F5-03 移交账的口径延续）。
     *
     * 换成这一屏时词卡屏自己离开组合、由它的退出停播守卫把停播恰好委托给宿主一次；
     * 本屏若长出一枚 `onSpeak` / provider 读点，就出现「同一轮音频两处都能控制」的第二条通路，
     * 那条守卫的计数判据（`AppScreenTest` 的 `stopCalls` 恰一次）也随之失真。
     */
    @Test
    fun `the summary screen never touches the pronunciation player`() {
        listOf("Pronunciation", "speakWord", "stopPlayback", "onSpeak", "Tts").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "收尾屏不碰播放器，也不接发音回调：停播归词卡屏那条退出守卫（`AppScreenTest` 钉计数）：$token",
            )
        }
    }
}
