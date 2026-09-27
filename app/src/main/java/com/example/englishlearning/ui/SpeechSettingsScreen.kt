package com.example.englishlearning.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.components.glass.pressableScale
import com.example.englishlearning.ui.theme.AppMotion
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.MintBackground

@Composable
fun SpeechSettingsScreen(
    state: SpeechSettingsUiState,
    onSelect: (PronunciationEngine, String?) -> Unit,
    onOpenAiProfiles: () -> Unit,
    onAddMiMoPreset: () -> Unit,
    onPreview: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MintBackground).verticalScroll(rememberScrollState()).padding(20.dp).testTag("speech_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 返回设置", style = AppType.Body) }
        Text("语音合成", style = AppType.Headline)
        Text("选择朗读服务。密钥仅保存在系统安全区。", style = AppType.Footnote)

        // 试听卡：用当前保存的语音偏好真实朗读一段文字，配置对不对当场听见。
        PreviewCard(state, onPreview)

        val statuses = state.engineStatuses()
        EngineRow(
            name = "系统 TTS",
            engine = PronunciationEngine.SystemTts,
            selected = state.selectedEngine,
            boundProfileId = null,
            detail = null,
            onSelect = onSelect,
        )
        // OpenAI 引擎行已移除；engineStatuses() 仍计算 openAi 字段供设置页摘要兼容，
        // 但本页 UI 不再消费。
        EngineRow(
            name = "小米 MiMo",
            engine = PronunciationEngine.MiMo,
            selected = state.selectedEngine,
            boundProfileId = state.miMoProfileId,
            detail = detailFor(state, PronunciationEngine.MiMo, statuses.miMo),
            onSelect = onSelect,
        )

        // 候选配置收进折叠二级菜单：引擎行已经显示绑定的配置名，列表平时不占屏，
        // 选中即收起——不再出现「上下两处都显示配置」的重复。
        if (state.selectedEngine != PronunciationEngine.SystemTts) {
            var candidatesExpanded by rememberSaveable { mutableStateOf(false) }
            val engineCandidates = state.candidatesForSelectedEngine()
            PillButton(
                text = if (candidatesExpanded) "收起语音配置" else "选择语音配置 ▾",
                onClick = { candidatesExpanded = !candidatesExpanded },
                style = PillStyle.Secondary,
                testTag = "speech_toggle_candidates",
            )
            Column(modifier = Modifier.animateContentSize(tween(AppMotion.Normal, easing = AppMotion.Easing))) {
                if (candidatesExpanded) {
                    val boundProfileId = when (state.selectedEngine) {
                        PronunciationEngine.OpenAi -> state.openAiProfileId
                        PronunciationEngine.MiMo -> state.miMoProfileId
                        PronunciationEngine.SystemTts -> null
                    }
                    engineCandidates.forEach { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(state.selectedEngine, candidate.profileId)
                                    candidatesExpanded = false
                                }
                                .padding(vertical = 10.dp)
                                .testTag("speech_profile_${candidate.profileId}")
                                .semantics { contentDescription = "选择 ${candidate.displayName}" },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(candidate.displayName, modifier = Modifier.weight(1f), style = AppType.Title)
                            if (candidate.profileId == boundProfileId) {
                                Text(
                                    "当前绑定",
                                    modifier = Modifier.testTag("speech_bound_profile_${candidate.profileId}"),
                                    style = AppType.Label,
                                    color = DomainColors.AiSpeech.deep,
                                )
                            }
                            Text(if (candidate.status == SpeechProfileStatus.Available) "可用" else "缺少密钥", style = AppType.Label)
                        }
                    }
                    if (state.selectedEngine == PronunciationEngine.MiMo && engineCandidates.isEmpty()) {
                        Text(
                            "还没有小米 MiMo 配置。可以一键创建预设：服务地址与模型名已按官方默认值填好，只需再填密钥。",
                            style = AppType.Footnote,
                        )
                        PillButton(
                            text = "添加小米 MiMo 预设",
                            onClick = onAddMiMoPreset,
                            style = PillStyle.Primary,
                            testTag = "speech_add_mimo_preset",
                        )
                    }
                    PillButton(
                        text = "AI 服务与密钥",
                        onClick = onOpenAiProfiles,
                        style = PillStyle.Text,
                        testTag = "speech_open_ai_profiles",
                    )
                }
            }
        }

        // 待接入厂商与本地方向收进折叠区：这是「以后可能有」的信息，不该常驻占用主列表。
        var moreProvidersExpanded by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { moreProvidersExpanded = !moreProvidersExpanded }, modifier = Modifier.testTag("speech_more_providers")) {
            Text(if (moreProvidersExpanded) "收起更多语音厂商" else "更多语音厂商 · 待接入", style = AppType.Label)
        }
        if (moreProvidersExpanded) {
            Text("本地 ZipVoice-Distill · 未下载", style = AppType.Footnote, modifier = Modifier.testTag("speech_zipvoice"))
            listOf("Azure" to "azure", "火山引擎" to "volcengine", "腾讯云" to "tencent", "阿里云百炼" to "bailian", "MiniMax" to "minimax").forEach { (name, tag) ->
                Text("$name · 待接入", style = AppType.Footnote, modifier = Modifier.testTag("speech_pending_$tag"))
            }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = AppType.Footnote, modifier = Modifier.testTag("speech_message")) }
    }
}

