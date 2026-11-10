package com.example.englishlearning.ui

import android.Manifest
import android.graphics.Bitmap
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.language.domain.OcrEngineKind
import com.example.englishlearning.language.domain.OcrModelAssetInstallOutcome
import com.example.englishlearning.language.domain.OcrModelAssetStatus
import com.example.englishlearning.export.PhotoOverlayShareLauncher
import com.example.englishlearning.phototranslation.OverlayBlockSource
import com.example.englishlearning.phototranslation.PhotoTranslationNotConfiguredReason
import com.example.englishlearning.phototranslation.SavedPhotoTranslation
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.EntryIconTone
import com.example.englishlearning.ui.components.EntryRow
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.NotConfiguredCard
import com.example.englishlearning.ui.components.OutboundConfirmDialog
import com.example.englishlearning.ui.components.RequestInProgress
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppMotion
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.screentranslation.ScreenTranslationSnapshot
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /**
     * 取消正在等待的翻译（批 C-4）。显式接线、不给默认空实现：默认 `{}` 等于允许宿主漏接而
     * 界面照旧画出一枚按了没反应的取消键。取消只收回等待，识别出的文本留在编辑框里可改可重试。
     */
    onCancelTranslate: () -> Unit,
    /** 原位覆盖（F3-04）：被选中的块要不要发出去翻、翻完什么时候画，都由 VM 决定，屏侧只报意愿。 */
    onOverlayTranslate: () -> Unit,
    onOverlayConfirmOutbound: (Boolean) -> Unit,
    onToggleOverlayBlock: (Int) -> Unit,
    onCancelOverlayTranslate: () -> Unit,
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
    /**
     * 悬浮窗屏幕翻译（F3-05）：会话在别的前台服务里跑，本屏只画入口段与两枚系统权限的流转。
     * 快照必须由宿主显式传进来——屏内不得调 hiltViewModel()（androidTest 无 Hilt 直接渲染本屏）。
     */
    screenTranslation: ScreenTranslationSnapshot,
    onScreenTranslationConfirmOutbound: (String) -> Unit,
    onScreenTranslationDeclineOutbound: () -> Unit,
    onScreenTranslationStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = appColors()
    var showCamera by rememberSaveable { mutableStateOf(false) }
    var captureNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var modelExpanded by rememberSaveable { mutableStateOf(false) }
    // 「逐行对照 / 原位覆盖」是屏内第二模式，不新增路由：识别出的块清单就是它俩的公共入口。
    var overlayMode by rememberSaveable { mutableStateOf(false) }
    // 三视图：0 = 覆盖，1 = 原文，2 = 上下对照。生成前 overlay 视图退回原文，不留空框。
    var overlayView by rememberSaveable { mutableStateOf(0) }
    var overlayShareNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val shareScope = rememberCoroutineScope()
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

        // 识别出块之后才出现模式切换：没有块时「原位覆盖」无从谈起，不摆一枚按了没反应的键。
        if (state.overlayBlocks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PillButton(
                    text = "逐行对照",
                    onClick = { overlayMode = false },
                    modifier = Modifier.weight(1f),
                    style = if (overlayMode) PillStyle.Secondary else PillStyle.Primary,
                    enabled = !state.busy,
                    testTag = "photo_translation_mode_text",
                    contentDescription = "逐行对照模式",
                )
                PillButton(
                    text = "原位覆盖",
                    onClick = { overlayMode = true },
                    modifier = Modifier.weight(1f),
                    style = if (overlayMode) PillStyle.Primary else PillStyle.Secondary,
                    enabled = !state.busy,
                    testTag = "photo_translation_overlay_mode",
                    contentDescription = "原位覆盖模式",
                )
            }
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

        if (state.ocrText != null && !overlayMode) {
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
                shape = AppShape.Button,
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
                // translating 也在闸内：翻译在飞时再点一次不是「再要一次翻译」，是重发一次付费出站。
                enabled = !state.busy && !state.translating && state.ocrText.isNotBlank(),
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

        // 批 C-4：翻译在飞时的进行中面。旧形状这里什么都没有——`busy` 只由识别路径写，
        // 于是按钮照旧可点、界面零回执，再点一次就是掐掉上一发重付一次。
        if (state.translating) {
            RequestInProgress(
                message = "正在向你配置的 AI 服务翻译这段文字",
                onCancel = onCancelTranslate,
                statusTestTag = "photo_translation_translating",
                cancelTestTag = "photo_translation_cancel_translate",
            )
        }

        if (!overlayMode) when (val issue = state.translationIssue) {
            null -> Unit
            is PhotoTranslationFailureCategory.NotConfigured -> NotConfiguredCard(
                featureLabel = "拍照翻译",
                onConfigure = onOpenAiSettings,
                modifier = Modifier.testTag("photo_translation_not_configured"),
                detail = issue.reason.guidance(),
            )
            is PhotoTranslationFailureCategory.Ai -> FailureCard(issue.failure.uiText)
        }

        if (!overlayMode) state.translation?.let { (source, target) ->
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

        if (overlayMode && state.overlayBlocks.isNotEmpty()) {
            OverlayCoverageSection(
                state = state,
                overlayView = overlayView,
                onSelectView = { overlayView = it },
                onToggleBlock = onToggleOverlayBlock,
                onTranslate = onOverlayTranslate,
                onCancelTranslate = onCancelOverlayTranslate,
                openSettings = onOpenAiSettings,
                shareNotice = overlayShareNotice,
                onShare = {
                    val image = state.overlayImage
                    if (image == null) {
                        overlayShareNotice = "还没有覆盖图，先点「翻译并覆盖」。"
                    } else {
                        overlayShareNotice = null
                        shareScope.launch {
                            val outcome =
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        // 文件名只用来区分分享物，用挂钟取时间戳即可（不参与任何业务判定）。
                                        PhotoOverlayShareLauncher(context)
                                            .createChooser(image, System.currentTimeMillis())
                                    }
                                }
                            outcome
                                .onSuccess { intent -> context.startActivity(intent) }
                                .onFailure { overlayShareNotice = "分享失败，请稍后重试。" }
                        }
                    }
                },
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
        AnimatedVisibility(
            visible = historyExpanded,
            enter = expandVertically(animationSpec = tween(AppMotion.Normal, easing = AppMotion.EasingOut)),
            exit = shrinkVertically(animationSpec = tween(AppMotion.Normal, easing = AppMotion.EasingIn)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SavedHistoryList(
                    records = state.history,
                    lastSavedRecordId = state.lastSavedRecordId,
                    onRequestDeleteSaved = onRequestDeleteSaved,
                )
            }
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
        AnimatedVisibility(
            visible = modelExpanded,
            enter = expandVertically(animationSpec = tween(AppMotion.Normal, easing = AppMotion.EasingOut)),
            exit = shrinkVertically(animationSpec = tween(AppMotion.Normal, easing = AppMotion.EasingIn)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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

        // F3-05 悬浮窗屏幕翻译入口：段落在自己的文件里（那一屏的版式契约按文件数字面量，
        // 这里只留一行调用）。会话不在这里启动——截屏授权与前台服务都在段落内部自持。
        ScreenTranslationSection(
            state = screenTranslation,
            onConfirmOutbound = onScreenTranslationConfirmOutbound,
            onDeclineOutbound = onScreenTranslationDeclineOutbound,
            onStop = onScreenTranslationStop,
        )
    }

    // U-1（决策 2026-11-03 整合 §1）：私写的 `OutboundConfirmationDialog` 下线，改调共享件。
    // 屏内**只此一处**调用共享件（OutboundDialogFamilyTest 按屏计数）：逐段翻译与原位覆盖是同一件事的
    // 两个模式，出站确认也就只能有一个落点——挂两枚同名对话框，第二枚迟早漏掉「并可能产生费用」。
    // 域名 + 密钥 + 计费三件事由 `outboundConfirmBody` 一处给；本屏只追加半句屏侧事实（图片不上传）。
    val outboundHost = if (overlayMode) state.overlayPendingConfirmationHost else state.pendingConfirmationHost
    outboundHost?.let { host ->
        OutboundConfirmDialog(
            endpointHost = host,
            payloadKind = AiPayloadKind.Text,
            onConfirm = { if (overlayMode) onOverlayConfirmOutbound(true) else onConfirmOutbound(true) },
            onDismiss = { if (overlayMode) onOverlayConfirmOutbound(false) else onConfirmOutbound(false) },
            note = if (overlayMode) {
                "发送的是每块文字块（一屏一次请求，块数见上方清单），图片不会发送。"
            } else {
                "发送的是识别出的文字（可能是你编辑过的版本），图片不会发送。"
            },
            bodyTestTag = "photo_translation_outbound_confirmation_text",
            confirmTestTag = "photo_translation_confirm_outbound",
            dismissTestTag = "photo_translation_decline_outbound",
        )
    }
}


/**
 * 原位覆盖的未配置态：一句现状 + 一个「去配置」出口（spec §3 的死屏禁令不分模式）。
 *
 * 不复用共享 `NotConfiguredCard` 的理由写在调用点：那一枚的份数契约按屏计数，一屏一枚。
 */
@Composable
private fun OverlayUnconfiguredCard(
    detail: String,
    onOpenSettings: () -> Unit,
) {
    val colors = appColors()
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "「原位覆盖翻译」还没有可用的 AI 服务",
            style = AppType.Label,
            color = colors.text1,
            modifier = Modifier.testTag("photo_translation_overlay_not_configured"),
        )
        if (detail.isNotBlank()) {
            Text(text = detail, style = AppType.Footnote, color = colors.text2)
        }
        PillButton(
            text = "去配置",
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            style = PillStyle.Secondary,
            testTag = "photo_translation_overlay_open_settings",
            contentDescription = "去配置 AI 服务",
        )
    }
}

/**
 * 原位覆盖（F3-04）：块清单逐块开关 → 一次请求翻译被勾选的块 → 三视图查看 → 分享。
 *
 * 这一整块是**纯展示 + 意图上报**：勾选、请求、合成、计数都在 VM 里做完，
 * 屏侧只决定「现在看哪一张图」。所以它没有自己的状态机，也不在这里发网络。
 */
@Composable
private fun OverlayCoverageSection(
    state: PhotoTranslationUiState,
    overlayView: Int,
    onSelectView: (Int) -> Unit,
    onToggleBlock: (Int) -> Unit,
    onTranslate: () -> Unit,
    onCancelTranslate: () -> Unit,
    // 参数名不复用 `onOpenAiSettings`：那份契约按屏数这一字面「必填形参」，第二处会被读成漂移。
    openSettings: () -> Unit,
    shareNotice: String?,
    onShare: () -> Unit,
) {
    val colors = appColors()
    val accent = appDomain(AppDomain.AiSpeech).deep
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("photo_translation_overlay_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "原位覆盖：把每块文字原地换成中文。原图不改，生成的是新图；放不下的块会标出来，不压扁。",
            style = AppType.Footnote,
            color = colors.text2,
        )

        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "要覆盖的文字块（把握较低的默认不覆盖）", style = AppType.Label, color = accent)
            state.overlayBlocks.forEach { block ->
                OverlayBlockRow(
                    block = block,
                    included = block.id in state.overlayIncludedIds,
                    translated = block.id in state.overlayTranslations,
                    onToggle = { onToggleBlock(block.id) },
                )
            }
        }

        PillButton(
            text = "翻译并覆盖",
            onClick = onTranslate,
            modifier = Modifier.fillMaxWidth(),
            style = PillStyle.Primary,
            // 在飞时禁用是第二道闸（VM 里是第一道）：再点一次就是重发一次付费出站。
            enabled = !state.busy && !state.overlayTranslating,
            testTag = "photo_translation_overlay_translate",
            contentDescription = "翻译并覆盖",
        )
        Text(
            text = "只把勾选的块文本发给 AI，一屏一次请求；图片不上传，覆盖图只在本机。",
            style = AppType.Footnote,
            color = colors.text2,
            modifier = Modifier.testTag("photo_translation_overlay_cost_hint"),
        )

        if (state.overlayTranslating) {
            RequestInProgress(
                message = "正在翻译被勾选的文字块",
                onCancel = onCancelTranslate,
                statusTestTag = "photo_translation_overlay_translating",
                cancelTestTag = "photo_translation_overlay_cancel",
            )
        }

        when (val issue = state.overlayIssue) {
            null -> Unit
            // 不走共享 `NotConfiguredCard`：契约钉「每屏恰一处把共享卡的出口接到 onOpenAiSettings」
            // （NotConfiguredCardContractTest），模式化后第二处调用会让那条判据读成「长了第二枚未配置卡」。
            // 出口该有还是得有——这里给的是本屏自己的那一份（一句现状 + 一个「去配置」）。
            is PhotoTranslationFailureCategory.NotConfigured -> OverlayUnconfiguredCard(
                detail = issue.reason.guidance(),
                onOpenSettings = openSettings,
            )
            is PhotoTranslationFailureCategory.Ai -> FailureCard(issue.failure.uiText)
        }

        val overlayImage = state.overlayImage
        val sourceImage = state.sourceImage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton(
                text = "覆盖图",
                onClick = { onSelectView(0) },
                style = if (overlayView == 0) PillStyle.Primary else PillStyle.Secondary,
                testTag = "photo_translation_overlay_view_overlay",
            )
            PillButton(
                text = "原图",
                onClick = { onSelectView(1) },
                style = if (overlayView == 1) PillStyle.Primary else PillStyle.Secondary,
                testTag = "photo_translation_overlay_view_original",
            )
            PillButton(
                text = "上下对照",
                onClick = { onSelectView(2) },
                style = if (overlayView == 2) PillStyle.Primary else PillStyle.Secondary,
                testTag = "photo_translation_overlay_view_compare",
            )
        }

        when (overlayView) {
            1 -> OverlayBitmap(
                bitmap = sourceImage,
                emptyHint = "还没有原图：先拍照或从相册选一张。",
                tag = "photo_translation_overlay_original",
            )
            2 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "原文", style = AppType.Label, color = accent)
                OverlayBitmap(
                    bitmap = sourceImage,
                    emptyHint = "还没有原图：先拍照或从相册选一张。",
                    tag = "photo_translation_overlay_compare_source",
                )
                Text(text = "覆盖后", style = AppType.Label, color = accent)
                OverlayBitmap(
                    bitmap = overlayImage,
                    emptyHint = "还没有覆盖图，先点「翻译并覆盖」。",
                    tag = "photo_translation_overlay_compare_target",
                )
            }
            else -> OverlayBitmap(
                bitmap = overlayImage ?: sourceImage,
                emptyHint = "还没有覆盖图，先点「翻译并覆盖」。",
                tag = "photo_translation_overlay_result",
            )
        }

        if (overlayImage != null) {
            Text(
                text = if (state.overlaySkippedCount == 0 && state.overlayTruncatedCount == 0) {
                    "勾选的块都已画出。"
                } else {
                    "${state.overlaySkippedCount} 块未能覆盖（空间不足或没有译文），" +
                        "${state.overlayTruncatedCount} 块文字过长已截断。"
                },
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("photo_translation_overlay_summary"),
            )
            PillButton(
                text = "分享覆盖图",
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                style = PillStyle.Secondary,
                testTag = "photo_translation_overlay_share",
                contentDescription = "分享覆盖图",
            )
        }

        shareNotice?.let { message ->
            Text(
                text = message,
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("photo_translation_overlay_share_notice"),
            )
        }
    }
}

