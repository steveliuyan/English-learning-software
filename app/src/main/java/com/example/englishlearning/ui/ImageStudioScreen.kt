package com.example.englishlearning.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.imagegen.DrawingPromptPolicy
import com.example.englishlearning.imagegen.ImageStudioNotConfiguredReason
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.NotConfiguredCard
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.GlassDialog
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.components.hairlineTop
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * AI 生图功能页（AI 助学 → AI 生图）。
 *
 * 两段式：主题（≤100 字，只作数据位）→ 生成提示词（文本载荷，按域名确认一次）→
 * 生成图片（图片载荷，**每次**确认，按张计费）。失败文案只来自常量映射；
 * 主题与提示词都不出现在函数签名里，屏幕拿到的只是状态。
 *
 * ## 版式（批 4 Task 6，spec §5「AI 生图」行）
 *
 * 旧形态是「`TextButton` 形态返回 + `headlineSmall` 自绘标题 + 步骤一次排开的两枚同权实底
 * `Button` + 描边 `Card` 装提示词」。现在：
 *  - 页头换共享 [AppScreenHeader]，返回位是固定可及名「返回」；
 *  - **描述可编辑**（spec §5 原话「画面描述可编辑后再生成」）：模型写出来的画面描述落在
 *    `SoftCard` 里的输入框，用户改完再生成。旧形态是只读 `Text`，想改只能重跑一次文本调用——
 *    那是白花钱；
 *  - **主操作吸底**：固定在滚动面之下的 `Surface` 上，描述再长也不会把主操作挤出屏外；
 *  - **主操作随状态换身份**：同一个槽位，主题态是「生成画面描述」，描述就绪后换成「生成图片」；
 *    没有第二枚常驻的「生成」按钮；
 *  - 令牌换装：`Mint*` 读点清零，配色走 `appColors()` / `appDomain()`，排印走六档 `AppType`。
 *
 * 数据通路、tag、费用口径与出站确认三要素一字不动：这一屏只搬外观，外加一处「把用户改过的
 * 描述真送出去」的形参（[onGenerateImage] 收 `String`，VM 侧见 `ImageStudioViewModel.generateImage`）。
 */
