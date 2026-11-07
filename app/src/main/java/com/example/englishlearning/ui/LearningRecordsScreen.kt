package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.LearningHistoryGroup
import com.example.englishlearning.learning.LearningRecord
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.DailyLearningHistory
import com.example.englishlearning.notices.LocalDataNotice
import com.example.englishlearning.ui.components.AppCard
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeAction
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.components.SkeletonList
import com.example.englishlearning.ui.components.WordRow
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 学习记录（原型 `#/me-records`，`assets/app.js:1304-1327`，`sMeRecords`）。
 *
 * 本批按原型补三件（批 2 Task 22-D 之一）：
 *  ① 页尾提示条（原型 `:1323` 的 `notice(...,'info')`）：措辞来自 `notices/LocalDataNotice`，
 *     本屏不自写中文警告（U-6 收源前本屏与备份 hub、导出声明各写一套，同一件事三种句子）；
 *  ② 「历史记录」页签每日行补「文章态」副案与右尾「N 词」，并挂 4dp 细进度条（原型 `.row` + `.meter-thin`）；
 *  ③ 「本周」两枚真统计（本周词数 / 打卡天数，`learningWeek` 派生）——批 2 当时还配了一组七格刻度，
 *     U-9 已把它退役（周视图归打卡屏一处，决策 2026-11-03 §10）。
 *
 * **原型的第三格「19 分 · 日均用时」整枚不画**：本仓没有任何「单轮用时 / 日累计时长」的采集，
 * 写出来就是编造指标（用户 2026-10-29 拍板「读不到的那枚整枚不显示」，见计划 Task 18 台账同口径）。
 *
 * **「本周」区块只在「历史记录」页签渲染**：`learningWeek` 吃的 `historyStats` 按既有契约**惰性加载**
 * （`LearningRecordsViewModelTest.first load is lazy…` 钉「首屏只加载今日页签，详展统计必须惰性」），
 * 首屏并无 7 天数据；要在默认页签也画周条，就得改成首屏预取统计——那是数据层改动且直接破那条既有判据。
 * 所以周条随它需要的数据一起住进历史页签（「读不到就不画」），并在此登记为与原型的偏离。
 */
