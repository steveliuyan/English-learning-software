package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.AiFailureUiText
import com.example.englishlearning.ui.components.glass.GlassDialog
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint
import com.example.englishlearning.wordqa.WordAiNote
import com.example.englishlearning.wordqa.WordQaKind
import com.example.englishlearning.wordqa.WordQaNotConfiguredReason

/**
 * 词 AI 问答屏（二级页面，从词卡详情进入）。
 *
 * 呈现层约束与文章生成一致：失败文案只来自 [AiFailureUiText] 常量，屏幕不拼接任何
 * 运行时数据；问题只限 [WordQaKind] 三种固定问法，没有自由输入框——「拒绝任意
 * 提示词」的约束在界面上同样是防线。出站确认在 [state] 为
 * [WordAiQaUiState.NeedsOutboundConfirmation] 时弹出，确认前零字节出网。
 */
@Composable
fun WordAiQaScreen(
    lemma: String,
    state: WordAiQaUiState,
    onAsk: (WordQaKind) -> Unit,
    onConfirmOutbound: (Boolean) -> Unit,
    onSaveNote: () -> Unit,
    onSavePersonalNote: (String) -> Unit = {},
    savedNotes: List<WordAiNote> = emptyList(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("word_qa_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("word_qa_back")
                .semantics { contentDescription = "返回上一层" },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
        ) { Text("← 返回上一层", fontWeight = FontWeight.Bold) }

        Text("AI 问词", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text("单词：$lemma", color = MintTextMuted, modifier = Modifier.testTag("word_qa_lemma"))

        var personalNote by remember(lemma) { mutableStateOf("") }
        OutlinedTextField(
            value = personalNote,
            onValueChange = { personalNote = it },
            label = { Text("我的笔记") },
            placeholder = { Text("记录记忆方法、易错点或例句") },
            modifier = Modifier.fillMaxWidth().testTag("word_qa_personal_note"),
            minLines = 3,
        )
        Button(
            onClick = {
                onSavePersonalNote(personalNote)
                personalNote = ""
            },
            enabled = personalNote.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag("word_qa_save_personal_note"),
            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
        ) { Text("保存我的笔记") }

        if (savedNotes.isNotEmpty()) {
            Text("已保存笔记", style = AppType.Title, color = MintPrimaryDark)
            savedNotes.forEach { note ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MintSurface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().testTag("word_qa_saved_note_${note.noteId}"),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(note.kind.label, style = AppType.Footnote, color = MintTextMuted)
                        Text(note.answer, color = MintPrimaryDark)
                    }
                }
            }
        }

        WordQaKind.entries.forEach { kind ->
            QuestionChip(kind, enabled = state != WordAiQaUiState.Asking, onAsk)
        }

        when (state) {
            WordAiQaUiState.Idle -> Text(
                "选一个问法，AI 会围绕这个单词作答。",
                color = MintTextMuted,
                modifier = Modifier.testTag("word_qa_idle_hint"),
            )
            WordAiQaUiState.Asking -> Text(
                "正在询问…",
                color = MintTextMuted,
                modifier = Modifier.testTag("word_qa_asking"),
            )
            is WordAiQaUiState.Answered -> AnsweredContent(state.kind, state.answer, state.saved, state.saveFailed, onSaveNote)
            is WordAiQaUiState.NeedsOutboundConfirmation -> OutboundConfirmationDialog(state.host, onConfirmOutbound)
            is WordAiQaUiState.NotConfigured -> NotConfiguredCard(state.reason)
            is WordAiQaUiState.Failed -> FailureCard(state.failure.uiText)
        }
    }
}

@Composable
private fun QuestionChip(kind: WordQaKind, enabled: Boolean, onAsk: (WordQaKind) -> Unit) {
    Button(
        onClick = { onAsk(kind) },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("word_qa_chip_${kind.name}")
            .semantics { contentDescription = "${kind.label}（问 AI）" },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MintPrimary,
            contentColor = Color.White,
            disabledContainerColor = MintTint,
            disabledContentColor = MintPrimaryDark,
        ),
    ) { Text(kind.label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun AnsweredContent(
    kind: WordQaKind,
    answer: String,
    saved: Boolean,
    saveFailed: Boolean,
    onSaveNote: () -> Unit,
) {
    Text(
        "问法：${kind.label}",
        color = MintTextMuted,
        modifier = Modifier.testTag("word_qa_answer_kind"),
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("word_qa_answer"),
    ) {
        Text(
            answer,
            color = MintPrimaryDark,
            modifier = Modifier
                .padding(18.dp)
                .semantics { contentDescription = "AI 回答" },
        )
    }
    Button(
        onClick = onSaveNote,
        enabled = !saved,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("word_qa_save_note")
            .semantics { contentDescription = "保存为笔记" },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MintPrimary,
            contentColor = Color.White,
            disabledContainerColor = MintTint,
            disabledContentColor = MintPrimaryDark,
        ),
    ) { Text(if (saved) "已保存到笔记" else "保存为笔记", fontWeight = FontWeight.Bold) }
    if (saveFailed) {
        Text(
            "笔记没有保存成功，可以再试一次。",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("word_qa_save_failed"),
        )
    }
}

@Composable
private fun OutboundConfirmationDialog(host: String, onConfirmOutbound: (Boolean) -> Unit) {
    GlassDialog(onDismiss = { onConfirmOutbound(false) }) {
        Text("确认发送请求", style = AppType.Headline)
        Text(
            "你的问题与该服务的密钥将发送给 $host，并可能产生费用。",
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("word_qa_outbound_confirmation_text"),
            style = AppType.Body,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PillButton(
                text = "取消",
                onClick = { onConfirmOutbound(false) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Secondary,
                accent = DomainColors.Learn,
                testTag = "word_qa_decline_outbound",
            )
            PillButton(
                text = "确认发送",
                onClick = { onConfirmOutbound(true) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Primary,
                accent = DomainColors.Learn,
                testTag = "word_qa_confirm_outbound",
            )
        }
    }
}

@Composable
private fun NotConfiguredCard(reason: WordQaNotConfiguredReason) {
    val message = when (reason) {
        WordQaNotConfiguredReason.NoDefaultProfile ->
            "还没有默认的 AI 服务，先去「设置 · AI」添加一套并设为默认。"
        WordQaNotConfiguredReason.DefaultProfileUnavailable ->
            "默认 AI 服务不可用或缺少密钥，去「设置 · AI」检查。"
        WordQaNotConfiguredReason.InvalidEndpoint ->
            "AI 服务的地址无效，去「设置 · AI」修正后再试。"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("word_qa_not_configured"),
    ) {
        Text(
            message,
            color = MintPrimaryDark,
            modifier = Modifier
                .padding(18.dp)
                .semantics { contentDescription = "AI 未配置提示" },
        )
    }
}

@Composable
private fun FailureCard(uiText: AiFailureUiText) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("word_qa_failure"),
    ) {
        Text(
            uiText.message,
            color = MintPrimaryDark,
            modifier = Modifier
                .padding(18.dp)
                .semantics { contentDescription = "问答失败原因" },
        )
    }
}
