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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import com.example.englishlearning.ui.components.SkeletonList
import com.example.englishlearning.ui.components.WordRow
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * 词书单词浏览页（D2）：字母分组 + 贴屏幕右边缘的竖排 A–Z 快速索引（m05053 改版；Task 21 起曾为顶部横向 48dp 芯片条）。
 * 默认落在 A 组顶部，点索引字母直接跳到对应组，不用长滑找词。
 *
 * 批 1 Task 5 起本屏还是词库页 `WordBankScreen` 的「词书段」：那种用法下由壳提供页头与四分段，
 * 本屏只出「筛选 chip + 字母栏 + 词行」，故 [showHeader] 可关（默认 true，既有挂载点与 connected 夹具不动）。
 * 两枚新形参都追加在末尾且有默认值，调用处不需要改。
 *
 * 2026-11-05 弹窗与查词域排版收口批：本屏是 UI 重设计轮最后一屏仍在读 `Mint*` 桥接令牌与手搓字号的地方。
 * 先说清事实，避免后人误判严重度：**`Mint*` 不是静态浅套值**，`theme/MintPalette.kt` 里它们是
 * `@Composable @ReadOnlyComposable` getter，内部逐条读 `LocalAppColors` / `LocalDomainColors`
 * （`MintPrimaryDark` = `text1`、`MintTextMuted` = `text2`、`MintOutline` = `outlineSoft`、
 * `MintPrimary` = `AppDomain.AiSpeech.base`），所以深色档此前**并没有拿到浅值**，本屏不是暗色缺陷现场。
 * 迁移的收益是两条：① 全站排印/取墨只剩 `appColors()` / `appDomain()` / `AppType` 一条读法，
 * 桥接令牌可以在后续批次整批下线；② 三处手搓字号（页头两枚默认 15sp、字母分组头 16.sp、索引芯片 17.sp/13.sp）
 * 换进六档，分组头与当前字母随之拿到档自带的字重与行高，动态字体档下缩放行为与全站一致。
 * 取值逐字不变（Title=17sp、Footnote=13sp 与原先的 `.sp` 数相同），connected 侧
 * `WordBookWordsScreenTest` 那两条「当前字母更大」「当前字母颜色区别于普通字母」的判据因此继续成立。
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
    showHeader: Boolean = true,
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .fillMaxSize()
            .background(appColors().background)
            .testTag("wordbook_words_screen"),
    ) {
        val colors = appColors()
        if (showHeader) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = "← 返回",
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(onClick = onBack)
                        .padding(8.dp)
                        .testTag("wordbook_words_back")
                        .semantics { contentDescription = "返回" },
                    style = AppType.Title,
                    color = colors.text1,
                )
                Text(
                    text = "词书单词",
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                    style = AppType.Title,
                    color = colors.text1,
                )
            }
        }
        WordBookWordsFilterBar(state, onFilter)
        // m05053：字母索引从「正文上方横向可滚 48dp 芯片条」改回「贴屏幕右边缘的竖排 A–Z」，
        // 且与列表**并排**而不是叠在列表上方——用户反馈：索引本该在屏幕右边；页头 + 筛选条 +
        // 索引条一起把上半屏吃掉、词区只剩半屏。Rail 的显示条件与挂载语义逐字保留
        // （载入中/不可用时不挂：没有数据就没有可导航的目标）。
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> SkeletonList(
                        // 批 C-3（Spec 05 F5-05 / 决策 2026-11-07 决定 3 第一类「等数据」）：
                        // 旧首帧是 48dp 转圈 + 一句「正在读取词书单词…」。真实列表那一族是
                        // `Card(surface + 1dp outlineSoft 描边)` 里套 `WordRow(padding 16/12)`、
                        // 行与行 `spacedBy(10.dp)`（`WordBookWordsRow`），所以这里走族里的**卡行档**
                        // （`framed = true`：壳、内边距、缝都由族按真实形状给），并且
                        // `leading = false`——`WordRow` 没有前导图标，词头在左列、状态词在右列。
                        // 宽度档按真实行：词头（`AppType.Body` 一个英文词）短、释义行（「释义：…」）宽。
                        // 字母索引条与页尾不铺：前者是有数据才挂的导航件（`!state.loading` 那一判），
                        // 后者是回执位，都不是「行」的形状。
                        rows = WORD_BOOK_WORDS_PLACEHOLDER_ROWS,
                        rowHeight = 48.dp,
                        tagPrefix = "wordbook_words_row_skeleton_",
                        leading = false,
                        framed = true,
                        titleFraction = 0.30f,
                        subtitleFraction = 0.62f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .testTag("wordbook_words_loading"),
                    )
                    state.error != null -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp)
                            .testTag("wordbook_words_unavailable"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(state.error, style = AppType.Body, color = colors.text1)
                        Button(
                            onClick = onRetry,
                            modifier = Modifier
                                .testTag("wordbook_words_retry")
                                .semantics { contentDescription = "重试读取词书单词" },
                            colors = ButtonDefaults.buttonColors(containerColor = appDomain(AppDomain.AiSpeech).base, contentColor = colors.onBrand),
                        ) { Text("重试") }
                    }
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
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
        }
    }
}

