package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.EntryIconTone
import com.example.englishlearning.ui.components.EntryRow
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 「备份与恢复」子页（裁决⑥）：设置页那一行**导航**到这里，不再就地折叠。
 *
 * 三节固定顺序——导出 / 恢复 / 删除全部学习数据——每节一个节标题 + 一张 `surface` 卡（18dp 圆角 +
 * `outlineSoft` 描边，与设置页分组卡同壳，但**不复用**设置页的 private `SettingsGroupSection*`：
 * 那几件是设置页 4 组 9 行台账的渲染形状，改成 public 等于给它开第二个消费口）。
 *
 * 四枚行标题与副标题在本屏是**唯一真值**（`SettingsGroups` 只管设置页那一行「备份与恢复」的标题）；
 * 删除行的副标题逐字沿用原展开区文案，删除语义一行未动：本屏只发 request，级联预览 + 二次确认
 * 仍由宿主 `AppScreen` 的 `AppUiState.Ready` 顶层承载（F4-01 Spec 04 L18）。
 *
 * 认证链路（配对挑战 / 签名 / BiometricPrompt）全在三枚子屏内部，本屏只做入口导航。
 *
 * @param deleteAllLearningDataNotice 「删除全部学习数据」的一句结果回执；`null` 时不占位（条件渲染，
 *   不是常驻空行）。档位取 [InlineNoticeKind.Info]：宿主传来裸 `String`，不做文本嗅探。
 */
@Composable
fun BackupHubScreen(
    onBack: () -> Unit,
    onOpenBackupExport: () -> Unit,
    onOpenBackupPairingExport: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onDeleteAllLearningData: () -> Unit,
    deleteAllLearningDataNotice: String? = null,
) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("backup_hub_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = "备份与恢复",
            subtitle = "导出留档、换机恢复与清空学习数据。API Key 与系统 TTS 数据不在任何备份里。",
            onBack = onBack,
        )

        BackupHubSection(title = "导出", tag = "backup_hub_section_export") {
            EntryRow(
                icon = AppIcon.Download,
                domain = AppDomain.Settings,
                title = "导出学习数据留档",
                subtitle = "生成 `.elpbak` 留档文件；不含密钥，不能用于换机恢复",
                iconTone = EntryIconTone.Tint,
                modifier = Modifier.testTag("backup_hub_open_export"),
                onClick = onOpenBackupExport,
            )
            EntryRow(
                icon = AppIcon.Shield,
                domain = AppDomain.Settings,
                title = "认证导出（可用于新设备恢复）",
                subtitle = "旧设备系统认证后生成配对码与受保护备份",
                iconTone = EntryIconTone.Tint,
                topDivider = true,
                modifier = Modifier.testTag("backup_hub_open_pairing_export"),
                onClick = onOpenBackupPairingExport,
            )
        }

        BackupHubSection(title = "恢复", tag = "backup_hub_section_restore") {
            EntryRow(
                icon = AppIcon.Upload,
                domain = AppDomain.Settings,
                title = "从旧设备恢复（认证恢复）",
                subtitle = "扫描旧设备配对码并替换本机数据",
                iconTone = EntryIconTone.Tint,
                modifier = Modifier.testTag("backup_hub_open_restore"),
                onClick = onOpenBackupRestore,
            )
        }

        BackupHubSection(title = "删除", tag = "backup_hub_section_delete") {
            // F4-01 Spec 04 L18：级联影响 + 二次确认在 AppScreen 宿主；本行只发 request。
            EntryRow(
                icon = AppIcon.Trash,
                domain = AppDomain.Danger,
                title = "删除全部学习数据",
                subtitle = "清空学习记录、复习调度、每日计划与阅读完成；文章与收藏保留",
                iconTone = EntryIconTone.Tint,
                modifier = Modifier.testTag("backup_hub_delete_all"),
                onClick = onDeleteAllLearningData,
            )
        }

        deleteAllLearningDataNotice?.let { message ->
            InlineNotice(
                text = message,
                kind = InlineNoticeKind.Info,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 一节 = 节标题 + `surface` 卡壳（18dp 圆角 + `outlineSoft` 描边）；节 tag 挂在整节的包络上。 */
@Composable
private fun BackupHubSection(
    title: String,
    tag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = appColors()
    Column(
        modifier = Modifier.testTag(tag),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = AppType.Body,
            fontWeight = FontWeight.Bold,
            color = colors.text1,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.outlineSoft, RoundedCornerShape(18.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                content = content,
            )
        }
    }
}
