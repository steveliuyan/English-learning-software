package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppCard
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.InlineIconBadge
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.components.Sticker
import com.example.englishlearning.ui.components.StickerBadge
import com.example.englishlearning.ui.components.StickerSize
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.hairlineTop
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 今日完成（批 1 Task 18，新建屏；对齐原型 `sSummary`，`assets/app.js:440-469`）。
 *
 * 这一屏在本轮之前没有承载点：最后一张词卡提交完、按「完成本轮」之后，画面停在 `WordCardScreen`
 * 的 `AllDone` 分段（「今天的词卡都提交完了」+ 一行计数 + 一枚「返回今日计划」），收尾没有「今天过了
 * 多少词、还差哪一步」这一层。用户 2026-10-29 拍板「三屏全部新建并接进导航」，本屏是三枚里的第三枚，
 * 入口就是原型自己的入口（`app.js:1688` / `:1701`：末张卡推进 → `go('learn-summary')`），所以宿主
 * 在 `showLearning` 分支里按 `WordCardUiState.AllDone` 挂本屏，不再挂词卡屏。
 *
 * ## 版面逐段对着原型抄
 *
 *  - `barTop({title:'今日完成', action:.bar-action「完成」→ learn-home})`（`:444`）→ 共享 [AppScreenHeader]。
 *    动作位与「今日词单」的「调整」同形（原型 `.bar-action`，`app.css:274-286`：透明底、`--brand-deep` 墨、
 *    `--fs-meta` 13px、字重 600、`padding: var(--s2)`），也沿用 `AppScreen.kt` 的 `SetupHeaderSave` 那份
 *    「纯 [Text] + 可点」写法，不另造页头文字按钮组件；本体 14dp 高低于 F5-03 的 48dp 水线，
 *    外层给 48dp 触控盒（与 `AppChip` 同一处置）。
 *  - hero 那一排（`:446-451`）：`badge-lg`（`app.css:560` = 64dp / 圆角 20）里一枚 `star` 贴纸 →
 *    共享 [StickerBadge] 的 [StickerSize.Large] 档，底与内描边都由组件给（`.badge` 的两条）；
 *    右侧两行是 `.eyebrow`（计划日期）+ `.serif` 26px 那句「今天的词都过了一遍」。
 *    日期是宿主那份计划快照的 [TodayPlanUiState.Ready.localDateLabel]；快照尚未就绪时传 `null`，
 *    `.eyebrow` 整行不进组合（原型那格是 mock 的 `D.today.planDate`，本仓没有日期就不摆一个空壳）。
 *    26px serif 这一档就是 [AppType.StatNum]（`app.css:696` 的 `.stat-num` 度量：26sp、500、`-.02em`、行高 1.1），
 *    本屏两处 26px serif 位（hero 主句与统计数字）共用同一档，不开第七档字号。
 *  - `.card` 统计格（`:452-456`）→ 共享 [AppCard]（原型 `.card` = `surface` 底 + 1dp `line` 描边，`app.css:363`）。
 *    原型是 `grid-template-columns:1fr 1fr 1fr`，本仓只有 `个词` 一枚有真数（见下面「不搬的三块」第 1 条），
 *    所以这一格按「有几枚真数占几列」铺：现在一枚就占满一行，而不是照抄三列再挖两个空位。
 *  - `section('还差一步', .rows)`（`:462-465`）→ [SectionHeading] + 裸 [BareRow]。
 *    `.rows` 是卡片汤之外的裸行列表（`app.css:509-525`），所以不套卡片；行前导是原型 `sticker:'newspaper'`
 *    那一枚，走共享 [InlineIconBadge]（本仓行首统一这一档：今日学习那条同目的地行、复习队列的默认模式行
 *    都是它，不为这一屏另立一种行首）。`value:'未读'` 绑宿主的真实阅读读数（下面第 3 条）。
 *  - `.foot`（`:467`）一枚主按钮「回到今日」→ [PillButton] 撑满 + 一条 [hairlineTop] 顶线，取仓内吸底槽的
 *    统一写法（`Alignment.BottomCenter` + 自铺页底色），与「今日词单」「复习队列」两屏同形；
 *    原型的 `.foot` 是渐变底（`app.css:1031`），差异登记在计划批 1 台账 Task 18 行第 8 条。
 *
 * ## 原型写了、本屏不写的（三块，全是「原型是 mock 数据、本仓没有对应采集」）
 *
 *  1. 统计格里的 `用时 6:42` 与 `一次答对 82%`：本仓不记单轮用时，也不记一次答对率
 *     （`LearningEvent` 只存逐卡的 `ReviewFeedback`，没有尝试次数）。按用户 2026-10-29 拍板
 *     「读到不到的那枚整枚不显示；新增采集属功能改动、未获授权」（计划「推进决定」第 3 条），
 *     两枚都不出现，也不换成「大约」「稍后」这类同义改写。
 *  2. `section('这一轮的反馈')` 整节（`:457-461`）：三枚计数要按档聚合今天的事件日志，是一条新的
 *     仓库→界面读数通路——本屏只吃已经备好的 [WordCardUiState.AllDone]（AGENTS.md「UI 之外的逻辑保持
 *     原样，只在必要时接上界面」，Task 17 台账第 2 行同口径）；三行副案「下次间隔 6 天 / 明天再来 /
 *     今天晚些时候再问一次」又与 `V1ReviewScheduler` 的真实档位（Again 10 分钟 / Hard 1 天 / Good 3 天）
 *     以及「提交一次有效反馈即记完成、本轮不重排」对冲。同口径先例还有批 1 Task 8 打卡页的完成度环。
 *  3. `还差一步` 的第二行「8 分钟后再看 1 个词 / 忘记的词会在最佳时点回来」：本仓「忘记」的词不会在
 *     本轮再出现（今日计划是首次进入时生成的不可变快照），那枚 `1` 也没有备好的数——与 Task 16 台账
 *     第 2 行删掉的那句 `.notice` 是同一条对冲。留下的「读今天的文章」是本仓真有的下一步，
 *     副案用今日学习那条行已经在说的「去读今天生成的短文」，不搬原型 `4 段 · 覆盖 4 个今日词`
 *     （文章还没生成，说不出段数与覆盖词）。
 *
 * 三个出口（页头 ← / 页头「完成」/ 吸底「回到今日」）在原型里都指向 `learn-home`，宿主把三者接到
 * 同一条 `exitLearning`（回今日学习并重算计划状态）；系统返回键由 `BackHandler(enabled = showLearning)`
 * 那一条承担，本屏不新置状态位。[onOpenReading] 的目的地是阅读（原型 `data-to="read-article"`），由宿主接线。
 */
