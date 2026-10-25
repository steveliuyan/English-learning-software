package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.example.englishlearning.ui.components.WordRow
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintTextMuted

@Composable
fun LearningRecordsScreen(
    state: LearningRecordsUiState,
    onBack: () -> Unit,
    onSelectTab: (LearningRecordsTab) -> Unit,
    onRetry: () -> Unit,
    onOpenRecord: (LearningRecord) -> Unit,
) {
    val tabs = LearningRecordsTab.entries
    val selectedIndex = tabs.indexOf(state.selectedTab).coerceAtLeast(0)
    val selectedError = state.errors[state.selectedTab]
    val isLoading = state.selectedTab in state.loading
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            .testTag("learning_records_screen"),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = "← 返回",
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onBack)
                    .padding(8.dp)
                    .testTag("learning_records_back")
                    .semantics { contentDescription = "返回" },
                color = MintPrimaryDark,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "学习记录",
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                color = MintPrimaryDark,
                fontWeight = FontWeight.Bold,
            )
        }
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
                            // 决策 §4（终审 I-1）：与 AppBottomBar.TabSlot 同形，选中态必须进播报，
                            // 不能只靠 M3 selectable 的 Selected 布尔属性代偿。
                            stateDescription = if (state.selectedTab == tab) "已选中" else "未选中"
                        },
                    text = { Text(label) },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 批 1 Task 8：横向缩进由行内 20dp 承担（`hairlineTop` 的线才能从行内起点起笔、
            // 而且行与行之间零间隙——分隔线替代了旧版卡与卡之间的 10dp 空当）。
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            when {
                isLoading -> item { LoadingState() }
                selectedError != null -> item { ErrorState(selectedError, onRetry) }
                else -> when (state.selectedTab) {
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
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("learning_records_loading"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(color = MintPrimary)
        Text("正在读取学习记录…", color = MintTextMuted)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("learning_records_unavailable"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(message, color = MintPrimaryDark)
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag("learning_records_retry").semantics { contentDescription = "重试读取学习记录" },
            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = appColors().onBrand),
        ) { Text("重试") }
    }
}

@Composable
private fun EmptyState(text: String, tag: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag(tag).semantics { contentDescription = text },
        color = MintTextMuted,
    )
}

@Composable
private fun HistoryHeader(group: LearningHistoryGroup, stats: DailyLearningHistory?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("learning_records_history_header_${group.localDate}")
            .semantics { contentDescription = "学习日期 ${group.localDate}，${group.count} 词" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${group.localDate} · ${group.count} 词",
            color = MintPrimaryDark,
            fontWeight = FontWeight.Bold,
        )
        if (stats != null) {
            // F4-01 详展行：册名 · 新词/复习完成数 · 解锁状态 · 文章生成与阅读状态（AC4-01）。
            val article = when {
                !stats.articleGenerated -> "文章未生成"
                stats.articleRead -> "文章已生成·已读"
                else -> "文章已生成·未读"
            }
            Text(
                text = "${stats.activeWordBookName} · 新词 ${stats.newDone}/${stats.newTarget} · 复习 ${stats.dueDone}/${stats.dueTarget} · ${if (stats.unlocked) "已解锁" else "未解锁"} · $article",
                color = MintTextMuted,
            )
        }
    }
}

/**
 * 一条学习记录：**裸行**（批 1 Task 8，spec §4.1 裸行族）。
 *
 * 三处迁移决定：
 *  ① **行本体就是可点面**：`WordRow` 的 `onClick` 直接传——`WordRow` 把 `clickable` 挂在
 *     `fillMaxWidth()` 之后、`heightIn`/`padding` 之前，可点面是整盒；旧版「卡负责可点、行不管」
 *     的双节点写法随 `Card` 一并删掉。禁用态由「不传 `onClick`」表达（`WordRow` 不给空
 *     `onClick` 挂 `clickable`，也就没有 `Role.Button`），原因仍写进 gloss 槽。
 *  ② **禁用行不再自带灰底**（旧版是 `MintTint.copy(alpha = 0.55f)` + 淡描边）：裸行族里
 *     一屏不许有第二种「行皮肤」，否则同一列表中两种底色的行又成了卡片汤的小号版本。
 *     禁用与可用的区分落在「不响应点击」+「gloss 写明原因」这两件事上；代价是不可用行
 *     与可用行外观接近，故原因串必须写清（「这个词已不在当前词书中」那类），不能只靠灰。
 *  ③ **不可用原因走 gloss 槽，不走 meta**：原因是 11 枚汉字，`meta` 在右列 `widthIn(max = 120.dp)`
 *     下会被卡成省略号；`gloss` 在左列 `weight(1f)`、两行、无宽度上限。成立前提是「禁用 ⇒
 *     `wordCard == null`」，与 WordBookWords 同一条互斥链：`LearningRecord.canOpenDetail` 默认值
 *     `= wordCard != null`，生产构造只有两处——`LearningRecord.missing(...)` 显式传 `null`/`false`，
 *     `RoomLearningRecordRepository` 查到卡时走默认值。前提若放开（`wordCard != null` 却
 *     `canOpenDetail == false`），原因串会被释义吃掉；本屏的互斥链由
 *     `LearningRecordsRowContractTest` 的 JVM 锚钉住（改动前先读那条用例）。
 */
@Composable
private fun LearningRecordRow(row: LearningRecord, index: Int, onOpenRow: (LearningRecord) -> Unit) {
    val safeId = row.safeId()
    val enabled = row.canOpenDetail
    val unavailableNotice = if (enabled) null else "这个词已不在当前词书中"
    WordRow(
        lemma = row.displayLemma,
        gloss = row.wordCard?.meaningZh?.takeIf { it.isNotBlank() } ?: unavailableNotice,
        // 批 1 Task 8：右列只写三档反馈词本身。「反馈：」前缀在 120dp 上限里占掉三枚汉字宽度，
        // 把「认识/模糊/不认识」挤进省略号；行的位置与右对齐已经说明这一列是什么。
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