/** 一行块：摘要 + 事实标记（把握较低 / 未识别到文字 / 已翻译）+ 覆盖开关。 */
@Composable
private fun OverlayBlockRow(
    block: OverlayBlockSource,
    included: Boolean,
    translated: Boolean,
    onToggle: () -> Unit,
) {
    val colors = appColors()
    val summary = remember(block.text) {
        val flat = block.text.replace(Regex("\\s+"), " ").trim()
        if (flat.length > 18) flat.take(18) + "…" else flat
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("photo_translation_overlay_block_${block.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (summary.isEmpty()) "第 ${block.id} 块" else "第 ${block.id} 块 · $summary",
                style = AppType.Footnote,
                color = colors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val flags = buildList {
                if (block.lowConfidence) add("把握较低")
                if (block.missingText) add("未识别到文字")
                if (translated) add("已翻译")
            }
            if (flags.isNotEmpty()) {
                Text(text = flags.joinToString(" · "), style = AppType.Footnote, color = colors.text2)
            }
        }
        PillButton(
            text = if (included) "不覆盖" else "覆盖",
            onClick = onToggle,
            style = PillStyle.Text,
            enabled = !block.missingText,
            testTag = "photo_translation_overlay_block_toggle_${block.id}",
            contentDescription = "切换第 ${block.id} 块是否覆盖",
        )
    }
}