@Composable
private fun WordBookWordsFilterBar(state: WordBookWordsUiState, onFilter: (WordBookRecordStatus?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 6.dp),
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
    val colors = appColors()
    Text(
        text = letter,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(vertical = 4.dp)
            .testTag("wordbook_words_letter_$letter")
            .semantics { contentDescription = "字母 $letter" },
        style = AppType.Title,
        color = colors.text1,
    )
}

/**
 * 首帧骨架的占位行数（批 C-3）。
 *
 * 算术：真实行 = `Card` 包 `WordRow`，行内容 `heightIn(min = 48.dp)` + 上下各 12dp ≈ 72dp（描边 +2dp），
 * 行缝 10dp → 每行占位约 84dp；小屏扣掉页头、筛选条与 `contentPadding` 后列表区约 420–500dp，
 * 取 5 行（420dp）填满可视区而不溢出。骨架没有可点面，铺过头会让首屏多一条无内容的滚动带。
 *
 * 与 C-2 那两枚常量的差别（`TODAY_PLAN_NEXT_ROWS` / `READING_MORE_ROWS` 的值必须等于源码里可数的
 * `BareRow(` 数）：本屏真实条数是数据量（这本词书有多少词），首帧无从可数，所以
 * `LocalSubpageSkeletonContractTest` 对本屏只钉**写法**（骨架行数读这枚常量、不写字面量），不钉相等。
 */
internal const val WORD_BOOK_WORDS_PLACEHOLDER_ROWS = 5

@Composable
private fun WordBookWordsRow(row: WordBookRecord, onOpenRow: (WordBookRecord) -> Unit) {
    val colors = appColors()
    val safeId = row.cardId.substringAfterLast(':').replace(Regex("[^A-Za-z0-9一-龥_-]"), "_")
    val enabled = row.canOpenDetail
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = { onOpenRow(row) }) else Modifier)
            .testTag("wordbook_words_row_$safeId"),
        colors = CardDefaults.cardColors(containerColor = if (enabled) colors.surface else colors.tint.copy(alpha = 0.55f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) colors.outlineSoft else colors.outlineSoft.copy(alpha = 0.65f)),
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
            lemmaStyle = AppType.Title,
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
        style = AppType.Body,
        color = appColors().text2,
    )
}

