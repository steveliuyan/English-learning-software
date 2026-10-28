package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.IllustratedIcon
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * F5-02（SP-B 决策 §3）：迁移/打开失败的全屏可恢复态。
 *
 * 这一屏的全部任务是让用户相信两件事——**数据还在**、**现在有两件该做的事**。
 * 页面**不提供**「清除数据」入口：诱导销毁数据不是「可恢复错误」，清理动作只作为
 * 系统设置里的路径写进文案。
 *
 * 批 5 Task 8 按 spec §5「恢复异常」行重排版式，状态机与数据通路一字不改：
 *  - **标题去技术串**：旧标题「数据库升级失败：<原因>」把引擎内部词汇摆到第一行，
 *    用户读完只会想到「坏了」；现在第一行是「数据暂时打不开」，原始原因收进「详情」折叠区，
 *    由用户自己决定要不要看；
 *  - **主操作换成「导出本机留档」**：旧形态只有「重试」，而本屏文案自己写着「先确认导出留档」——
 *    承诺了却不给入口。现在留档是实底主档，直达备份中心的导出屏；「重试」退成次档；
 *  - **去静态色桥**：`Mint*` 八枚退役，墨色一律走 `appColors()`，深浅两套各自成立；
 *  - **整屏可滚**：旧形态是居中列，大字号下标题 + 说明 + 两枚出路会越过一屏被裁掉。
 */
@Composable
internal fun RecoveryScreen(
    reason: String,
    onRetry: () -> Unit,
    onOpenBackupExport: () -> Unit,
) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("recovery_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecoveryHeadline()
        RecoveryDetails(reason = reason)
        RecoveryActions(
            onOpenBackupExport = onOpenBackupExport,
            onRetry = onRetry,
        )
    }
}

/** 一句事实 + 一枚轻插画（spec §4.6）：先定性，再解释。 */
@Composable
private fun RecoveryHeadline() {
    val colors = appColors()
    IllustratedIcon(icon = AppIcon.Shield, size = 56.dp)
    Text(
        text = "数据暂时打不开",
        style = AppType.Headline,
        color = colors.text1,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "你的学习数据仍在设备上，只是这次没能读出来。可以先重试；" +
            "如果反复失败，先导出一份本机留档，再决定下一步。",
        style = AppType.Body,
        color = colors.text2,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 原始原因默认收起：诊断信息一个不丢，但不摆到第一眼。 */
@Composable
private fun RecoveryDetails(reason: String) {
    val colors = appColors()
    var showDetails by remember { mutableStateOf(false) }
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        BareRow(
            title = "详情",
            subtitle = if (showDetails) null else "展开可看到这次失败的原因",
            trailing = if (showDetails) "收起" else "展开",
            showChevron = false,
            onClick = { showDetails = !showDetails },
        )
        if (showDetails) {
            Text(
                text = reason,
                style = AppType.Footnote,
                color = colors.text3,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** 两条出路：留档是实底主档（保命那一步），重试是次档。 */
@Composable
private fun RecoveryActions(
    onOpenBackupExport: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = appColors()
    Text(
        text = "导出后这份留档存在你自己的设备上，重装或换机都还在。",
        style = AppType.Footnote,
        color = colors.text2,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    PillButton(
        text = "导出本机留档",
        onClick = { onOpenBackupExport() },
        modifier = Modifier.fillMaxWidth(),
        style = PillStyle.Primary,
        domain = AppDomain.Settings,
        testTag = "recovery_export_archive",
        contentDescription = "导出本机留档",
    )
    PillButton(
        text = "重试",
        onClick = { onRetry() },
        modifier = Modifier.fillMaxWidth(),
        style = PillStyle.Secondary,
        domain = AppDomain.Settings,
        testTag = "recovery_retry",
        contentDescription = "重试",
    )
}
