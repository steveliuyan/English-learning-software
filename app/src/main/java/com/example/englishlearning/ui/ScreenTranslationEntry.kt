package com.example.englishlearning.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.screentranslation.ScreenTranslationCopy
import com.example.englishlearning.screentranslation.ScreenTranslationService
import com.example.englishlearning.screentranslation.ScreenTranslationSnapshot
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.EntryIconTone
import com.example.englishlearning.ui.components.EntryRow
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.OutboundConfirmDialog
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppMotion
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 悬浮窗屏幕翻译（F3-05）的入口段：一枚折叠行 + 段内自持的授权与会话发起。
 *
 * 为什么单独一个文件、而不是写进 [PhotoTranslationScreen]：那一屏的版式契约按**文件**数字面量
 * （EntryRow 恰两枚、SoftCard 下限、出站确认恰一处……），把新入口塞进去等于同时改三条既有判据；
 * 而且这一段的重点不是版式而是**两条系统权限**，分开写更好讲清楚。
 *
 * 三件事只在这里做：
 * 1. SYSTEM_ALERT_WINDOW：跳系统设置手动开（一次授权长期有效），回前台重查；
 * 2. MediaProjection：点一次开始 = 一次系统授权弹窗（Android 14+ 没有「始终允许」），
 *    resultCode/resultData 直接交给前台服务；
 * 3. 出站确认闭环：服务在首次发送前会先停会话并把域名放进快照，这里弹共享确认框，
 *    确认后**重新申请一次截屏授权**（旧令牌一次性作废）再重开会话。
 *
 * 截屏帧不出本机这件事在界面上要写出来，但不要写成「安全承诺」的口水文：说清楚「只在会话内存一遍、
 * 上行只有识别出的文字」就够。
 */
