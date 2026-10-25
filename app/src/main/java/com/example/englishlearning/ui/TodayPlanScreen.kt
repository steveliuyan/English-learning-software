package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.StrongCard
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * 学习页（2026-10-25 版式重做 §3.2）：**搜索 / 强档卡 / 今日文章 / 词库** 四段。
 *
 * 为什么只剩四段：批 2 的「1 主 CTA + 一组入口行」把八个按钮收成一屏九块彩色容器，用户读到的是
 * 「一个学习页面太挤」。版式重做把入口按目的地拆开——目录型动作不再占用「今天做什么」的版面：
 *  - 词书单词 / 我的词 → **词库**页（`WordBankScreen`，底部一级以外的一个聚合页）；
 *  - 学习记录 / 打卡与成就 → **我的**页（`ProfileScreen`）；
 *  - 调整词书与目标 → 我的页里的设置组（词书组）。
 * 于是本屏只回答两件事：今天还差多少（强档卡）、接下来去哪（今日文章 / 词库两条裸行）。
 * 五枚入口锚（`today_plan_open_wordbook_words` / `_my_words` / `_learning_records` / `_check_in` /
 * `_setup_entry`）随功能一起离场，判决由 `TodayPlanBareRowContractTest` 双向钉住。
 *
 * 三档容器各司其职（§4.1）：
 *  - [StrongCard] 恰一枚，深墨实底（`strongSurface`）配 `onStrong` 前景。**卡上的正文必须显式
 *    `color = colors.onStrong`**：`AppType` 六档的 getter 自带 `text1/text2`，压深底不可读，
 *    `LocalContentColor` 拦不住它们；无色的 `Text` 才吃容器内容色。
 *  - [SoftCard] 承载两条裸行，不描边——层次由 `surface` 与 `strongSurface` 的明度差表达。
 *  - [BareRow] 52dp 行高（≥ F5-03 的 48dp 水线），第二条带 1dp 发丝线（`topDivider`）。
 *
 * 数字的权威仍只有一处（AC-R-20）：`AppType.Display` 全屏恰一枚，落在今日完成数那一格；
 * 其余数字一律 `Footnote`/`Label`，墨色取同一个 `onStrong`。`AppType.Title` 只给词书名。
 * 这条连同「本屏零 `EntryRow`、恰两条 `BareRow`」由 `TodayPlanNumberAuthorityTest` 与
 * `TodayPlanBareRowContractTest` 分别按源码形状钉住。
 *
 * 主 CTA 四态同锚点（`today_plan_start_learning`），文案随状态换：未学完 →「开始学习」、
 * 新词学完但仍欠复习 →「开始复习」、全部完成且文章未读 →「去读今日文章」、全部完成且文章已读 →
 * 「复习生词本」。阅读的解锁说明不再单起一行（旧 `today_plan_open_reading` 那枚 M3 `Button`
 * 已降为裸行），而是由行标题的「未解锁 / 已读 / 可阅读」与强档卡里的 `unlockReason` 共同承载。
 *
 * **Ready 分支的渲染体刻意不抽成私有组合函数**：`TodayPlanNumberAuthorityTest` 按花括号配平取
 * Ready 分支体，要求主 CTA 的 testTag 与 `PillButton` 计数都长在体内（评审 I-4 的变异形是
 * 「把 CTA 抽到分支之后另写的私有组合件」，分支渲染为空而 tail 计数照旧绿）。抽出去就等于
 * 放宽那条判据，故保留内联。
 *
 * [streakDays] / [todayArticleRead] 由宿主（`AppScreen`）从打卡快照里取：本屏不自己去读仓库，
 * 与「业务规则在 ViewModel/仓库、版面只读状态」的分工一致。
 */
