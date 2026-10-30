package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.ui.components.AppChip
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.components.WordRow
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.hairlineTop
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 今日词单（批 1 Task 16，新建屏；对齐原型 `sLearnQueue`，`assets/app.js:357-390`）。
 *
 * 这一屏在本轮之前没有承载点：背词与复习都从今日学习那枚 CTA 直接进词卡，中间没有「今天到底排了
 * 哪些词」这一层。用户 2026-10-29 拍板「三屏全部新建」，本屏是其中之一，入口是今日学习软卡里的第一条
 * 裸行（`today_plan_open_word_list`，原型 `app.js:342` 的 `rowItem({sticker:'list', …, to:'learn-queue'})`）。
 *
 * ## 版面逐段对着原型抄
 *
 *  - `barTop({title:'今日词单', action: .bar-action「调整」→ plan-edit})` → 共享 [AppScreenHeader]；
 *    动作位是原型 `.bar-action`（`:274-286`：透明底、`brand-deep` 墨、`--fs-meta` 13px、字重 600、
 *    `padding: var(--s2)` 8dp）。这里与 `AppScreen.kt` 的 `SetupHeaderSave` 同形——纯 `Text` + 可点，
 *    不另造一枚「页头文字按钮」组件（计划 Task 12 的组件份数棘轮）。
 *    `.bar-action` 本体 14dp 高、低于 F5-03 的 48dp 水线，取法与 `AppChip` 一致：视觉厚度照原型，
 *    外层给 48dp 触控盒。
 *  - `.meta`（`:376`，样式 `:1020` = 13px `--ink-2`）一句「{planDate} 的词单由…共同决定。」
 *    逐字保留，只把 `D.today.planDate` 换成计划快照的真实日期（[TodayPlanUiState.Ready.localDateLabel]）。
 *    档位取 [AppType.Meta]（13sp，与稿的 13px 同档）。
 *  - 三枚 `chip()`（`:377-380` 全部 / 新学 / 复习，`aria-pressed` 就是本屏的过滤器）→ 共享 [AppChip]，
 *    绑同一枚 [QueueFilter] 状态。行用 `FlowRow` 而不是 `Row`：原型是 `display:flex` + `gap`，
 *    2× 字号下三枚必然超一行。
 *  - `section('新学 · N 个词', .rows)` + `section('复习 · N 个词', .rows, '按遗忘曲线排序')`
 *    （`:363-372`）→ 两枚 [SectionHeading]，`trailing` 那一句只挂复习节。
 *    「按遗忘曲线排序」在本仓是**真话**：到期队列就是快照 `dueCardIds`，而它的 DAO 排序是
 *    `ORDER BY nextReviewAtEpochMillis ASC`（`InternalLearningEventDao`），屏侧不再重排。
 *  - `.rows` 是**卡片汤之外**的裸行列表（`assets/app.css:509-525`：透明底、无外框，只在行与行之间
 *    画 1dp `line-soft`，首行不画）。所以这里不套 `SoftCard`，分隔交给 [WordRow] 的 `topDivider`，
 *    取法与今日学习页的分组裸行同族。
 *  - `.foot`（`:388`）一枚主按钮「开始第 N 个词」→ [PillButton] 撑满 + 一条 `hairlineTop` 顶线。
 *    原型的 `.foot` 用渐变底（`app.css:1031`）而不是分隔线；本仓的吸底条统一是「实底 + 顶部分界线」
 *    （`SetupSaveBar` 同形），差异登记在计划批 1 偏离表 Task 16 行。
 *
 * ## 两条有意的偏离（判据 `TodayWordListLayoutContractTest` 同条消息里也写着）
 *
 *  1. 原型 `.notice` 那句走向文案（`:385`）整条不搬。它的三个分句在本仓要么由三枚真开关驱动
 *     （写死就是假话），要么与 AGENTS.md「今日计划是首次进入时生成的**不可变快照**」直接对冲
 *     （「标记『忘记』的词会在本轮末尾再出现一次」）。按开关动态给出的同一句话已经住在学习计划屏
 *     （`PlanEstimate.kt` 的 `revealCaptionText`，Task 12 台账第 3 行同口径）。
 *  2. 原型给到期词**一律**标「到期」；本仓把今日已完成的复习项标「已学」。快照里那一项已经记为完成，
 *     再标「到期」是假话。行尾状态逐行取 [TodayPlanUiState.Ready.doneCardIds]，不取计数——计数只说
 *     「做了几件」，说不出**哪一件**做完了，而词单是逐行摆的。
 *
 * 另两条**原型画了、本屏不画**的（都在数据侧没有对应物，登记不补）：
 *  - 原型 `wordRow` 的 44dp 贴纸位来自 `D.words[key].icon`（每词一枚插画）；本仓 `WordCard` 没有
 *    词级图标字段，行前导槽留空（不拿域徽标冒充「这个词的贴纸」）。
 *  - 原型词头是 `serif` + 18px；本屏走共享 [WordRow]，词头是 `AppType.Body`（15sp，`.row-title` 的
 *    档）。共享份是全仓词行的唯一真值（棘轮族 4），不为这一屏另开一档。
 *
 * ## 空态与非本轮的取舍
 *
 *  - 某一节没有词（今日 `newTarget == 0`，或词书包被换掉导致内容源读不到任何一张卡）时**整节不渲染**：
 *    原型是写死数据，永远两节都有内容，没画过「0 个词」那一节。空节留一枚标题 + 零行是原型没有的形态。
 *  - 全部做完（剩余 0）时吸底按钮**整条不出现**。同样是原型没画到的状态：那句话写「开始第 N 个词」，
 *    而没有第 N 个词可开始。此时回到今日学习页读完成态文案，本屏只做「今天排了哪些词」的查阅。
 *  - 行点开走共享词义详情（宿主 `AppScreen` 的 `selectedSearchCard` 载体），「下一个」沿本屏的
 *    呈现序（新学段 + 复习段）推进，不复用别的列表。
 *
 * [onOpenSetup] 的目的地是学习计划屏（原型 `data-to="plan-edit"`），[onStartLearning] 是词卡流程
 * （原型 `data-to="learn-session"`）。两者都由宿主接线，本屏不认识导航状态。
 */

