package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.LearningHistoryGroup
import com.example.englishlearning.learning.LearningRecord
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint

@Composable
fun LearningRecordsScreen(
    state: LearningRecordsUiState,
    onBack: () -> Unit,
    onSelectTab: (LearningRecordsTab) -> Unit,
    onRetry: () -> Unit,
    onFilter: (WordBookRecordStatus?) -> Unit,
    onOpenRecord: (LearningRecord) -> Unit,
    onOpenWord: (WordBookRecord) -> Unit,
) {
    val tabs = LearningRecordsTab.entries
    val selectedIndex = tabs.indexOf(state.selectedTab).coerceAtLeast(0)
    val selectedError = state.errors[state.selectedTab]
    val isLoading = state.selectedTab in state.loading
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            .testTag("learning_records_screen")
            .semantics { contentDescription = "learning_records_screen" },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = "← 返回",
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(8.dp)
                    .testTag("learning_records_back")
                    .semantics { contentDescription = "learning_records_back" },
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
                        .semantics { contentDescription = label },
                    text = { Text(label) },
                )
            }
        }
        if (state.selectedTab == LearningRecordsTab.ALL_WORDS) {
            FilterBar(state, onFilter)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                isLoading -> item { LoadingState() }
                selectedError != null -> item { ErrorState(selectedError, onRetry) }
                else -> when (state.selectedTab) {
                    LearningRecordsTab.TODAY -> {
                        if (state.today.isEmpty()) {
                            item { EmptyState("今天还没有学习记录", "learning_records_empty_today") }
                        } else {
                            items(state.today, key = { "today-${it.planId}-${it.cardId}" }) { row ->
                                LearningRecordRow(row, onOpenRecord)
                            }
                        }
                    }
                    LearningRecordsTab.HISTORY -> {
                        if (state.history.isEmpty()) {
                            item { EmptyState("还没有跨日期的学习记录", "learning_records_empty_history") }
                        } else {
                            state.history.forEach { group ->
                                item(key = "date-${group.localDate}") { HistoryHeader(group) }
                                items(group.records, key = { "history-${it.planId}-${it.cardId}" }) { row ->
                                    LearningRecordRow(row, onOpenRecord)
                                }
                            }
                        }
                    }
                    LearningRecordsTab.ALL_WORDS -> {
                        val rows = state.allWords.filter { state.filter == null || it.status == state.filter }
                        if (rows.isEmpty()) {
                            item { EmptyState(allWordsEmptyText(state.filter), "learning_records_empty_all_words") }
                        } else {
                            items(rows, key = { "word-${it.cardId}" }) { row ->
                                WordBookRecordRow(row, onOpenWord)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(state: LearningRecordsUiState, onFilter: (WordBookRecordStatus?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val filters = listOf<WordBookRecordStatus?>(null) + WordBookRecordStatus.entries
        filters.forEach { filter ->
            val count = filter?.let { status -> state.allWords.count { it.status == status } } ?: state.allWords.size
            FilterChip(
                selected = state.filter == filter,
                onClick = { onFilter(filter) },
                modifier = Modifier
                    .testTag(filter.testTag())
                    .semantics { contentDescription = "${filter.label()} $count" },
                label = { Text("${filter.label()} $count") },
            )
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
            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
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
private fun HistoryHeader(group: LearningHistoryGroup) {
    Text(
        text = "${group.localDate} · ${group.count} 词",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics { contentDescription = "学习日期 ${group.localDate}，${group.count} 词" },
        color = MintPrimaryDark,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun LearningRecordRow(row: LearningRecord, onOpenRow: (LearningRecord) -> Unit) {
    val label = row.displayLemma
    val safeId = row.safeId()
    RecordRow(
        lemma = label,
        meaning = row.wordCard?.meaningZh,
        status = "反馈：${row.feedback.label()}",
        enabled = row.canOpenDetail,
        reason = if (row.canOpenDetail) null else "这个词已不在当前词书中",
        tag = if (row.canOpenDetail) "learning_record_row_$safeId" else "learning_record_missing_$safeId",
        onClick = { onOpenRow(row) },
    )
}

@Composable
private fun WordBookRecordRow(row: WordBookRecord, onOpenRow: (WordBookRecord) -> Unit) {
    RecordRow(
        lemma = row.displayLemma,
        meaning = row.card?.meaningZh,
        status = row.status.statusLabel(),
        enabled = row.canOpenDetail,
        reason = if (row.canOpenDetail) null else "这个词已不在当前词书中",
        tag = if (row.canOpenDetail) "learning_record_row_${row.safeId()}" else "learning_record_missing_${row.safeId()}",
        onClick = { onOpenRow(row) },
    )
}

@Composable
private fun RecordRow(
    lemma: String,
    meaning: String?,
    status: String,
    enabled: Boolean,
    reason: String?,
    tag: String,
    onClick: () -> Unit,
) {
    val textColor = if (enabled) MintPrimaryDark else MintTextMuted
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag(tag)
            .semantics {
                // 保留既有稳定 contentDescription；中文释义、反馈和原因同时作为可见语义文本。
                contentDescription = tag
            },
        colors = CardDefaults.cardColors(containerColor = if (enabled) MintSurface else MintTint.copy(alpha = 0.55f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) MintOutline else MintOutline.copy(alpha = 0.65f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(lemma, color = textColor, fontWeight = FontWeight.Bold)
            if (!meaning.isNullOrBlank()) Text("释义：$meaning", color = textColor)
            Text(status, color = textColor)
            reason?.let { Text(it, color = MintTextMuted) }
        }
    }
}

private fun LearningRecordsTab.label(): String = when (this) {
    LearningRecordsTab.TODAY -> "今日已学"
    LearningRecordsTab.HISTORY -> "历史记录"
    LearningRecordsTab.ALL_WORDS -> "全部单词"
}

private fun LearningRecordsTab.testTag(): String = when (this) {
    LearningRecordsTab.TODAY -> "learning_records_tab_today"
    LearningRecordsTab.HISTORY -> "learning_records_tab_history"
    LearningRecordsTab.ALL_WORDS -> "learning_records_tab_all_words"
}

private fun WordBookRecordStatus?.label(): String = when (this) {
    null -> "全部"
    WordBookRecordStatus.Unlearned -> "未学"
    WordBookRecordStatus.Learning -> "学习中"
    WordBookRecordStatus.Due -> "待复习"
}

private fun WordBookRecordStatus?.testTag(): String = when (this) {
    null -> "learning_records_filter_all"
    WordBookRecordStatus.Unlearned -> "learning_records_filter_unlearned"
    WordBookRecordStatus.Learning -> "learning_records_filter_learning"
    WordBookRecordStatus.Due -> "learning_records_filter_due"
}

private fun LearningRecord.safeId(): String = cardId.substringAfterLast(':').safeId()
private fun WordBookRecord.safeId(): String = cardId.substringAfterLast(':').safeId()
private fun String.safeId(): String = replace(Regex("[^A-Za-z0-9一-龥_-]"), "_")

private fun CardFeedback.label(): String = when (this) {
    CardFeedback.Known -> "认识"
    CardFeedback.Fuzzy -> "模糊"
    CardFeedback.Unknown -> "不认识"
}

private fun WordBookRecordStatus.statusLabel(): String = when (this) {
    WordBookRecordStatus.Unlearned -> "未学"
    WordBookRecordStatus.Learning -> "学习中"
    WordBookRecordStatus.Due -> "待复习"
}

private fun allWordsEmptyText(filter: WordBookRecordStatus?): String = when (filter) {
    null -> "当前还没有可显示的词书单词"
    WordBookRecordStatus.Unlearned -> "当前没有未学单词"
    WordBookRecordStatus.Learning -> "当前没有学习中的单词"
    WordBookRecordStatus.Due -> "当前没有待复习单词"
}
