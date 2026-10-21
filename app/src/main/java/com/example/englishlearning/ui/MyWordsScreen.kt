package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.FavoriteWord
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.ConfirmDestructiveDialog
import com.example.englishlearning.ui.components.ScreenUnavailable
import com.example.englishlearning.ui.components.WordRow
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import java.time.ZoneId
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * 词汇域三入口的第三枚（裁决① `docs/decisions/2026-10-17-ui-redesign-information-architecture.md` ①）：
 * **收藏 · 生词本 · 错词** 合成一页三段，不再让用户为「我攒下来的词」跑三个页面。
 *
 * 三段是**同屏并列的三个容器**，不是 tab 页：`section` 形参只决定初始落位（滚到哪一段），
 * **永远不决定渲染哪一段**（R-20A.2）。这条由两处双向钉住：
 *  - `MyWordsScreenContractTest.section_param_lands_instead_of_filtering` 钉源码形状（本文件不得出现 `when (section)`）；
 *  - connected `MyWordsScreenTest.three_sections_render_in_one_composition` 钉行为（一次 `setContent`、三段各自的数据都在）。
 *
 * 三份状态来自三枚 ViewModel（`FavoritesViewModel` / `VocabularyViewModel` / `WrongWordsViewModel`），
 * 但**本屏是 state-in**：不取 ViewModel、不调 `hiltViewModel()`。同宿主并存这件事在生产侧早已成立
 * （26 枚 `hiltViewModel()` 在 `MainActivity.kt` 同一装配点注入 `AppScreen`，`AppScreen` 同一组合里
 * 已同时收集 `VocabularyUiState` 与 `FavoritesUiState`），所以本屏不需要新的宿主形态，也不需要宿主拆分（R-20A.1）。
 *
 * 只建不摘：本屏是**新增**入口，收藏页 / 错词页 / 生词本页三条老路由仍在册（摘除归 Task 22）。
 *
 * 词行一律调共享 [WordRow]（词行族：本屏是新屏，不在 `SharedComponentUniquenessRatchetTest` 的词行 seed 台账里，
 * 写私有词行即判新增债务）；页头调共享 [AppScreenHeader]；段内降级调共享 [ScreenUnavailable]；
 * 出口动作调共享 [PillButton]（份数棘轮不允许再造「行内专用按钮」）。
 * 墨色只走 `appColors()`，不写裸 hex、不读 `AppPalette.` / `DomainColors.` 静态值（三张棘轮的新屏口径）。
 */
enum class MyWordsSection { Favorites, Wordbook, Wrong }

// m-5 / m-6（修复轮 2）：日期档、时分档、来源档、释义档四枚 helper 已从本屏的私有副本
// 收敛到 `ui/WordListFormat.kt`（与 `VocabularyScreen` 共用同一份，逐字同文），
// 本屏不再自拼时间戳或来源文案。

