package com.example.englishlearning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.SearchVocabularyResult
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted

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
    val keyboard = LocalSoftwareKeyboardController.current
    val submit: () -> Unit = {
        keyboard?.hide()
        onSubmit()
    }
    BackHandler(onBack = onBack)
    Scaffold(containerColor = MintBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 两个入口（主页查词、阅读页点未知词）都会落到这里，所以只能说「返回上一层」：
                // 从阅读页进来时回的是文章，写「返回首页」就是在指错方向。
                Text(
                    "← 返回上一层",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MintPrimaryDark,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .testTag("global_search_back")
                        .semantics { contentDescription = "返回上一层" },
                )
                Text(
                    "查词",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MintPrimaryDark,
                )
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().testTag("global_search_input"),
                singleLine = true,
                placeholder = { Text("输入英文单词或短语") },
                leadingIcon = { Text("⌕", color = MintPrimary, style = MaterialTheme.typography.titleLarge) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.semantics { contentDescription = "清除搜索" },
                        ) { Text("×", style = MaterialTheme.typography.titleLarge) }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MintSurface,
                    unfocusedContainerColor = MintSurface,
                    focusedIndicatorColor = MintPrimary,
                    unfocusedIndicatorColor = MintOutline,
                    cursorColor = MintPrimary,
                ),
            )
            Button(
                onClick = submit,
                enabled = query.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("global_search_submit"),
                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
            ) { Text("全量搜索") }

            when (state) {
                GlobalVocabularySearchUiState.Idle -> GlobalSearchHint()
                GlobalVocabularySearchUiState.Loading -> GlobalSearchProgress("正在查询本地词库…")
                GlobalVocabularySearchUiState.ClearingHistory -> GlobalSearchProgress("正在清空搜索历史…")
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
                            "找到 ${state.results.size} 个结果",
                            color = MintTextMuted,
                            modifier = Modifier.testTag("global_search_results_count"),
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f).testTag("global_search_results"),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
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
    Text(
        "输入单词后开始全量查词；空输入会显示最近搜索。",
        color = MintTextMuted,
        modifier = Modifier.testTag("global_search_hint"),
    )
}

@Composable
private fun GlobalSearchProgress(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MintPrimary)
        Spacer(Modifier.height(12.dp))
        Text(text, color = MintTextMuted)
    }
}

@Composable
private fun GlobalSearchFailure(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("搜索暂时不可用，请重试", color = MintPrimaryDark)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag("global_search_retry"),
            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary),
        ) { Text("重试") }
    }
}

@Composable
private fun GlobalSearchEmpty() {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("未找到匹配词卡", color = MintPrimaryDark, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "当前只覆盖已安装词本，不是完整词典。",
            color = MintTextMuted,
            modifier = Modifier.testTag("global_search_not_full_dictionary"),
        )
    }
}

/**
 * 历史列表是 [Column] 的直接子节点，需要 `Modifier.weight(1f)` 把剩余高度让给 `LazyColumn`，
 * 所以这里必须声明成 `ColumnScope` 的扩展——否则拿不到 Column 的作用域，列表会按内容无限撑高。
 */
@Composable
private fun ColumnScope.GlobalSearchHistory(
    history: List<GlobalVocabularySearchHistoryItem>,
    onOpenHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (history.isEmpty()) {
        Text(
            "还没有搜索记录",
            color = MintTextMuted,
            modifier = Modifier.testTag("global_search_history_empty"),
        )
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "最近搜索",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MintPrimaryDark,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onClearHistory,
            modifier = Modifier.testTag("global_search_clear_history"),
        ) { Text("清空搜索历史") }
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f).testTag("global_search_history"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = history, key = { it.history.normalizedQuery }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MintSurface, RoundedCornerShape(14.dp))
                    .border(1.dp, MintOutline, RoundedCornerShape(14.dp))
                    .clickable { onOpenHistory(item.history.displayQuery) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .semantics { contentDescription = "重新搜索 ${item.history.displayQuery}" }
                    .testTag("global_search_history_${item.history.normalizedQuery}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.history.displayQuery, style = MaterialTheme.typography.titleMedium, color = MintPrimaryDark)
                    item.searchCountForDisplay?.let { count ->
                        Text(
                            "搜索 $count 次",
                            style = MaterialTheme.typography.labelMedium,
                            color = MintTextMuted,
                            modifier = Modifier.testTag("global_search_history_count_${item.history.normalizedQuery}"),
                        )
                    }
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MintPrimary)
            }
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
    Text(
        "这个词卡暂时打不开，词书可能已被移除；可重新搜索。",
        color = MintPrimaryDark,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("global_search_open_failed"),
    )
}

@Composable
private fun GlobalSearchResultRow(item: GlobalVocabularySearchResultItem, onClick: () -> Unit) {
    val result = item.result
    val key = result.wordBookId + ":" + result.cardId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MintSurface, RoundedCornerShape(14.dp))
            .border(1.dp, MintOutline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = "${result.lemma}，${result.meaningZh}，来源词书 ${result.wordBookName}" }
            .testTag("global_search_result_$key"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(result.lemma, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            if (result.ipa.isNotBlank()) {
                Text(result.ipa, style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
            }
            Spacer(Modifier.height(4.dp))
            Text(result.meaningZh, style = MaterialTheme.typography.bodyMedium, color = MintPrimaryDark)
            Spacer(Modifier.height(4.dp))
            Text(
                "来源：${result.wordBookName}",
                style = MaterialTheme.typography.labelMedium,
                color = MintTextMuted,
                modifier = Modifier.testTag("global_search_result_book_$key"),
            )
            item.searchCountForDisplay?.let { count ->
                Text(
                    "搜索 $count 次",
                    style = MaterialTheme.typography.labelMedium,
                    color = MintTextMuted,
                    modifier = Modifier.testTag("global_search_result_count_$key"),
                )
            }
        }
        Text("›", style = MaterialTheme.typography.headlineMedium, color = MintPrimary)
    }
}
