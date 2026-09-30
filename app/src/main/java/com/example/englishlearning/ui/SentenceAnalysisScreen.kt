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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.AiFailureUiText
import com.example.englishlearning.sentence.SentenceAnalysisNotConfiguredReason
import com.example.englishlearning.sentence.SentenceAnalysisPromptPolicy
import com.example.englishlearning.sentence.SentenceSegment
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

/**
 * 长难句分析功能页（AI 学 → 长难句分析）。
 *
 * 句子输入框只收**数据**：上限 600 字符（提示词模板的数据位），指令部分固定不可注入。
 * 失败文案只来自 [AiFailureUiText] 常量；出站确认在 [state] 为
 * [SentenceAnalysisUiState.NeedsOutboundConfirmation] 时弹出，确认前零字节出网。
 */
@Composable
fun SentenceAnalysisScreen(
    state: SentenceAnalysisUiState,
    onAnalyze: (String) -> Unit,
    onConfirmOutbound: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sentence by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("sentence_analysis_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(
            onClick = onBack,
            colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark),
            modifier = Modifier
                .testTag("sentence_analysis_back")
                .semantics { contentDescription = "返回 AI 学" },
        ) { Text("← 返回 AI 学", fontWeight = FontWeight.Bold) }

        Text("长难句分析", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text("把一句英文拆成主句、从句和短语，逐段给中文解释。", color = MintTextMuted)

        OutlinedTextField(
            value = sentence,
            onValueChange = { sentence = it.take(SentenceAnalysisPromptPolicy.maxSentenceLength) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sentence_analysis_input")
                .semantics { contentDescription = "句子输入" },
            label = { Text("输入一句英文（最多 600 字符）") },
            supportingText = { Text("${sentence.length}/${SentenceAnalysisPromptPolicy.maxSentenceLength}") },
            enabled = state != SentenceAnalysisUiState.Analyzing,
        )
        Button(
            onClick = { onAnalyze(sentence) },
            enabled = state != SentenceAnalysisUiState.Analyzing && sentence.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("sentence_analysis_analyze")
                .semantics { contentDescription = "分析句子" },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MintPrimary,
                contentColor = Color.White,
                disabledContainerColor = MintTint,
                disabledContentColor = MintPrimaryDark,
            ),
        ) { Text("分析句子", fontWeight = FontWeight.Bold) }
        Text(
            "每次分析都会真实调用你配置的 AI 服务并可能产生费用",
            style = MaterialTheme.typography.bodySmall,
            color = MintTextMuted,
            modifier = Modifier.testTag("sentence_analysis_cost_hint"),
        )

        when (state) {
            SentenceAnalysisUiState.Idle -> Unit
            SentenceAnalysisUiState.Analyzing -> Text(
                "正在分析…",
                color = MintTextMuted,
                modifier = Modifier.testTag("sentence_analysis_analyzing"),
            )
            is SentenceAnalysisUiState.Analyzed -> SegmentList(state.segments)
            is SentenceAnalysisUiState.NeedsOutboundConfirmation -> OutboundConfirmationDialog(state.host, onConfirmOutbound)
            is SentenceAnalysisUiState.NotConfigured -> NotConfiguredCard(state.reason)
            is SentenceAnalysisUiState.Failed -> FailureCard(state.failure.uiText)
        }
    }
}

@Composable
private fun SegmentList(segments: List<SentenceSegment>) {
    Column(modifier = Modifier.fillMaxWidth().testTag("sentence_analysis_result"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        segments.forEachIndexed { index, segment ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MintSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
                    .testTag("sentence_analysis_segment_$index"),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        segment.role.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MintPrimary,
                        modifier = Modifier.testTag("sentence_analysis_role_$index"),
                    )
                    Text(
                        segment.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MintPrimaryDark,
                    )
                    Text(segment.explanation, color = MintTextMuted)
                }
            }
        }
    }
}

@Composable
private fun OutboundConfirmationDialog(host: String, onConfirmOutbound: (Boolean) -> Unit) {
    GlassDialog(onDismiss = { onConfirmOutbound(false) }) {
        Text("确认发送请求", style = AppType.Headline)
        Text(
            "你的句子与该服务的密钥将发送给 $host，并可能产生费用。",
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("sentence_analysis_outbound_confirmation_text"),
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
                testTag = "sentence_analysis_decline_outbound",
            )
            PillButton(
                text = "确认发送",
                onClick = { onConfirmOutbound(true) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Primary,
                accent = DomainColors.Learn,
                testTag = "sentence_analysis_confirm_outbound",
            )
        }
    }
}

@Composable
private fun NotConfiguredCard(reason: SentenceAnalysisNotConfiguredReason) {
    val message = when (reason) {
        SentenceAnalysisNotConfiguredReason.NoDefaultProfile ->
            "还没有默认的 AI 服务，先去「设置 · AI」添加一套并设为默认。"
        SentenceAnalysisNotConfiguredReason.DefaultProfileUnavailable ->
            "默认 AI 服务不可用或缺少密钥，去「设置 · AI」检查。"
        SentenceAnalysisNotConfiguredReason.InvalidEndpoint ->
            "AI 服务的地址无效，去「设置 · AI」修正后再试。"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("sentence_analysis_not_configured"),
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
            .testTag("sentence_analysis_failure"),
    ) {
        Text(
            uiText.message,
            color = MintPrimaryDark,
            modifier = Modifier
                .padding(18.dp)
                .semantics { contentDescription = "分析失败原因" },
        )
    }
}
