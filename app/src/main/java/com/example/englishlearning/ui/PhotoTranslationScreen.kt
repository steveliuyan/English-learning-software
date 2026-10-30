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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.englishlearning.ai.AiFailureUiText
import com.example.englishlearning.language.domain.OcrEngineKind
import com.example.englishlearning.language.domain.OcrModelAssetInstallOutcome
import com.example.englishlearning.language.domain.OcrModelAssetStatus
import com.example.englishlearning.phototranslation.PhotoTranslationNotConfiguredReason
import com.example.englishlearning.phototranslation.SavedPhotoTranslation
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.EntryIconTone
import com.example.englishlearning.ui.components.EntryRow
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.NotConfiguredCard
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.GlassDialog
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * 拍照翻译功能页（AI 助学 → 拍照翻译）。
 *
 * 三段红线在这里各自落位：
 * - **图片不出设备**：相机帧与相册字节只进入 [PhotoTranslationViewModel] 的临时工作区，
 *   识别完成/取消/失败任何出口都零残留（AC3-05）；页面本身不持有任何图片文件。
 * - **确认门每次都要过**：翻译按钮只触发状态机，域名与内容类别确认框由
 *   [PhotoTranslationUiState.pendingConfirmationHost] 弹出，确认前零字节出网（AC3-06）。
 * - **OCR ≠ 翻译**：识别问题（不可用/失败）与翻译问题（未配置/AI 失败）分卡片、分文案，
 *   永不混排（AC3-04）。
 *
 * 批 4 Task 4 的版式重做（spec §5「拍照翻译」行）：这一屏原来是一根 760 行长滚动，五块说明性容器
 * （已保存历史 / 离线备用模型 / 隐私说明 / 成本句 / 引擎标签）平铺在同一条流里，历史「删除」点了就执行。
 * 现在：
 *  - 页头交给共享 [AppScreenHeader]（旧「← 返回 AI 学」药片下线，返回位是固定可及名「返回」）；
 *  - **5 块收成历史一行 + 离线模型折叠**：两块各变成一枚 [EntryRow] 折叠行、默认收起，
 *    隐私说明搬进模型展开体；成本句只贴在翻译按钮之后一次，引擎标签全局只留一处；
 *  - 历史删除只发 [onRequestDeleteSaved]，确认框归宿主 `AppScreen` 的 `ConfirmDestructiveDialog`
 *    （屏内零破坏性对话框，判据见 `PhotoTranslationLayoutContractTest` 与
 *    `AppScreenOverlayContainerContractTest`）；
 *  - 容器走三档共享件（[SoftCard] / [EntryRow]），按钮一律 [PillButton]，排印走六档 [AppType]，
 *    墨色走 `appColors()` / `appDomain(AppDomain.AiSpeech)`——本文件 `Mint*` 读点为零。
 *
 * 数据通路、tag、校验文案与出站确认三要素一字不动：这一屏只搬外观与确认路径。
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
    onRequestDeleteSaved: (String) -> Unit,
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = appColors()
    var showCamera by rememberSaveable { mutableStateOf(false) }
    var captureNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var modelExpanded by rememberSaveable { mutableStateOf(false) }
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
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("photo_translation_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = "拍照翻译",
            subtitle = "拍一页英文或从相册选图，先本机识别文字，校对后再交给 AI 翻译成中文。图片不会上传。",
            onBack = onBack,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PillButton(
                text = "拍照",
                onClick = {
                    if (hasCameraPermission) {
                        showCamera = true
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.weight(1f),
                style = PillStyle.Primary,
                enabled = !state.busy,
                testTag = "photo_translation_camera_button",
                contentDescription = "拍照",
            )
            PillButton(
                text = "从相册选图",
                onClick = {
                    galleryPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.weight(1f),
                style = PillStyle.Secondary,
                enabled = !state.busy,
                testTag = "photo_translation_gallery_button",
                contentDescription = "从相册选图",
            )
        }

        captureNotice?.let { message ->
            Text(
                text = message,
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("photo_translation_permission_hint"),
            )
        }
        if (captureNotice == CAMERA_DENIED_NOTICE) {
            // D-1：提示必须兑现它承诺的出口——跳转系统设置为本应用开启相机权限。
            PillButton(
                text = "去系统设置开启相机权限",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                style = PillStyle.Secondary,
                testTag = "photo_translation_permission_settings",
            )
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
            Text(
                text = "正在本机识别…",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("photo_translation_busy"),
            )
        }

        when (val issue = state.ocrIssue) {
            null -> Unit
            PhotoTranslationOcrIssue.Unavailable -> OcrIssueCard(
                title = "OCR 不可用（不是翻译失败）",
                body = "本机文字识别暂不可用。可展开下方的「离线备用识别模型」安装备用引擎，或稍后重试。",
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
                text = "识别引擎：${engine.label}",
                style = AppType.Footnote,
                color = colors.text2,
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
                shape = RoundedCornerShape(14.dp),
            )
            if (state.lowConfidenceLineIndices.isNotEmpty()) {
                Text(
                    text = "以下行识别把握较低，请重点核对：${state.lowConfidenceLineIndices.sorted().joinToString("、") { "第 ${it + 1} 行" }}",
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.testTag("photo_translation_low_confidence"),
                )
            }
            // 原型 `sAiPhoto` 的页内提示：编辑框之后、翻译键之前——先看到字，再被提醒校对，
            // 翻完再提醒就晚了（识别错一个词，译文会差很远，而译文按次计费）。
            InlineNotice(
                text = "先校对文字再翻译，识别错一个词，译文会差很远。",
                kind = InlineNoticeKind.Info,
                modifier = Modifier.testTag("photo_translation_proofread_notice"),
            )
            PillButton(
                text = "翻译成中文",
                onClick = onTranslate,
                modifier = Modifier.fillMaxWidth(),
                style = PillStyle.Primary,
                enabled = !state.busy && state.ocrText.isNotBlank(),
                testTag = "photo_translation_translate_button",
                contentDescription = "翻译文本",
            )
            Text(
                text = "翻译会把编辑后的文本发送给 AI 服务并可能产生费用；图片不发送。",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("photo_translation_cost_hint"),
            )
        }

        when (val issue = state.translationIssue) {
            null -> Unit
            is PhotoTranslationFailureCategory.NotConfigured -> NotConfiguredCard(
                featureLabel = "拍照翻译",
                onConfigure = onOpenAiSettings,
                modifier = Modifier.testTag("photo_translation_not_configured"),
                detail = issue.reason.guidance(),
            )
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

        // 历史：常驻一枚折叠入口行，展开才渲染记录（旧「已保存的翻译」整卡下线）。
        EntryRow(
            icon = AppIcon.Clock,
            domain = AppDomain.AiSpeech,
            title = "已保存的翻译",
            subtitle = if (state.history.isEmpty()) {
                "还没有保存的记录：翻译后点「保存到本机」才会留下一条"
            } else {
                "共 ${state.history.size} 条 · 只在本机，删除前会确认"
            },
            trailing = if (historyExpanded) "收起" else "展开",
            expanded = historyExpanded,
            iconTone = EntryIconTone.Tint,
            modifier = Modifier.testTag("photo_translation_history_entry"),
            onClick = { historyExpanded = !historyExpanded },
        )
        if (historyExpanded) {
            SavedHistoryList(
                records = state.history,
                lastSavedRecordId = state.lastSavedRecordId,
                onRequestDeleteSaved = onRequestDeleteSaved,
            )
        }

        // 离线备用模型：第二枚折叠入口行，隐私说明跟着它一起收起。
        EntryRow(
            icon = AppIcon.Download,
            domain = AppDomain.AiSpeech,
            title = "离线备用识别模型",
            subtitle = modelSubtitle(state.modelStatus),
            trailing = if (modelExpanded) "收起" else "展开",
            expanded = modelExpanded,
            iconTone = EntryIconTone.Tint,
            modifier = Modifier.testTag("photo_translation_model_entry"),
            onClick = { modelExpanded = !modelExpanded },
        )
        if (modelExpanded) {
            OfflineModelCard(
                status = state.modelStatus,
                progress = state.modelInstallProgress,
                issue = state.modelIssue,
                onInstall = onInstallModel,
                onUninstall = onUninstallModel,
            )
            Text(
                text = "隐私说明：本机识别（ML Kit）可能向 Google 发送与图片、文字内容无关的性能指标；" +
                    "识别结果与图片默认不保存，只有你点「保存到本机」才会留下记录。",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("photo_translation_privacy_note"),
            )
        }
    }

    state.pendingConfirmationHost?.let { host ->
        OutboundConfirmationDialog(host, onConfirmOutbound)
    }
}

/** 折叠行副标题：模型状态一句话说完（不展开也看得出「装没装」）。 */
private fun modelSubtitle(status: OcrModelAssetStatus?): String = when (status) {
    null -> "状态加载中…（约 4.1 MB，仅在主识别不可用时使用）"
    OcrModelAssetStatus.NotInstalled -> "未安装 · 约 4.1 MB，仅在主识别不可用时使用"
    is OcrModelAssetStatus.Installed -> "已安装 · 主识别不可用时自动接管"
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
            PillButton(
                text = "取消",
                onClick = onClose,
                style = PillStyle.Secondary,
                testTag = "photo_translation_camera_close",
            )
            Spacer(Modifier.width(4.dp))
            PillButton(
                text = "快门 · 拍照",
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
                modifier = Modifier.weight(1f),
                style = PillStyle.Primary,
                enabled = visible,
                testTag = "photo_translation_shutter_button",
            )
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
        Text(text = title, style = AppType.Body, color = appColors().text1)
        Text(text = body, style = AppType.Footnote, color = appColors().text2)
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
    val colors = appColors()
    val accent = appDomain(AppDomain.AiSpeech).deep
    val clipboard = LocalClipboardManager.current
    SoftCard(modifier = Modifier.testTag("photo_translation_result")) {
        Text(text = "原文", style = AppType.Label, color = accent)
        Text(text = source, style = AppType.Body, color = colors.text1)
        Text(text = "译文", style = AppType.Label, color = accent)
        Text(text = target, style = AppType.Body, color = colors.text1)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton(
                text = "复制译文",
                onClick = { clipboard.setText(AnnotatedString(target)) },
                style = PillStyle.Text,
                testTag = "photo_translation_copy_button",
            )
            PillButton(
                text = "朗读原文",
                onClick = { onSpeak(source) },
                style = PillStyle.Text,
                testTag = "photo_translation_speak_button",
            )
        }
        speakMessage?.let { message ->
            Text(
                text = message,
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("photo_translation_speak_feedback"),
            )
        }

        val words = remember(source) {
            Regex("[A-Za-z][A-Za-z'-]+").findAll(source).map { it.value }.distinct().take(30).toList()
        }
        if (words.isNotEmpty()) {
            Text(text = "点词查词典", style = AppType.Label, color = colors.text2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                words.forEachIndexed { index, word ->
                    PillButton(
                        text = word,
                        onClick = { onOpenDictionary(word) },
                        style = PillStyle.Text,
                        testTag = "photo_translation_word_$index",
                    )
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
    val colors = appColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(
            text = if (saved) "已保存" else "保存到本机",
            onClick = onSave,
            style = PillStyle.Secondary,
            enabled = !saved,
            testTag = "photo_translation_save_button",
            contentDescription = if (saved) "已保存到本机" else "保存到本机",
        )
        when {
            saved -> Text(
                text = "原图与译文已存到本机，可在备份中勾选导出。",
                style = AppType.Footnote,
                color = colors.text2,
            )
            saveIssue != null -> Text(
                text = "保存失败：本机存储不可用，请重试。",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("photo_translation_save_issue"),
            )
        }
    }
}

/** 已明确保存的历史：只读列表 + 逐条删除（删除只发请求，宿主确认后才连带清掉保存的原图）。 */
@Composable
private fun SavedHistoryList(
    records: List<SavedPhotoTranslation>,
    lastSavedRecordId: String?,
    onRequestDeleteSaved: (String) -> Unit,
) {
    val colors = appColors()
    SoftCard(modifier = Modifier.testTag("photo_translation_history_list")) {
        if (records.isEmpty()) {
            Text(
                text = "还没有保存的记录。",
                style = AppType.Footnote,
                color = colors.text2,
            )
        }
        records.take(3).forEach { record ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("photo_translation_history_${record.recordId}"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = buildString {
                        append(
                            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                                .format(java.util.Date(record.createdAtEpochMillis)),
                        )
                        append(" · ")
                        append(if (record.imageAssetId != null) "含原图" else "仅文本")
                        if (record.recordId == lastSavedRecordId) append(" · 刚刚保存")
                    },
                    style = AppType.Footnote,
                    color = colors.text2,
                )
                Text(
                    text = record.sourceText,
                    style = AppType.Body,
                    color = colors.text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = record.translatedText,
                    style = AppType.Footnote,
                    color = colors.text2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                PillButton(
                    text = "删除",
                    onClick = { onRequestDeleteSaved(record.recordId) },
                    style = PillStyle.Text,
                    testTag = "photo_translation_history_delete_${record.recordId}",
                    contentDescription = "删除这条翻译记录",
                )
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
    val colors = appColors()
    val accent = appDomain(AppDomain.AiSpeech).deep
    val issueText: String? = when (issue) {
        null, is OcrModelAssetInstallOutcome.Installed -> null
        OcrModelAssetInstallOutcome.IntegrityMismatch ->
            "模型校验失败：大小或哈希与官方发布不符，已丢弃，未保留任何文件。请重试下载。"
        is OcrModelAssetInstallOutcome.DownloadFailed ->
            "下载失败：${issue.reason ?: "网络不可用"}。检查网络后重试。"
    }
    SoftCard(modifier = Modifier.testTag("photo_translation_model_card")) {
        Text(
            text = "Tesseract 英文模型（约 4.1 MB，Apache-2.0 许可），仅在主识别不可用时使用；可随时删除。",
            style = AppType.Footnote,
            color = colors.text2,
        )
        when (status) {
            null -> Text(text = "状态加载中…", style = AppType.Footnote, color = colors.text2)
            OcrModelAssetStatus.NotInstalled -> PillButton(
                text = if (progress == null) "下载模型（约 4.1 MB）" else "下载中…",
                onClick = onInstall,
                style = PillStyle.Primary,
                enabled = progress == null,
                testTag = "photo_translation_model_install",
            )
            is OcrModelAssetStatus.Installed -> Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已安装（${status.sizeBytes} 字节）",
                    style = AppType.Footnote,
                    color = accent,
                )
                PillButton(
                    text = "删除",
                    onClick = onUninstall,
                    style = PillStyle.Text,
                    testTag = "photo_translation_model_uninstall",
                )
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
        issueText?.let { message ->
            Text(
                text = message,
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("photo_translation_model_issue"),
            )
        }
    }
}

@Composable
private fun OutboundConfirmationDialog(host: String, onConfirmOutbound: (Boolean) -> Unit) {
    GlassDialog(onDismiss = { onConfirmOutbound(false) }) {
        Text("确认发送请求", style = AppType.Headline, color = appColors().text1)
        Text(
            "你的识别文本（编辑后的版本）与该服务的密钥将发送给 $host，并可能产生费用。图片不会发送。",
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("photo_translation_outbound_confirmation_text"),
            style = AppType.Body,
            color = appColors().text1,
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
                domain = AppDomain.AiSpeech,
                testTag = "photo_translation_decline_outbound",
            )
            PillButton(
                text = "确认发送",
                onClick = { onConfirmOutbound(true) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Primary,
                domain = AppDomain.AiSpeech,
                testTag = "photo_translation_confirm_outbound",
            )
        }
    }
}

/**
 * 各拒绝原因对应的**补路指引**（卡的第二行）；第一行「一句现状」由共享 `NotConfiguredCard` 统一措辞。
 *
 * Task 26 Step 3 删掉了本文件同名的私有 `NotConfiguredCard`：旧卡只有一段文字、**没有出口**
 * （spec §3 点名的死屏）；出口现在由共享件提供并接到 `onOpenAiSettings`。
 */
private fun PhotoTranslationNotConfiguredReason.guidance(): String = when (this) {
    PhotoTranslationNotConfiguredReason.NoDefaultProfile ->
        "去「设置 · AI」添加一套并设为默认。"
    PhotoTranslationNotConfiguredReason.DefaultProfileUnavailable ->
        "去「设置 · AI」检查默认服务或补上密钥。"
    PhotoTranslationNotConfiguredReason.InvalidEndpoint ->
        "去「设置 · AI」修正服务地址后再试。"
}

@Composable
private fun FailureCard(uiText: AiFailureUiText) {
    IssueCard(tag = "photo_translation_failure") {
        Text(
            text = uiText.message,
            style = AppType.Body,
            color = appColors().text1,
            modifier = Modifier.semantics { contentDescription = "翻译失败原因" },
        )
    }
}

@Composable
private fun IssueCard(
    tag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SoftCard(modifier = Modifier.testTag(tag)) { content() }
}

private val OcrEngineKind.label: String
    get() = when (this) {
        OcrEngineKind.MlKit -> "ML Kit（本机）"
        OcrEngineKind.Tesseract -> "Tesseract（离线备用）"
    }
