package com.example.englishlearning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.SearchVocabularyResult
import com.example.englishlearning.ui.components.AppGlyph
import com.example.englishlearning.ui.components.AppGlyphLayer
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.hairlineTop
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * 全量词汇搜索：只回答一个问题——这个词在哪些已安装词本里、它的意思是什么。
 *
 * 两个入口共用这一页：**主页查词**（传空串，落在「最近搜索」）与**阅读页点未知词**
 * （传被点中的词，直接出结果）。刻意**没有「浏览词书」**：那属于词书管理页面的职责，
 * 放在查词页里既和「查一个词」这个动作无关，也会让用户以为查词等于翻词书。
 *
 * 候选来自本地词条索引（`VocabularySearchIndexRepository`），不再逐册解析词书包，
 * 所以打开与查询都不再随词书数量线性变慢。2026-10-05 起阅读页入口也切成这里，
 * 原先那条「逐册解析整包」的路径已随旧查词页一并移除。
 *
 * 无状态：状态与回调全部由调用方注入，便于在仪器测试里直接驱动每一个分支。
 *
 * ## 版式（批 3 Task 5）
 *
 * 三条带：共享页头 / 输入区（弱卡 + 恒「搜索」的主 CTA）/ 结果或历史。
 * 四处信息架构改动：
 *  1. **页头归共享组件**——原来自写「← 返回上一层」可点文字 + `headlineSmall` 标题，
 *     可见文案与可及名各写一遍。返回位改由 [AppScreenHeader] 的图标位承担：本屏两个入口
 *     里「阅读页点未知词」回的是文章，任何写死的目的地文案都会在某个入口上说错。
 *  2. **按钮文案恒「搜索」**——原先按输入在「查看最近搜索」/「全量搜索」之间切换，同一枚按钮
 *     两个身份。能力不减：空输入仍可点（点下去落到最近搜索），只是不再让按钮自己解释这件事。
 *  3. **历史行去药片**——原来每行一枚 `MintSurface + 1dp MintOutline` 的盒子，历史一多就是
 *     一行一个盒子。换成共享裸行 [BareRow]：同组关系由发丝线 + 52dp 缩进表达，次数收进尾部值
 *     `N 次`（行标题已经是那个查询词，再写「搜索 N 次」就是把动词讲两遍）。
 *  4. **取色只读组合期令牌**——七枚静态薄荷桥在深浅两套里是同一枚浅墨，压深套底色即失效。
 *     主 CTA 走主操作档 `strongSurface`/`onStrong`（同粘贴导入、文章阅读的吸底 CTA），
 *     域色只出现在输入框的聚焦指示与光标（`AppDomain.Library`）：这屏查的是词库里的词。
 */
