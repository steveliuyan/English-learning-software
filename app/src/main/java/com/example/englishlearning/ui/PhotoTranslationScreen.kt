package com.example.englishlearning.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.englishlearning.ai.AiFailureUiText
import com.example.englishlearning.language.domain.OcrEngineKind
import com.example.englishlearning.language.domain.OcrModelAssetInstallOutcome
import com.example.englishlearning.language.domain.OcrModelAssetStatus
import com.example.englishlearning.phototranslation.PhotoTranslationNotConfiguredReason
import com.example.englishlearning.ui.components.glass.GlassDialog
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint

/**
 * 拍照翻译功能页（AI 学 → 拍照翻译）。
 *
 * 三段红线在这里各自落位：
 * - **图片不出设备**：相机帧与相册字节只进入 [PhotoTranslationViewModel] 的临时工作区，
 *   识别完成/取消/失败任何出口都零残留（AC3-05）；页面本身不持有任何图片文件。
 * - **确认门每次都要过**：翻译按钮只触发状态机，域名与内容类别确认框由
 *   [PhotoTranslationUiState.pendingConfirmationHost] 弹出，确认前零字节出网（AC3-06）。
 * - **OCR ≠ 翻译**：识别问题（不可用/失败）与翻译问题（未配置/AI 失败）分卡片、分文案，
 *   永不混排（AC3-04）。
 */
internal const val CAMERA_DENIED_NOTICE =
    "相机权限被拒绝。可改用相册选图；如需拍照，请在系统设置中为本应用开启相机权限后返回重试。"

