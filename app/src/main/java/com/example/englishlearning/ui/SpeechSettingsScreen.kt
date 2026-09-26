package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintTextMuted

@Composable
fun SpeechSettingsScreen(
    state: SpeechSettingsUiState,
    onSelect: (PronunciationEngine, String?) -> Unit,
    onOpenAiProfiles: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MintBackground).verticalScroll(rememberScrollState()).padding(20.dp).testTag("speech_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 返回设置") }
        Text("语音合成", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text("选择朗读服务。密钥仅保存在系统安全区。", color = MintTextMuted, style = MaterialTheme.typography.bodySmall)
        EngineRow("系统 TTS", PronunciationEngine.SystemTts, state.selectedEngine, null, onSelect)
        EngineRow("OpenAI TTS", PronunciationEngine.OpenAi, state.selectedEngine, state.openAiProfileId, onSelect)
        EngineRow("小米 MiMo", PronunciationEngine.MiMo, state.selectedEngine, state.miMoProfileId, onSelect)
        if (state.selectedEngine != PronunciationEngine.SystemTts) {
            Text("选择语音配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            state.candidates.forEach { candidate ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(state.selectedEngine, candidate.profileId) }
                        .padding(vertical = 10.dp).testTag("speech_profile_${candidate.profileId}")
                        .semantics { contentDescription = "选择 ${candidate.displayName}" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(candidate.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                    Text(if (candidate.status == SpeechProfileStatus.Available) "可用" else "缺少密钥", color = MintTextMuted)
                }
            }
            TextButton(onClick = onOpenAiProfiles, modifier = Modifier.testTag("speech_open_ai_profiles")) { Text("AI 服务与密钥") }
        }
        Text("本地 ZipVoice-Distill · 未下载", color = MintTextMuted, modifier = Modifier.testTag("speech_zipvoice"))
        listOf("Azure" to "azure", "火山引擎" to "volcengine", "腾讯云" to "tencent", "阿里云百炼" to "bailian", "MiniMax" to "minimax").forEach { (name, tag) ->
            Text("$name · 待接入", color = MintTextMuted, modifier = Modifier.testTag("speech_pending_$tag"))
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("speech_message")) }
    }
}

@Composable
private fun EngineRow(
    name: String,
    engine: PronunciationEngine,
    selected: PronunciationEngine,
    profileId: String?,
    onSelect: (PronunciationEngine, String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(engine, profileId) }.padding(vertical = 10.dp).testTag("speech_engine_${engine.name.lowercase()}")
            .semantics { contentDescription = "选择 $name" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        if (engine == selected) Text("当前供应商", modifier = Modifier.testTag("speech_current_${engine.name.lowercase()}"), color = MintPrimaryDark)
    }
}