/** 引擎行右侧的详情：绑定了具体配置就显示「配置名 · 密钥状态」，否则回落到摘要状态。 */
private fun detailFor(state: SpeechSettingsUiState, engine: PronunciationEngine, fallback: String): String? {
    val bound = state.boundCandidateFor(engine) ?: return fallback.removePrefix("当前供应商 · ")
    val keyState = if (bound.status == SpeechProfileStatus.Available) "可用" else "缺少密钥"
    return "${bound.displayName} · $keyState"
}

@Composable
private fun PreviewCard(state: SpeechSettingsUiState, onPreview: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("Hello! 你好，世界！") }
    Surface(
        shape = AppShape.Card,
        color = AppPalette.GlassFill,
        border = BorderStroke(1.dp, AppPalette.GlassHighlight),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("试听", style = AppType.Title)
            Text("用当前选中的朗读服务播放这段文字。", style = AppType.Footnote)
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("speech_preview_input"),
                minLines = 2,
                placeholder = { Text("输入要试听的内容") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = DomainColors.AiSpeech.base,
                    unfocusedIndicatorColor = DomainColors.AiSpeech.base.copy(alpha = 0.3f),
                ),
            )
            PillButton(
                text = "试听",
                onClick = { onPreview(text) },
                modifier = Modifier.fillMaxWidth(),
                style = PillStyle.Primary,
                enabled = text.isNotBlank(),
                testTag = "speech_preview_button",
                contentDescription = "试听",
            )
            state.previewMessage?.let { Text(it, style = AppType.Footnote, modifier = Modifier.testTag("speech_preview_message")) }
        }
    }
}

@Composable
private fun EngineRow(
    name: String,
    engine: PronunciationEngine,
    selected: PronunciationEngine,
    boundProfileId: String?,
    detail: String?,
    onSelect: (PronunciationEngine, String?) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(AppMotion.Normal, easing = AppMotion.Easing))
            .pressableScale(interaction)
            .clickable(interactionSource = interaction, indication = null) { onSelect(engine, boundProfileId) }
            .padding(vertical = 10.dp)
            .testTag("speech_engine_${engine.name.lowercase()}")
            .semantics { contentDescription = "选择 $name" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f), style = AppType.Title)
        if (engine == selected) {
            Text(
                "当前供应商",
                modifier = Modifier.testTag("speech_current_${engine.name.lowercase()}"),
                style = AppType.Label,
                color = DomainColors.AiSpeech.deep,
            )
        }
        if (detail != null) {
            Text(detail, modifier = Modifier.padding(start = 8.dp).testTag("speech_engine_status_${engine.name.lowercase()}"), style = AppType.Footnote)
        }
    }
}
