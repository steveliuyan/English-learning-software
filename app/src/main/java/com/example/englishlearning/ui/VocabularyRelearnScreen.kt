package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.domain.CardFeedback

@Composable
fun VocabularyRelearnScreen(
    state: VocabularyRelearnUiState,
    onSubmit: (CardFeedback) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) { Text("返回生词本") }
            Text("重新学习")
        }
        when (state) {
            VocabularyRelearnUiState.Idle, VocabularyRelearnUiState.Loading -> CircularProgressIndicator()
            VocabularyRelearnUiState.Unavailable -> {
                Text("生词本暂时无法加载")
                Button(onClick = onRetry) { Text("重试") }
            }
            is VocabularyRelearnUiState.Ready -> {
                Text("第 ${state.position} / ${state.total} 个")
                Text(state.card.lemma)
                Text(state.card.ipa)
                Text(state.card.meaningZh)
                state.message?.let { Text(it) }
                if (state.submitting) Text("正在保存复习结果…")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !state.submitting, onClick = { onSubmit(CardFeedback.Unknown) }) { Text("不认识") }
                    Button(enabled = !state.submitting, onClick = { onSubmit(CardFeedback.Fuzzy) }) { Text("模糊") }
                    Button(enabled = !state.submitting, onClick = { onSubmit(CardFeedback.Known) }) { Text("认识") }
                }
            }
            is VocabularyRelearnUiState.Done -> {
                Text("重新学习完成")
                Text("本次完成 ${state.total} 个生词")
                Text("认识的词已移出生词本，其他词会保留供下次复习。")
                Button(onClick = onBack) { Text("返回生词本") }
            }
        }
    }
}