@Composable
internal fun ScreenTranslationSection(
    state: ScreenTranslationSnapshot,
    onConfirmOutbound: (String) -> Unit,
    onDeclineOutbound: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = appColors()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var startNotice by rememberSaveable { mutableStateOf<String?>(null) }
    // 最近一次请求的档位：确认后重开会话要按同一个档位继续。
    var onceRequested by rememberSaveable { mutableStateOf(true) }

    // 权限是一次授权长期有效，但用户可能在会话之间去设置里关掉：每次回到前台都重查一次。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) overlayGranted = Settings.canDrawOverlays(context)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val projectionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode != Activity.RESULT_OK || data == null) {
                startNotice = "没有拿到截屏授权：屏幕翻译只在一次授权内有效，随时可以再点开始。"
                return@rememberLauncherForActivityResult
            }
            val intent =
                ScreenTranslationService.startIntent(
                    context = context,
                    resultCode = result.resultCode,
                    resultData = data,
                    once = onceRequested,
                )
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure {
                    startNotice = "启动屏幕翻译会话失败：请确认本应用允许前台服务，并已开启悬浮窗权限。"
                }
        }

    fun requestSession(once: Boolean) {
        startNotice = null
        onceRequested = once
        if (!Settings.canDrawOverlays(context)) {
            // 没权限就不发截屏请求：先让用户去开悬浮窗，否则会话起来也没有地方画译文。
            overlayGranted = false
            startNotice = ScreenTranslationCopy.noticeText(
                com.example.englishlearning.screentranslation.ScreenTranslationNotice.OverlayPermissionMissing,
            )
            return
        }
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            startNotice = "这台设备不支持屏幕截取（拿不到 MediaProjection 服务）。"
            return
        }
        val launched =
            runCatching { projectionLauncher.launch(manager.createScreenCaptureIntent()) }
        if (launched.isFailure) {
            startNotice = "打开截屏授权失败：请稍后重试。"
        }
    }

    fun openOverlaySettings() {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName),
            )
        runCatching { context.startActivity(intent) }
            .onFailure { startNotice = "打不开系统设置：请手动到「设置 → 应用 → 显示在其他应用上层」里允许。" }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EntryRow(
            icon = AppIcon.Translate,
            domain = AppDomain.AiSpeech,
            title = "悬浮窗屏幕翻译",
            subtitle = if (state.running) {
                "会话进行中 · " + ScreenTranslationCopy.phaseLabel(state.phase, state.mode)
            } else {
                "把屏幕上的英文原位置覆盖成中文 · 每次开始都要一次截屏授权"
            },
            trailing = if (expanded) "收起" else "展开",
            expanded = expanded,
            iconTone = EntryIconTone.Tint,
            modifier = Modifier.testTag("screen_translation_entry"),
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(AppMotion.Normal, easing = AppMotion.EasingOut)),
            exit = shrinkVertically(animationSpec = tween(AppMotion.Normal, easing = AppMotion.EasingIn)),
        ) {
            SoftCard(modifier = Modifier.testTag("screen_translation_panel")) {
                Text(
                    text = "只在这次会话里截屏：画面在本机内存里过一遍、不落盘也不上传，上行只有识别出的文字，" +
                        "首次发送前会先确认发去的域名。随时按「停止」结束，授权令牌一次会话有效。",
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.testTag("screen_translation_privacy_note"),
                )
                if (!overlayGranted) {
                    InlineNotice(
                        text = "还需要「显示在其他应用上层」权限：译文要画在别的应用上面，没有这个权限开不了会话。",
                        kind = InlineNoticeKind.Error,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .testTag("screen_translation_overlay_permission"),
                        action = {
                            PillButton(
                                text = "去系统设置开启",
                                onClick = { openOverlaySettings() },
                                style = PillStyle.Secondary,
                                testTag = "screen_translation_overlay_permission_settings",
                                contentDescription = "去系统设置开启悬浮窗权限",
                            )
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PillButton(
                        text = "单次翻译当前屏",
                        onClick = { requestSession(once = true) },
                        modifier = Modifier.weight(1f),
                        style = PillStyle.Primary,
                        enabled = !state.running,
                        testTag = "screen_translation_once_button",
                        contentDescription = "单次翻译当前屏",
                    )
                    PillButton(
                        text = "连续翻译",
                        onClick = { requestSession(once = false) },
                        modifier = Modifier.weight(1f),
                        style = PillStyle.Secondary,
                        enabled = !state.running,
                        testTag = "screen_translation_continuous_button",
                        contentDescription = "连续翻译",
                    )
                }
                Text(
                    text = ScreenTranslationCopy.costLine(state.sentRequests, state.modelName),
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.testTag("screen_translation_cost_line"),
                )
                if (state.running) {
                    PillButton(
                        text = "停止屏幕翻译",
                        onClick = {
                            // 服务端 finish 会自己 endSession；这里再兜一次，避免服务已被系统回收时界面卡在「进行中」。
                            runCatching { context.startService(ScreenTranslationService.stopIntent(context)) }
                            onStop()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = PillStyle.Secondary,
                        testTag = "screen_translation_stop_button",
                        contentDescription = "停止屏幕翻译",
                    )
                }
                state.notice?.let { notice ->
                    InlineNotice(
                        text = ScreenTranslationCopy.noticeText(notice),
                        kind = InlineNoticeKind.Error,
                        modifier = Modifier.testTag("screen_translation_notice"),
                    )
                }
                startNotice?.let { message ->
                    InlineNotice(
                        text = message,
                        kind = InlineNoticeKind.Info,
                        modifier = Modifier.testTag("screen_translation_start_notice"),
                    )
                }
            }
        }
    }

    // 出站确认：与逐行翻译、原位覆盖同一条纪律（域名 + 密钥 + 计费），复用共享对话框，
    // 只在「服务已经停下来等确认」时出现。确认之后必须重新申请截屏授权——旧令牌一次性。
    state.pendingConfirmationHost?.let { host ->
        OutboundConfirmDialog(
            endpointHost = host,
            payloadKind = AiPayloadKind.Text,
            onConfirm = {
                onConfirmOutbound(host)
                requestSession(once = onceRequested)
            },
            onDismiss = { onDeclineOutbound() },
            note = "发送的是这一屏识别出的文字块（一屏一次请求），截屏画面不会发送。",
            bodyTestTag = "screen_translation_outbound_confirmation_text",
            confirmTestTag = "screen_translation_confirm_outbound",
            dismissTestTag = "screen_translation_decline_outbound",
        )
    }
}
