package com.example.englishlearning.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.imagegen.DrawingPromptPolicy
import com.example.englishlearning.imagegen.ImageStudioNotConfiguredReason
import com.example.englishlearning.ui.components.glass.GlassDialog
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
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
 * AI 生图功能页（AI 学 → AI 生图）。
 *
 * 两段式：主题（≤100 字，只作数据位）→ 生成提示词（文本载荷，按域名确认一次）→
 * 生成图片（图片载荷，**每次**确认，按张计费）。失败文案只来自常量映射；
 * 主题与提示词都不出现在函数签名里，屏幕拿到的只是状态。
 */
@Composable
fun ImageStudioScreen(
    state: ImageStudioUiState,
    onGeneratePrompt: (String) -> Unit,
    onGenerateImage: () -> Unit,
    onConfirm: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var subject by rememberSaveable { mutableStateOf("") }
    val busy = state == ImageStudioUiState.DraftingPrompt || state == ImageStudioUiState.GeneratingImage

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("image_studio_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(
            onClick = onBack,
            colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark),
            modifier = Modifier
                .testTag("image_studio_back")
                .semantics { contentDescription = "返回 AI 学" },
        ) { Text("← 返回 AI 学", fontWeight = FontWeight.Bold) }

        Text("AI 生图", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text("先用文字 AI 写出英文画面描述，再交给生图服务出图。", color = MintTextMuted)

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
        Button(
            onClick = { onGeneratePrompt(subject) },
            enabled = !busy && subject.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("image_studio_generate_prompt")
                .semantics { contentDescription = "生成画面描述" },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MintPrimary,
                contentColor = Color.White,
                disabledContainerColor = MintTint,
                disabledContentColor = MintPrimaryDark,
            ),
        ) { Text("生成画面描述", fontWeight = FontWeight.Bold) }
        Text(
            "每次调用都会真实使用「设置 · AI」里的服务并可能产生费用",
            style = MaterialTheme.typography.bodySmall,
            color = MintTextMuted,
            modifier = Modifier.testTag("image_studio_cost_hint"),
        )

        when (state) {
            ImageStudioUiState.Idle -> Unit
            ImageStudioUiState.DraftingPrompt -> Text(
                "正在生成画面描述…",
                color = MintTextMuted,
                modifier = Modifier.testTag("image_studio_drafting"),
            )
            is ImageStudioUiState.PromptReady -> PromptCard(state.prompt, onGenerateImage)
            is ImageStudioUiState.AwaitingConfirmation -> ConfirmationDialog(state, onConfirm)
            ImageStudioUiState.GeneratingImage -> Text(
                "正在生成图片…",
                color = MintTextMuted,
                modifier = Modifier.testTag("image_studio_generating"),
            )
            is ImageStudioUiState.ImageReady -> ResultImage(state.filePath)
            is ImageStudioUiState.NotConfigured -> NotConfiguredCard(state.reason)
            is ImageStudioUiState.Failed -> FailureCard(state.failure.uiText.message)
        }
    }
}

@Composable
private fun PromptCard(prompt: String, onGenerateImage: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("image_studio_prompt"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("画面描述", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MintPrimary)
            Text(prompt, style = MaterialTheme.typography.bodyLarge, color = MintPrimaryDark)
        }
    }
    Button(
        onClick = onGenerateImage,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("image_studio_generate_image")
            .semantics { contentDescription = "生成图片" },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MintPrimary,
            contentColor = Color.White,
        ),
    ) { Text("生成图片", fontWeight = FontWeight.Bold) }
    Text(
        "生成一张图片按张计费，费用通常是文本调用的数十倍",
        style = MaterialTheme.typography.bodySmall,
        color = MintTextMuted,
        modifier = Modifier.testTag("image_studio_image_cost_hint"),
    )
}

@Composable
private fun ConfirmationDialog(state: ImageStudioUiState.AwaitingConfirmation, onConfirm: (Boolean) -> Unit) {
    GlassDialog(onDismiss = { onConfirm(false) }) {
        Text("确认发送请求", style = AppType.Headline)
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
                accent = DomainColors.Learn,
                testTag = "image_studio_decline_outbound",
            )
            PillButton(
                text = "确认发送",
                onClick = { onConfirm(true) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Primary,
                accent = DomainColors.Learn,
                testTag = "image_studio_confirm_outbound",
            )
        }
    }
}

@Composable
private fun ResultImage(filePath: String) {
    val bitmap = remember(filePath) { BitmapFactory.decodeFile(filePath) }
    if (bitmap == null) {
        Text(
            "图片已生成，但缓存文件读不出来，请重新生成一次。",
            color = MintTextMuted,
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

@Composable
private fun NotConfiguredCard(reason: ImageStudioNotConfiguredReason) {
    val message = when (reason) {
        ImageStudioNotConfiguredReason.NoDefaultProfile ->
            "还没有默认的生图服务，先去「设置 · AI」添加一套并设为生图默认。"
        ImageStudioNotConfiguredReason.DefaultProfileUnavailable ->
            "默认生图服务不可用或缺少密钥，去「设置 · AI」检查。"
        ImageStudioNotConfiguredReason.InvalidEndpoint ->
            "生图服务的地址无效，去「设置 · AI」修正后再试。"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("image_studio_not_configured")
            // 卡片作为整体被朗读/断言：文本合并到本节点上。
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            message,
            color = MintPrimaryDark,
            modifier = Modifier
                .padding(18.dp)
                .semantics { contentDescription = "生图未配置提示" },
        )
    }
}

@Composable
private fun FailureCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("image_studio_failure")
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            message,
            color = MintPrimaryDark,
            modifier = Modifier
                .padding(18.dp)
                .semantics { contentDescription = "生图失败原因" },
        )
    }
}