@Composable
fun PhotoTranslationScreen(
    state: PhotoTranslationUiState,
    onImageBytes: (ByteArray) -> Unit,
    onOcrTextEdited: (String) -> Unit,
    onTranslate: () -> Unit,
    onConfirmOutbound: (Boolean) -> Unit,
    onRefreshModelStatus: () -> Unit,
    onInstallModel: () -> Unit,
    onUninstallModel: () -> Unit,
    onOpenDictionary: (String) -> Unit,
    speakMessage: String?,
    onSpeak: (String) -> Unit,
    onSave: () -> Unit,
    onDeleteSaved: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showCamera by rememberSaveable { mutableStateOf(false) }
    var captureNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        captureNotice = if (!granted) CAMERA_DENIED_NOTICE else null
        showCamera = granted
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) {
                showCamera = false
                onImageBytes(bytes)
            } else {
                captureNotice = "无法读取所选图片，请换一张试试。"
            }
        }
    }

    LaunchedEffect(Unit) { onRefreshModelStatus() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("photo_translation_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(
            onClick = onBack,
            colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark),
            modifier = Modifier
                .testTag("photo_translation_back")
                .semantics { contentDescription = "返回 AI 学" },
        ) { Text("← 返回 AI 学", fontWeight = FontWeight.Bold) }

        Text("拍照翻译", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text("拍一页英文或从相册选图，先本机识别文字，校对后再交给 AI 翻译成中文。图片不会上传。", color = MintTextMuted)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    if (hasCameraPermission) {
                        showCamera = true
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
                enabled = !state.busy,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("photo_translation_camera_button")
                    .semantics { contentDescription = "拍照" },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MintPrimary,
                    contentColor = AppPalette.OnMintPrimary,
                    disabledContainerColor = MintSurface,
                    disabledContentColor = MintTextMuted,
                ),
            ) { Text("拍照", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = {
                    galleryPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = !state.busy,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("photo_translation_gallery_button")
                    .semantics { contentDescription = "从相册选图" },
                shape = RoundedCornerShape(16.dp),
            ) { Text("从相册选图") }
        }
        captureNotice?.let { message ->
            Text(message, color = MintTextMuted, modifier = Modifier.testTag("photo_translation_permission_hint"))
        }
        if (captureNotice == CAMERA_DENIED_NOTICE) {
            // D-1：提示必须兑现它承诺的出口——跳转系统设置为本应用开启相机权限。
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("photo_translation_permission_settings"),
                shape = RoundedCornerShape(16.dp),
            ) { Text("去系统设置开启相机权限") }
        }

        if (showCamera && hasCameraPermission) {
            CameraCaptureBox(
                visible = showCamera,
                onClose = { showCamera = false },
                onCaptured = { bytes ->
                    showCamera = false
                    onImageBytes(bytes)
                },
                onError = { reason -> captureNotice = "拍照失败：$reason" },
                modifier = Modifier.testTag("photo_translation_camera_box"),
            )
        }

        if (state.busy) {
            Text("正在本机识别…", color = MintTextMuted, modifier = Modifier.testTag("photo_translation_busy"))
        }

        when (val issue = state.ocrIssue) {
            null -> Unit
            PhotoTranslationOcrIssue.Unavailable -> OcrIssueCard(
                title = "OCR 不可用（不是翻译失败）",
                body = "本机文字识别暂不可用。可以安装下方离线备用模型，或稍后重试。",
                tag = "photo_translation_ocr_unavailable",
            )
            is PhotoTranslationOcrIssue.Failed -> OcrIssueCard(
                title = "识别失败",
                body = buildString {
                    append("未能从图片识别出文字，可重新拍照或换一张更清晰的图片。")
                    if (issue.reasons.isNotEmpty()) {
                        append("\n")
                        append(issue.reasons.entries.joinToString("；") { (engine, reason) -> "${engine.label}：${reason ?: "未知原因"}" })
                    }
                },
                tag = "photo_translation_ocr_failed",
            )
        }

        state.engine?.let { engine ->
            Text(
                "识别引擎：${engine.label}",
                style = MaterialTheme.typography.labelLarge,
                color = MintPrimary,
                modifier = Modifier.testTag("photo_translation_engine_label"),
            )
        }

        if (state.ocrText != null) {
            OutlinedTextField(
                value = state.ocrText,
                onValueChange = onOcrTextEdited,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("photo_translation_text_editor")
                    .semantics { contentDescription = "识别文本编辑" },
                label = { Text("识别出的文字（可直接编辑）") },
                minLines = 3,
                maxLines = 10,
                enabled = !state.busy,
            )
            if (state.lowConfidenceLineIndices.isNotEmpty()) {
                Text(
                    "以下行识别把握较低，请重点核对：${state.lowConfidenceLineIndices.sorted().joinToString("、") { "第 ${it + 1} 行" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MintPrimaryDark,
                    modifier = Modifier.testTag("photo_translation_low_confidence"),
                )
            }
            Button(
                onClick = onTranslate,
                enabled = !state.busy && state.ocrText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag("photo_translation_translate_button")
                    .semantics { contentDescription = "翻译文本" },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MintPrimary,
                    contentColor = AppPalette.OnMintPrimary,
                    disabledContainerColor = MintTint,
                    disabledContentColor = MintPrimaryDark,
                ),
            ) { Text("翻译成中文", fontWeight = FontWeight.Bold) }
            Text(
                "翻译会把编辑后的文本发送给 AI 服务并可能产生费用；图片不发送。",
                style = MaterialTheme.typography.bodySmall,
                color = MintTextMuted,
                modifier = Modifier.testTag("photo_translation_cost_hint"),
            )
        }

        when (val issue = state.translationIssue) {
            null -> Unit
            is PhotoTranslationFailureCategory.NotConfigured -> NotConfiguredCard(issue.reason)
            is PhotoTranslationFailureCategory.Ai -> FailureCard(issue.failure.uiText)
        }

        state.translation?.let { (source, target) ->
            TranslationCompareCard(
                source = source,
                target = target,
                onOpenDictionary = onOpenDictionary,
                speakMessage = speakMessage,
                onSpeak = onSpeak,
            )
            SaveRow(
                saved = state.lastSavedRecordId != null,
                saveIssue = state.saveIssue,
                onSave = onSave,
            )
        }

        if (state.history.isNotEmpty()) {
            SavedHistoryList(
                records = state.history,
                lastSavedRecordId = state.lastSavedRecordId,
                onDelete = onDeleteSaved,
            )
        }

        OfflineModelCard(
            status = state.modelStatus,
            progress = state.modelInstallProgress,
            issue = state.modelIssue,
            onInstall = onInstallModel,
            onUninstall = onUninstallModel,
        )

        Text(
            "隐私说明：本机识别（ML Kit）可能向 Google 发送与图片、文字内容无关的性能指标；识别结果与图片默认不保存，只有你点「保存到本机」才会留下记录。",
            style = MaterialTheme.typography.bodySmall,
            color = MintTextMuted,
            modifier = Modifier.testTag("photo_translation_privacy_note"),
        )
    }

    state.pendingConfirmationHost?.let { host ->
        OutboundConfirmationDialog(host, onConfirmOutbound)
    }
}

