package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@androidx.compose.runtime.Composable
fun AppScreen(
    viewModel: AppViewModel,
    learningSetupViewModel: LearningSetupViewModel,
) {
    var name by remember { mutableStateOf("") }
    when (val state = viewModel.uiState.collectAsState().value) {
        AppUiState.Loading -> Text("正在加载")
        AppUiState.NeedsProfile -> {
            Column(Modifier.padding(24.dp)) {
                TextField(
                    name,
                    { name = it },
                    label = { Text("姓名") },
                    modifier = Modifier.semantics { contentDescription = "姓名输入" },
                )
                Button(
                    { viewModel.createProfile(name) },
                    modifier = Modifier.semantics { contentDescription = "创建资料" },
                ) { Text("创建") }
            }
        }
        is AppUiState.Ready -> LearningSetupScreen(state.profile.id, learningSetupViewModel)
        is AppUiState.Error -> Text("无法创建资料，请检查姓名")
    }
}

@androidx.compose.runtime.Composable
private fun LearningSetupScreen(profileId: String, viewModel: LearningSetupViewModel) {
    val state by viewModel.uiState.collectAsState()
    androidx.compose.runtime.LaunchedEffect(profileId) { viewModel.load(profileId) }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置学习词书", style = MaterialTheme.typography.headlineSmall)
        Text("应用内学习分组，不是官方考试大纲", style = MaterialTheme.typography.bodyMedium)
        state.wordBooks.forEach { book ->
            val selected = book.id == state.selectedWordBookId
            Card(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.selectWordBook(book.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp).semantics {
                        contentDescription = "选择词书 ${book.displayName}"
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(book.id == state.selectedWordBookId, { viewModel.selectWordBook(book.id) })
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(book.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(book.level)
                    }
                }
            }
        }
        Text("每日新增 ${state.dailyNewTarget} 词")
        Slider(
            value = state.dailyNewTarget.toFloat(),
            onValueChange = { viewModel.updateDailyNewTarget(it.toInt()) },
            valueRange = 1f..50f,
            steps = 48,
            modifier = Modifier.semantics { contentDescription = "每日新词目标" },
        )
        Button(
            onClick = { viewModel.save(profileId) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "保存学习设置" },
        ) { Text("保存学习设置") }
        state.savedWordBookName?.let { Text("当前词书：$it；每日新增${state.dailyNewTarget}词") }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