/** 本屏的三档筛选（原型 `S.colFilter` 的 `all` / `new` / `due` 三个取值）。 */
private enum class QueueFilter { All, New, Due }

@Composable
fun TodayWordListScreen(
    state: TodayPlanUiState.Ready,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onStartLearning: () -> Unit,
    onOpenWord: (WordCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    var filter by rememberSaveable { mutableStateOf(QueueFilter.All) }
    val showNew = filter != QueueFilter.Due
    val showDue = filter != QueueFilter.New
    val newCount = state.newQueue.size
    val dueCount = state.dueQueue.size
    val doneCount = (state.newDone + state.dueDone).coerceAtMost(state.totalTasks)
    val remaining = (state.totalTasks - doneCount).coerceAtLeast(0)
    Box(modifier = modifier.fillMaxSize().background(colors.background).testTag("today_word_list_screen")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .verticalScroll(rememberScrollState())
                // 底部留 76dp：吸底条压在滚动面之上，最后一行不给它盖住（原型 `.scr-body` 的
                // `padding-bottom: var(--s6)` + `.foot` 自己不占滚动流）。
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppScreenHeader(
                title = "今日词单",
                onBack = onBack,
                action = {
                    Text(
                        text = "调整",
                        style = AppType.Meta,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.brandDeep,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(AppShape.Pill)
                            .clickable(onClick = onOpenSetup)
                            .semantics { contentDescription = "调整今日计划" }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                    )
                },
            )
            Text(
                text = "${state.localDateLabel} 的词单由「当前词书 + 每日新增 + 到期复习」共同决定。",
                style = AppType.Meta,
                color = colors.text2,
                modifier = Modifier.testTag("today_word_list_meta"),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppChip(
                    label = "全部 ${newCount + dueCount}",
                    selected = filter == QueueFilter.All,
                    onClick = { filter = QueueFilter.All },
                    modifier = Modifier.testTag("today_word_list_filter_all"),
                )
                AppChip(
                    label = "新学 $newCount",
                    selected = filter == QueueFilter.New,
                    onClick = { filter = QueueFilter.New },
                    modifier = Modifier.testTag("today_word_list_filter_new"),
                )
                AppChip(
                    label = "复习 $dueCount",
                    selected = filter == QueueFilter.Due,
                    onClick = { filter = QueueFilter.Due },
                    modifier = Modifier.testTag("today_word_list_filter_due"),
                )
            }
            // 新学节：标题前缀与「N 个词」逐字照原型 `app.js:363`；行的状态逐 id 判定。
            if (showNew && newCount > 0) {
                SectionHeading(text = "新学 · $newCount 个词")
                state.newQueue.forEachIndexed { index, card ->
                    WordRow(
                        lemma = card.lemma,
                        gloss = wordListGloss(card),
                        status = if (card.cardId in state.doneCardIds) "已学" else "待学",
                        onClick = { onOpenWord(card) },
                        topDivider = index > 0,
                        modifier = Modifier.testTag("today_word_list_row_new_${card.cardId}"),
                    )
                }
            }
            // 复习节：`trailing` 那句只挂这一节（原型 `app.js:371`），排序口径见类 KDoc。
            if (showDue && dueCount > 0) {
                SectionHeading(text = "复习 · $dueCount 个词", trailing = "按遗忘曲线排序")
                state.dueQueue.forEachIndexed { index, card ->
                    WordRow(
                        lemma = card.lemma,
                        gloss = wordListGloss(card),
                        status = if (card.cardId in state.doneCardIds) "已学" else "到期",
                        onClick = { onOpenWord(card) },
                        topDivider = index > 0,
                        modifier = Modifier.testTag("today_word_list_row_due_${card.cardId}"),
                    )
                }
            }
        }
        // 吸底主按钮：剩余为 0 时整条不出现（原型没画「没有下一个词」的那一态，见类 KDoc）。
        // 对齐位取仓内吸底槽的既有写法 `Alignment.BottomCenter`（不是 BottomStart）：
        // `StickyFooterHairlineGuardTest` 的识别符号之一就是这一枚，本槽已登记进它的台账。
        if (remaining > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(colors.background)
                    .then(hairlineTop(colors.outline))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                PillButton(
                    text = "开始第 ${doneCount + 1} 个词",
                    onClick = onStartLearning,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "today_word_list_start",
                    contentDescription = "开始学习今日词单",
                )
            }
        }
    }
}