@Composable
fun GlobalVocabularySearchScreen(
    state: GlobalVocabularySearchUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenHistory: (String) -> Unit,
    onSelect: (SearchVocabularyResult) -> Unit,
    onBack: () -> Unit,
) {
    val colors = appColors()
    val keyboard = LocalSoftwareKeyboardController.current
    val submit: () -> Unit = {
        keyboard?.hide()
        onSubmit()
    }
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
            .testTag("global_search_screen"),
    ) {
        AppScreenHeader(
            title = "查词",
            onBack = onBack,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        // 内容列：输入区与主 CTA 固定在上，状态区吃掉剩余高度（历史 / 结果两条列表靠 `weight(1f)` 自己滚）。
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 输入区落在弱卡里：这屏没有「一屏一枚」的强卡需求，强卡额度留给真正要立起来的那一处。
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("global_search_input"),
                    singleLine = true,
                    placeholder = { Text("输入英文单词或短语", style = AppType.Body, color = colors.text3) },
                    leadingIcon = { AppGlyph(AppIcon.Search, colors.text2, 20.dp) },
                    // 清除是次要动作：用带可见文案的 `TextButton`（「清除」）而不是只有一枚符号的
                    // 图标按钮——屏内不自造可及名，靠可见文案播报比靠一串语义描更稳。
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            TextButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.heightIn(min = 40.dp),
                            ) {
                                Text("清除", style = AppType.Label, color = colors.text2)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedIndicatorColor = appDomain(AppDomain.Library).deep,
                        unfocusedIndicatorColor = colors.outlineSoft,
                        cursorColor = appDomain(AppDomain.Library).deep,
                        focusedTextColor = colors.text1,
                        unfocusedTextColor = colors.text1,
                    ),
                )
            }
            // 本屏唯一的主 CTA：文案一字不变地恒为「搜索」。
            //
            // 不设 `enabled`：空输入时点下去落回最近搜索，这是页面明说的能力（「空输入会显示最近搜索」），
            // 按钮一禁用，用户清空输入后就回不到最近搜索，只能退出页面从主页重进。
            // 具体落哪一支由宿主的 `onSubmit` 判定，按钮不重复判定（重复判定就是两个真值）。
            Button(
                onClick = submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("global_search_submit"),
                shape = AppShape.Button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.strongSurface,
                    contentColor = colors.onStrong,
                ),
            ) {
                Text("搜索", style = AppType.Label, color = colors.onStrong)
            }
            when (state) {
                GlobalVocabularySearchUiState.Idle -> GlobalSearchHint()
                GlobalVocabularySearchUiState.Loading -> GlobalSearchProgress("正在查询本地词库…")
                GlobalVocabularySearchUiState.ClearingHistory -> GlobalSearchProgress("正在清空历史…")
                GlobalVocabularySearchUiState.Failure -> GlobalSearchFailure(onRetry = onRetry)
                GlobalVocabularySearchUiState.Empty -> GlobalSearchEmpty()
                is GlobalVocabularySearchUiState.Ready -> {
                    if (state.results.isEmpty()) {
                        GlobalSearchHistory(
                            history = state.history,
                            onOpenHistory = onOpenHistory,
                            onClearHistory = onClearHistory,
                        )
                    } else {
                        if (state.openFailed) GlobalSearchOpenFailed()
                        Text(
                            text = "找到 ${state.results.size} 个结果",
                            style = AppType.Footnote,
                            color = colors.text2,
                            modifier = Modifier.testTag("global_search_results_count"),
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("global_search_results"),
                        ) {
                            items(
                                items = state.results,
                                key = { item -> item.result.wordBookId + ":" + item.result.cardId },
                            ) { item ->
                                GlobalSearchResultRow(item = item, onClick = { onSelect(item.result) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchHint() {
    val colors = appColors()
    Text(
        text = "输入英文单词后点「搜索」；输入为空时，搜索会落到最近搜索。",
        style = AppType.Body,
        color = colors.text2,
        modifier = Modifier.testTag("global_search_hint"),
    )
}

@Composable
private fun GlobalSearchProgress(text: String) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = colors.text2)
        Text(text, style = AppType.Footnote, color = colors.text2)
    }
}

@Composable
private fun GlobalSearchFailure(onRetry: () -> Unit) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("搜索暂时不可用，请重试", style = AppType.Body, color = colors.text1)
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag("global_search_retry"),
            shape = AppShape.Button,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.strongSurface,
                contentColor = colors.onStrong,
            ),
        ) {
            Text("重试", style = AppType.Label, color = colors.onStrong)
        }
    }
}

@Composable
private fun GlobalSearchEmpty() {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("未找到匹配词卡", style = AppType.Title, color = colors.text1)
        Text(
            text = "当前只覆盖已安装词本，不是完整词典。",
            style = AppType.Footnote,
            color = colors.text2,
            modifier = Modifier.testTag("global_search_not_full_dictionary"),
        )
    }
}

/**
 * 历史列表是 [Column] 的直接子节点，需要 `Modifier.weight(1f)` 把剩余高度让给 `LazyColumn`，
 * 所以这里必须声明成 `ColumnScope` 的扩展——否则拿不到 Column 的作用域，列表会按内容无限撑高。
 *
 * 行是共享裸行 [BareRow]：同组关系由发丝线（`topDivider`）与 52dp 缩进表达，不再一行一枚药片。
 * 次数进尾部值 `N 次`；`trailingTag` 把它单独标出来，因为 connected 用例要按
 * `global_search_history_count_<query>` 断言「关闭次数显示时这枚节点不存在」——
 * 尾部值的文案由组件渲染，标签只能由调用方给。
 */