@Composable
fun LearningRecordsScreen(
    state: LearningRecordsUiState,
    onBack: () -> Unit,
    onSelectTab: (LearningRecordsTab) -> Unit,
    onRetry: () -> Unit,
    onOpenRecord: (LearningRecord) -> Unit,
) {
    val colors = appColors()
    val tabs = LearningRecordsTab.entries
    val selectedIndex = tabs.indexOf(state.selectedTab).coerceAtLeast(0)
    val selectedError = state.errors[state.selectedTab]
    val isLoading = state.selectedTab in state.loading
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("learning_records_screen"),
    ) {
        AppScreenHeader(
            title = "学习记录",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        TabRow(selectedTabIndex = selectedIndex) {
            tabs.forEach { tab ->
                val label = tab.label()
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier
                        .testTag(tab.testTag())
                        .semantics {
                            contentDescription = label
                            role = Role.Tab
                            // 决策 §4（终审 I-1）：选中态必须进播报，不能只靠 M3 selectable 的布尔属性代偿。
                            stateDescription = if (state.selectedTab == tab) "已选中" else "未选中"
                        },
                    text = { Text(label) },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            when {
                isLoading -> item { LoadingState() }
                selectedError != null -> item { ErrorState(selectedError, onRetry) }
                else -> {
                    when (state.selectedTab) {
                        LearningRecordsTab.TODAY -> {
                            if (state.today.isEmpty()) {
                                item { EmptyState("今天还没有学习记录", "learning_records_empty_today") }
                            } else {
                                itemsIndexed(state.today, key = { _, it -> "today-${it.planId}-${it.cardId}" }) { index, row ->
                                    LearningRecordRow(row, index, onOpenRecord)
                                }
                            }
                        }
                        LearningRecordsTab.HISTORY -> {
                            val week = state.todayLocalDate?.let { learningWeek(state.historyStats, it) }
                            if (week != null && !week.isEmpty) {
                                item(key = "week") { WeekSection(week) }
                            }
                            if (state.history.isEmpty()) {
                                item { EmptyState("还没有跨日期的学习记录", "learning_records_empty_history") }
                            } else {
                                state.history.forEach { group ->
                                    item(key = "date-${group.localDate}") {
                                        HistoryHeader(group, state.historyStats.firstOrNull { it.localDate == group.localDate })
                                    }
                                    itemsIndexed(group.records, key = { _, it -> "history-${it.planId}-${it.cardId}" }) { index, row ->
                                        LearningRecordRow(row, index, onOpenRecord)
                                    }
                                }
                            }
                        }
                    }
                    // 页尾提示条（原型 `:1323`）：措辞取单一真源，与本屏之外的同类警告同句。
                    item(key = "notice") {
                        InlineNotice(
                            text = LocalDataNotice.LearningRecordsWarning,
                            kind = InlineNoticeKind.Info,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------- 「本周」区块（U-9：七格刻度退役归打卡屏，只留「x / 7 天」+ 两格数值） ----------

/**
 * 本周小结：共享分组标题（左「本周」、右「N / 7 天」）+ 两枚真统计。
 *
 * 两枚统计都来自 `learningWeek` 的派生值，渲染侧不再各数一遍：
 *  - 「本周词数」= 本窗口内 `newDone + dueDone` 之和；
 *  - 「打卡天数」= 本周「完成」格数（与标题右侧「N / 7 天」的分子同源）。
 *
 * 批 2 这里还画一组七格刻度（`WeekTicks`），U-9 裁定退役（决策 2026-11-03 §10）：
 * 它与打卡屏的 7 根柱同义（只说「哪几天完成了」），周视图归打卡屏一处；
 * 本屏留数值，`learningWeek` 的派生链不动。
 */
@Composable
private fun WeekSection(week: LearningWeek) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("learning_records_week"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeading(text = "本周", trailing = "${week.doneDays} / 7 天")
        AppCard(modifier = Modifier.testTag("learning_records_stats")) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(value = "${week.wordCount}", label = "本周词数", modifier = Modifier.weight(1f))
                StatCell(value = "${week.doneDays}", label = "打卡天数", modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 一枚统计格（原型 `.stat`：serif 数字 + 12sp 说明，居中）。 */
@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = appColors()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = value, style = AppType.StatNum, color = colors.text1)
        Text(text = label, style = AppType.Caption, color = colors.text3)
    }
}

// ---------- 加载 / 错误 / 空态 ----------

/**
 * 首帧（批 C-3；决策 2026-11-07 决定 3 的第一类「等数据」）。
 *
 * 旧形状是 48dp 转圈 + 一句「正在读取学习记录…」——手里还没有数据时先给一句话，
 * 回填时那一句话整块长成一条条 `WordRow`，是版面跳变而不是渐进加载。现在首帧画同形状骨架：
 * 无前导图标（[SkeletonList] 的 `leading = false`——`WordRow` 的真实行没有前导图标，词头在左列、
 * 三档反馈词在右列），行档取真行的 `heightIn(min = 48.dp)`，宽度档按真行左列通栏那一族（词头短、释义宽）。
 * 进行中语义由形状承担：这里不许出现文案，「0 条记录」与「正在读取」都不是首帧该念的东西。
 *
 * 稳定锚 `learning_records_loading` 挂在容器上：connected 侧与既有 JVM 契约都读它，换实现时不能丢。
 */
@Composable
private fun LoadingState() {
    SkeletonList(
        rows = LEARNING_RECORDS_PLACEHOLDER_ROWS,
        rowHeight = 48.dp,
        tagPrefix = "learning_records_row_skeleton_",
        leading = false,
        titleFraction = 0.34f,
        subtitleFraction = 0.68f,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("learning_records_loading"),
    )
}

/**
 * 首帧骨架的行数（批 C-3）。
 *
 * 取值口径与选词书屏 `WORD_BOOK_PLACEHOLDER_ROWS` 是同一句话：够填满列表那一屏，又不至于真实列表到位后
 * 滚动位置大幅跳动。算术：真行档 `heightIn(min = 48.dp)` + 骨架族上下各 12dp 内边距 ≈ 每行 72dp；
 * 小屏减去页头、页签与 `contentPadding` 还剩约 500dp，6 行刚好铺满而不溢出（铺过头会让空列表长出滚动条，
 * 而骨架没有可点面，滚动一块空区是负体验）。
 *
 * **为什么这里没有 C-2 那条「值必须等于真实行数」的判据**：C-2 那两屏的行组是版面常量（「接下来」四行、
 * 「更多」三行，写在源码里可数）；本屏的行数是数据量（今日已学 N 条），首帧根本不知道 N。所以本屏钉的是
 * **写法**（骨架行数读常量、不许写字面量）而不是**取值相等**，判据在 `LocalSubpageSkeletonContractTest`。
 */
internal const val LEARNING_RECORDS_PLACEHOLDER_ROWS = 6

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    InlineNotice(
        text = message,
        kind = InlineNoticeKind.Error,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .testTag("learning_records_unavailable"),
        action = { InlineNoticeAction(InlineNoticeKind.Error, "重试", onClick = onRetry) },
    )
}

@Composable
private fun EmptyState(text: String, tag: String) {
    val colors = appColors()
    Text(
        text = text,
        style = AppType.Footnote,
        color = colors.text2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag(tag)
            .semantics { contentDescription = text },
    )
}

// ---------- 每日记录行 ----------

/**
 * 一天的分组头（原型 `.row-flat`：日期 + 「新学 / 复习 / 文章」副案 + 4dp 细进度条 + 右尾「N 词」）。
 *
 * 「N 词」取 `group.count`（当天提交过反馈的词条数，本仓既有字段）；文章态取 `DailyLearningHistory`
 * 的 `articleGenerated` / `articleRead`。读不到该日的详展（`stats == null`）时只画日期与「N 词」，
 * 副案与进度条整块不画——不拿「0/0」冒充「没有数据」。
 */
@Composable
private fun HistoryHeader(group: LearningHistoryGroup, stats: DailyLearningHistory?) {
    val colors = appColors()
    val target = stats?.let { it.newTarget + it.dueTarget } ?: 0
    val done = stats?.let { it.newDone + it.dueDone } ?: 0
    val progress = if (target > 0) (done.toFloat() / target).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 6.dp)
            .testTag("learning_records_history_header_${group.localDate}")
            .semantics { contentDescription = "学习日期 ${group.localDate}，${group.count} 词" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = group.localDate.toString(),
                style = AppType.Body,
                color = colors.text1,
                fontWeight = FontWeight(550),
            )
            if (stats != null) {
                val article = when {
                    !stats.articleGenerated -> "文章未生成"
                    stats.articleRead -> "文章已读"
                    else -> "文章未读"
                }
                Text(
                    text = "${stats.activeWordBookName} · 新学 ${stats.newDone}/${stats.newTarget} · 复习 ${stats.dueDone}/${stats.dueTarget} · $article",
                    style = AppType.Caption,
                    color = colors.text2,
                )
                if (target > 0) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(AppShape.Pill)
                            .testTag("learning_records_day_meter_${group.localDate}"),
                        color = colors.brandDeep,
                        trackColor = colors.tint,
                    )
                }
            }
        }
        Text(
            text = "${group.count} 词",
            style = AppType.Meta,
            color = colors.text2,
        )
    }
}

/**
 * 一条学习记录：**裸行**（批 1 Task 8，spec §4.1 裸行族）。
 *
 * 三处迁移决定：
 *  ① **行本体就是可点面**：`WordRow` 的 `onClick` 直接传——`WordRow` 把 `clickable` 挂在
 *     `fillMaxWidth()` 之后、`heightIn`/`padding` 之前，可点面是整盒；禁用态由「不传 `onClick`」表达。
 *  ② **禁用行不再自带灰底**：裸行族里一屏不许有第二种「行皮肤」；禁用与可用的区分落在
 *     「不响应点击」+「gloss 写明原因」这两件事上。
 *  ③ **不可用原因走 gloss 槽，不走 meta**：原因是 11 枚汉字，`meta` 在右列 `widthIn(max = 120.dp)`
 *     下会被卡成省略号；`gloss` 在左列 `weight(1f)`、两行、无宽度上限。成立前提是「禁用 ⇒
 *     `wordCard == null`」，由 `LearningRecordsRowContractTest` 的 JVM 锚钉住。
 */
@Composable
private fun LearningRecordRow(row: LearningRecord, index: Int, onOpenRow: (LearningRecord) -> Unit) {
    val safeId = row.safeId()
    val enabled = row.canOpenDetail
    val unavailableNotice = if (enabled) null else "这个词已不在当前词书中"
    WordRow(
        lemma = row.displayLemma,
        gloss = row.wordCard?.meaningZh?.takeIf { it.isNotBlank() } ?: unavailableNotice,
        // 右列只写三档反馈词本身；行的位置与右对齐已经说明这一列是什么。
        status = row.feedback.label(),
        topDivider = index > 0,
        onClick = if (enabled) ({ onOpenRow(row) }) else null,
        modifier = Modifier
            .testTag(if (enabled) "learning_record_row_$safeId" else "learning_record_missing_$safeId")
            .padding(horizontal = 20.dp),
    )
}

private fun LearningRecordsTab.label(): String = when (this) {
    LearningRecordsTab.TODAY -> "今日已学"
    LearningRecordsTab.HISTORY -> "历史记录"
}

private fun LearningRecordsTab.testTag(): String = when (this) {
    LearningRecordsTab.TODAY -> "learning_records_tab_today"
    LearningRecordsTab.HISTORY -> "learning_records_tab_history"
}

private fun LearningRecord.safeId(): String = cardId.substringAfterLast(':').safeId()
private fun String.safeId(): String = replace(Regex("[^A-Za-z0-9一-龥_-]"), "_")

private fun CardFeedback.label(): String = when (this) {
    CardFeedback.Known -> "认识"
    CardFeedback.Fuzzy -> "模糊"
    CardFeedback.Unknown -> "不认识"
}
