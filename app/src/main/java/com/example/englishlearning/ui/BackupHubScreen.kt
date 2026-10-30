package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppGlyph
import com.example.englishlearning.ui.components.AppGlyphLayer
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * 「备份与恢复」子页（裁决⑥）：设置页那一行**导航**到这里，不再就地折叠。
 *
 * 三分区固定顺序——导出 / 恢复 / 危险区——每区一个节标题 + 一张软档卡（`SoftCard`：`surface` 实底 +
 * 20dp 圆角、**不描边**），区内行级统一走 `BareRow`（52dp 最小高 + 发丝线）。批 5 Task 4 把旧形态
 * （节标题 + M3 `Card` 18dp 描边壳 + 四枚等权 `EntryRow`）整体换掉，理由是三条入口原来长得一模一样：
 *
 *  - **两条导出分主次**：留档导出是推荐路径（行尾一枚「推荐」小字 + 独立锚 `backup_hub_export_recommended`），
 *    认证导出是次选。主次不用实底药片表达——这两条标题最长 13 个字，药片在 `AppType.Title` 下会
 *    折成两三行、把 48dp 下限顶到 90dp 一带；而且 hub 屏是「选路径」，摆实底大键会把「去哪」
 *    读成「就在这做」，还会与子屏吸底条上的真主键抢焦点。
 *  - **危险区沉底**：清空数据是唯一会**改用户数据**的入口，独占最后一节，且这一节的话用
 *    `colors.error` 一档墨说出来。以前它和导出、恢复同色同形，等于把不可撤销的操作伪装成普通入口。
 *  - **回执条排在危险区之前**：危险区必须是全屏最后一段，删除回执不许把「清空数据」那行顶到屏中间。
 *
 * 四枚行标题与副标题在本屏是**唯一真值**（`SettingsGroups` 只管设置页那一行「备份与恢复」的标题，
 * `BackupEntryUnificationTest` 按这几句做文案取证），重排只动容器与顺序，不动一个字。
 *
 * 删除语义一行未动：本屏只发 request，级联预览 + 二次确认仍由宿主 `AppScreen` 的
 * `AppUiState.Ready` 顶层承载（F4-01 Spec 04 L18）。认证链路（配对挑战 / 签名 / BiometricPrompt）
 * 全在三枚子屏内部，本屏只做入口导航。
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
    val settingsInk = appDomain(AppDomain.Settings).deep
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("backup_hub_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AppScreenHeader(
            title = "备份与恢复",
            subtitle = "导出留档、换机恢复与清空学习数据。API Key 与系统 TTS 数据不在任何备份里。",
            onBack = onBack,
        )

        // ---------- 导出：主（推荐）+ 次 ----------
        BackupHubSection(title = "导出", tag = "backup_hub_section_export") {
            BareRow(
                title = "导出学习数据留档",
                subtitle = "生成 .elpbak 留档文件；不含密钥，不能用于换机恢复",
                trailing = "推荐",
                trailingTag = "backup_hub_export_recommended",
                leading = {
                    AppGlyph(
                        icon = AppIcon.Download,
                        tint = settingsInk,
                        size = 20.dp,
                        layer = AppGlyphLayer.StrokeOnly,
                    )
                },
                modifier = Modifier.testTag("backup_hub_open_export"),
                onClick = onOpenBackupExport,
            )
            BareRow(
                title = "认证导出（可用于新设备恢复）",
                subtitle = "旧设备系统认证后生成配对码与受保护备份",
                topDivider = true,
                leading = {
                    AppGlyph(
                        icon = AppIcon.Shield,
                        tint = settingsInk,
                        size = 20.dp,
                        layer = AppGlyphLayer.StrokeOnly,
                    )
                },
                modifier = Modifier.testTag("backup_hub_open_pairing_export"),
                onClick = onOpenBackupPairingExport,
            )
            Text(
                text = "两枚导出都写到你选定的本机目录，App 自己不留副本。",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 12.dp),
            )
        }

        // ---------- 恢复 ----------
        BackupHubSection(title = "恢复", tag = "backup_hub_section_restore") {
            BareRow(
                title = "从旧设备恢复（认证恢复）",
                subtitle = "扫描旧设备配对码并替换本机数据",
                leading = {
                    AppGlyph(
                        icon = AppIcon.Upload,
                        tint = settingsInk,
                        size = 20.dp,
                        layer = AppGlyphLayer.StrokeOnly,
                    )
                },
                modifier = Modifier.testTag("backup_hub_open_restore"),
                onClick = onOpenBackupRestore,
            )
            Text(
                text = "恢复会先给出完整预览，确认之后才替换本机数据。",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 12.dp),
            )
        }

        // 回执条件渲染，且必须排在危险区之前（危险区是全屏最后一段）。
        deleteAllLearningDataNotice?.let { message ->
            InlineNotice(
                text = message,
                kind = InlineNoticeKind.Info,
            )
        }

        // ---------- 危险区（沉底）----------
        BackupHubSection(title = "危险操作", tag = "backup_hub_section_delete") {
            Text(
                text = "不可撤销",
                style = AppType.Label,
                color = colors.error,
                modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 6.dp),
            )
            // F4-01 Spec 04 L18：级联影响 + 二次确认在 AppScreen 宿主；本行只发 request。
            BareRow(
                title = "删除全部学习数据",
                subtitle = "清空学习记录、复习调度、每日计划与阅读完成；文章与收藏保留",
                leading = {
                    AppGlyph(
                        icon = AppIcon.Trash,
                        tint = appDomain(AppDomain.Danger).deep,
                        size = 20.dp,
                        layer = AppGlyphLayer.StrokeOnly,
                    )
                },
                modifier = Modifier.testTag("backup_hub_delete_all"),
                onClick = onDeleteAllLearningData,
            )
        }

        // 原型 `sMeData`（`app.js:1421`）的页尾提示条，逐字成立：本仓数据默认只在本机、
        // 无云同步，卸载即随应用数据删除。危险区仍必须是全屏最后一段入口，提示条排在它之后不抢位置。
        InlineNotice(
            text = "所有数据默认只在本机；卸载 App 会一并删除，请先导出备份。",
            kind = InlineNoticeKind.Error,
        )
    }
}

/**
 * 一节 = 节标题 + 一张软档卡；节 tag 挂在整节的包络上。
 *
 * 卡片传 `contentPadding = 0.dp`：裸行自己带 15/13 内边距，卡再打 16dp 底会叠成 31dp，
 * 与全站裸行的左缘对不齐（`LicensesScreen` 的条目卡同一条裁决）。卡内的说明文字因此自己补
 * `padding(start = 15.dp)`，与行标题同一条起始线。
 */
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
        SoftCard(contentPadding = PaddingValues(0.dp), content = content)
    }
}
