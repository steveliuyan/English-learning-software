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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.englishlearning.backup.device.QrFrameAnalyzer
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain
import java.io.InputStream

/**
 * F4-04 新设备恢复页（AC4-06 无旁路）：本屏不存在“跳过校验/强制导入/降级为
 * Salvage 恢复”的入口；配对仅支持相机实时扫码（决策 D2），不提供手动输入或
 * 相册识码，削弱截图转发通道。
 *
 * 批 5 Task 6 按 spec §5「恢复」行重排版式，状态机与数据通路一字不改：
 *  - **页头**换共享 [AppScreenHeader]（旧的两处自写 `OutlinedButton("返回")` 下线，返回位归页头）；
 *  - **四态骨架**逐段一张 [SoftCard]：选包 → 扫码 → 口令 → 确认，每段只有一枚主键，
 *    旧形态把六枚 M3 按钮平铺在一条长流里，用户看不出「现在在第几步」；
 *  - **口令掩码**：`visualTransformation = PasswordVisualTransformation()`——旧形态明文回显；
 *  - **替换后果讲全**：确认段写「覆盖本机现有数据，且无法恢复为合并」，不再只有「确认替换」一句；
 *  - **两分支补滚动**：大字号下「立即重启应用」原本会被推出屏外。
 */
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel? = null,
) {
    val colors = appColors()
    // 屏锚挂在这枚唯一的根盒上：两分支共用同一个 `backup_restore_screen`。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("backup_restore_screen"),
    ) {
        if (viewModel == null) {
            UnavailableContent(onBack = onBack)
        } else {
            BackupRestoreContent(viewModel = viewModel, onBack = onBack)
        }
    }
}

/** `viewModel == null` 的降级分支：只读提示，零假入口。 */
@Composable
private fun UnavailableContent(onBack: () -> Unit) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(title = "从旧设备恢复", onBack = onBack)
        SoftCard {
            Text(text = "恢复功能暂不可用", style = AppType.Body, color = colors.text1)
            Text(
                text = "恢复服务还没准备好。返回上一页再进来，通常就恢复了。",
                style = AppType.Footnote,
                color = colors.text2,
            )
        }
    }
}