/** 一张图或一句「还没有」的说明：三视图共用，避免三处各写一遍空态文案。 */
@Composable
private fun OverlayBitmap(
    bitmap: Bitmap?,
    emptyHint: String,
    tag: String,
) {
    val colors = appColors()
    if (bitmap == null) {
        Text(
            text = emptyHint,
            style = AppType.Footnote,
            color = colors.text2,
            modifier = Modifier.testTag(tag),
        )
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = emptyHint,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag),
            contentScale = ContentScale.FillWidth,
        )
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

/**
 * 各拒绝原因对应的**补路指引**（卡的第二行）；第一行「一句现状」由共享 `NotConfiguredCard` 统一措辞。
 *
 * Task 26 Step 3 删掉了本文件同名的私有 `NotConfiguredCard`：旧卡只有一段文字、**没有出口**
 * （spec §3 点名的死屏）；出口现在由共享件提供并接到 `onOpenAiSettings`。
 */
private fun PhotoTranslationNotConfiguredReason.guidance(): String = when (this) {
    PhotoTranslationNotConfiguredReason.NoDefaultProfile ->
        "去「我的 → AI 服务」添加一套并设为默认。"
    PhotoTranslationNotConfiguredReason.DefaultProfileUnavailable ->
        "去「我的 → AI 服务」检查默认服务或补上密钥。"
    PhotoTranslationNotConfiguredReason.InvalidEndpoint ->
        "去「我的 → AI 服务」修正服务地址后再试。"
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