@Composable
fun MyWordsScreen(
    favoritesState: FavoritesUiState,
    vocabularyState: VocabularyUiState,
    wrongWordsState: WrongWordsUiState,
    modifier: Modifier = Modifier,
    section: MyWordsSection = MyWordsSection.Favorites,
    zoneId: ZoneId = ZoneId.systemDefault(),
    onBack: () -> Unit = {},
    onOpenFavoriteWord: (FavoriteWord) -> Unit = {},
    onUnfavorite: (FavoriteWord) -> Unit = {},
    onOpenVocabularyCard: (WordCard) -> Unit = {},
    onOpenWrongWord: (WordCard) -> Unit = {},
    // I-4（修复轮 1，报告 §1「摘除前必须补齐的能力」）：老生词页独有的两枚动作在合并页补齐——
    // 段头整册「开始重新学习」与逐行「标记为已掌握」。宿主接线用现成通路
    // （`showVocabularyRelearn` / `vocabularyViewModel?.markMastered`），本屏不新建任何数据通路。
    onStartRelearn: () -> Unit = {},
    onMarkMastered: (VocabularyItem) -> Unit = {},
    // m-2（修复轮 1）：重试带段标，只刷用户点的那一册。原先三段共用一个无参 onRetry，
    // 任一段的「重试」都会把另外两册打回 Loading（还连带触发 I-1 的旧缺陷）。
    onRetry: (MyWordsSection) -> Unit = {},
) {
    val colors = appColors()
    val scrollState = rememberScrollState()
    // I-4：逐行「标记为已掌握」先落进本屏的待确认态，共享确认框确认后才回交宿主——
    // 「点了就移行、没有反悔闸」是这条 finding 点名要补的动作形状
    // （connected `MyWordsScreenTest.wordbook_offers_relearn_and_mastered_behind_confirmation`）。
    var pendingMastery by remember { mutableStateOf<VocabularyItem?>(null) }
    // 段容器顶边的偏移由各自的 onGloballyPositioned 写进来；键是段，值是本 Column 内容坐标系里的 y（px）。
    val sectionTops = remember { mutableStateMapOf<MyWordsSection, Int>() }
    // I-1（修复轮 1）：键**只有 `section`**。三份 UiState 都是会发射的数据源（收藏 reload、生词本后台 load…），
    // 把它们放进键集等于「每次数据发射都重做一次落位」——用户读到一半会被拽回段首。
    // `section` 的语义是初始落位/聚焦（R-20A.2），落位每段组合只发生一次；判红形状见
    // connected `MyWordsScreenTest.state_emit_after_landing_does_not_yank_the_viewport_back`。
    LaunchedEffect(section) {
        // 等第一个非空偏移再滚：段容器的顶边只有在这次 layout 之后才量得到，而 LaunchedEffect 可能跑在
        // 测量之前，那时 scrollTo 一个不存在的偏移就是空滚（connected 的落位用例正是抓这条）。
        snapshotFlow { sectionTops[section] }.filterNotNull().first().let { top ->
            scrollState.scrollTo(top)
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("my_words_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = "我的词",
            subtitle = "收藏 · 生词本 · 错词，三处攒下来的词都在这一页",
            onBack = onBack,
        )
        MyWordsSectionCard(
            title = "收藏",
            sectionTag = "my_words_section_favorites",
            count = (favoritesState as? FavoritesUiState.Ready)?.words?.size,
            onTopChanged = { sectionTops[MyWordsSection.Favorites] = it },
        ) {
            when (favoritesState) {
                FavoritesUiState.Loading -> SectionLoading("my_words_favorites_loading", "正在读取收藏…")
                FavoritesUiState.Unavailable -> SectionUnavailable(
                    tag = "my_words_favorites_unavailable",
                    reason = "收藏暂时无法读取，请稍后重试",
                    onRetry = { onRetry(MyWordsSection.Favorites) },
                )
                is FavoritesUiState.Ready -> {
                    favoritesState.notice?.let {
                        Text(it, style = AppType.Footnote, color = colors.text2)
                    }
                    if (favoritesState.words.isEmpty()) {
                        SectionEmpty(
                            tag = "my_words_favorites_empty",
                            text = "还没有收藏。在词卡详情页点星标即可收藏。",
                        )
                    } else {
                        favoritesState.words.forEachIndexed { index, favorite ->
                            WordRow(
                                lemma = favorite.lemma,
                                gloss = null,
                                meta = "收藏于 ${wordListDateLabel(favorite.addedAt, zoneId)}",
                                modifier = Modifier
                                    .testTag("my_words_favorite_row_$index")
                                    .semantics { contentDescription = "收藏 ${favorite.lemma}" },
                                onClick = { onOpenFavoriteWord(favorite) },
                                actions = {
                                    PillButton(
                                        text = "取消收藏",
                                        onClick = { onUnfavorite(favorite) },
                                        style = PillStyle.Secondary,
                                        testTag = "my_words_favorite_unfavorite_$index",
                                        contentDescription = "取消收藏 ${favorite.lemma}",
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        MyWordsSectionCard(
            title = "生词本",
            sectionTag = "my_words_section_wordbook",
            count = (vocabularyState as? VocabularyUiState.Ready)?.items?.size,
            onTopChanged = { sectionTops[MyWordsSection.Wordbook] = it },
        ) {
            when (vocabularyState) {
                VocabularyUiState.Loading -> SectionLoading("my_words_wordbook_loading", "正在读取生词本…")
                VocabularyUiState.Unavailable -> SectionUnavailable(
                    tag = "my_words_wordbook_unavailable",
                    reason = "生词本暂时无法读取，请稍后重试",
                    onRetry = { onRetry(MyWordsSection.Wordbook) },
                )
                is VocabularyUiState.Ready -> {
                    if (vocabularyState.items.isEmpty()) {
                        SectionEmpty(
                            tag = "my_words_wordbook_empty",
                            text = "还没有生词，提交“不认识”后会自动加入。",
                            // m-7：逐字搬旧生词页那条专属朗读描述（缺它即比旧页少一层）。
                            readingDescription = "生词本为空，提交不认识后会自动加入",
                        )
                    } else {
                        // I-4：整册「开始重新学习」——老生词页 Ready 非空时列表上方的同名动作，
                        // 合并页同规格补回（空册时这一段根本没有词，按钮不出现，由 connected 反向钉）。
                        PillButton(
                            text = "开始重新学习",
                            onClick = onStartRelearn,
                            testTag = "my_words_wordbook_start_relearn",
                            contentDescription = "开始重新学习，共 ${vocabularyState.items.size} 个生词",
                        )
                        vocabularyState.items.forEachIndexed { index, item ->
                            val card = item.card
                            if (card == null) {
                                WordRow(
                                    lemma = item.entry.cardId,
                                    gloss = "词卡内容暂不可用",
                                    status = wordListSourceLabel(item.entry.lastFeedback),
                                    meta = "加入于 ${wordListDateTimeLabel(item.entry.addedAt, zoneId)}",
                                    modifier = Modifier
                                        .testTag("my_words_wordbook_row_$index")
                                        .semantics { contentDescription = "生词本条目 ${item.entry.cardId}" },
                                )
                            } else {
                                WordRow(
                                    lemma = card.lemma,
                                    gloss = wordListMeaning(card),
                                    status = wordListSourceLabel(item.entry.lastFeedback),
                                    meta = "加入于 ${wordListDateTimeLabel(item.entry.addedAt, zoneId)}",
                                    modifier = Modifier
                                        .testTag("my_words_wordbook_row_$index")
                                        .semantics { contentDescription = "生词本 ${card.lemma}" },
                                    onClick = { onOpenVocabularyCard(card) },
                                    actions = {
                                        // I-4：逐行「标记为已掌握」。只落待确认态，
                                        // 真回交在页尾共享确认框的确认回调里（无 card 的行没有可提交的对象，不给这枚出口）。
                                        PillButton(
                                            text = "标记为已掌握",
                                            onClick = { pendingMastery = item },
                                            style = PillStyle.Secondary,
                                            testTag = "my_words_wordbook_mastered_$index",
                                            contentDescription = "将 ${card.lemma} 标记为已掌握并移出生词本",
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        MyWordsSectionCard(
            title = "错词",
            sectionTag = "my_words_section_wrong",
            count = (wrongWordsState as? WrongWordsUiState.Ready)?.cards?.size,
            onTopChanged = { sectionTops[MyWordsSection.Wrong] = it },
        ) {
            when (wrongWordsState) {
                WrongWordsUiState.Loading -> SectionLoading("my_words_wrong_loading", "正在统计错词…")
                WrongWordsUiState.Unavailable -> SectionUnavailable(
                    tag = "my_words_wrong_unavailable",
                    reason = "错词暂时无法读取，请稍后重试",
                    onRetry = { onRetry(MyWordsSection.Wrong) },
                )
                is WrongWordsUiState.Ready -> {
                    if (wrongWordsState.cards.isEmpty()) {
                        SectionEmpty(
                            tag = "my_words_wrong_empty",
                            text = "当前没有待纠正的错词。复习中标记「不认识」的词会出现在这里，重新答对后自动消失。",
                        )
                    } else {
                        wrongWordsState.cards.forEachIndexed { index, card ->
                            WordRow(
                                lemma = card.lemma,
                                gloss = wordListMeaning(card),
                                modifier = Modifier
                                    .testTag("my_words_wrong_row_$index")
                                    .semantics { contentDescription = "错词 ${card.lemma}" },
                                onClick = { onOpenWrongWord(card) },
                            )
                        }
                    }
                }
            }
        }
    }
    // I-4（修复轮 1）：「标记为已掌握」的二次确认——复用共享 ConfirmDestructiveDialog，不另起私有
    // AlertDialog（对话框份数棘轮把私有确认框记新增债务）。措辞要素照老生词页搬齐：移出行 + 保留面；
    // 该操作可经复习里再提交「不认识」找回，所以 irreversible = false——谎称不可撤销会让人放弃本可挽回的操作。
    // 已知差异（登记于报告 §1）：老页在成功后另有结果对话框（Success/Failure 两态），合并页以
    // 「markMastered 内部 reload ⇒ 行消失 + 段计数变化」为成功反馈；失败时行不消失、可原地重试。
    pendingMastery?.let { item ->
        ConfirmDestructiveDialog(
            state = true,
            title = "确认标记为已掌握？",
            cascadeLines = listOf("${item.card?.lemma ?: item.entry.cardId} 将从生词本移除"),
            keepLines = listOf("复习记录会保留"),
            irreversible = false,
            confirmLabel = "确认",
            onConfirm = {
                pendingMastery = null
                onMarkMastered(item)
            },
            onDismiss = { pendingMastery = null },
        )
    }
}

/**
 * 一段的容器：与「今日计划」入口组同一规格的卡（`surface` 底 + 1dp `outlineSoft` 描边 + 18dp 圆角），
 * 组标题一行是「段名 + 条数」，段名是这条落位与朗读的唯一锚点。
 *
 * [onTopChanged] 把本段顶边在本 Column 内容坐标系里的 y 交回调用方，[MyWordsScreen] 用它做 `section` 落位；
 * 偏移必须来自实测而不是「按段序号估高」——三段的行数是运行时数据，估出来的位置一定错。
 */
@Composable
private fun MyWordsSectionCard(
    title: String,
    sectionTag: String,
    count: Int?,
    onTopChanged: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = appColors()
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineSoft, RoundedCornerShape(18.dp))
            .testTag(sectionTag)
            .onGloballyPositioned { onTopChanged(it.positionInParent().y.toInt()) },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = AppType.Title, color = colors.text1, modifier = Modifier.weight(1f))
                if (count != null) {
                    Text(
                        text = "$count 个",
                        style = AppType.Footnote,
                        color = colors.text2,
                        modifier = Modifier.semantics { contentDescription = "$title 共 $count 个" },
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun SectionLoading(tag: String, text: String) {
    val colors = appColors()
    Text(
        text = text,
        style = AppType.Footnote,
        color = colors.text2,
        modifier = Modifier.testTag(tag),
    )
}

/**
 * 空态沿用三页现有的措辞（逐字照抄 `FavoritesScreen` / `VocabularyScreen` / `WrongWordsScreen`），
 * 合并的是入口、不是说法：用户在任何一处读到的解释必须还是同一句。
 *
 * m-7（修复轮 2）：旧生词页的空态除文案外还带一条专属朗读描述（`VocabularyScreen.kt:64`），
 * 合并页缺这一层 ⇒ TalkBack 档比旧页少一层，所以本组件收 [readingDescription]，生词本段把旧页那句
 * 逐字搬过来。另两段的旧页从来没有专属描述（读的是文案本体），本屏也不给它们新造一句——
 * `contentDescription` 一旦存在就**取代**文案被朗读，新造短句反而读得比旧页少。
 * 文案本体与描述层的判据：connected `MyWordsScreenTest.empty_sections_show_their_own_honest_copy`、
 * JVM `MyWordsScreenContractTest.empty_states_keep_the_copy_and_the_reading_layer`。
 */
@Composable
private fun SectionEmpty(tag: String, text: String, readingDescription: String? = null) {
    val colors = appColors()
    Text(
        text = text,
        style = AppType.Footnote,
        color = colors.text2,
        modifier = Modifier
            .padding(bottom = 8.dp)
            .testTag(tag)
            .then(
                if (readingDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = readingDescription }
                },
            ),
    )
}

@Composable
private fun SectionUnavailable(tag: String, reason: String, onRetry: () -> Unit) {
    ScreenUnavailable(
        title = "读不出来",
        reason = reason,
        actionLabel = "重试",
        onAction = onRetry,
        modifier = Modifier.testTag(tag),
    )
}