@Composable
fun ImageStudioScreen(
    state: ImageStudioUiState,
    onGeneratePrompt: (String) -> Unit,
    onGenerateImage: (String) -> Unit,
    onConfirm: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    var subject by rememberSaveable { mutableStateOf("") }
    val busy = state == ImageStudioUiState.DraftingPrompt || state == ImageStudioUiState.GeneratingImage
    val modelPrompt = (state as? ImageStudioUiState.PromptReady)?.prompt

    // 可编辑的画面描述：初值直接取模型稿，首帧就有字（不留一帧空框）。
    var editedPrompt by rememberSaveable { mutableStateOf(modelPrompt.orEmpty()) }
    // 已经落到 `editedPrompt` 的那一稿（哨兵空串代替 null，避免 saveable 为 null 的形状）。
    var appliedPrompt by rememberSaveable { mutableStateOf("") }
    // 只在**真的换了新草稿**时重置。出站确认框取消后会回到同一份 PromptReady，
    // 那不是新草稿——用户改过的字必须还在（转屏/重建同理，两个态都走 rememberSaveable）。
    LaunchedEffect(modelPrompt) {
        if (modelPrompt != null && modelPrompt != appliedPrompt) {
            editedPrompt = modelPrompt
            appliedPrompt = modelPrompt
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("image_studio_screen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AppScreenHeader(title = "AI 生图", onBack = onBack)
                Text(
                    text = "先用文字 AI 写出英文画面描述，再交给生图服务出图。",
                    style = AppType.Footnote,
                    color = colors.text2,
                )

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it.take(DrawingPromptPolicy.maxSubjectLength) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_studio_subject_input")
                        .semantics { contentDescription = "绘画主题输入" },
                    label = { Text("想画什么（最多 ${DrawingPromptPolicy.maxSubjectLength} 字）") },
                    supportingText = { Text("${subject.length}/${DrawingPromptPolicy.maxSubjectLength}") },
                    enabled = !busy,
                )
                Text(
                    text = "每次调用都会真实使用「设置 · AI」里的服务并可能产生费用",
                    style = AppType.Footnote,
                    color = colors.text3,
                    modifier = Modifier.testTag("image_studio_cost_hint"),
                )

                when (state) {
                    ImageStudioUiState.Idle -> Text(
                        text = "填一个主题，页底的主操作会变成「生成画面描述」。",
                        style = AppType.Footnote,
                        color = colors.text2,
                    )
                    ImageStudioUiState.DraftingPrompt -> Text(
                        text = "正在生成画面描述…",
                        style = AppType.Footnote,
                        color = colors.text2,
                        modifier = Modifier.testTag("image_studio_drafting"),
                    )
                    is ImageStudioUiState.PromptReady -> PromptEditor(
                        editedPrompt = editedPrompt,
                        onEditedPromptChange = { editedPrompt = it },
                    )
                    is ImageStudioUiState.AwaitingConfirmation -> ConfirmationDialog(state, onConfirm)
                    ImageStudioUiState.GeneratingImage -> Text(
                        text = "正在生成图片…",
                        style = AppType.Footnote,
                        color = colors.text2,
                        modifier = Modifier.testTag("image_studio_generating"),
                    )
                    is ImageStudioUiState.ImageReady -> ResultImage(state.filePath)
                    is ImageStudioUiState.NotConfigured -> NotConfiguredCard(
                        featureLabel = "AI 生图",
                        onConfigure = onOpenAiSettings,
                        modifier = Modifier
                            .testTag("image_studio_not_configured")
                            // 旧私有卡按整卡合并朗读；共享件不预置合并，调用方保留这一层
                            // （connected 断言按整卡取文本）。
                            .semantics(mergeDescendants = true) {},
                        detail = state.reason.guidance(),
                    )
                    is ImageStudioUiState.Failed -> FailureCard(state.failure.uiText.message)
                }
            }

            // 吸底主操作槽：滚动面之上的独立一档，不透明 `Surface`（内容从它下面穿过，不叠字）。
            // 同一槽位随状态换身份——主题态「生成画面描述」，描述就绪「生成图片」，没有第三枚生成键。
            // 槽色与屏体软卡同为 `colors.surface`，故槽顶压一条发丝线（批 5 遗留 7 收口，
            // 与 `BackupExportScreen.kt:221` 同形）：长描述在 2× 字号下正好断在槽沿上。
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = colors.surface,
                    modifier = hairlineTop(colors.outline)
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .testTag("image_studio_action_bar"),
                ) {
                    when (state) {
                        is ImageStudioUiState.PromptReady -> ActionPill(
                            text = "生成图片",
                            onClick = { onGenerateImage(editedPrompt) },
                            enabled = editedPrompt.isNotBlank(),
                            testTag = "image_studio_generate_image",
                            contentDescription = "生成图片",
                        )
                        else -> ActionPill(
                            text = if (busy) "请稍候…" else "生成画面描述",
                            onClick = { onGeneratePrompt(subject) },
                            enabled = !busy && subject.isNotBlank(),
                            testTag = "image_studio_generate_prompt",
                            contentDescription = "生成画面描述",
                        )
                    }
                }
            }
        }
    }
}

/**
 * 吸底主操作槽里的那一枚药片：实底档 + 54dp 最小点按高度 + 槽内边距收成一处。
 *
 * 「两枚生成键」长得一样、只有文案与回调不同——身份由调用处决定（状态分叉），尺寸与档位不由
 * 调用处各写一遍，否则第三次改动就会漂成两个尺寸。
 */
@Composable
private fun ActionPill(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String,
    contentDescription: String,
) {
    PillButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        style = PillStyle.Primary,
        domain = AppDomain.AiSpeech,
        testTag = testTag,
        contentDescription = contentDescription,
    )
}

