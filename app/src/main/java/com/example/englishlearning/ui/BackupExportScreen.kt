package com.example.englishlearning.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.englishlearning.ui.theme.AppPalette

/**
 * F4-03 备份导出确认页（接线级，不做视觉打磨）。
 *
 * 两段式：先「生成备份包」得到诚实的范围/数量/大小估算，再经 SAF「另存为」落盘；取消不写文件。
 * 声明固定为「不可用于认证恢复的 Salvage 包 / 不含 API Key」。可空 ViewModel 是生产组装点
 * 防回归约定（见 AppScreenProductionWiringTest）；为 null 时页面退化为只读提示、不崩溃。
 */
@Composable
fun BackupExportScreen(
    onBack: () -> Unit,
    viewModel: BackupExportViewModel? = null,
) {
    if (viewModel == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).testTag("backup_export_screen")) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("backup_export_back")) { Text("返回") }
            Text("备份导出", fontSize = 22.sp)
            Text("备份导出功能暂不可用")
        }
        return
    }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { if (!state.packageReady && !state.busy) viewModel.prepare() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        viewModel.onDocumentPicked(uri)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("backup_export_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("backup_export_back")) { Text("返回") }
        Text("备份导出（数据留档）", fontSize = 22.sp)

        Text(
            text = state.disclosure,
            fontSize = 13.sp,
            modifier = Modifier.testTag("backup_export_disclosure"),
        )

        Text("包含范围：", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.testTag("backup_export_scope_header"))
        BackupScopeRows.forEach { row ->
            Text("· ${row.title} —— ${row.note}", fontSize = 12.sp, modifier = Modifier.testTag("backup_export_scope_${row.tag}"))
        }

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("包含 AI 音频文件")
                Text("校验失败的音频会中止导出，不会静默跳过", fontSize = 12.sp, modifier = Modifier.testTag("backup_export_audio_note"))
            }
            Switch(
                checked = state.includeAudio,
                onCheckedChange = { viewModel.setIncludeAudio(it) },
                modifier = Modifier.testTag("backup_export_audio_switch"),
            )
        }

        if (state.busy) {
            Text("正在生成备份包…", modifier = Modifier.testTag("backup_export_busy"))
        } else if (state.packageReady) {
            Text(state.summary, modifier = Modifier.testTag("backup_export_summary"))
            state.fileName?.let { Text("文件名：$it", fontSize = 12.sp, modifier = Modifier.testTag("backup_export_filename")) }
            Button(
                onClick = { launcher.launch(state.fileName ?: "backup.elpbak") },
                modifier = Modifier.fillMaxWidth().testTag("backup_export_save"),
            ) { Text("选择保存位置并导出") }
        } else {
            Button(
                onClick = { viewModel.prepare() },
                modifier = Modifier.fillMaxWidth().testTag("backup_export_prepare"),
            ) { Text("生成备份包") }
        }

        state.error?.let { Text(it, color = AppPalette.ErrorText, modifier = Modifier.testTag("backup_export_error")) }
        state.message?.let { Text(it, color = AppPalette.SuccessText, modifier = Modifier.testTag("backup_export_message")) }
    }
}

private data class BackupScopeRow(val title: String, val note: String, val tag: String)

private val BackupScopeRows = listOf(
    BackupScopeRow("词书与学习配置", "本机词书选择、每日目标与学习设置", "config"),
    BackupScopeRow("今日计划与任务", "当日计划快照及其任务", "plans"),
    BackupScopeRow("学习事件与复习状态", "三档反馈历史与 FSRS 复习状态", "events"),
    BackupScopeRow("每日文章与阅读记录", "已解锁文章、完成状态与阅读偏好", "reading"),
    BackupScopeRow("查词历史与 AI 笔记", "搜索历史与词卡 AI 问答笔记（不含密钥）", "vocabulary"),
    BackupScopeRow("已缓存 AI 音频", "可通过上方开关排除", "audio"),
    BackupScopeRow("明确不包含", "API Key / Keystore 密钥 / 系统 TTS / 临时图片 / 提醒设置", "excluded"),
)
