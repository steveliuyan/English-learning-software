package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlinx.coroutines.launch
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import com.example.englishlearning.ui.theme.AppPalette
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
    listState: LazyListState = rememberLazyListState(),
) {
    val groups = state.groups
    val railScope = rememberCoroutineScope()
    // 字母 → 该组 sticky header 在 LazyColumn 里的 item 下标（1 个 header + N 行为一组）。
    val headerIndices = remember(groups) {
        var running = 0
        groups.associate { group -> group.letter to running.also { running += 1 + group.records.size } }
    }
    val currentLetter by remember(groups, headerIndices) {
        derivedStateOf {
            if (!listState.canScrollForward && listState.canScrollBackward) {
                groups.lastOrNull()?.letter
            } else {
                val firstVisibleIndex = listState.firstVisibleItemIndex
                groups
                    .asReversed()
                    .firstOrNull { group ->
                        headerIndices[group.letter]?.let { it <= firstVisibleIndex } == true
                    }
                    ?.letter
                    ?: groups.firstOrNull()?.letter
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            .testTag("wordbook_words_screen"),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = "← 返回",
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(8.dp)
                    .testTag("wordbook_words_back")
                    .semantics { contentDescription = "返回" },
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
                        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = AppPalette.OnMintPrimary),
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
                    currentLetter = currentLetter,
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
                    .testTag(filter.testTag()),
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
            .testTag("wordbook_words_row_$safeId"),
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
    currentLetter: String?,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnLetter by rememberUpdatedState(onLetter)
    BoxWithConstraints(modifier.width(24.dp).fillMaxHeight()) {
        val density = LocalDensity.current
        // 布局和手势共用整数像素槽高，避免小高度下逐槽取整产生累计定位误差。
        val slotPixels = with(density) { floor(minOf(18.dp, maxHeight / 26).toPx()).coerceAtLeast(1f) }
        val slotHeight = with(density) { slotPixels.toDp() }
        val activeFontSize = with(density) {
            // 压缩高度下按字形安全比例缩小，保留比普通字母更醒目的字号但不让字形越过行框。
            minOf(16.sp.toPx(), (slotHeight.toPx() * 0.7f).coerceAtLeast(10.sp.toPx())).toSp()
        }
        val activeLineHeight = 16.sp
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .testTag("wordbook_words_rail")
                .pointerInput(slotPixels) {
                    fun navigate(y: Float) {
                        val index = (y / slotPixels).toInt().coerceIn(0, 25)
                        latestOnLetter(('A'.code + index).toChar().toString())
                    }
                    detectDragGestures(
                        onDragStart = { navigate(it.y) },
                        onDrag = { change, _ ->
                            change.consume()
                            navigate(change.position.y)
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ('A'..'Z').forEach { letter ->
                val letterText = letter.toString()
                val available = letterText in availableLetters
                val active = letterText == currentLetter
                Box(
                    modifier = Modifier.fillMaxWidth().height(slotHeight).testTag("wordbook_words_rail_$letterText"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letterText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = { onLetter(letterText) })
                            .then(if (active) Modifier.wrapContentHeight(unbounded = true) else Modifier)
                            .padding(vertical = if (active) 0.dp else 1.dp, horizontal = 2.dp)
                            .then(
                                if (active) {
                                    Modifier.testTag("wordbook_words_rail_active_$letterText")
                                } else {
                                    Modifier
                                },
                            )
                            .semantics { contentDescription = "导航字母 $letterText" },
                        color = when {
                            active -> MintPrimary
                            available -> MintPrimaryDark
                            else -> MintTextMuted.copy(alpha = 0.45f)
                        },
                        fontSize = if (active) activeFontSize else 9.sp,
                        lineHeight = if (active) activeLineHeight else 10.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        style = if (active) TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)) else TextStyle.Default,
                    )
                }
            }
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
