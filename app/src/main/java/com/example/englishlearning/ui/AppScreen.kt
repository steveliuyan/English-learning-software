package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun AppScreen(viewModel: AppViewModel) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.padding(24.dp)) {
        when (val state = viewModel.uiState.collectAsState().value) {
            AppUiState.Loading -> Text("正在加载")
            AppUiState.NeedsProfile -> {
                TextField(name, { name = it }, label = { Text("姓名") }, modifier = Modifier.semantics { contentDescription = "姓名输入" })
                Button({ viewModel.createProfile(name) }, modifier = Modifier.semantics { contentDescription = "创建资料" }) { Text("创建") }
            }
            is AppUiState.Ready -> Text("欢迎，${state.profile.displayName}")
            is AppUiState.Error -> Text("无法创建资料，请检查姓名")
        }
    }
}