@Composable
fun TodayPlanScreen(
    state: TodayPlanUiState,
    onRetry: () -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onStartLearning: () -> Unit = {},
    onOpenReading: () -> Unit = {},
    onOpenWordBank: () -> Unit = {},
    onReviewWords: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    streakDays: Int = 0,
    todayArticleRead: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
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
        AppScreenHeader(title = "今日学习")
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
                val newDone = state.newDone.coerceAtMost(state.newTarget)
                val dueDone = state.dueDone.coerceAtMost(state.dueTarget)
                val totalDone = newDone + dueDone
                val totalTarget = state.totalTasks
                val allDone = totalTarget > 0 && totalDone >= totalTarget
                val ctaLabel: String
                val ctaAction: () -> Unit
                when {
                    allDone && !todayArticleRead -> {
                        ctaLabel = "去读今日文章"
                        ctaAction = onOpenReading
                    }
                    allDone -> {
                        ctaLabel = "复习生词本"
                        ctaAction = onReviewWords
                    }
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
                    Text(state.wordBookName, style = AppType.Title, color = colors.onStrong)
                    Text(
                        "新学 $newDone/${state.newTarget} · 复习 $dueDone/${state.dueTarget}",
                        style = AppType.Footnote,
                        color = colors.onStrong.copy(alpha = 0.72f),
                        modifier = Modifier.testTag("today_plan_task_total"),
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().testTag("today_plan_progress_summary"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "$totalDone/$totalTarget",
                                style = AppType.Display,
                                color = colors.onStrong,
                                modifier = Modifier.testTag("today_plan_total_progress"),
                            )
                            Text(
                                " 项已完成",
                                style = AppType.Footnote,
                                color = colors.onStrong.copy(alpha = 0.72f),
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        ThinProgress(
                            label = "新学",
                            done = newDone,
                            target = state.newTarget,
                            tag = "today_plan_new_progress",
                            track = colors.onStrong.copy(alpha = 0.18f),
                            ink = colors.onStrong,
                        )
                        ThinProgress(
                            label = "复习",
                            done = dueDone,
                            target = state.dueTarget,
                            tag = "today_plan_review_progress",
                            track = colors.onStrong.copy(alpha = 0.18f),
                            ink = colors.onStrong,
                        )
                        when {
                            totalTarget == 0 -> Text(
                                "今天没有待办",
                                style = AppType.Footnote,
                                color = colors.onStrong.copy(alpha = 0.72f),
                                modifier = Modifier.testTag("today_plan_empty"),
                            )
                            allDone -> Text(
                                // 没有连续天数时不摆一个「连续 0 天」：那是把「还没开始」说成了「断了」。
                                if (streakDays > 0) "今日已完成 · 连续 $streakDays 天" else "今日已完成",
                                style = AppType.Footnote,
                                color = colors.onStrong.copy(alpha = 0.72f),
                                modifier = Modifier.testTag("today_plan_plan_footnote"),
                            )
                            else -> Text(
                                if (state.isUnlocked) "今日已解锁文章" else state.unlockReason,
                                style = AppType.Footnote,
                                color = colors.onStrong.copy(alpha = 0.72f),
                                modifier = Modifier.testTag("today_plan_progress"),
                            )
                        }
                    }
                    PillButton(
                        text = ctaLabel,
                        onClick = ctaAction,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "today_plan_start_learning",
                        contentDescription = ctaLabel,
                    )
                }
                // 软档卡只做「一层明度差 + 圆角裁切」：内边距交给裸行自己（15/13），
                // 卡与行都不再套第二层 padding。两行之间的 8dp 间距由 SoftCard 的
                // `Arrangement.spacedBy` 表达不了（那会让发丝线两侧各留一条空气），
                // 所以两行收进一个零间距的 Column 里，分隔仍只有那 1dp 顶线。
                SoftCard(
                    modifier = Modifier.testTag("today_plan_secondary_card"),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        BareRow(
                            title = when {
                                !state.isUnlocked -> "今日文章 · 未解锁"
                                todayArticleRead -> "今日文章 · 已读"
                                else -> "今日文章 · 可阅读"
                            },
                            subtitle = if (state.isUnlocked) "去读今天生成的短文" else "完成今日计划后解锁",
                            onClick = onOpenReading,
                            leading = {
                                AppGlyph(
                                    icon = AppIcon.Newspaper,
                                    tint = appDomain(AppDomain.Reading).deep,
                                    size = 24.dp,
                                    layer = AppGlyphLayer.StrokeOnly,
                                )
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
                                AppGlyph(
                                    icon = AppIcon.Book,
                                    tint = appDomain(AppDomain.Library).deep,
                                    size = 24.dp,
                                    layer = AppGlyphLayer.StrokeOnly,
                                )
                            },
                            modifier = Modifier.testTag("today_plan_open_word_bank"),
                        )
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
 * 强档卡里的一条细进度：左标签、中间 6dp 轨、右计数。
 *
 * 三段都用调用方给的同一枚墨（`ink`），轨道是它的 18% 稀释——「今天还差多少」的精确数已在
 * 上方 `Display` 那一格，这里只负责形状，所以标签与计数一律 `Label` 档，不再长出第二种尺寸。
 * `target <= 0` 时按满格画（0/0 不是「没开始」，是「今天没有这一类」）。
 */
@Composable
private fun ThinProgress(label: String, done: Int, target: Int, tag: String, track: Color, ink: Color) {
    val ratio = if (target <= 0) 1f else (done.toFloat() / target).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = AppType.Label, color = ink.copy(alpha = 0.72f), modifier = Modifier.width(28.dp))
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(track)
                .testTag(tag),
        ) {
            Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().background(ink))
        }
        Text("$done/$target", style = AppType.Label, color = ink.copy(alpha = 0.72f))
    }
}
