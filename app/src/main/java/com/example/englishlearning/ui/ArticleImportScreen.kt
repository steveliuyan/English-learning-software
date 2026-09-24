package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.reading.ImportRejection
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted

/**
 * 粘贴导入页。全程不出网（`ImportArticleUseCase` 无任何网络依赖），
 * 所以这里没有出站确认，只有责任声明与逐项拒绝原因。
 */
@Composable
fun ArticleImportScreen(
    state: ImportUiState,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("article_import_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(
            onClick = onBack,
            colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark),
            modifier = Modifier.testTag("import_back").semantics { contentDescription = "返回上一层" },
        ) { Text("← 返回上一层", fontWeight = FontWeight.Bold) }

        Text("粘贴一篇英文文章", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text("标题与正文都会原样保存到本设备。", style = MaterialTheme.typography.bodyMedium, color = MintTextMuted)

        TextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text("标题") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("import_title"),
            colors = importFieldColors(),
        )
        TextField(
            value = state.body,
            onValueChange = onBodyChange,
            label = { Text("英文正文") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth().testTag("import_body"),
            colors = importFieldColors(),
        )

        Text(
            "导入不会发起任何网络请求；请确保你有权使用粘贴的内容。",
            style = MaterialTheme.typography.bodySmall,
            color = MintTextMuted,
            modifier = Modifier.testTag("import_disclaimer"),
        )

        if (state.rejection != null) {
            Text(
                state.rejection.message(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("import_rejection_reason"),
            )
        }
        if (state.storageFailed) {
            Text(
                "本地存储暂时不可用，文章没有保存。",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("import_storage_failed"),
            )
        }
        if (state.submitting) {
            Text("正在保存…", color = MintTextMuted, modifier = Modifier.testTag("import_submitting"))
        }

        Button(
            onClick = onImport,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("import_submit").semantics { contentDescription = "导入并阅读" },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
        ) { Text("导入并阅读", fontWeight = FontWeight.Bold) }
    }
}

/** 拒绝原因的固定文案：逐项对应校验规则，不含任何运行时拼接的用户输入。 */
private fun ImportRejection.message(): String = when (this) {
    ImportRejection.BlankTitle -> "标题不能为空。"
    ImportRejection.BlankBody -> "正文不能为空。"
    ImportRejection.BodyTooShort -> "正文太短，至少需要 40 个单词。"
    ImportRejection.BodyTooLong -> "正文太长，请缩短内容后重试。"
    ImportRejection.NotEnglish -> "正文需要是英文。"
    ImportRejection.DangerousMarkup -> "正文包含不允许的字符或标记，请检查后重试。"
}

@Composable
private fun importFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MintSurface,
    unfocusedContainerColor = MintSurface,
    focusedIndicatorColor = MintPrimary,
    unfocusedIndicatorColor = MintOutline,
    cursorColor = MintPrimary,
    focusedLabelColor = MintPrimaryDark,
    unfocusedLabelColor = MintTextMuted,
    focusedTextColor = MintPrimaryDark,
    unfocusedTextColor = MintPrimaryDark,
)
