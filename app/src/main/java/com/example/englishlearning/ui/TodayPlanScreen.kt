package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppGlyph
import com.example.englishlearning.ui.components.AppGlyphLayer
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.EmptyState
import com.example.englishlearning.ui.components.InlineIconBadge
import com.example.englishlearning.ui.components.PlanProgressBar
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.Sticker
import com.example.englishlearning.ui.components.StrongCard
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * 学习页（2026-10-25 版式重做 §3.2）：**搜索 / 强档卡 / 今日词单 / 今日文章 / 词库** 五段。
 *
 * 为什么只剩这几段：批 2 的「1 主 CTA + 一组入口行」把八个按钮收成一屏九块彩色容器，用户读到的是
 * 「一个学习页面太挤」。版式重做把入口按目的地拆开——目录型动作不再占用「今天做什么」的版面：
 *  - 词书单词 / 我的词 → **词库**页（`WordBankScreen`，底部一级以外的一个聚合页）；
 *  - 学习记录 / 打卡与成就 → **我的**页（`ProfileScreen`）；
 *  - 调整词书与目标 → 我的页里的设置组（词书组）。
 * 于是本屏只回答两件事：今天还差多少（强档卡）、接下来去哪（今日词单 / 复习队列 / 今日文章 / 词库四条裸行）。
 * 头两条是批 1 补的原型入口（`assets/app.js:342-343` 的
 * `rowItem({sticker:'list', to:'learn-queue'})` 与 `rowItem({sticker:'clock', to:'learn-review'})`）：
 * 「今天到底排了哪些词」与「哪些词到期」此前都没有承载点，背词与复习都从主 CTA 直接进词卡。
 * 五枚入口锚（`today_plan_open_wordbook_words` / `_my_words` / `_learning_records` / `_check_in` /
 * `_setup_entry`）随功能一起离场，判决由 `TodayPlanBareRowContractTest` 双向钉住。
 *
 * 三档容器各司其职（§4.1）：
 *  - [StrongCard] 恰一枚：浅套「浅薄荷一档 `tint2` + `text1`」、深套「深墨实底 `strongSurface` +
 *    `onStrong`」——分套由容器自己定（2026-10-31 用户评审 ③：浅套那块深墨板太重、喧宾夺主）。
 *    **卡上的正文必须显式 `color = LocalContentColor.current`**：`AppType` 六档的 getter 自带
 *    `text1/text2`，压深套的深底不可读，`LocalContentColor` 拦不住它们；无色的 `Text` 才吃容器内容色。
 *    卡内一律**不写死 `onStrong`**：那是上一版「双套都是深底」的取法，浅套改浅之后会得到浅底近白字。
 *  - [SoftCard] 承载四条裸行，不描边——层次由 `surface` 与强档卡的明度差表达。
 *  - [BareRow] 52dp 行高（≥ F5-03 的 48dp 水线），第二、三、四条各带 1dp 发丝线（`topDivider`）。
 *
 * 数字的权威仍只有一处（AC-R-20）：`AppType.PlanNum`（32sp serif，原型 `.plan-count b`）全屏恰一枚，
 * 落在今日完成数那一格；其余数字一律 `Footnote`，墨色与卡上正文同一支取色入口（`LocalContentColor.current`）。
 * 卡外那行状态注脚压 `text2`（原型 `.caption` 的 `--ink-2`）。
 * 这条连同「本屏零 `EntryRow`、恰四条 `BareRow`」由 `TodayPlanNumberAuthorityTest` 与
 * `TodayPlanBareRowContractTest` 分别按源码形状钉住。
 *
 * 强卡版面（原型 `sLearnHome`，`assets/app.js:316-336`）：眉标「今日计划」→ 32sp 计数 + 「 / N 个词」→
 * 右侧「还剩 N 个词」→ 双段进度条（`PlanProgressBar`）→ 图例（新学 / 复习）→ 主 CTA + 「计划」次按钮。
 * 次按钮原型是 `.btn-secondary`（透明底 + 32% 描边），本应用取 `PillStyle.Secondary`
 * （surface 底 + outlineSoft 描边）——两者同为「有边界的次级按钮」，色值差登记在批 1 报告。
 *
 * 主 CTA 两态同锚点（`today_plan_start_learning`），文案随状态换：未学完 →「开始学习」、
 * 新词学完但仍欠复习 →「开始复习」。阅读的解锁说明不再单起一行（旧 `today_plan_open_reading` 那枚 M3
 * `Button` 已降为裸行），而是由行标题的「未解锁 / 已读 / 可阅读」与卡外那行注脚共同承载。
 *
 * ## 批 1 Task 19：「今天全学完了」那一态（原型 `sLearnEmpty`，`assets/app.js:501-518`）
 *
 * 原型把这一态画成「学习」这个 tab 的另一份 `.scr`（同一个 `nav('learn-home')`、同一个页名），
 * 与 `sLearnHome` 不共存——所以本屏在 Ready 分支里按 `todayAllDone` 二选一，而不是新建第六屏
 * （本轮获授权的新增屏只有五枚，见 `docs/superpowers/plans/2026-10-29-ui-prototype-alignment.md`
 * 「推进决定」第 2 条）。这一态在场时，强档卡、卡外注脚、「接下来」节标题与四条裸行**整段不进组合**：
 * 原型那一屏的 `.scr-body` 只有 `emptyState` 与 `section('明天')` 两块。
 *
 *  1. [EmptyState] 走批 0 的共享件（本仓第一处真实调用点）：84dp `star` 贴纸 + 15sp/600 标题
 *     「今天的词都学完了」+ 12sp `text3` 说明 + 尾部动作槽，度量逐字对 `app.css:931-940`。
 *     说明里的两枚数量分句取**计划快照的目标数**（与未学完那一支的入口行、强卡图例同一权威），
 *     某一档为 0 时那一枚分句整段不拼——「新学 0 个都已完成」是把「今天没排新词」说成「排了 0 个并学完」。
 *  2. 尾部两枚出口逐字取原型那枚 260dp 网格：primary「调整每日新增」→ [onOpenSetup]、
 *     secondary「去读今日文章」→ [onOpenReading]。「复习生词本」随四态主 CTA 一起下线：
 *     那一枚从来没有被画在任何一屏上，生词段的出口是词库页自己的分段控件（四格 tab），
 *     「我的」页词库格开的是同一页——`onReviewWords` 形参一并摘掉，不留无人调用的接线。
 *  3. 页头按 `.head-plain` 撤齿轮、副标题只留「连续打卡 N 天」。`.head-plain` 全原型只两处
 *     （`app.js:503` 与 `:1257` 的「我的」根页），共同点是「这一屏不办事、只交代状态」，
 *     所以这是画法不是漏画；`streakDays == 0` 时那一行整行不出现（既有口径：不摆「连续 0 天」）。
 *  4. `section('明天')` 整节不搬：「明天新学 4 个词」要么把今天的 `newTarget` 当成明天的（设置默认
 *     次日生效，今天改过就成假话），要么新开一条明日份额预生成读数；「今天模糊的 2 个」要按档聚合
 *     今天的 `LearningEvent`（与今日完成屏 `section('这一轮的反馈')` 同一条下线理由）；
 *     「约 6 个词」是派生数；「文章会在 0 点后生成」在本仓不成立——今日文章是解锁后按需生成。
 *     依据是「推进决定」第 3 条：读不到的那枚整枚不显示，新增采集属功能改动、未获授权。
 *
 * 完成判定 `todayAllDone` 在页头之前算一次（coerce 口径与强卡的「x / N」同源），页头的齿轮与副标题
 * 和 Ready 分支都吃同一个值——两处各写一遍就是第二份权威。
 *
 * **Ready 分支的渲染体刻意不抽成私有组合函数**：`TodayPlanNumberAuthorityTest` 按花括号配平取
 * Ready 分支体，要求主 CTA 的 testTag 与 `PillButton` 计数都长在体内（评审 I-4 的变异形是
 * 「把 CTA 抽到分支之后另写的私有组合件」，分支渲染为空而 tail 计数照旧绿）。抽出去就等于
 * 放宽那条判据，故保留内联。
 *
 * [streakDays] / [todayArticleRead] 由宿主（`AppScreen`）从打卡快照里取：本屏不自己去读仓库，
 * 与「业务规则在 ViewModel/仓库、版面只读状态」的分工一致。
 *
 * ## 2026-10-30 版式重做（可见层）→ 批 1 起按原型对齐
 *
 *  - 页头走 `AppScreenHeader(hero = true)`：眉标 = 计划日期（原型 `.eyebrow`）、28sp 大标题、
 *    副标题「连续打卡 N 天 · 今日目标 N 个词」（原型 `.head-sub`）、行尾 40dp 齿轮动作。
 *    40dp 插画徽标（`AppIcon.Clock`）随批 1 撤掉：原型的 `.head` 里没有徽标位，
 *    层级由「眉标 + 大标题 + 副标题」讲。
 *  - 强卡把「今天完成了几个」与「还剩几个」并排，下面一根双段条 + 图例；
 *    旧的 78dp `ProgressRing` 与两条 `ThinProgress` 一并下线（原型 `assets/app.css:427`
 *    明确写着「一屏一根进度条，不再用圆环」）。
 *  - 强卡与软档卡之间留一行 `SectionHeading`「接下来」：一级页是「一件大事 + 一组小事」。
 * 三处都不新增可点节点（首页用例按「可点节点闭合集合」逐枚数），组件也不读静态色。
 */
