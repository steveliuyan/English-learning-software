package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.example.englishlearning.ui.theme.MintBackground

@Composable
fun VocabularyScreen(
    state: VocabularyUiState,
    onBack: () -> Unit,
    onOpenCard: (com.example.englishlearning.learning.domain.WordCard) -> Unit,
    onRemove: (VocabularyItem) -> Unit,
    masteryState: VocabularyMasteryState = VocabularyMasteryState.Idle,
    onDismissMasteryMessage: () -> Unit = {},
    onStartRelearn: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    var pendingMastery by remember { mutableStateOf<VocabularyItem?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .semantics { contentDescription = "生词本列表，共 ${if (state is VocabularyUiState.Ready) state.items.size else 0} 个词" },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) { Text("返回") }
            Text("生词本", style = MaterialTheme.typography.headlineSmall)
            Text("")
        }
        when (state) {
            VocabularyUiState.Loading -> CircularProgressIndicator(Modifier.padding(top = 32.dp))
            VocabularyUiState.Unavailable -> {
                Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("生词本暂时无法加载")
                    Button(onClick = onRetry) { Text("重试") }
                }
            }
            is VocabularyUiState.Ready -> if (state.items.isEmpty()) {
                Text(
                    "还没有生词，提交“不认识”后会自动加入。",
                    Modifier.padding(top = 32.dp).semantics { contentDescription = "生词本为空，提交不认识后会自动加入" },
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("共 ${state.items.size} 个词", Modifier.padding(vertical = 16.dp))
                    Button(
                        onClick = onStartRelearn,
                        modifier = Modifier.semantics { contentDescription = "开始重新学习，共 ${state.items.size} 个生词" },
                    ) { Text("开始重新学习") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.items, key = { it.entry.wordBookId + it.entry.cardId }) { item ->
                        VocabularyRow(item, onOpenCard) { pendingMastery = item }
                    }
                }
            }
        }
    }
    when (masteryState) {
        VocabularyMasteryState.Success -> {
            AlertDialog(
                onDismissRequest = onDismissMasteryMessage,
                title = { Text("已标记为掌握") },
                text = { Text("该词已从生词本移除，复习记录已保留。") },
                confirmButton = { TextButton(onClick = onDismissMasteryMessage) { Text("知道了") } },
            )
        }
        VocabularyMasteryState.Failure -> {
            AlertDialog(
                onDismissRequest = onDismissMasteryMessage,
                title = { Text("保存失败") },
                text = { Text("复习状态未更新，生词仍保留在生词本中。请重试。") },
                confirmButton = { TextButton(onClick = onDismissMasteryMessage) { Text("知道了") } },
            )
        }
        VocabularyMasteryState.Idle,
        VocabularyMasteryState.Submitting,
        -> Unit
    }
    pendingMastery?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingMastery = null },
            title = { Text("确认标记为已掌握？") },
            text = { Text("标记后，${item.card?.lemma ?: item.entry.cardId} 将从生词本移除，但复习记录会保留。") },
            confirmButton = {
                TextButton(
                    enabled = masteryState != VocabularyMasteryState.Submitting,
                    onClick = {
                        pendingMastery = null
                        onRemove(item)
                    },
                ) {
                    Text(if (masteryState == VocabularyMasteryState.Submitting) "保存中…" else "确认")
                }
            },
            dismissButton = { TextButton(onClick = { pendingMastery = null }) { Text("取消") } },
        )
    }
}

private val vocabularyDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun vocabularySourceLabel(feedback: String): String = when (feedback) {
    "Again" -> "来源：不认识"
    "Manual" -> "来源：手动加入"
    else -> "来源：${feedback}"
}

@Composable
private fun VocabularyRow(
    item: VocabularyItem,
    onOpenCard: (com.example.englishlearning.learning.domain.WordCard) -> Unit,
    onMarkMastered: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text(item.card?.lemma ?: item.entry.cardId, style = MaterialTheme.typography.titleMedium)
        Text(
            text = vocabularySourceLabel(item.entry.lastFeedback),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = "加入时间：${item.entry.addedAt.atZone(ZoneId.systemDefault()).format(vocabularyDateFormatter)}",
            style = MaterialTheme.typography.labelSmall,
        )
        item.card?.let { card ->
            if (card.ipa.isNotBlank()) Text(card.ipa, style = MaterialTheme.typography.bodySmall)
            if (card.senses.isNotEmpty()) {
                card.senses.forEach { sense ->
                    Text(
                        text = "${sense.partOfSpeech} · ${sense.meaningZh}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Text(card.meaningZh, style = MaterialTheme.typography.bodyMedium)
            }
            card.example?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            card.exampleZh?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (card.derived.isNotEmpty()) {
                Text("派生词", style = MaterialTheme.typography.labelMedium)
                Text(
                    card.derived.joinToString("、") { derived ->
                        if (derived.partOfSpeech.isBlank()) derived.lemma else "${derived.lemma}（${derived.partOfSpeech}）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (card.phrases.isNotEmpty()) {
                Text("关联短语", style = MaterialTheme.typography.labelMedium)
                card.phrases.forEach { phrase ->
                    Text("${phrase.text} · ${phrase.meaningZh}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (card.synonyms.isNotEmpty()) {
                Text("近义词", style = MaterialTheme.typography.labelMedium)
                Text(
                    card.synonyms.joinToString("、") { synonym -> synonym.lemma },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenCard(card) }) { Text("查看详情") }
                Button(
                    onClick = onMarkMastered,
                    modifier = Modifier.semantics {
                        contentDescription = "将 ${card.lemma} 标记为已掌握并移出生词本"
                    },
                ) { Text("标记为已掌握") }
            }
        } ?: Text("词卡内容暂不可用")
    }
}
