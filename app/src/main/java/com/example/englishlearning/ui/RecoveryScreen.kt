package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintTextMuted

/**
 * F5-02（SP-B 决策 §3）：迁移/打开失败的全屏可恢复态。
 *
 * 文案固定为「数据库升级失败：<原因摘要>。你的学习数据仍在设备上。可重试；如反复失败，
 * 请先用另一台设备确认导出留档。」；页面**不提供**「清除数据」入口——诱导销毁数据不是
 * 「可恢复错误」，系统设置里的清除路径只写进文案。
 */
@Composable
internal fun RecoveryScreen(reason: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MintBackground).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "数据库升级失败：$reason。",
            color = MintPrimaryDark,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "你的学习数据仍在设备上。可重试；如反复失败，请先用另一台设备确认导出留档。",
            color = MintTextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag("recovery_retry"),
            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = appColors().onBrand),
        ) {
            Text("重试")
        }
    }
}