@Composable
fun TodayPlanScreen(
    state: TodayPlanUiState,
    onRetry: () -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onStartLearning: () -> Unit = {},
    onOpenReading: () -> Unit = {},
    onOpenWordBank: () -> Unit = {},
    /** 「今日词单」裸行的落点（批 1 Task 16，原型 `to:'learn-queue'`）：打开新建的 [TodayWordListScreen]。 */
    onOpenWordList: () -> Unit = {},
    /** 「复习队列」裸行的落点（批 1 Task 17，原型 `to:'learn-review'`）：打开新建的 [ReviewQueueScreen]。 */
    onOpenReviewQueue: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    streakDays: Int = 0,
    todayArticleRead: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val ready = state as? TodayPlanUiState.Ready
    // 「今天排了任务、且每一颗都提交完」= 原型 `sLearnEmpty` 那一态的触发条件（`app.js:501-518`）。
    // 在页头之前算一次：页头的 `.head-plain`（不挂齿轮）与副标题那一档都跟着它，Ready 分支复用同一个值。
    // coerce 与强卡的「x / N」同一口径——多提交的那几颗不该把「还剩 0 个词」算成负数，也不该把
    // 「一个任务都没排」（`totalTasks == 0`）算成「学完了」：那一态走卡外「今天没有待办」那条注脚。
    val todayAllDone = ready?.let { plan ->
        val done = plan.newDone.coerceAtMost(plan.newTarget) + plan.dueDone.coerceAtMost(plan.dueTarget)
        plan.totalTasks > 0 && done >= plan.totalTasks
    } ?: false
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            // 底部导航会吃掉 64dp，这里必须可滚动，否则小屏上最后一段会被裁掉。
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("today_plan_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 原型 `.head`（`assets/app.js:309-314`）：眉标 = 计划日期、副标题 = 连续打卡 + 今日目标、
        // 行尾一枚 40dp 齿轮动作。徽标位（批 0 那枚 Clock 插画）按原型撤掉——`.head` 里没有徽标，
        // 层级由「眉标 + 28sp 大标题 + 副标题」讲，多一枚 40dp 图章是原型没有的排印。
        //
        // 「今天全学完了」那一态走原型的 `head({plain:true})`（`app.js:503`）：不挂齿轮、副标题只剩
        // 「连续打卡 N 天」。「今日目标 N 个词」在那一屏是已经完成的事，原型把它从 `.head-sub` 里拿掉了，
        // 本屏也照拿（这一份文案只留在未学完那一档，别让同一句话在两态各写一遍）。
        AppScreenHeader(
            title = "今日学习",
            hero = true,
            eyebrow = ready?.localDateLabel,
            subtitle = ready?.let { plan ->
                // 没有连续记录时不摆「连续 0 天」：那是把「还没开始」说成「断了」（既有口径）。
                val streak = if (streakDays > 0) "连续打卡 $streakDays 天" else null
                val goal = "今日目标 ${plan.totalTasks} 个词"
                when {
                    todayAllDone -> streak
                    streak != null -> "$streak · $goal"
                    else -> goal
                }
            },
            action = { if (!todayAllDone) SettingsAction(onClick = onOpenSettings) },
        )
        // 查词入口**四态常驻**，位置在 `when (state)` 之前（F3 / 评审 I-1）：计划读不出来时，
        // 用户仍要知道去哪儿查词（AGENTS.md「无网络或无 AI Key 时……离线查词仍应可用」）。
        SearchBox(onClick = onOpenSearch)
        when (state) {
            TodayPlanUiState.Loading -> {
                CircularProgressIndicator(
                    color = appDomain(AppDomain.AiSpeech).deep,
                    modifier = Modifier.size(48.dp).testTag("today_plan_loading"),
                )
                Text("正在准备今日计划…", style = AppType.Footnote, color = colors.text2)
            }
            TodayPlanUiState.MissingSetup -> {
                Text(
                    "先选择词书并设置每日目标",
                    style = AppType.Body,
                    color = colors.text2,
                    modifier = Modifier.testTag("today_plan_missing_setup"),
                )
                PillButton(
                    text = "去设置词书",
                    onClick = onOpenSetup,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "today_plan_open_setup",
                    contentDescription = "去设置词书",
                )
            }
            TodayPlanUiState.Unavailable -> {
                Text(
                    "今日计划暂时无法读取，请稍后重试",
                    style = AppType.Body,
                    color = colors.text2,
                    modifier = Modifier.testTag("today_plan_unavailable"),
                )
                PillButton(
                    text = "重试",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "today_plan_retry",
                    contentDescription = "重试",
                )
            }
            is TodayPlanUiState.Ready -> {
                if (todayAllDone) {
                    // 原型 `sLearnEmpty`（`app.js:501-518`）：`.scr-body` 只有 `emptyState` 一节
                    // （`section('明天')` 整节不搬，理由见文件头第 4 条与 `TodayPlanEmptyStateContractTest`）。
                    // 强档卡、卡外注脚、「接下来」节标题与四条裸行都不进这一态的组合。
                    //
                    // 说明句里的两枚数量分句取**计划快照的目标数**（与未学完那一支的入口行、强卡图例同一权威），
                    // 不是原型 mock 的「4 个 / 2 个」；某一档为 0 时那一枚分句整段不拼——
                    // 「新学 0 个都已完成」是把「今天没排新词」说成「排了 0 个并且学完了」。
                    // `totalTasks = newTarget + dueTarget`（`TodayPlanViewModel:176-178`），而 `todayAllDone`
                    // 要求 `totalTasks > 0`，所以两枚分句至少一枚在场，前缀不会拼空。
                    val doneCounts = listOfNotNull(
                        if (state.newTarget > 0) "新学 ${state.newTarget} 个" else null,
                        if (state.dueTarget > 0) "复习 ${state.dueTarget} 个" else null,
                    )
                    EmptyState(
                        sticker = Sticker.Star,
                        title = "今天的词都学完了",
                        body = "${doneCounts.joinToString("、")}都已完成。想多学可以调整每日新增，或者读一篇今天生成的文章。",
                        modifier = Modifier.testTag("today_plan_all_done_state"),
                        action = {
                            // 原型那枚 grid：`width:100%; max-width:260px; gap:var(--s2)=8; margin-top:var(--s2)=8`
                            // （`app.js:509`）。`widthIn` 必须在 `fillMaxWidth` 之外，否则上限被填满宽度顶掉；
                            // 列本身由 `EmptyState` 的居中对齐摆在中间。
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 260.dp)
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PillButton(
                                    text = "调整每日新增",
                                    onClick = onOpenSetup,
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "today_plan_adjust_goal",
                                    contentDescription = "调整每日新增",
                                )
                                PillButton(
                                    text = "去读今日文章",
                                    onClick = onOpenReading,
                                    style = PillStyle.Secondary,
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "today_plan_read_article",
                                    contentDescription = "去读今日文章",
                                )
                            }
                        },
                    )
                } else {
                    val newDone = state.newDone.coerceAtMost(state.newTarget)
                    val dueDone = state.dueDone.coerceAtMost(state.dueTarget)
                    val totalDone = newDone + dueDone
                    val totalTarget = state.totalTasks
                    val remaining = (totalTarget - totalDone).coerceAtLeast(0)
                    val ctaLabel: String
                    val ctaAction: () -> Unit
                    when {
                        newDone < state.newTarget -> {
                            ctaLabel = "开始学习"
                            ctaAction = onStartLearning
                        }
                        else -> {
                            ctaLabel = "开始复习"
                            ctaAction = onStartLearning
                        }
                    }
                    StrongCard(modifier = Modifier.testTag("today_plan_summary")) {
                        // 卡内单列自己排间距：原型 `.plan-head` → 条（mt16）→ 图例（mt12）→ 按钮（mt16）
                        // 是四段不同的上边距，交给 StrongCard 的等间距会把这套节奏抹平。
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 原型 `.plan-head`（`assets/app.js:317-323`）：左列「今日计划」眉标 + 32sp 计数，
                            // 右侧一枚「还剩 N 个词」。计数是整屏唯一的大数字（AC-R-20），
                            // 其余数字一律 Footnote。
                            Row(
                                modifier = Modifier.fillMaxWidth().testTag("today_plan_progress_summary"),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "今日计划",
                                        style = AppType.Eyebrow,
                                        color = LocalContentColor.current.copy(alpha = 0.74f),
                                    )
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            "$totalDone",
                                            style = AppType.PlanNum,
                                            color = LocalContentColor.current,
                                            modifier = Modifier.testTag("today_plan_total_progress"),
                                        )
                                        Text(
                                            " / $totalTarget 个词",
                                            style = AppType.Footnote,
                                            color = LocalContentColor.current.copy(alpha = 0.74f),
                                            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                                        )
                                    }
                                }
                                Text(
                                    "还剩 $remaining 个词",
                                    style = AppType.Footnote,
                                    color = LocalContentColor.current.copy(alpha = 0.74f),
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = 4.dp).testTag("today_plan_remaining"),
                                )
                            }
                            // 原型 `.plan-bar`：两段段宽 = 目标份额、段内填充 = 完成比（见 `PlanBar.kt`）。
                            PlanProgressBar(
                                newDone = newDone,
                                newTarget = state.newTarget,
                                dueDone = dueDone,
                                dueTarget = state.dueTarget,
                                modifier = Modifier.padding(top = 16.dp).testTag("today_plan_plan_bar"),
                                contentDescription = "今日学习进度",
                            )
                            // 原型 `.plan-legend`：8dp 方点 + 「新学 x / y」「复习 x / y」。
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                PlanLegendKey(
                                    label = "新学",
                                    done = newDone,
                                    target = state.newTarget,
                                    swatch = colors.brand,
                                    tag = "today_plan_new_legend",
                                )
                                PlanLegendKey(
                                    label = "复习",
                                    done = dueDone,
                                    target = state.dueTarget,
                                    swatch = LocalContentColor.current.copy(alpha = 0.62f),
                                    tag = "today_plan_due_legend",
                                )
                            }
                            // 原型 `.plan-head` 下方的一对按钮（`:332-335`）：主 CTA 撑满，次按钮「计划」按内容取宽。
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PillButton(
                                    text = ctaLabel,
                                    onClick = ctaAction,
                                    modifier = Modifier.weight(1f),
                                    testTag = "today_plan_start_learning",
                                    contentDescription = ctaLabel,
                                )
                                PillButton(
                                    text = "计划",
                                    onClick = onOpenSetup,
                                    style = PillStyle.Secondary,
                                    testTag = "today_plan_open_plan",
                                    contentDescription = "学习计划",
                                )
                            }
                        }
                    }
                    // 原型 `.caption`（`assets/app.js:339`）：一句「今天学了 N 个词，还差 N 个就算完成」。
                    // 这里的文案仍是本应用的解锁口径（原型没有「解锁文章」这一条规则），
                    // 位置与档位照原型——卡外一行 Footnote，压 `text2` 次级墨。
                    //
                    // 「今日已完成 · 连续 N 天」那第三档随批 1 Task 19 离场：`allDone` 已经在上一个 `if` 分流去
                    // 空态那一支（原型的空态 `.scr-body` 里没有 `.caption`，`app.js:501-518`），
                    // 连续天数改由页头 `.head-sub` 那一行说（`app.js:503`）。留在这儿就是一句没人读的第二遍。
                    when {
                        totalTarget == 0 -> Text(
                            "今天没有待办",
                            style = AppType.Footnote,
                            color = colors.text2,
                            modifier = Modifier.testTag("today_plan_empty"),
                        )
                        else -> Text(
                            if (state.isUnlocked) "今日已解锁文章" else state.unlockReason,
                            style = AppType.Footnote,
                            color = colors.text2,
                            modifier = Modifier.testTag("today_plan_progress"),
                        )
                    }
                    // 软档卡只做「一层明度差 + 圆角裁切」：内边距交给裸行自己（15/13），
                    // 卡与行都不再套第二层 padding。两行之间的 8dp 间距由 SoftCard 的
                    // `Arrangement.spacedBy` 表达不了（那会让发丝线两侧各留一条空气），
                    // 所以两行收进一个零间距的 Column 里，分隔仍只有那 1dp 顶线。
                    SectionHeading(text = "接下来")
                    SoftCard(
                        modifier = Modifier.testTag("today_plan_secondary_card"),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            BareRow(
                                // 原型 `app.js:342` 的 `rowItem({sticker:'list', title:'今日词单',
                                // sub:'新学 3 + 复习 2，共 5 个词', to:'learn-queue', value:'5'})`。
                                // 这里的两枚数取**计划快照的目标数**（`newTarget` / `dueTarget`），与同屏强卡
                                // 「x / N 个词」同一套权威；词单屏的分节标题取的是**列出行数**（那一屏的权威是那份列表）。
                                title = "今日词单",
                                subtitle = "新学 ${state.newTarget} + 复习 ${state.dueTarget}，共 ${state.totalTasks} 个词",
                                trailing = "${state.totalTasks}",
                                onClick = onOpenWordList,
                                leading = {
                                    InlineIconBadge(icon = AppIcon.List, domain = AppDomain.Learn)
                                },
                                modifier = Modifier.testTag("today_plan_open_word_list"),
                            )
                            BareRow(
                                // 原型 `app.js:343` 的 `rowItem({sticker:'clock', title:'复习队列',
                                // sub:'2 个词到期，约 8 分钟后开始遗忘', to:'learn-review', value:'2'})`。
                                // 两枚数同样取**计划快照的到期目标**（与首行、与强卡图例同一权威）。
                                // 副案的后半句「约 8 分钟后开始遗忘」不搬：那是原型 mock 里的遗忘预测，
                                // 本仓没有任何「多少分钟后开始遗忘」的模型或采集，写出来就是编造指标；
                                // 「到期」本身是真话（快照 dueCardIds 的来源查询就是 `nextReviewAt <= now`）。
                                title = "复习队列",
                                subtitle = "${state.dueTarget} 个词到期",
                                trailing = "${state.dueTarget}",
                                onClick = onOpenReviewQueue,
                                topDivider = true,
                                leading = {
                                    InlineIconBadge(icon = AppIcon.Clock, domain = AppDomain.Review)
                                },
                                modifier = Modifier.testTag("today_plan_open_review_queue"),
                            )
                            BareRow(
                                title = when {
                                    !state.isUnlocked -> "今日文章 · 未解锁"
                                    todayArticleRead -> "今日文章 · 已读"
                                    else -> "今日文章 · 可阅读"
                                },
                                subtitle = if (state.isUnlocked) "去读今天生成的短文" else "完成今日计划后解锁",
                                topDivider = true,
                                onClick = onOpenReading,
                                leading = {
                                    InlineIconBadge(icon = AppIcon.Newspaper, domain = AppDomain.Reading)
                                },
                                modifier = Modifier.testTag("today_plan_reading_status"),
                            )
                            BareRow(
                                title = state.bookProgress?.let { "${state.wordBookName} · 已学 ${it.learned} / ${it.total}" }
                                    ?: state.wordBookName,
                                subtitle = "词书 · 生词 · 错词 · 收藏",
                                topDivider = true,
                                onClick = onOpenWordBank,
                                leading = {
                                    InlineIconBadge(icon = AppIcon.Book, domain = AppDomain.Library)
                                },
                                modifier = Modifier.testTag("today_plan_open_word_bank"),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 常驻查词条：薄荷底、无描边，一整个可点盒；48dp 高度下限与入口行同一水线（F5-03）。 */
@Composable
private fun SearchBox(onClick: () -> Unit) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.tint)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("today_plan_search_entry")
            .semantics { contentDescription = "搜索单词或短语" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppGlyph(icon = AppIcon.Search, tint = colors.text2, size = 18.dp, layer = AppGlyphLayer.StrokeOnly)
        Text("查一个词", style = AppType.Body, color = colors.text2)
    }
}

