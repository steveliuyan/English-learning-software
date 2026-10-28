package com.example.englishlearning.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.englishlearning.backup.device.AndroidMigrationKey
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * F4-04 旧设备受保护导出页：系统认证（BiometricPrompt，含凭据降级）→ 一次性挑战
 * 二维码 → 可选密码 → 受保护导出。本页不存在任何“跳过认证”入口；密码遗忘不可
 * 恢复为固定文案。
 *
 * 批 5 Task 7 按 spec §5「认证导出」行重排版式，认证门控与配对码语义一字不改
 * （D-9 认证紧邻动作、D-10 像素网格 QR、D-11 完成态经再次认证重显同一码、AC4-05 一次性）：
 *  - **页头**换共享 [AppScreenHeader]（旧的两处自写 `TextButton("返回")` + `22.sp` 手搓标题下线）；
 *  - **三段骨架**：认证 → 配对与口令 → 保存并配对，每段一张 [SoftCard]、一枚主键，
 *    旧形态是五枚 M3 `Button` 平铺在一条流里；页级动作一律走 `PillButton`；
 *  - **遗忘警告只讲一次**：合进口令区上方那一句（旧形态在引导句与输入框标签各讲一遍）；
 *  - **口令掩码**：`visualTransformation = PasswordVisualTransformation()`（与恢复屏同口径）；
 *  - **两分支补滚动**：旧降级分支没有滚动壳。
 */
@Composable
fun BackupPairingExportScreen(
    onBack: () -> Unit,
    viewModel: BackupProtectedExportViewModel? = null,
) {
    val colors = appColors()
    // 屏锚挂在这枚唯一的根盒上：两分支共用同一个 `backup_pairing_export_screen`。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("backup_pairing_export_screen"),
    ) {
        if (viewModel == null) {
            UnavailableContent(onBack = onBack)
        } else {
            PairingExportContent(viewModel = viewModel, onBack = onBack)
        }
    }
}

/** `viewModel == null` 的降级分支：只读提示，零假入口（没有认证键、没有导出键）。 */
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
        AppScreenHeader(title = "认证导出", onBack = onBack)
        SoftCard {
            Text(text = "受保护导出功能暂不可用", style = AppType.Body, color = colors.text1)
            Text(
                text = "导出服务还没准备好。返回上一页再进来，通常就恢复了。",
                style = AppType.Footnote,
                color = colors.text2,
            )
        }
    }
}