/** CameraX 取景 + 单拍。字节只在内存回调里交给状态机，不落地任何文件。 */
@Composable
private fun CameraCaptureBox(
    visible: Boolean,
    onClose: () -> Unit,
    onCaptured: (ByteArray) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    Column(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            factory = { viewContext ->
                val previewView = PreviewView(viewContext)
                val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                    } catch (_: Exception) {
                        onError("相机不可用（可能被占用或不存在），请改用相册选图。")
                    }
                }, ContextCompat.getMainExecutor(viewContext))
                previewView
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onClose, modifier = Modifier.testTag("photo_translation_camera_close")) { Text("取消") }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = {
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                // JPEG 单 plane：ImageProxy.image 是 android.media.Image，字节在 plane 0 的 buffer 里。
                                val buffer: java.nio.ByteBuffer? = image.image?.planes?.firstOrNull()?.buffer
                                val bytes = buffer?.let { source ->
                                    ByteArray(source.remaining()).also { target -> source.get(target) }
                                }
                                image.close()
                                if (bytes != null) onCaptured(bytes) else onError("相机没有返回图像数据，请重试。")
                            }

                            override fun onError(exception: ImageCaptureException) {
                                onError(exception.message ?: "拍照失败")
                            }
                        },
                    )
                },
                enabled = visible,
                modifier = Modifier.testTag("photo_translation_shutter_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = AppPalette.OnMintPrimary),
            ) { Text("快门 · 拍照", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun OcrIssueCard(
    title: String,
    body: String,
    tag: String,
) {
    IssueCard(tag = tag) {
        Text(title, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text(body, color = MintTextMuted)
    }
}

@Composable
private fun TranslationCompareCard(
    source: String,
    target: String,
    onOpenDictionary: (String) -> Unit,
    speakMessage: String?,
    onSpeak: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("photo_translation_result"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("原文", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MintPrimary)
            Text(source, color = MintPrimaryDark)
            Text("译文", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MintPrimary)
            Text(target, color = MintPrimaryDark)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(target)) },
                    modifier = Modifier.testTag("photo_translation_copy_button"),
                ) { Text("复制译文", color = MintPrimaryDark, fontWeight = FontWeight.Bold) }
                TextButton(
                    onClick = { onSpeak(source) },
                    modifier = Modifier.testTag("photo_translation_speak_button"),
                ) { Text("朗读原文", color = MintPrimaryDark, fontWeight = FontWeight.Bold) }
            }
            speakMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MintTextMuted,
                    modifier = Modifier.testTag("photo_translation_speak_feedback"),
                )
            }

            val words = remember(source) {
                Regex("[A-Za-z][A-Za-z'-]+").findAll(source).map { it.value }.distinct().take(30).toList()
            }
            if (words.isNotEmpty()) {
                Text("点词查词典", style = MaterialTheme.typography.labelMedium, color = MintTextMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    words.forEachIndexed { index, word ->
                        TextButton(
                            onClick = { onOpenDictionary(word) },
                            modifier = Modifier.testTag("photo_translation_word_$index"),
                        ) { Text(word, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

/** 明确的保存动作：只在这一步把原图 + 文本 + 译文写入本机（AC3-05「图片默认不保存」）。 */
@Composable
private fun SaveRow(
    saved: Boolean,
    saveIssue: com.example.englishlearning.core.error.AppError?,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onSave,
            enabled = !saved,
            modifier = Modifier
                .testTag("photo_translation_save_button")
                .semantics { contentDescription = if (saved) "已保存到本机" else "保存到本机" },
            shape = RoundedCornerShape(14.dp),
        ) { Text(if (saved) "已保存" else "保存到本机", fontWeight = FontWeight.Bold) }
        when {
            saved -> Text("原图与译文已存到本机，可在备份中勾选导出。", style = MaterialTheme.typography.bodySmall, color = MintPrimaryDark)
            saveIssue != null -> Text(
                "保存失败：本机存储不可用，请重试。",
                style = MaterialTheme.typography.bodySmall,
                color = MintPrimaryDark,
                modifier = Modifier.testTag("photo_translation_save_issue"),
            )
        }
    }
}

/** 已明确保存的历史：只读列表 + 逐条删除（删除会连带清掉保存的原图）。 */
@Composable
private fun SavedHistoryList(
    records: List<com.example.englishlearning.phototranslation.SavedPhotoTranslation>,
    lastSavedRecordId: String?,
    onDelete: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("photo_translation_history_card"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("已保存的翻译", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            records.take(3).forEach { record ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .testTag("photo_translation_history_${record.recordId}"),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        buildString {
                            append(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                                .format(java.util.Date(record.createdAtEpochMillis)))
                            append(" · ")
                            append(if (record.imageAssetId != null) "含原图" else "仅文本")
                            if (record.recordId == lastSavedRecordId) append(" · 刚刚保存")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MintTextMuted,
                    )
                    Text(record.sourceText, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(record.translatedText, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MintPrimaryDark)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { onDelete(record.recordId) },
                            modifier = Modifier.testTag("photo_translation_history_delete_${record.recordId}"),
                        ) { Text("删除", color = MintPrimaryDark) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineModelCard(
    status: OcrModelAssetStatus?,
    progress: Float?,
    issue: OcrModelAssetInstallOutcome?,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("photo_translation_model_card"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("离线备用识别模型", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            Text(
                "Tesseract 英文模型（约 4.1 MB，Apache-2.0 许可），仅在主识别不可用时使用；可随时删除。",
                style = MaterialTheme.typography.bodySmall,
                color = MintTextMuted,
            )
            when (status) {
                null -> Text("状态加载中…", style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
                OcrModelAssetStatus.NotInstalled -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onInstall,
                        enabled = progress == null,
                        modifier = Modifier.testTag("photo_translation_model_install"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = AppPalette.OnMintPrimary),
                    ) { Text(if (progress == null) "下载模型（约 4.1 MB）" else "下载中…", fontWeight = FontWeight.Bold) }
                }
                is OcrModelAssetStatus.Installed -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("已安装（${status.sizeBytes} 字节）", style = MaterialTheme.typography.bodySmall, color = MintPrimaryDark)
                    OutlinedButton(
                        onClick = onUninstall,
                        modifier = Modifier.testTag("photo_translation_model_uninstall"),
                    ) { Text("删除") }
                }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("photo_translation_model_progress"),
                )
            }
            when (issue) {
                null, is OcrModelAssetInstallOutcome.Installed -> Unit
                OcrModelAssetInstallOutcome.IntegrityMismatch -> Text(
                    "模型校验失败：大小或哈希与官方发布不符，已丢弃，未保留任何文件。请重试下载。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MintPrimaryDark,
                    modifier = Modifier.testTag("photo_translation_model_issue"),
                )
                is OcrModelAssetInstallOutcome.DownloadFailed -> Text(
                    "下载失败：${issue.reason ?: "网络不可用"}。检查网络后重试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MintPrimaryDark,
                    modifier = Modifier.testTag("photo_translation_model_issue"),
                )
            }
        }
    }
}

@Composable
private fun OutboundConfirmationDialog(host: String, onConfirmOutbound: (Boolean) -> Unit) {
    GlassDialog(onDismiss = { onConfirmOutbound(false) }) {
        Text("确认发送请求", style = AppType.Headline)
        Text(
            "你的识别文本（编辑后的版本）与该服务的密钥将发送给 $host，并可能产生费用。图片不会发送。",
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("photo_translation_outbound_confirmation_text"),
            style = AppType.Body,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PillButton(
                text = "取消",
                onClick = { onConfirmOutbound(false) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Secondary,
                accent = DomainColors.Learn,
                testTag = "photo_translation_decline_outbound",
            )
            PillButton(
                text = "确认发送",
                onClick = { onConfirmOutbound(true) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Primary,
                accent = DomainColors.Learn,
                testTag = "photo_translation_confirm_outbound",
            )
        }
    }
}

@Composable
private fun NotConfiguredCard(reason: PhotoTranslationNotConfiguredReason) {
    val message = when (reason) {
        PhotoTranslationNotConfiguredReason.NoDefaultProfile ->
            "还没有默认的 AI 服务，先去「设置 · AI」添加一套并设为默认。"
        PhotoTranslationNotConfiguredReason.DefaultProfileUnavailable ->
            "默认 AI 服务不可用或缺少密钥，去「设置 · AI」检查。"
        PhotoTranslationNotConfiguredReason.InvalidEndpoint ->
            "AI 服务的地址无效，去「设置 · AI」修正后再试。"
    }
    IssueCard(tag = "photo_translation_not_configured") {
        Text(
            message,
            color = MintPrimaryDark,
            modifier = Modifier.semantics { contentDescription = "AI 未配置提示" },
        )
    }
}

@Composable
private fun FailureCard(uiText: AiFailureUiText) {
    IssueCard(tag = "photo_translation_failure") {
        Text(
            uiText.message,
            color = MintPrimaryDark,
            modifier = Modifier.semantics { contentDescription = "翻译失败原因" },
        )
    }
}

@Composable
private fun IssueCard(
    tag: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag(tag),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

private val OcrEngineKind.label: String
    get() = when (this) {
        OcrEngineKind.MlKit -> "ML Kit（本机）"
        OcrEngineKind.Tesseract -> "Tesseract（离线备用）"
    }