/**
 * 画面描述编辑器：模型写出来的那一稿落进输入框，用户改完再生成（spec §5「可编辑后再生成」）。
 *
 * 输入框的 `value` 必须是屏内的 `editedPrompt`（`ImageStudioLayoutContractTest` 第 2 条按符号钉住），
 * 所以本组件只收值与回调、不自己持有状态——转屏/重建时的那份状态归屏上的 `rememberSaveable`。
 * 按张计费的费用句只在这里讲一次（生成前），不随每帧重复。
 */
@Composable
private fun PromptEditor(
    editedPrompt: String,
    onEditedPromptChange: (String) -> Unit,
) {
    val colors = appColors()
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "画面描述（可以直接改，改完再生成）",
            style = AppType.Label,
            color = colors.text2,
        )
        OutlinedTextField(
            value = editedPrompt,
            onValueChange = onEditedPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("image_studio_prompt"),
            label = { Text("英文画面描述", style = AppType.Label, color = colors.text2) },
            textStyle = AppType.Body,
            minLines = 3,
        )
        Text(
            text = "生成一张图片按张计费，费用通常是文本调用的数十倍",
            style = AppType.Footnote,
            color = colors.text3,
            modifier = Modifier.testTag("image_studio_image_cost_hint"),
        )
    }
}

@Composable
private fun ConfirmationDialog(state: ImageStudioUiState.AwaitingConfirmation, onConfirm: (Boolean) -> Unit) {
    GlassDialog(onDismiss = { onConfirm(false) }) {
        Text("确认发送请求", style = AppType.Headline, color = appColors().text1)
        val message = when (state.stage) {
            ImageStudioConfirmationStage.Prompt ->
                "你的主题与该服务的密钥将发送给 ${state.host}，并可能产生费用。同一域名只需确认一次。"
            ImageStudioConfirmationStage.Image ->
                "将向 ${state.host} 发送一次生图请求：按张计费，费用可能是文本调用的数十倍，每次出图都要重新确认。"
        }
        Text(
            message,
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("image_studio_outbound_confirmation_text"),
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
                onClick = { onConfirm(false) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Secondary,
                domain = AppDomain.AiSpeech,
                testTag = "image_studio_decline_outbound",
            )
            PillButton(
                text = "确认发送",
                onClick = { onConfirm(true) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Primary,
                domain = AppDomain.AiSpeech,
                testTag = "image_studio_confirm_outbound",
            )
        }
    }
}

@Composable
private fun ResultImage(filePath: String) {
    val colors = appColors()
    val bitmap = remember(filePath) { BitmapFactory.decodeFile(filePath) }
    if (bitmap == null) {
        Text(
            text = "图片已生成，但缓存文件读不出来，请重新生成一次。",
            style = AppType.Footnote,
            color = colors.text2,
            modifier = Modifier.testTag("image_studio_result_unreadable"),
        )
        return
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "生成的图片",
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("image_studio_result"),
    )
}

/**
 * 各拒绝原因对应的**补路指引**（卡的第二行）；第一行「一句现状」由共享 `NotConfiguredCard` 统一措辞。
 *
 * Task 26 Step 3 删掉了本文件同名的私有 `NotConfiguredCard`（同名遮蔽让共享件永远进不来）：旧卡把
 * 「一句现状 + 补路」揉成一段文字、且**没有出口**，正是 spec §3 点名的死屏；现在出口「去配置 ›」由
 * 共享件提供并接到 `onOpenAiSettings`。
 */
private fun ImageStudioNotConfiguredReason.guidance(): String = when (this) {
    ImageStudioNotConfiguredReason.NoDefaultProfile ->
        "去「设置 · AI」添加一套并设为生图默认。"
    ImageStudioNotConfiguredReason.DefaultProfileUnavailable ->
        "去「设置 · AI」检查默认生图服务或补上密钥。"
    ImageStudioNotConfiguredReason.InvalidEndpoint ->
        "去「设置 · AI」修正生图服务地址后再试。"
}

@Composable
private fun FailureCard(message: String) {
    val colors = appColors()
    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("image_studio_failure")
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = message,
            style = AppType.Body,
            color = colors.text1,
            modifier = Modifier.semantics { contentDescription = "生图失败原因" },
        )
    }
}
