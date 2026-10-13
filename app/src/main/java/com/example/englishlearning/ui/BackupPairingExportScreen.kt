package com.example.englishlearning.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.englishlearning.backup.device.AndroidMigrationKey

/**
 * F4-04 旧设备受保护导出页：系统认证（BiometricPrompt，含凭据降级）→ 一次性挑战
 * 二维码 → 可选密码 → 受保护导出。本页不存在任何“跳过认证”入口；密码遗忘不可
 * 恢复为固定文案。
 */
@Composable
fun BackupPairingExportScreen(
    onBack: () -> Unit,
    viewModel: BackupProtectedExportViewModel? = null,
) {
    if (viewModel == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).testTag("backup_pairing_export_screen")) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("backup_pairing_export_back")) { Text("返回") }
            Text("受保护导出", fontSize = 22.sp)
            Text("受保护导出功能暂不可用")
        }
        return
    }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        viewModel.onDocumentPicked(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("backup_pairing_export_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("backup_pairing_export_back")) { Text("返回") }
        Text("认证导出（可用于新设备恢复）", fontSize = 22.sp)
        Text(
            "本流程生成“配对认证 + 可选密码保护”的备份包：只在 30 分钟配对有效期内可恢复；" +
                "如设置密码，密码遗忘则备份无法恢复（如实声明，无任何找回途径）。",
            style = MaterialTheme.typography.bodySmall,
        )

        when (state.phase) {
            ProtectedExportPhase.Idle -> Button(
                onClick = {
                    val activity = context as? FragmentActivity
                    if (activity == null) {
                        return@Button
                    }
                    if (AndroidMigrationKey.ensureKey()) {
                        showBiometricPrompt(activity, viewModel)
                    } else {
                        viewModel.onAuthenticationRejected()
                    }
                },
                modifier = Modifier.testTag("backup_pairing_export_authenticate"),
            ) { Text("开始（需系统认证）") }

            ProtectedExportPhase.Authenticating -> CircularProgressIndicator()

            ProtectedExportPhase.Active -> {
                state.qrPayload?.let { payload ->
                    QrCodeDisplay(payload)
                    Text("请在新设备上打开“从旧设备恢复”并实时扫描此码。", style = MaterialTheme.typography.bodySmall)
                }
                var usePassword by remember { mutableStateOf(false) }
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = usePassword, onCheckedChange = {
                        usePassword = it
                        viewModel.setUsePassword(it)
                    })
                    Text("设置备份密码（10–128 字符）")
                }
                var password by remember { mutableStateOf("") }
                if (usePassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("备份密码（遗忘则无法恢复）") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_pairing_export_password"),
                    )
                }
                Button(
                    onClick = {
                        val chars = if (usePassword) password.toCharArray() else null
                        viewModel.exportProtected(includeMediaAssets = true, password = chars)
                    },
                    modifier = Modifier.testTag("backup_pairing_export_export"),
                ) { Text("导出受保护备份包") }
            }

            ProtectedExportPhase.Exporting -> CircularProgressIndicator()

            ProtectedExportPhase.Done -> {
                state.message?.let { Text(it) }
                if (state.packageReady) {
                    state.fileName?.let { Text("文件名：$it", style = MaterialTheme.typography.bodySmall) }
                    Button(
                        onClick = { launcher.launch(state.fileName ?: "backup-protected.elpbak") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_pairing_export_save"),
                    ) { Text("选择保存位置并导出") }
                }
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("backup_pairing_export_error"))
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun showBiometricPrompt(activity: FragmentActivity, viewModel: BackupProtectedExportViewModel) {
    val manager = BiometricManager.from(activity)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.onAuthenticated()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                viewModel.onAuthenticationRejected()
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("认证以发起设备迁移")
        .setSubtitle("仅本次会话可签发配对码并导出受保护备份")
        .setAllowedAuthenticators(authenticators)
        .build()
    prompt.authenticate(info)
}

/** QR matrix via the already-registered zxing-core dependency, rendered as text blocks. */
@Composable
private fun QrCodeDisplay(payload: String) {
    val matrix = remember(payload) {
        runCatching {
            com.google.zxing.qrcode.QRCodeWriter().encode(
                payload,
                com.google.zxing.BarcodeFormat.QR_CODE,
                0,
                0,
            )
        }.getOrNull()
    }
    if (matrix == null) {
        Text("二维码生成失败（配对码过长）。")
        return
    }
    val text = remember(matrix) {
        buildString {
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) append(if (matrix.get(x, y)) '█' else '　')
                append('\n')
            }
        }
    }
    Text(text, fontSize = 7.sp, modifier = Modifier.testTag("backup_pairing_export_qr"))
}
