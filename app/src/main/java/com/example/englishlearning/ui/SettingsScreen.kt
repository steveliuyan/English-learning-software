package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint

data class SpeechEngineStatuses(
    val miMo: String = "未配置",
    val openAi: String = "未配置",
    val zipVoice: String = "未下载",
    val currentProvider: String = "系统 TTS",
)

fun SpeechSettingsUiState.engineStatuses(): SpeechEngineStatuses {
    // 状态按协议匹配的候选派生：OpenAI 槽位只认 OPENAI_COMPATIBLE，MiMo 槽位只认
    // XIAOMI_MIMO。把通用 Profile 算进 MiMo、或把 MiMo 预设算进 OpenAI，都会把
    // 「根本发不出正确请求的绑定」误报成已配置。
    fun status(profileId: String?, kind: AiProviderKind): String {
        val kindCandidates = candidates.filter { it.providerKind == kind }
        return when {
            profileId == null && kindCandidates.any { it.status == SpeechProfileStatus.Available } -> "可选配置 · 未绑定"
            profileId == null -> "未配置"
            kindCandidates.firstOrNull { it.profileId == profileId }?.status == SpeechProfileStatus.Available -> "已配置"
            kindCandidates.any { it.profileId == profileId } -> "缺少密钥"
            else -> "绑定失效"
        }
    }
    val openAiStatus = status(openAiProfileId, AiProviderKind.OPENAI_COMPATIBLE)
    val miMoStatus = status(miMoProfileId, AiProviderKind.XIAOMI_MIMO)
    return SpeechEngineStatuses(
        openAi = if (selectedEngine == PronunciationEngine.OpenAi) "当前供应商 · $openAiStatus" else openAiStatus,
        miMo = if (selectedEngine == PronunciationEngine.MiMo) "当前供应商 · $miMoStatus" else miMoStatus,
        currentProvider = when (selectedEngine) {
            PronunciationEngine.SystemTts -> "系统 TTS"
            PronunciationEngine.OpenAi -> "OpenAI TTS"
            PronunciationEngine.MiMo -> "小米 MiMo"
        },
    )
}

/**
 * 「设置」一级页。
 *
 * 收纳了原先挂在「今日计划 → 学习工具」里的入口。已实现的项可点；未实现的项**禁用并把
 * 「后续版本」写在副标题里**，而不是做成能点却没反应的假入口。
 *
 * 资料卡刻意**不显示「每日新增 N 词」**：当日计划里的 `newTarget` 在词书新词学完时会小于
 * 用户配置的每日目标（PRD FR-01 第 8 条），拿它当「每日新增」是错的。这里只报今日计划的
 * 真实数量，改目标请走「调整词书与目标」。
 *
 * @param todayNewTarget 今日计划的新词数，`null` 表示今日计划还不可读。
 * @param todayDueTarget 今日计划的待复习词数，`null` 同上。
 */
@Composable
fun SettingsScreen(
    profileName: String,
    wordBookName: String?,
    todayNewTarget: Int?,
    todayDueTarget: Int?,
    onOpenSetup: () -> Unit,
    onOpenWorksheet: () -> Unit,
    onOpenAiProfiles: () -> Unit,
    onOpenSpeechSettings: () -> Unit = {},
    /** 已配置的 AI 服务摘要；`null` 表示还没读出本地配置。 */
    aiProfileSubtitle: String? = null,
    speechEngineStatuses: SpeechEngineStatuses = SpeechEngineStatuses(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)

        Card(
            colors = CardDefaults.cardColors(containerColor = MintSurface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MintOutline, RoundedCornerShape(24.dp))
                .testTag("settings_profile_card"),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(profileName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                Text(
                    text = wordBookName ?: "尚未选择词书",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MintTextMuted,
                    modifier = Modifier.testTag("settings_profile_word_book"),
                )
                Text(
                    text = when {
                        todayNewTarget == null || todayDueTarget == null -> "今日计划还没读出来"
                        else -> "今日计划：新增 $todayNewTarget · 复习 $todayDueTarget"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MintTextMuted,
                    modifier = Modifier.testTag("settings_profile_summary"),
                )
            }
        }

        SettingsGroup(title = "学习", tag = "settings_group_learning") {
            SettingsActionRow(
                title = "调整词书与目标",
                subtitle = "换词书、改每日新增量、改反馈后的详情开关",
                tag = "settings_open_setup",
                onClick = onOpenSetup,
            )
            SettingsActionRow(
                title = "生成默写纸",
                subtitle = "选中译英或英译中，预览后导出可打印 PDF",
                tag = "settings_open_worksheet",
                onClick = onOpenWorksheet,
            )
        }

        SettingsGroup(title = "阅读", tag = "settings_group_reading") {
            SettingsPendingRow(
                title = "文章类型与长度偏好",
                subtitle = "后续版本：目前可在「阅读」页选择默认类型",
                tag = "settings_pending_reading",
            )
        }

        // 「AI 与语音」是这一页唯一的云服务入口组：一级页只放两个可点入口，引擎状态与
        // 「待接入」厂商属于二级页的信息量——此前它们在一级页占了近一半屏（9 行里 6 行
        // 点不了），用户学习成本过高。
        SettingsGroup(title = "AI 与语音", tag = "settings_group_ai_speech") {
            SettingsActionRow(
                title = "AI 服务与密钥",
                subtitle = aiProfileSubtitle ?: "添加多套 OpenAI 兼容服务，管理 Endpoint、模型与密钥",
                tag = "settings_open_ai_profiles",
                onClick = onOpenAiProfiles,
            )
            SettingsActionRow(
                title = "语音朗读",
                subtitle = "当前供应商：${speechEngineStatuses.currentProvider}",
                tag = "settings_open_speech",
                onClick = onOpenSpeechSettings,
            )
        }

        SettingsGroup(title = "账户", tag = "settings_group_account") {
            SettingsPendingRow(
                title = "昵称与头像",
                subtitle = "后续版本：支持更换昵称与自定义头像",
                tag = "settings_pending_profile",
            )
            SettingsPendingRow(
                title = "数据与备份",
                subtitle = "后续版本：导出学习档案与恢复",
                tag = "settings_pending_backup",
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, tag: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MintPrimaryDark,
            modifier = Modifier.testTag(tag),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MintSurface),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, MintOutline, RoundedCornerShape(22.dp)),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsActionRow(title: String, subtitle: String, tag: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag(tag)
            .clickable(onClick = onClick)
            .semantics { contentDescription = title },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MintPrimary)
    }
}

/**
 * 未实现项：整行不可点，并在右侧挂一个明确的标记，避免被误认为坏掉的按钮。
 */
@Composable
private fun SettingsPendingRow(title: String, subtitle: String, tag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MintTextMuted)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
        }
        Row(
            modifier = Modifier.background(MintTint, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("后续版本", style = MaterialTheme.typography.labelSmall, color = MintPrimaryDark)
        }
    }
}