@Composable
fun TodaySummaryScreen(
    state: WordCardUiState.AllDone,
    dateLabel: String?,
    articleRead: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("today_summary_screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .verticalScroll(rememberScrollState())
                // 底部留 76dp：吸底条压在滚动面之上，最后一行不给它盖住（与「今日词单」那一屏同形）。
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppScreenHeader(
                title = "今日完成",
                onBack = onBack,
                action = {
                    Text(
                        text = "完成",
                        style = AppType.Meta,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.brandDeep,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(AppShape.Pill)
                            .clickable(onClick = onDone)
                            .semantics { contentDescription = "完成，回到今日学习" }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                    )
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today_summary_hero"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StickerBadge(
                    sticker = Sticker.Star,
                    size = StickerSize.Large,
                    modifier = Modifier.testTag("today_summary_badge"),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // 原型 `.eyebrow` 那一格是 `D.today.planDate`；这里换成本仓真实的计划日期（宿主给）。
                    if (dateLabel != null) {
                        Text(
                            text = dateLabel,
                            style = AppType.Eyebrow,
                            color = colors.text3,
                            modifier = Modifier.testTag("today_summary_date"),
                        )
                    }
                    Text(
                        text = "今天的词都过了一遍",
                        style = AppType.StatNum,
                        color = colors.text1,
                    )
                }
            }
            AppCard(modifier = Modifier.testTag("today_summary_stats")) {
                // 原型是三列 `1fr 1fr 1fr`；本仓此刻只有一枚真数，所以那一枚占满整行（见类 KDoc 第 1 条）。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("today_summary_stat_words"),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        // `.stat { display:grid; gap:1px }`（`app.css:695`）。
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = "${state.completedCount}",
                            style = AppType.StatNum,
                            color = colors.text1,
                        )
                        Text(
                            text = "个词",
                            style = AppType.Caption,
                            color = colors.text3,
                        )
                    }
                }
            }
            SectionHeading(text = "还差一步")
            BareRow(
                title = "读今天的文章",
                subtitle = "去读今天生成的短文",
                trailing = if (articleRead) "已读" else "未读",
                leading = {
                    InlineIconBadge(icon = AppIcon.Newspaper, domain = AppDomain.Reading)
                },
                onClick = onOpenReading,
                modifier = Modifier.testTag("today_summary_open_reading"),
            )
        }
        // 吸底主按钮：本屏永远有「回到今日」这一件事可做（原型 `.foot` 那枚 `btn-block`），
        // 所以不像「今日词单 / 复习队列」那样按剩余量收条。对齐位取仓内吸底槽的既有写法
        // `Alignment.BottomCenter`，本槽已登记进 `StickyFooterHairlineGuardTest` 的台账。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colors.background)
                .then(hairlineTop(colors.outline))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            PillButton(
                text = "回到今日",
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                testTag = "today_summary_back_to_today",
                contentDescription = "回到今日学习",
            )
        }
    }
}
