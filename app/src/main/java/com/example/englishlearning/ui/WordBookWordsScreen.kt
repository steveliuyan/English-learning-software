package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.getValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import com.example.englishlearning.ui.components.WordRow
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint

/**
 * 词书单词浏览页（D2）：字母分组 + 顶部横向可滚的 A–Z 快速索引（Task 21 起为 48dp 芯片条）。
 * 默认落在 A 组顶部，点索引字母直接跳到对应组，不用长滑找词。
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
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
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
        // Task 21：字母索引从右侧竖排压缩槽搬到正文上方的横向可滚 48dp 芯片条
        // （Rail 的显示条件与挂载语义逐字保留：载入中/不可用时不出现）。
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
            )
        }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = { onOpenRow(row) }) else Modifier)
            .testTag("wordbook_words_row_$safeId"),
        colors = CardDefaults.cardColors(containerColor = if (enabled) MintSurface else MintTint.copy(alpha = 0.55f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) MintOutline else MintOutline.copy(alpha = 0.65f)),
    ) {
        // Task 20-B（计划 Step 4）：卡内那枚私有 `Column` + 四枚 `Text` 换成共享 [WordRow]。
        // 卡面归调用方（本屏的 enabled/disabled 底色与描边、testTag、可点面都原样保留），行本体只管版式；
        // **不传 onClick**：可点面只有卡那一枚，禁用态由「不挂 clickable」表达。
        // 禁用行的行墨因此与可用行统一（旧的 `textColor` 双档随私有行一起删掉）。口径更正（Task 20-D 结转 §五.1）：
        // 「设备侧读不到颜色」不准确——**语义树**读不到取色，但文本节点可以经 `textLayout().layoutInput.style`
        // 读到 `style.color`（现场证据即同测试类 `activeLetterUsesADifferentColor`，它就在读这一枚）。
        // 所以「本屏没有任何用例断过禁用墨」是**可测而不测**，不是测不了（重算命令：
        // `grep -n "layoutInput.style" app/src/androidTest/java/com/example/englishlearning/ui/WordBookWordsScreenTest.kt`）；
        // 禁用墨若要补取证走这条读法即可，判据仍在 connected `WordBookWordsScreenTest`。
        // 修复轮 1 · F1（控制者裁定 R-20B.3，授权偏离 20-B 派工单槽位映射表第三行）：禁用行原因串 11 枚汉字
        // ≈143dp，走 `meta` 会被右列 `widthIn(max = 120.dp)` + `maxLines = 1` 卡成省略号（设备实测 120.0dp），
        // 呈现层丢信息；改走 `gloss` 槽（左列 weight(1f)、maxLines = 2、无 120dp 上限），`meta` 置空。
        // 成立前提是「两槽互斥」：禁用 ⇒ `card == null` ⇒ 释义侧按构造恒为 `null`，`?:` 才落到原因串；
        // 前提由 JVM 锚 `WordBookWordsViewModelTest`「can open detail stays derived from card presence…」钉死，
        // 哪天放开（如 `card != null` 却 `canOpenDetail == false`）锚当场红，提示改本屏写法——
        // 那种形态下原因串会被释义吃掉（新的信息丢失）。connected 判据：`disabledRowShowsTheUnavailableNoticeInFull`。
        val unavailableNotice = if (enabled) null else "这个词已不在当前词书中"
        WordRow(
            lemma = row.displayLemma,
            gloss = row.card?.meaningZh?.takeIf { it.isNotBlank() }?.let { "释义：$it" } ?: unavailableNotice,
            status = row.status.statusLabel(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
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

/**
 * 正文上方的 A–Z 导航条（Task 21 / F5-03 移交账 AC-R-04）：26 枚 **48dp 见方**字母芯片横向可滚。
 * 有单词的字母高亮、当前组更醒目，没有的变淡但仍可点（挂载点保留「跳字母序最近的下一组」）。
 *
 * 版面选型（裁定 R-21.1，理由见 task-21 报告）：AC-R-04 约束的是控件**高度** ≥48dp，
 * 横向滚动容器让 26 枚方形目标各占 48dp 宽且互不重叠；本屏 `WordBookWordsFilterBar` 已是
 * `horizontalScroll` + chip 的同款先例。三段折叠会把「任一字母一次直达」变成两次操作，
 * 且 26×48dp=1248dp 的纵向空间远超屏高。
 * 旧竖排压缩槽（`slotPixels = min(18dp, maxHeight/26)`，命中区 ≈24×13dp）连同其**代偿输入路径**
 * ——整轨 `pointerInput` 拖动面——一并退役（豁免注记见 `docs/decisions/2026-10-15-f5-03-accessibility-hardening-semantics.md` §5）。
 */
@Composable
private fun WordBookWordsAlphabetRail(
    availableLetters: Set<String>,
    currentLetter: String?,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("wordbook_words_rail")
            // 左右 20dp 与本屏 `WordBookWordsFilterBar` 的同款横向滚动条一致（贴边会把芯片顶到屏幕边缘）；
            // 竖向只给 6dp：芯片本身已是 48dp，再叠 FilterBar 那 10dp 会让索引条吃掉过多正文高度。
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ('A'..'Z').forEach { letter ->
            val letterText = letter.toString()
            val available = letterText in availableLetters
            val active = letterText == currentLetter
            // 外层只管「当前字母」这枚标签：**同一个节点只能留一枚 `testTag`**
            // （设备实测：两枚并挂时 `wordbook_words_rail_active_*` 在语义树里彻底消失，23 例里 17 例连带红），
            // 所以 active 标签必须由独立语义节点承载。旧竖排也是这个形状（槽 Box + 字形 Text 两枚节点）。
            Box(
                modifier = if (active) Modifier.testTag("wordbook_words_rail_active_$letterText") else Modifier,
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        // 48dp 下限钉在**这枚带 clickable 的节点自身**（AC-R-04 要的是可点区，不是视觉高度）；
                        // 用下限而不是 `size`，动态字体下芯片随字号只增不减。`spacedBy(2.dp)` 保证相邻命中区不相交，
                        // 旧压缩槽「命中区落在整槽内并覆盖槽心」那套代偿护栏因此退役。
                        // 颜色只读既有薄荷令牌，本函数不新增裸 hex / `AppPalette.` / `DomainColors.` 静态读点。
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(onClick = { onLetter(letterText) })
                        .testTag("wordbook_words_rail_$letterText"),
                    // 字形由 Box 居中，不靠 `lineHeight` 撑高：后者会让 `TextLayoutResult.hasVisualOverflow`
                    // 为真（设备实测），把「字形不越界」这条护栏变成假红。
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letterText,
                        // 朗读定语挂在字形这一枚：合并语义后由上面那枚可点芯片承载（TalkBack 念的是按钮），
                        // 而 connected 侧要读字形取值时用 `useUnmergedTree = true` 取本节点。
                        modifier = Modifier.semantics { contentDescription = "导航字母 $letterText" },
                        color = when {
                            active -> MintPrimary
                            available -> MintPrimaryDark
                            else -> MintTextMuted.copy(alpha = 0.45f)
                        },
                        fontSize = if (active) 17.sp else 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
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
