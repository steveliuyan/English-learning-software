package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.AiFailureUiText
import com.example.englishlearning.sentence.SentenceAnalysisNotConfiguredReason
import com.example.englishlearning.sentence.SentenceAnalysisPromptPolicy
import com.example.englishlearning.sentence.SentenceSegment
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.NotConfiguredCard
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.GlassDialog
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * 长难句分析功能页（AI 助学 → 长难句分析）。
 *
 * 句子输入框只收**数据**：上限 600 字符（提示词模板的数据位），指令部分固定不可注入。
 * 失败文案只来自 [AiFailureUiText] 常量；出站确认在 [state] 为
 * [SentenceAnalysisUiState.NeedsOutboundConfirmation] 时弹出，确认前零字节出网。
 *
 * ## 版式（批 4 Task 5）
 *
 * 页头换共享 [AppScreenHeader]：标题进页头，说明句进 `subtitle`，旧「← 返回 AI 学」药片与自绘
 * `headlineSmall` 标题一起下线。逐段结果卡从 M3 `Card(` 换 [SoftCard]（零强卡、零描边）；结果区
 * 末尾补两枚**文字药片**操作：复制译文走平台剪贴板（不新增依赖、不落盘），朗读原文**只上报**
 * [onSpeak]，语音能力留在宿主（与拍照翻译同形，代次校验也归宿主）。常驻成本句保留为费用披露的
 * 唯一一处，出站确认句只讲「发给谁、发什么」。
 */
@Composable
fun SentenceAnalysisScreen(
    state: SentenceAnalysisUiState,
    onAnalyze: (String) -> Unit,
    onConfirmOutbound: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
    speakMessage: String?,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val accent = appDomain(AppDomain.AiSpeech).deep
    var sentence by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("sentence_analysis_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = "长难句分析",
            subtitle = "把一句英文拆成主句、从句和短语，逐段给中文解释。",
            onBack = onBack,
        )

        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                PillButton(
                    text = "分析句子",
                    onClick = { onAnalyze(sentence) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    style = PillStyle.Primary,
                    domain = AppDomain.AiSpeech,
                    enabled = state != SentenceAnalysisUiState.Analyzing && sentence.isNotBlank(),
                    testTag = "sentence_analysis_analyze",
                    contentDescription = "分析句子",
                )
                Text(
                    text = "每次分析都会真实调用你配置的 AI 服务并可能产生费用",
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.testTag("sentence_analysis_cost_hint"),
                )
            }
        }

        when (state) {
            SentenceAnalysisUiState.Idle -> Unit
            SentenceAnalysisUiState.Analyzing -> Text(
                text = "正在分析…",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("sentence_analysis_analyzing"),
            )
            is SentenceAnalysisUiState.Analyzed -> SegmentList(
                segments = state.segments,
                accent = accent,
                speakMessage = speakMessage,
                onSpeak = onSpeak,
            )
            is SentenceAnalysisUiState.NeedsOutboundConfirmation -> OutboundConfirmationDialog(state.host, onConfirmOutbound)
            is SentenceAnalysisUiState.NotConfigured -> NotConfiguredCard(
                featureLabel = "长难句分析",
                onConfigure = onOpenAiSettings,
                modifier = Modifier.testTag("sentence_analysis_not_configured"),
                detail = state.reason.guidance(),
            )
            is SentenceAnalysisUiState.Failed -> FailureCard(state.failure.uiText)
        }
    }
}

/**
 * 逐段结果 + 结果区两枚操作。
 *
 * 「复制译文」的落点是各段中文解释按行拼接（有译文才有可复制的东西，不把原始英文混进去）；
 * 「朗读原文」按空格把各段英文接回整句再上报——分段是切分视角，朗读对象仍是那句原话。
 * 两块都可空：`segments` 为空时不渲染任何操作，避免给用户一枚复制空串的按钮。
 */
@Composable
private fun SegmentList(
    segments: List<SentenceSegment>,
    accent: Color,
    speakMessage: String?,
    onSpeak: (String) -> Unit,
) {
    val colors = appColors()
    val clipboard = LocalClipboardManager.current
    val original = segments.joinToString(separator = " ") { it.text }
    val translated = segments.joinToString(separator = "\n") { it.explanation }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sentence_analysis_result"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            SoftCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sentence_analysis_segment_$index"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = segment.role.label,
                        style = AppType.Label,
                        color = accent,
                        modifier = Modifier.testTag("sentence_analysis_role_$index"),
                    )
                    Text(
                        text = segment.text,
                        style = AppType.Body,
                        color = colors.text1,
                    )
                    Text(
                        text = segment.explanation,
                        style = AppType.Body,
                        color = colors.text2,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton(
                text = "复制译文",
                onClick = { clipboard.setText(AnnotatedString(translated)) },
                style = PillStyle.Text,
                testTag = "sentence_analysis_copy",
            )
            PillButton(
                text = "朗读原文",
                onClick = { onSpeak(original) },
                style = PillStyle.Text,
                testTag = "sentence_analysis_speak",
            )
        }
        speakMessage?.let { message ->
            Text(
                text = message,
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("sentence_analysis_speak_feedback"),
            )
        }
    }
}

@Composable
private fun OutboundConfirmationDialog(host: String, onConfirmOutbound: (Boolean) -> Unit) {
    GlassDialog(onDismiss = { onConfirmOutbound(false) }) {
        Text("确认发送请求", style = AppType.Headline, color = appColors().text1)
        Text(
            text = "你的句子与该服务的密钥将发送给 $host。",
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("sentence_analysis_outbound_confirmation_text"),
            style = AppType.Body,
            color = appColors().text1,
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
                domain = AppDomain.AiSpeech,
                testTag = "sentence_analysis_decline_outbound",
            )
            PillButton(
                text = "确认发送",
                onClick = { onConfirmOutbound(true) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Primary,
                domain = AppDomain.AiSpeech,
                testTag = "sentence_analysis_confirm_outbound",
            )
        }
    }
}

/**
 * 各拒绝原因对应的**补路指引**（卡的第二行）；第一行「一句现状」由共享 `NotConfiguredCard` 统一措辞。
 *
 * Task 26 Step 3 删掉了本文件同名的私有 `NotConfiguredCard`：旧卡只有一段文字、**没有出口**
 * （spec §3 点名的死屏）；出口现在由共享件提供并接到 `onOpenAiSettings`。
 */
private fun SentenceAnalysisNotConfiguredReason.guidance(): String = when (this) {
    SentenceAnalysisNotConfiguredReason.NoDefaultProfile ->
        "去「设置 · AI」添加一套并设为默认。"
    SentenceAnalysisNotConfiguredReason.DefaultProfileUnavailable ->
        "去「设置 · AI」检查默认服务或补上密钥。"
    SentenceAnalysisNotConfiguredReason.InvalidEndpoint ->
        "去「设置 · AI」修正服务地址后再试。"
}

@Composable
private fun FailureCard(uiText: AiFailureUiText) {
    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sentence_analysis_failure"),
    ) {
        Text(
            text = uiText.message,
            style = AppType.Body,
            color = appColors().text1,
            modifier = Modifier.semantics { contentDescription = "分析失败原因" },
        )
    }
}