/**
 * 强卡图例的一枚键（原型 `.plan-key`）：8dp 方点 + 「新学 x / y」。
 *
 * 点色按调用方给（新学 `brand`、复习内容色 62%），文字走容器内容色 74%——
 * 与原型 `.plan-key` 的 `--on-strong-2` 同义。标签挂在 `Text` 上而不是外层 `Row`：
 * `TodayPlanNumberAuthorityTest` 的窗口取样器按「标签落在某一枚 `Text(` 实参内」判归属，
 * 挂到容器上会被判红（那条判据正是防「tag 上提后取样失明」）。
 */
@Composable
private fun PlanLegendKey(label: String, done: Int, target: Int, swatch: Color, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(swatch),
        )
        Text(
            "$label $done / $target",
            style = AppType.Footnote,
            color = LocalContentColor.current.copy(alpha = 0.74f),
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp).testTag(tag),
        )
    }
}

/**
 * 页头行尾的齿轮动作（原型 `.icon-btn`：40dp、透明底、药丸形、`ink` 墨）。
 *
 * 外层 48dp 可点盒承载命中区与无障碍名，内层 40dp 才是稿上的视觉容器——
 * 与 `AppScreenHeader` 的返回位同一套两层口径（F5-03 的 48dp 水线）。
 */
@Composable
private fun SettingsAction(onClick: () -> Unit) {
    val colors = appColors()
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(AppShape.Pill)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "设置" }
            .testTag("today_plan_settings_action"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(AppShape.Pill),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyph(icon = AppIcon.Gear, tint = colors.text1, size = 20.dp, layer = AppGlyphLayer.StrokeOnly)
        }
    }
}