/**
 * 贴屏幕右边缘的竖排 A–Z 导航轨（m05053 改版，取代 Task 21 的顶部横向 48dp 芯片条）。
 *
 * 现场：用户反馈「字母应该是在屏幕的右边，不在最上面」，且顶部页头 + 筛选条 + 48dp 索引条
 * 一起把上半屏吃掉、词区只剩半屏。改版后本轨与列表**并排**
 * （`Row { Box(weight(1f)) { 列表 } ; 本轨 }`），列表因此拿到整块可用高度。
 *
 * 触控口径不变（F5-03 移交账 AC-R-04）：每枚字母仍是 **48dp 见方**的可点面、相邻不相交。
 * 26 × 48dp = 1248dp 远超屏高，所以竖轨自身可滚——这与旧横向条「26 × 48dp = 1295dp 也要横向滚」
 * 是**同等**的操作代价（旧条在本类 connected 用例里也是先 `performScrollTo()` 再点），不是新退化。
 * 当前字母由 `LaunchedEffect(currentLetter)` 自动滚进视野：否则滚列表时高亮会滚出可视区，
 * 「当前字母更大/异色」这两条判据在真机上就无从看起。
 *
 * 仍然只有一层可点节点、字形单独一枚语义节点（active 标签必须由独立节点承载：同一节点只能留一枚
 * `testTag`，设备实测两枚并挂时 `wordbook_words_rail_active_*` 会从语义树里消失）。
 */
@Composable
private fun WordBookWordsAlphabetRail(
    availableLetters: Set<String>,
    currentLetter: String?,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val accent = appDomain(AppDomain.AiSpeech).base
    val railScroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(currentLetter) {
        val index = ('A'..'Z').indexOfFirst { it.toString() == currentLetter }
        if (index >= 0) {
            val slotPx = with(density) { WordBookWordsRailSlotSize.toPx() }
            // 让当前字母落在可见区靠上的位置（留一个槽的余量），不追求精确居中：
            // 26 个槽的轨道上「居中」在两端会被夹住、反而看不出滚动。
            railScroll.animateScrollTo(((index - 1) * slotPx).toInt().coerceIn(0, railScroll.maxValue))
        }
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(WordBookWordsRailSlotSize)
            .verticalScroll(railScroll)
            .testTag("wordbook_words_rail")
            .semantics { contentDescription = "字母索引 A 到 Z" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ('A'..'Z').forEach { letter ->
            val letterText = letter.toString()
            val available = letterText in availableLetters
            val active = letterText == currentLetter
            // 外层只管「当前字母」这枚标签（同一个节点只能留一枚 `testTag`），
            // active 标签必须由独立语义节点承载——与旧横条同形（槽 Box + 字形 Text 两枚节点）。
            Box(
                modifier = if (active) Modifier.testTag("wordbook_words_rail_active_" + letterText) else Modifier,
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        // 48dp 下限钉在**这枚带 clickable 的节点自身**（AC-R-04 要的是可点区，不是视觉高度）；
                        // 用下限而不是 `size`，动态字体下芯片随字号只增不减。轨内纵向无间隙 ⇒ 命中区天然不相交。
                        .sizeIn(minWidth = WordBookWordsRailSlotSize, minHeight = WordBookWordsRailSlotSize)
                        .clickable(onClick = { onLetter(letterText) })
                        .testTag("wordbook_words_rail_" + letterText),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letterText,
                        // 朗读定语挂在字形这一枚：合并语义后由上面那枚可点芯片承载（TalkBack 念的是按钮），
                        // 而 connected 侧要读字形取值时用 `useUnmergedTree = true` 取本节点。
                        modifier = Modifier.semantics { contentDescription = "导航字母 " + letterText },
                        color = when {
                            active -> accent
                            available -> colors.text1
                            else -> colors.text2.copy(alpha = 0.45f)
                        },
                        // Title 17sp/650、Footnote 13sp/Normal：档自带字重，不 `copy(fontWeight)`；
                        // connected 侧「当前字母更大」与 `activeLetterUsesADifferentColor` 读 `layoutInput.style`。
                        style = if (active) AppType.Title else AppType.Footnote,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

//** 索引轨的槽边尺寸（m05053）：既是竖轨宽度、也是每枚字母的方形触控下限（AC-R-04 的 48dp）。 */
private val WordBookWordsRailSlotSize = 48.dp

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
