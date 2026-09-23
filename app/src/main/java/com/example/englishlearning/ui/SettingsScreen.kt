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
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint

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

        SettingsGroup(title = "AI", tag = "settings_group_ai") {
            SettingsPendingRow(
                title = "AI 服务与密钥",
                subtitle = "后续版本：需要先接通 AI 网关，再支持多套配置与测试连接",
                tag = "settings_pending_ai",
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