@Composable
private fun BackupRestoreContent(
    viewModel: BackupRestoreViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val colors = appColors()
    val settingsPalette = appDomain(AppDomain.Settings)
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val bytes = uri?.let { selected ->
            runCatching {
                context.contentResolver.openInputStream(selected)?.use(InputStream::readBytes)
            }.getOrNull()
        }
        viewModel.onPackagePicked(uri, bytes)
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // 授权结果回来后刷新本地状态，让取景框立即出现/保持提示
        hasCameraPermission = granted
    }

    // 离开本页 = 放弃暂存会话（旧形态把这件事写在自写返回键里，返回位归页头后仍要保留：
    // 暂存工作区不解会一直留在磁盘上）。提交后例外——重启提示必须留在界面上。
    val back: () -> Unit = {
        if (!state.appRestartRequired) viewModel.cancel()
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = "从旧设备恢复",
            subtitle = "整机替换：新设备上的数据会被备份内容覆盖，不会合并。",
            onBack = back,
        )

        SoftCard {
            Text(text = "开始之前", style = AppType.Body, fontWeight = FontWeight.Bold, color = colors.text1)
            Text(
                text = "请在旧设备上用「认证导出」生成配对码和备份包：配对码 30 分钟内有效，恢复完成后所有 AI " +
                    "服务的 API Key 会被删除，需要重新填写。",
                style = AppType.Footnote,
                color = colors.text2,
            )
        }

        when (state.phase) {
            BackupRestorePhase.Picking -> SoftCard {
                Text(text = "第一步 · 选择备份包", style = AppType.Body, fontWeight = FontWeight.Bold, color = colors.text1)
                Text(
                    text = "选那枚 .elpbak 文件，通常就在你从旧设备导出时挑的目录里。",
                    style = AppType.Footnote,
                    color = colors.text2,
                )
                PillButton(
                    text = "选择备份文件",
                    onClick = { filePicker.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    style = PillStyle.Primary,
                    domain = AppDomain.Settings,
                    testTag = "backup_restore_pick",
                )
            }

            BackupRestorePhase.Scanning -> SoftCard {
                Text(text = "第二步 · 扫描配对码", style = AppType.Body, fontWeight = FontWeight.Bold, color = colors.text1)
                Text(
                    text = "用本机相机对准旧设备屏幕上的配对二维码（仅支持实时扫码，不支持相册识码）。",
                    style = AppType.Footnote,
                    color = colors.text2,
                )
                QrScanBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .testTag("backup_restore_scan_box"),
                    hasPermission = hasCameraPermission,
                    onRequestPermission = { cameraPermission.launch(Manifest.permission.CAMERA) },
                    onPayload = viewModel::onCodeScanned,
                )
            }

            BackupRestorePhase.Password -> SoftCard {
                var password by remember { mutableStateOf(charArrayOf()) }
                Text(text = "第三步 · 输入备份密码", style = AppType.Body, fontWeight = FontWeight.Bold, color = colors.text1)
                Text(
                    text = "这个包受密码保护。密码遗忘则备份无法恢复，请先把旧设备上的密码找回来。",
                    style = AppType.Footnote,
                    color = colors.text2,
                )
                OutlinedTextField(
                    value = password.concatToString(),
                    onValueChange = { password = it.toCharArray() },
                    label = { Text("备份密码") },
                    singleLine = true,
                    // 口令必须掩码：这是这台机器的最后一道防线，明文回显等于把它摆在肩窥视野里。
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_restore_password"),
                )
                PillButton(
                    text = "继续",
                    onClick = { viewModel.onPasswordEntered(password) },
                    modifier = Modifier.fillMaxWidth(),
                    style = PillStyle.Primary,
                    domain = AppDomain.Settings,
                    enabled = !state.busy,
                    testTag = "backup_restore_password_submit",
                )
            }

            BackupRestorePhase.Confirming -> SoftCard {
                val staged = state.staged
                Text(text = "最后一步 · 确认替换", style = AppType.Body, fontWeight = FontWeight.Bold, color = colors.text1)
                Text(
                    text = "验证已通过。包里装的是这些：",
                    style = AppType.Footnote,
                    color = colors.text2,
                )
                if (staged != null) {
                    BareRow(title = "记录", subtitle = "${staged.recordCount} 条", showChevron = false)
                    BareRow(
                        title = "音频文件",
                        subtitle = "${staged.mediaCount} 个",
                        showChevron = false,
                        topDivider = true,
                    )
                    BareRow(
                        title = "解压后体量",
                        subtitle = "约 ${staged.plaintextBytes / 1024} KB",
                        showChevron = false,
                        topDivider = true,
                    )
                }
                Text(
                    text = "继续将覆盖本机现有数据，且无法恢复为合并——旧数据不会保留。",
                    style = AppType.Body,
                    color = settingsPalette.deep,
                )
                PillButton(
                    text = "替换本机数据",
                    onClick = viewModel::onReplaceConfirmed,
                    modifier = Modifier.fillMaxWidth(),
                    style = PillStyle.Primary,
                    domain = AppDomain.Settings,
                    enabled = !state.busy,
                    testTag = "backup_restore_confirm_replace",
                )
                PillButton(
                    text = "取消",
                    onClick = { viewModel.cancel() },
                    modifier = Modifier.fillMaxWidth(),
                    style = PillStyle.Text,
                    domain = AppDomain.Settings,
                    testTag = "backup_restore_cancel",
                )
            }

            BackupRestorePhase.Restoring -> RestoringCard()

            BackupRestorePhase.Done -> Unit
        }

        // Task 25：私有错误提示条换共享 `InlineNotice`（棘轮「错误提示条」族摘行同批）。
        state.errorMessage?.let { InlineNotice(text = it, kind = InlineNoticeKind.Error) }
        state.resultMessage?.let {
            Text(text = it, style = AppType.Footnote, color = colors.text1, modifier = Modifier.testTag("backup_restore_result"))
        }

        // AC4-07（D-12）：替换提交后本进程数据库访问已终止，唯一出路是重启应用。
        if (state.appRestartRequired) {
            RestartNoticeCard(onRestart = viewModel::restartNow)
        }
    }
}

/**
 * 「导入中」卡：恢复是长任务，没有进度反馈用户会当成卡死。
 * 进度环取域 `deep`（Settings 域），墨取 `text1`——直落 M3 默认色在深套会黑压黑。
 */
@Composable
private fun RestoringCard() {
    val colors = appColors()
    SoftCard {
        CircularProgressIndicator(color = appDomain(AppDomain.Settings).deep)
        Text(
            text = "正在恢复，请勿关闭应用…",
            style = AppType.Body,
            color = colors.text1,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** AC4-07（D-12）：提交后本进程的数据访问到此为止，唯一出路是重启。 */
@Composable
private fun RestartNoticeCard(onRestart: () -> Unit) {
    val colors = appColors()
    SoftCard {
        Text(
            text = "恢复流程已结束，本进程的数据访问到此为止——请重启应用后继续使用。",
            style = AppType.Body,
            color = colors.text1,
            modifier = Modifier.testTag("backup_restore_restart_notice"),
        )
        PillButton(
            text = "立即重启应用",
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(),
            style = PillStyle.Primary,
            domain = AppDomain.Settings,
            testTag = "backup_restore_restart",
        )
    }
}

/** Live-camera-only QR box (decision D2). No gallery import, no manual entry. */
@Composable
private fun QrScanBox(
    modifier: Modifier,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPayload: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = appColors()

    LaunchedEffect(hasPermission) {
        if (!hasPermission) onRequestPermission()
    }

    if (!hasPermission) {
        Column(modifier.padding(16.dp)) {
            // 这两句原本是裸 `Text`（默认墨 = 黑），深套下等于压在深底上看不见。
            Text(
                text = "需要相机权限才能扫描配对码。若刚才拒绝过，请点下方按钮重新授权或在系统设置中开启。",
                style = AppType.Footnote,
                color = colors.text1,
            )
            Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 8.dp)) { Text("授权相机") }
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
