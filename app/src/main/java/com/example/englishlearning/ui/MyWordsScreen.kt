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
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.ScreenUnavailable
import com.example.englishlearning.ui.components.WordRow
import com.example.englishlearning.ui.components.glass.GlassDialog
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
 * 摘除进度（按 R-22.1 的两席拆分）：本屏是**新增**入口；三条老路由里生词本页那条
 * （`today_plan_open_vocabulary` / `showVocabulary` / `VocabularyScreen` 挂载）已由 **Task 22-A** 摘除，
 * 本屏的生词本段自此是这条通路的全站唯一出口；收藏页与错词页另两条（打卡页页头那两条裸文字链及其宿主路由）
 * 已由 **Task 22-B** 摘除，两枚独立屏随之从生产树下线，本屏的收藏段与错词段是这两族词表的唯一出口。
 *
 * 词行一律调共享 [WordRow]（词行族：本屏是新屏，不在 `SharedComponentUniquenessRatchetTest` 的词行 seed 台账里，
 * 写私有词行即判新增债务）；页头调共享 [AppScreenHeader]；段内降级调共享 [ScreenUnavailable]；
 * 出口动作调共享 [PillButton]（份数棘轮不允许再造「行内专用按钮」）；
 * 「标记为已掌握」的结果反馈调共享 [GlassDialog]（N-1 修复轮 3：本屏消费 `masteryState` 并用宿主
 * `onDismissMasteryMessage` 复位，老页那句「已标记为掌握 / 保存失败」在这里仍是同一句）。
 * 墨色只走 `appColors()`，不写裸 hex、不读 `AppPalette.` / `DomainColors.` 静态值（三张棘轮的新屏口径）。
 */
enum class MyWordsSection { Favorites, Wordbook, Wrong }

