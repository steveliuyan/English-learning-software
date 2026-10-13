package com.example.englishlearning.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.englishlearning.backup.device.QrFrameAnalyzer
import java.io.InputStream

/**
 * F4-04 新设备恢复页（AC4-06 无旁路）：本屏不存在“跳过校验/强制导入/降级为
 * Salvage 恢复”的入口；配对仅支持相机实时扫码（决策 D2），不提供手动输入或
 * 相册识码，削弱截图转发通道。
 */
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel? = null,
) {
    if (viewModel == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("backup_restore_screen"),
        ) {
            Text("从旧设备恢复", style = MaterialTheme.typography.titleLarge)
            Text("恢复功能暂不可用")
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("backup_restore_back")) { Text("返回") }
        }
        return
    }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val bytes = uri?.let { selected ->
            runCatching {
                context.contentResolver.openInputStream(selected)?.use(InputStream::readBytes)
            }.getOrNull()
        }
        viewModel.onPackagePicked(uri, bytes)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("backup_restore_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("从旧设备恢复", style = MaterialTheme.typography.titleLarge)
        Text(
            "恢复将“替换本机数据”，不可合并。恢复成功后所有 AI Profile 的 API Key 会被删除，需要重新填写。",
            style = MaterialTheme.typography.bodySmall,
        )

        when (state.phase) {
            BackupRestorePhase.Picking -> {
                Button(
                    onClick = { filePicker.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
                    modifier = Modifier.testTag("backup_restore_pick"),
                ) { Text("选择备份文件 (.elpbak)") }
            }

            BackupRestorePhase.Scanning -> {
                Text("请用本机相机扫描旧设备显示的配对二维码（仅支持实时扫码）。")
                QrScanBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .testTag("backup_restore_scan_box"),
                    onPermissionNeeded = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onPayload = viewModel::onCodeScanned,
                )
            }

            BackupRestorePhase.Password -> {
                var password by remember { mutableStateOf(charArrayOf()) }
                Text("该备份包受密码保护，请输入备份密码。")
                OutlinedTextField(
                    value = password.concatToString(),
                    onValueChange = { password = it.toCharArray() },
                    label = { Text("备份密码") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_restore_password"),
                )
                Button(
                    onClick = { viewModel.onPasswordEntered(password) },
                    modifier = Modifier.testTag("backup_restore_password_submit"),
                ) { Text("继续") }
            }

            BackupRestorePhase.Confirming -> {
                val staged = state.staged
                Text("验证通过。即将替换本机数据：")
                Text(
                    staged?.let { "共 ${it.recordCount} 条记录 · ${it.mediaCount} 个音频文件 · 约 ${it.plaintextBytes / 1024} KB" }
                        ?: "",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = viewModel::onReplaceConfirmed,
                    modifier = Modifier.testTag("backup_restore_confirm_replace"),
                ) { Text("确认替换（当前数据将被替换，不可恢复为合并）") }
                OutlinedButton(
                    onClick = viewModel::cancel,
                    modifier = Modifier.testTag("backup_restore_cancel"),
                ) { Text("取消") }
            }

            BackupRestorePhase.Restoring -> {
                CircularProgressIndicator()
                Text("正在恢复，请勿关闭应用…")
            }

            BackupRestorePhase.Done -> Unit
        }

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("backup_restore_error"))
        }
        state.resultMessage?.let { Text(it, modifier = Modifier.testTag("backup_restore_result")) }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            viewModel.cancel()
            onBack()
        }, modifier = Modifier.testTag("backup_restore_back")) { Text("返回") }
    }
}

/** Live-camera-only QR box (decision D2). No gallery import, no manual entry. */
@Composable
private fun QrScanBox(
    modifier: Modifier,
    onPermissionNeeded: () -> Unit,
    onPayload: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) onPermissionNeeded()
    }

    if (!hasPermission) {
        Column(modifier.padding(16.dp)) {
            Text("需要相机权限才能扫描配对码。")
            Button(onClick = {
                onPermissionNeeded()
                hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }) { Text("授权相机") }
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val previewView = PreviewView(viewContext)
            val providerFuture = ProcessCameraProvider.getInstance(viewContext)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(ContextCompat.getMainExecutor(viewContext), QrFrameAnalyzer(onPayload)) }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) {
                    // camera unavailable (no rear camera / in use); the flow stays on this screen
                }
            }, ContextCompat.getMainExecutor(viewContext))
            previewView
        },
    )
}
