package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint

/**
 * 词书单词浏览页（D2）：字母分组 + 右侧 A–Z 快速导航栏。
 * 默认落在 A 组顶部，点右侧字母直接跳到对应组，不用长滑找词。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordBookWordsScreen(
    state: WordBookWordsUiState,
    onBack: () -> Unit,
    onFilter: (WordBookRecordStatus?) -> Unit,
    onRetry: () -> Unit,
    onOpenWord: (WordBookRecord) -> Unit,
) {
    val groups = state.groups
    val listState = rememberLazyListState()
    val railScope = rememberCoroutineScope()
    // 字母 → 该组 sticky header 在 LazyColumn 里的 item 下标（1 个 header + N 行为一组）。
    val headerIndices = remember(groups) {
        var running = 0
        groups.associate { group -> group.letter to running.also { running += 1 + group.records.size } }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            .testTag("wordbook_words_screen")
            .semantics { contentDescription = "wordbook_words_screen" },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = "← 返回",
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(8.dp)
                    .testTag("wordbook_words_back")
                    .semantics { contentDescription = "wordbook_words_back" },
                color = MintPrimaryDark,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "词书单词",
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                color = MintPrimaryDark,
                fontWeight = FontWeight.Bold,
            )
        }
        WordBookWordsFilterBar(state, onFilter)
        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                        .testTag("wordbook_words_loading"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(color = MintPrimary)
                    Text("正在读取词书单词…", color = MintTextMuted)
                }
                state.error != null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                        .testTag("wordbook_words_unavailable"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(state.error, color = MintPrimaryDark)
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .testTag("wordbook_words_retry")
                            .semantics { contentDescription = "重试读取词书单词" },
                        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
                    ) { Text("重试") }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 36.dp, top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (groups.isEmpty()) {
                        item { WordBookWordsEmpty() }
                    }
                    groups.forEach { group ->
                        stickyHeader(key = "letter-${group.letter}") {
                            WordBookWordsLetterHeader(group.letter)
                        }
                        items(group.records, key = { "word-${it.cardId}" }) { row ->
                            WordBookWordsRow(row, onOpenWord)
                        }
                    }
                }
            }
            if (!state.loading && state.error == null) {
                WordBookWordsAlphabetRail(
                    availableLetters = groups.map { it.letter }.toSet(),
                    onLetter = { letter ->
                        // 点了没有单词的字母时跳到「字母序最近的下一组」，避免点了没反应。
                        val target = groups.firstOrNull { it.letter >= letter }?.letter
                            ?: groups.lastOrNull()?.letter
                            ?: return@WordBookWordsAlphabetRail
                        headerIndices[target]?.let { index ->
                            railScope.launch { listState.scrollToItem(index) }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun WordBookWordsFilterBar(state: WordBookWordsUiState, onFilter: (WordBookRecordStatus?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val filters = listOf<WordBookRecordStatus?>(null) + WordBookRecordStatus.entries
        filters.forEach { filter ->
            val count = filter?.let { status -> state.words.count { it.status == status } } ?: state.words.size
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
private fun WordBookWordsLetterHeader(letter: String) {
    Text(
        text = letter,
        modifier = Modifier
            .fillMaxWidth()
            .background(MintBackground)
            .padding(vertical = 4.dp)
            .testTag("wordbook_words_letter_$letter")
            .semantics { contentDescription = "字母 $letter" },
        color = MintPrimaryDark,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    )
}

@Composable
private fun WordBookWordsRow(row: WordBookRecord, onOpenRow: (WordBookRecord) -> Unit) {
    val safeId = row.cardId.substringAfterLast(':').replace(Regex("[^A-Za-z0-9一-龥_-]"), "_")
    val enabled = row.canOpenDetail
    val textColor = if (enabled) MintPrimaryDark else MintTextMuted
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = { onOpenRow(row) }) else Modifier)
            .testTag("wordbook_words_row_$safeId")
            .semantics { contentDescription = "wordbook_words_row_$safeId" },
        colors = CardDefaults.cardColors(containerColor = if (enabled) MintSurface else MintTint.copy(alpha = 0.55f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) MintOutline else MintOutline.copy(alpha = 0.65f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.displayLemma, color = textColor, fontWeight = FontWeight.Bold)
            row.card?.meaningZh?.takeIf { it.isNotBlank() }?.let { Text("释义：$it", color = textColor) }
            Text(row.status.statusLabel(), color = textColor)
            if (!enabled) Text("这个词已不在当前词书中", color = MintTextMuted)
        }
    }
}

@Composable
private fun WordBookWordsEmpty() {
    val text = "当前还没有可显示的词书单词"
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("wordbook_words_empty")
            .semantics { contentDescription = text },
        color = MintTextMuted,
    )
}

/** 右侧 A–Z 导航栏：26 个字母常驻；有单词的字母高亮，没有的变淡但仍可点（跳到最近一组）。 */
@Composable
private fun WordBookWordsAlphabetRail(
    availableLetters: Set<String>,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(20.dp)
            .fillMaxHeight()
            .testTag("wordbook_words_rail"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ('A'..'Z').forEach { letter ->
            val letterText = letter.toString()
            val available = letterText in availableLetters
            Text(
                text = letterText,
                modifier = Modifier
                    .clickable(onClick = { onLetter(letterText) })
                    .padding(vertical = 1.dp, horizontal = 2.dp)
                    .testTag("wordbook_words_rail_$letterText")
                    .semantics { contentDescription = "导航字母 $letterText" },
                color = if (available) MintPrimaryDark else MintTextMuted.copy(alpha = 0.45f),
                fontSize = 9.sp,
                lineHeight = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun WordBookRecordStatus?.label(): String = when (this) {
    null -> "全部"
    WordBookRecordStatus.Unlearned -> "未学"
    WordBookRecordStatus.Learning -> "学习中"
    WordBookRecordStatus.Due -> "待复习"
}

private fun WordBookRecordStatus?.testTag(): String = when (this) {
    null -> "wordbook_words_filter_all"
    WordBookRecordStatus.Unlearned -> "wordbook_words_filter_unlearned"
    WordBookRecordStatus.Learning -> "wordbook_words_filter_learning"
    WordBookRecordStatus.Due -> "wordbook_words_filter_due"
}

private fun WordBookRecordStatus.statusLabel(): String = when (this) {
    WordBookRecordStatus.Unlearned -> "未学"
    WordBookRecordStatus.Learning -> "学习中"
    WordBookRecordStatus.Due -> "待复习"
}
