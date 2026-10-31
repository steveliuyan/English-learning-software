package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.EntryIconTone
import com.example.englishlearning.ui.components.EntryRow
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.appColors

/**
 * 「提醒与发音」二级导航页（决策 §4）。
 *
 * 参考 `sMeReminder` 把「每日提醒」与「发音」画在同一张屏里；本应用是两套独立 ViewModel 与两屏
 * （[ReminderSettingsScreen] 管通知权限与时间，[SpeechSettingsScreen] 管引擎与语速）。
 * 决策的裁定是**先以导航页保真、不重写两屏**：合屏要动两套状态机与权限时序，那是功能改动而不是版式改动。
 *
 * 因此本页只做两件事：两枚共享 [EntryRow] 的落点，以及把「当前供应商」这句真值带到行上
 * （[engineStatuses] 与 [SpeechEngineStatuses] 从 `SettingsScreen.kt` 搬到这里——它们只被发音摘要消费，
 * 而设置子页已不再有发音行）。两屏本体一字未动。
 */
@Composable
fun ReminderSpeechScreen(
    speechEngineStatuses: SpeechEngineStatuses = SpeechEngineStatuses(),
    onOpenReminderSettings: () -> Unit,
    onOpenSpeechSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("reminder_speech_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = "提醒与发音",
            subtitle = "都在本机设置，不属于学习数据",
            onBack = onBack,
        )
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            EntryRow(
                icon = AppIcon.Bell,
                domain = AppDomain.Settings,
                title = "学习提醒",
                subtitle = "只在当天没完成计划时提醒 · 默认关闭（非精确、每天最多一次）",
                iconTone = EntryIconTone.Tint,
                modifier = Modifier.testTag("reminder_speech_open_reminder"),
                onClick = onOpenReminderSettings,
            )
            EntryRow(
                icon = AppIcon.Speaker,
                domain = AppDomain.AiSpeech,
                title = "语音朗读",
                subtitle = "当前供应商：${speechEngineStatuses.currentProvider}",
                iconTone = EntryIconTone.Gradient,
                topDivider = true,
                modifier = Modifier.testTag("reminder_speech_open_speech"),
                onClick = onOpenSpeechSettings,
            )
        }
    }
}

/**
 * 发音引擎的配置状态摘要（原住在 `SettingsScreen.kt`，2026-11-02 随发音行搬到本页）。
 *
 * 搬到本页的理由：设置子页只剩「外观」，而这两枚状态是「语音朗读」行的副标题真值。
 * 同包移动，调用点（[SpeechSettingsScreen] 与宿主）无需改 import。
 */
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
        openAi = openAiStatus,
        miMo = if (selectedEngine == PronunciationEngine.MiMo) "当前供应商 · $miMoStatus" else miMoStatus,
        currentProvider = when (selectedEngine) {
            PronunciationEngine.SystemTts -> "系统 TTS"
            PronunciationEngine.MiMo -> "小米 MiMo"
        },
    )
}