// 批 1 Task 5：本屏多了一种用法——词库页 `WordBankScreen` 把三段各当一「面」复用（`visibleSections`
// 参数收窄到一段、`showHeader = false` 关掉页头）。**默认值仍是全集 + 带页头**，所以「一次组合三段齐现」
// 的既有行为判据（connected `MyWordsScreenTest`）、R-20A.2 的落位语义与原挂载点一格不动；
// 收窄只是让同一块代码能被一段一段地借出去，不是把本屏改成 tab 页（本屏依然没有 `when (section)`）。

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
    // N-1（修复轮 3）：`markMastered` 的结果态必须由合并页**消费并给出复位出口**。
    // `VocabularyViewModel._mastery` 只有 `clearMasteryMessage()` 会复位，成功留 `Success`、
    // `StorageUnavailable` 留 `Failure` 且**不 reload**；原先合并页挂点两枚都没传 ⇒ 失败静默（用户点了确认、
    // 行还在、没有任何解释），而残留的 `Success` 会让随后打开的老生词页弹出一枚**没有上下文**的结果框。
    // 两枚都带默认值：本屏仍是 state-in，既有夹具与用例不因签名变化而碎。
    masteryState: VocabularyMasteryState = VocabularyMasteryState.Idle,
    onDismissMasteryMessage: () -> Unit = {},
    // m-2（修复轮 1）：重试带段标，只刷用户点的那一册。原先三段共用一个无参 onRetry，
    // 任一段的「重试」都会把另外两册打回 Loading（还连带触发 I-1 的旧缺陷）。
    onRetry: (MyWordsSection) -> Unit = {},
    // 批 1 Task 5：词库页把本屏的三段各当一「面」复用（`WordBankScreen` 的 new / wrong / favorite pane）。
    // 两枚形参都追加在末尾且带默认值（全集 / 显示页头），所以既有的整屏挂载点、connected 夹具与
    // 「三段同屏」的行为判据一格不动——本屏自己仍是「一页三段」，只是**也能**被裁成一段来用。
    // `visibleSections` 只裁剪渲染，不改变 `section` 的语义（仍是初始落位/聚焦，R-20A.2）。
    visibleSections: List<MyWordsSection> = MyWordsSection.entries,
    showHeader: Boolean = true,
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
    //
    // Task 5 补充：被裁成一段时（词库页的 pane）不落位——那一段本身就是视口起点，滚到自己的顶边
    // 只会把本屏的 24dp 上边距吃掉。落位语义只对「多段同屏」成立，所以这里按可见段数早退，
    // 而不是把 `section` 换成一个假的「无落位」值（那会让 R-20A.2 的语义在两种用法间分裂）。
    LaunchedEffect(section) {
        if (visibleSections.size < 2) return@LaunchedEffect
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
        if (showHeader) {
            AppScreenHeader(
                title = "我的词",
                subtitle = "收藏 · 生词本 · 错词，三处攒下来的词都在这一页",
                onBack = onBack,
            )
        }
        if (MyWordsSection.Favorites in visibleSections) {
            MyWordsSectionCard(
                title = "收藏",
                sectionTag = "my_words_section_favorites",
                count = (favoritesState as? FavoritesUiState.Ready)?.words?.size,
                // 原型 `sMeWords`（`assets/app.js:1294`）每段一句说明，只搬**本仓成立**的那一句：
                // 收藏不参与计划生成（`StoredPlanCardSource` 的新词 / 到期集合只由词书内容与学习事件派生，
                // 收藏表不在其中），所以「不会被排进复习队列，只做手动回看」是事实。
                // 生词本那句（明天的词单里排到前面）与易错那句（按答错次数排序、答对两次后移出）在本仓不成立，
                // 整句不写——检查见 `MyWordsScreenContractTest.favorites_caption_and_the_closing_notice_are_carried_verbatim`。
                caption = "收藏的词不会被排进复习队列，只做手动回看。",
                captionTag = "my_words_favorites_caption",
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
        }
        if (MyWordsSection.Wordbook in visibleSections) {
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
        }
        if (MyWordsSection.Wrong in visibleSections) {
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
        // 原型 `sMeWords`（`assets/app.js:1298`）的页尾提示条：词义详情确有「加入生词本」与「取消收藏」
        // 两枚能力（`CardDetailScreen` 的生词本开关药片 + 页头收藏键读成「取消收藏 <lemma>」），
        // 所以逐字搬。外壳只用共享 InlineNotice（提示条族不许自搓），墨色与几何归组件。
        // 它落在三段之后 = 页尾最后一段；每格单独成面时也是该格的页尾，与原型按 tab 渲染的形状一致。
        InlineNotice(
            text = "词义详情里可以加入生词本或取消收藏。",
            kind = InlineNoticeKind.Info,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
    // I-4（修复轮 1）：「标记为已掌握」的二次确认——复用共享 ConfirmDestructiveDialog，不另起私有
    // AlertDialog（对话框份数棘轮把私有确认框记新增债务）。措辞要素照老生词页搬齐：移出行 + 保留面；
    // 该操作可经复习里再提交「不认识」找回，所以 irreversible = false——谎称不可撤销会让人放弃本可挽回的操作。
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
    // N-1（修复轮 3）：结果对话框——`Idle` / `Submitting` 什么都不画（与老页 `VocabularyScreen.kt:98-100`
    // 那两条 `-> Unit` 分支同口径：提交中弹「已标记为掌握」是比缺反馈更坏的错态），`Success` / `Failure`
    // 各出与老页 `VocabularyScreen.kt:81-97` **逐字同文**的结果框（本屏 KDoc 自述「用户在任何一处读到的
    // 解释必须还是同一句」）。外壳只用现成的共享 [GlassDialog]，确认按钮只用 [PillButton]，不新建组件。
    // 点击「知道了」与点 scrim 走同一枚复位出口 onDismissMasteryMessage（宿主接现成 clearMasteryMessage，
    // 与老页 `:853` 同源）；它**不是**重做动作，所以这里绝不回调 onMarkMastered。
    // 两枚对话框互不吞：确认闸由 `pendingMastery` 管显隐、结果框由 `masteryState` 管显隐，各读各的状态，
    // 「确认前不得回调」那条既有断言不受本框影响（connected `wordbook_offers_relearn_and_mastered_behind_confirmation`）。
    val masteryResultCopy: Pair<String, String>? = when (masteryState) {
        VocabularyMasteryState.Success -> "已标记为掌握" to "该词已从生词本移除，复习记录已保留。"
        VocabularyMasteryState.Failure -> "保存失败" to "复习状态未更新，生词仍保留在生词本中。请重试。"
        VocabularyMasteryState.Idle,
        VocabularyMasteryState.Submitting,
        -> null
    }
    masteryResultCopy?.let { (masteryTitle, masteryBody) ->
        GlassDialog(
            onDismiss = onDismissMasteryMessage,
            modifier = Modifier.testTag("my_words_mastery_result_dialog"),
        ) {
            Text(
                text = masteryTitle,
                style = AppType.Headline,
                color = colors.text1,
            )
            Text(
                text = masteryBody,
                modifier = Modifier.padding(top = 8.dp),
                style = AppType.Body,
                color = colors.text1,
            )
            PillButton(
                text = "知道了",
                onClick = onDismissMasteryMessage,
                style = PillStyle.Secondary,
                testTag = "my_words_mastery_result_dismiss",
                contentDescription = "$masteryTitle，知道了",
                modifier = Modifier.padding(top = 16.dp),
            )
        }
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
    caption: String? = null,
    captionTag: String? = null,
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
            // 段内说明：原型 `.caption` 同档（12sp / text3）。放在标题行与内容之间 = 段的顶部，
            // 「先说明这一列按什么来、再列词」；挪到内容之后就成脚注。
            if (caption != null) {
                Text(
                    text = caption,
                    style = AppType.Caption,
                    color = colors.text3,
                    modifier = Modifier.then(
                        if (captionTag == null) Modifier else Modifier.testTag(captionTag),
                    ),
                )
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
 * 空态沿用三页现有的措辞（逐字照抄旧收藏页 / 旧生词页 / 旧错词页三页的原文，其中收藏页与错词页两枚屏
 * 已于 Task 22-B 下线，措辞仍按当页在册的原文逐字保留），
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