@Composable
private fun ColumnScope.GlobalSearchHistory(
    history: List<GlobalVocabularySearchHistoryItem>,
    onOpenHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val colors = appColors()
    if (history.isEmpty()) {
        Text(
            text = "还没有搜索记录",
            style = AppType.Body,
            color = colors.text2,
            modifier = Modifier.testTag("global_search_history_empty"),
        )
        return
    }
    // 节标题与「清空」同一行：标题是「最近搜索」，「清空」的宾语由这行标题给出，不必再写一遍。
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "最近搜索",
            style = AppType.Title,
            color = colors.text1,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onClearHistory,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("global_search_clear_history"),
        ) {
            Text("清空", style = AppType.Label, color = colors.text2)
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("global_search_history"),
    ) {
        itemsIndexed(
            items = history,
            key = { _, item -> item.history.normalizedQuery },
        ) { index, item ->
            BareRow(
                title = item.history.displayQuery,
                modifier = Modifier.testTag("global_search_history_${item.history.normalizedQuery}"),
                trailing = item.searchCountForDisplay?.let { count -> "$count 次" },
                trailingTag = item.searchCountForDisplay?.let { count ->
                    "global_search_history_count_${item.history.normalizedQuery}"
                },
                topDivider = index > 0,
                onClick = { onOpenHistory(item.history.displayQuery) },
            )
        }
    }
}

/**
 * 点开的那条结果读不到词卡（索引残留了已删除词书，或词书包损坏）。
 *
 * 只提示、不把整页切成失败态：搜索结果本身是好的，不该因为一条打不开就丢掉整份列表。
 */
@Composable
private fun GlobalSearchOpenFailed() {
    val colors = appColors()
    Text(
        text = "这个词卡暂时打不开，词书可能已被移除；可重新搜索。",
        style = AppType.Footnote,
        color = colors.error,
        modifier = Modifier.testTag("global_search_open_failed"),
    )
}

/**
 * 结果行：**五枚信息一个不少**（lemma / IPA / 释义 / 来源词书 / 次数）。
 *
 * R-20C.1 已裁决这枚行**不迁 `WordRow`**：`WordRow` 只有四槽，强迁必丢 IPA 或截断词书名；
 * 它同时是词行族 seed 的落窗判据（`SharedComponentUniquenessRatchetTest` 口径「本文件恰 1 处」），
 * 所以函数名、函数形状与信息构成都不动，只换取色源与去掉药片壳。版式改动仅两处：
 * 盒子换成「发丝线 + 行尾 chevron」，行内墨色走六档排印（`AppType`）与令牌（`appColors()`）。
 */
@Composable
private fun GlobalSearchResultRow(item: GlobalVocabularySearchResultItem, onClick: () -> Unit) {
    val colors = appColors()
    val result = item.result
    val key = result.wordBookId + ":" + result.cardId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("global_search_result_$key")
            .then(hairlineTop(colors.outlineSoft))
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(result.lemma, style = AppType.Title, color = colors.text1)
                if (result.ipa.isNotBlank()) {
                    Text(result.ipa, style = AppType.Footnote, color = colors.text2)
                }
            }
            Text(result.meaningZh, style = AppType.Body, color = colors.text1)
            Text(
                text = "来源：${result.wordBookName}",
                style = AppType.Footnote,
                color = colors.text3,
                modifier = Modifier.testTag("global_search_result_book_$key"),
            )
            item.searchCountForDisplay?.let { count ->
                Text(
                    text = "搜索 $count 次",
                    style = AppType.Footnote,
                    color = colors.text3,
                    modifier = Modifier.testTag("global_search_result_count_$key"),
                )
            }
        }
        AppGlyph(AppIcon.ChevronRight, colors.text3, 16.dp, layer = AppGlyphLayer.StrokeOnly)
    }
}