@Composable
private fun PairingExportContent(
    viewModel: BackupProtectedExportViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val colors = appColors()
    val settingsPalette = appDomain(AppDomain.Settings)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        viewModel.onDocumentPicked(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = "认证导出",
            subtitle = "配对码 30 分钟内有效、只能用一次；只在新设备的「从旧设备恢复」里可用。",
            onBack = onBack,
        )

        SoftCard {
            Text(text = "这一步会发生什么", style = AppType.Body, fontWeight = FontWeight.Bold, color = colors.text1)
            Text(
                text = "先过系统认证（锁屏 / 指纹），再签发一次性配对码；码过期或已用过就得重新来一遍。",
                style = AppType.Footnote,
                color = colors.text2,
            )
        }

        when (state.phase) {
            ProtectedExportPhase.Idle -> SoftCard {
                Text(text = "第一步 · 系统认证", style = AppType.Body, fontWeight = FontWeight.Bold, color = colors.text1)
                Text(
                    text = "认证成功后 30 秒内完成下一步动作，超时要重新认证。",
                    style = AppType.Footnote,
                    color = colors.text2,
                )
                PillButton(
                    text = "开始（需系统认证）",
                    onClick = {
                        if (AndroidMigrationKey.ensureKey()) {
                            runWithMigrationAuth(
                                context,
                                onRejected = { viewModel.onAuthenticationRejected() },
                            ) { viewModel.onAuthenticated() }
                        } else {
                            viewModel.onMigrationKeyUnavailable()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = PillStyle.Primary,
                    domain = AppDomain.Settings,
                    testTag = "backup_pairing_export_authenticate",
                )
            }

            ProtectedExportPhase.Authenticating -> SoftCard {
                CircularProgressIndicator(color = settingsPalette.deep)
                Text(
                    text = "正在等待系统认证…",
                    style = AppType.Body,
                    color = colors.text1,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            ProtectedExportPhase.Active -> SoftCard {
                Text(text = "第二步 · 配对与口令", style = AppType.Body, fontWeight = FontWeight.Bold, color = colors.text1)
                if (state.qrVisible) {
                    state.qrPayload?.let { payload ->
                        QrCodeDisplay(payload)
                        Text(
                            text = "请在新设备上打开「从旧设备恢复」并实时扫描此码。",
                            style = AppType.Footnote,
                            color = colors.text2,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                var usePassword by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = usePassword,
                        onCheckedChange = {
                            usePassword = it
                            viewModel.setUsePassword(it)
                        },
                    )
                    Text(text = "设置备份密码（10–128 字符）", style = AppType.Body, color = colors.text1)
                }

                // 遗忘警告只在这里讲一次（旧形态在引导句和输入框标签各讲一遍）。
                Text(
                    text = "密码遗忘则备份无法恢复：没有找回途径，这是硬件级加密的底线，请先记牢再导出。",
                    style = AppType.Footnote,
                    color = colors.text2,
                )

                var password by remember { mutableStateOf("") }
                if (usePassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("备份密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_pairing_export_password"),
                    )
                }

                PillButton(
                    text = "导出受保护备份包",
                    onClick = {
                        val chars = if (usePassword) password.toCharArray() else null
                        // D-9：签名密钥的 30 秒认证窗口——认证必须紧邻导出动作，成功回调内立即签名
                        runWithMigrationAuth(
                            context,
                            onRejected = { viewModel.onAuthenticationRejected() },
                        ) { viewModel.exportProtected(includeMediaAssets = true, password = chars) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = PillStyle.Primary,
                    domain = AppDomain.Settings,
                    enabled = !state.busy,
                    testTag = "backup_pairing_export_export",
                )
            }

            ProtectedExportPhase.Exporting -> SoftCard {
                CircularProgressIndicator(color = settingsPalette.deep)
                Text(
                    text = "正在签名并生成备份包，请勿关闭应用…",
                    style = AppType.Body,
                    color = colors.text1,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            ProtectedExportPhase.Done -> SoftCard {
                Text(text = "第三步 · 保存并配对", style = AppType.Body, fontWeight = FontWeight.Bold, color = colors.text1)
                state.message?.let {
                    Text(text = it, style = AppType.Footnote, color = colors.text1)
                }
                if (state.packageReady) {
                    state.fileName?.let { name ->
                        Text(
                            text = "文件名：$name",
                            style = AppType.Label,
                            color = colors.text2,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    PillButton(
                        text = "选择保存位置并导出",
                        onClick = { launcher.launch(state.fileName ?: "backup-protected.elpbak") },
                        modifier = Modifier.fillMaxWidth(),
                        style = PillStyle.Primary,
                        domain = AppDomain.Settings,
                        testTag = "backup_pairing_export_save",
                    )
                }
                // D-11（AC4-08）：包落盘后至配对有效期内，经再次认证重显同一配对码。
                state.qrPayload?.let { payload ->
                    if (state.qrVisible) {
                        QrCodeDisplay(payload)
                        Text(
                            text = "以上是本次会话签发的同一个配对码，签发后 30 分钟内有效；请在新设备实时扫描完成配对。",
                            style = AppType.Footnote,
                            color = colors.text2,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        PillButton(
                            text = "显示配对码（需再次认证）",
                            onClick = {
                                runWithMigrationAuth(
                                    context,
                                    // 重显被拒：仅保持隐藏，不作废本次配对会话
                                    onRejected = { },
                                ) { viewModel.onPairingRevealed() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            style = PillStyle.Secondary,
                            domain = AppDomain.Settings,
                            testTag = "backup_pairing_export_reveal_qr",
                        )
                    }
                }
            }
        }

        // Task 25：私有错误提示条换共享 `InlineNotice`（棘轮「错误提示条」族摘行同批）。
        state.error?.let { InlineNotice(text = it, kind = InlineNoticeKind.Error) }
    }
}

private fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onRejected: () -> Unit,
) {
    val manager = BiometricManager.from(activity)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onRejected()
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

/** D-9：每次敏感动作（签发、导出签名、重显配对码）各自要求一次系统认证。 */
private fun runWithMigrationAuth(
    context: android.content.Context,
    onRejected: () -> Unit,
    onSuccess: () -> Unit,
) {
    val activity = context as? FragmentActivity
    if (activity == null) {
        onRejected()
        return
    }
    showBiometricPrompt(activity, onSuccess = onSuccess, onRejected = onRejected)
}

/** QR matrix via the already-registered zxing-core dependency, drawn as a square pixel grid (D-10). */
@Composable
private fun QrCodeDisplay(payload: String) {
    val colors = appColors()
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
        // 失败提示原本是裸 `Text`（默认墨 = 黑），深套下压在深底上看不见。
        Text(text = "二维码生成失败（配对码过长）。", style = AppType.Footnote, color = colors.text1)
        return
    }
    val modules = matrix.width + QR_QUIET_MODULES * 2
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag("backup_pairing_export_qr"),
    ) {
        val moduleSize = size.minDimension / modules
        drawRect(QrBackgroundColor)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) {
                    drawRect(
                        QrForegroundColor,
                        Offset((x + QR_QUIET_MODULES) * moduleSize, (y + QR_QUIET_MODULES) * moduleSize),
                        Size(moduleSize, moduleSize),
                    )
                }
            }
        }
    }
}

private const val QR_QUIET_MODULES = 4
private val QrBackgroundColor = androidx.compose.ui.graphics.Color.White
private val QrForegroundColor = androidx.compose.ui.graphics.Color.Black
