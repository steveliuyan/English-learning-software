package com.example.englishlearning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiVoiceCatalog
import com.example.englishlearning.ui.components.glass.GlassOverlay
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.components.glass.frosted
import com.example.englishlearning.ui.components.glass.pressableScale
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.MintBackground

/**
 * 「设置 · AI」下的 AI 服务管理页（二级全屏层）。
 *
 * 同一个 composable 承担列表与编辑两态：`editor == null` 时是列表，否则是编辑页。这样返回键
 * 只有一条链路——编辑态下先关编辑页、列表态下才退出整层，不需要在 [AppScreen] 里再排一次顺序。
 *
 * 密钥输入框只写不回显：编辑已有配置时永远显示「已设置密钥 / 还没有设置密钥」，不把已保存的值
 * 填进输入框。
 */
@Composable
fun AiProfileSettingsScreen(
    listState: AiProfileListUiState,
    editor: AiProfileEditorUiState?,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDraftChange: (AiProfileDraft) -> Unit,
    onSave: () -> Unit,
    onCloseEditor: () -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDeleteKey: (String) -> Unit,
    onBack: () -> Unit,
    onSetDefaultTextProfile: (String) -> Unit = {},
    onSetDefaultImageProfile: (String) -> Unit = {},
) {
    if (editor != null) {
        // 在列表层之后声明，因此编辑态的返回键优先：先关编辑页，再退整层。
        BackHandler { onCloseEditor() }
        AiProfileEditor(
            state = editor,
            onDraftChange = onDraftChange,
            onSave = onSave,
            onClose = onCloseEditor,
            onDeleteProfile = onDeleteProfile,
            onDeleteKey = onDeleteKey,
        )
    } else {
        AiProfileList(
            state = listState,
            onAdd = onAdd,
            onEdit = onEdit,
            onSetDefaultTextProfile = onSetDefaultTextProfile,
            onSetDefaultImageProfile = onSetDefaultImageProfile,
            onBack = onBack,
        )
    }
}

@Composable
private fun AiProfileList(
    state: AiProfileListUiState,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onSetDefaultTextProfile: (String) -> Unit,
    onSetDefaultImageProfile: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("ai_profiles_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PillButton(
            text = "← 返回上一层",
            onClick = onBack,
            style = PillStyle.Text,
            accent = DomainColors.AiSpeech,
            // 这一层有两个入口：「设置 · AI 服务与密钥」和「AI 学」功能页的「去配置 AI 服务」。
            // 从后者进来时底部选中的仍是 AI 学栏，写死「返回设置」就会指错地方，所以用中性的表述。
            contentDescription = "返回上一层",
        )

        Text("AI 服务", style = AppType.Headline)
        Text(
            text = "可以配置多套 OpenAI 兼容服务。密钥保存在本机系统安全区，不写进数据库、日志或备份。",
            style = AppType.Footnote,
        )

        when (state) {
            AiProfileListUiState.Loading -> Text("正在读取本地配置…", style = AppType.Body, color = AppPalette.TextSecondary)
            AiProfileListUiState.Unavailable -> Text(
                text = "本机配置暂时读不出来，稍后再试。",
                style = AppType.Body,
                color = AppPalette.TextSecondary,
                modifier = Modifier.testTag("ai_profiles_unavailable"),
            )
            is AiProfileListUiState.Ready -> {
                state.message?.let {
                    Text(it, style = AppType.Body, color = DomainColors.Reading.deep, modifier = Modifier.testTag("ai_profiles_message"))
                }
                if (state.items.isEmpty()) {
                    Text(
                        text = "还没有任何 AI 配置。添加一套之后，「AI 学」里的功能才有可以调用的服务。",
                        style = AppType.Body,
                        color = AppPalette.TextSecondary,
                        modifier = Modifier.testTag("ai_profiles_empty"),
                    )
                }
                state.items.forEach { item ->
                    AiProfileRow(
                        item = item,
                        onEdit = onEdit,
                        onSetDefaultTextProfile = onSetDefaultTextProfile,
                        onSetDefaultImageProfile = onSetDefaultImageProfile,
                    )
                }
            }
        }

        PillButton(
            text = "新增配置",
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
            style = PillStyle.Primary,
            accent = DomainColors.AiSpeech,
            testTag = "ai_profiles_add",
            contentDescription = "新增 AI 配置",
        )
    }
}

@Composable
private fun AiProfileRow(
    item: AiProfileListItem,
    onEdit: (String) -> Unit,
    onSetDefaultTextProfile: (String) -> Unit,
    onSetDefaultImageProfile: (String) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = AppShape.Card,
        color = AppPalette.Surface,
        border = BorderStroke(1.dp, AppPalette.Separator),
        modifier = Modifier
            .fillMaxWidth()
            .pressableScale(interaction)
            .testTag("ai_profile_item_${item.profile.profileId}")
            .clickable(interactionSource = interaction, indication = null) { onEdit(item.profile.profileId) }
            .semantics { contentDescription = "编辑配置 ${item.profile.displayName}" },
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.profile.displayName, style = AppType.Title)
            Text(
                text = "${item.profile.model} · ${hostOf(item.profile.endpoint)}",
                style = AppType.Footnote,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (item.hasKey) "已设置密钥" else "还没有设置密钥",
                    style = AppType.Label,
                    color = DomainColors.AiSpeech.deep,
                    modifier = Modifier
                        .background(DomainColors.AiSpeech.base.copy(alpha = 0.12f), AppShape.Pill)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                        .testTag("ai_profile_key_state_${item.profile.profileId}"),
                )
                Text(
                    text = capabilitiesLabel(item.profile.capabilities),
                    style = AppType.Label,
                )
            }
            when {
                item.isDefaultTextProfile -> Text(
                    text = "文章默认",
                    style = AppType.Label,
                    color = DomainColors.AiSpeech.deep,
                    modifier = Modifier.testTag("ai_profile_default_${item.profile.profileId}"),
                )
                item.canBeDefaultTextProfile -> PillButton(
                    text = "设为文章默认",
                    onClick = { onSetDefaultTextProfile(item.profile.profileId) },
                    style = PillStyle.Text,
                    accent = DomainColors.AiSpeech,
                    modifier = Modifier.testTag("ai_profile_set_default_${item.profile.profileId}"),
                    contentDescription = "将 ${item.profile.displayName} 设为文章默认",
                )
            }
            when {
                item.isDefaultImageProfile -> Text(
                    text = "生图默认",
                    style = AppType.Label,
                    color = DomainColors.AiSpeech.deep,
                    modifier = Modifier.testTag("ai_profile_image_default_${item.profile.profileId}"),
                )
                item.canBeDefaultImageProfile -> PillButton(
                    text = "设为生图默认",
                    onClick = { onSetDefaultImageProfile(item.profile.profileId) },
                    style = PillStyle.Text,
                    accent = DomainColors.AiSpeech,
                    modifier = Modifier.testTag("ai_profile_set_image_default_${item.profile.profileId}"),
                    contentDescription = "将 ${item.profile.displayName} 设为生图默认",
                )
            }
        }
    }
}

@Composable
private fun AiProfileEditor(
    state: AiProfileEditorUiState,
    onDraftChange: (AiProfileDraft) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDeleteKey: (String) -> Unit,
) {
    val draft = state.draft
    var voiceOverlayVisible by rememberSaveable { mutableStateOf(false) }
    // 声明在编辑层 BackHandler（AiProfileSettingsScreen 里）之后，浮层打开时返回键先关浮层，
    // 不会直达编辑层把未保存的草稿一起丢掉。
    BackHandler(enabled = voiceOverlayVisible) { voiceOverlayVisible = false }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MintBackground)
                .frosted(voiceOverlayVisible)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .testTag("ai_profile_editor"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PillButton(
                text = "← 返回 AI 服务",
                onClick = onClose,
                style = PillStyle.Text,
                accent = DomainColors.AiSpeech,
                contentDescription = "返回 AI 服务",
            )

            Text(
                text = if (state.profileId == null) "新增 AI 配置" else "编辑 AI 配置",
                style = AppType.Headline,
            )

            SectionCard {
                EditorField(
                    label = "名称",
                    value = draft.displayName,
                    tag = "ai_profile_name",
                    onValueChange = { onDraftChange(draft.copy(displayName = it)) },
                )
                EditorField(
                    label = "官网",
                    value = draft.websiteUrl,
                    tag = "ai_profile_website",
                    hint = "https://example.com",
                    onValueChange = { onDraftChange(draft.copy(websiteUrl = it)) },
                )
                EditorField(
                    label = "Endpoint",
                    value = draft.endpoint,
                    tag = "ai_profile_endpoint",
                    hint = "https://api.example.com/v1",
                    onValueChange = { onDraftChange(draft.copy(endpoint = it)) },
                )
                EditorField(
                    label = "模型",
                    value = draft.model,
                    tag = "ai_profile_model",
                    hint = "gpt-4o-mini",
                    onValueChange = { onDraftChange(draft.copy(model = it)) },
                )
            }

            SectionCard {
                Text("能力", style = AppType.Title)
                Text("声明这套服务支持的模态；生成文章至少要有「文本」。", style = AppType.Footnote)
                AiCapability.entries.forEach { capability ->
                    CapabilityToggle(
                        capability = capability,
                        selected = capability in draft.capabilities,
                        onToggle = { enabled ->
                            val next = if (enabled) draft.capabilities + capability else draft.capabilities - capability
                            onDraftChange(draft.copy(capabilities = next))
                        },
                    )
                }
            }

            if (AiCapability.Speech in draft.capabilities) {
                SectionCard {
                    Text("语音角色", style = AppType.Title)
                    Text(
                        text = "留空 = 自动：MiMo 按语言选冰糖/Mia，OpenAI 兼容走服务默认。",
                        style = AppType.Footnote,
                    )
                    PillButton(
                        text = if (draft.voice.isBlank()) "自动（推荐）  ▾" else "${draft.voice}  ▾",
                        onClick = { voiceOverlayVisible = true },
                        style = PillStyle.Secondary,
                        accent = DomainColors.AiSpeech,
                        testTag = "ai_profile_voice",
                        contentDescription = "选择语音角色",
                    )
                }
            }

            SectionCard {
                Text("高级参数", style = AppType.Title)
                Text(
                    text = "可选范围：temperature 0–2、top_p 0–1、max_tokens 1–4096、超时 5–120 秒。",
                    style = AppType.Footnote,
                )
                EditorField("temperature", draft.temperature, "ai_profile_temperature", numeric = true) {
                    onDraftChange(draft.copy(temperature = it))
                }
                EditorField("top_p", draft.topP, "ai_profile_top_p", numeric = true) {
                    onDraftChange(draft.copy(topP = it))
                }
                EditorField("max_tokens", draft.maxTokens, "ai_profile_max_tokens", numeric = true) {
                    onDraftChange(draft.copy(maxTokens = it))
                }
                EditorField("timeout_seconds", draft.timeoutSeconds, "ai_profile_timeout_seconds", numeric = true) {
                    onDraftChange(draft.copy(timeoutSeconds = it))
                }
            }

            SectionCard {
                Text("密钥", style = AppType.Title)
                Text(
                    text = if (state.hasStoredKey) "已设置密钥" else "还没有设置密钥",
                    style = AppType.Body,
                    color = DomainColors.AiSpeech.deep,
                    modifier = Modifier.testTag("ai_profile_key_state"),
                )
                EditorField(
                    label = "API Key",
                    value = draft.pendingKey,
                    tag = "ai_profile_key_input",
                    hint = if (state.hasStoredKey) "留空表示不改动已保存的密钥" else "粘贴你的 API Key",
                    masked = true,
                    onValueChange = { onDraftChange(draft.copy(pendingKey = it)) },
                )
                if (state.hasStoredKey && state.profileId != null) {
                    PillButton(
                        text = "清除已保存的密钥",
                        onClick = { onDeleteKey(state.profileId) },
                        style = PillStyle.Text,
                        accent = DomainColors.Reading,
                        testTag = "ai_profile_key_clear",
                        contentDescription = "清除已保存的密钥",
                    )
                }
            }

            state.fieldError?.let {
                Text(
                    text = it.message,
                    style = AppType.Body,
                    color = DomainColors.Reading.deep,
                    modifier = Modifier.testTag("ai_profile_field_error"),
                )
            }
            state.message?.let {
                Text(it, style = AppType.Body, color = DomainColors.Reading.deep, modifier = Modifier.testTag("ai_profile_message"))
            }

            PillButton(
                text = if (state.saving) "保存中…" else "保存",
                onClick = onSave,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { if (state.saving) disabled() },
                style = PillStyle.Primary,
                accent = DomainColors.AiSpeech,
                testTag = "ai_profile_save",
                contentDescription = "保存 AI 配置",
            )

            if (state.profileId != null) {
                PillButton(
                    text = "删除这套配置",
                    onClick = { onDeleteProfile(state.profileId) },
                    modifier = Modifier.fillMaxWidth(),
                    style = PillStyle.Text,
                    accent = DomainColors.Reading,
                    testTag = "ai_profile_delete",
                    contentDescription = "删除这套配置",
                )
            }
        }

        // 音色选择浮层：挂在页面根 Box 上，盖住主内容；主内容容器 frosted 做真模糊。
        GlassOverlay(
            visible = voiceOverlayVisible,
            onDismiss = { voiceOverlayVisible = false },
            modifier = Modifier.testTag("ai_profile_voice_overlay"),
        ) {
            VoiceOverlayOption(
                tag = "ai_profile_voice_auto",
                label = if (draft.voice.isBlank()) "自动（当前）" else "自动（推荐）",
                selected = draft.voice.isBlank(),
                onSelect = {
                    onDraftChange(draft.copy(voice = ""))
                    voiceOverlayVisible = false
                },
            )
            AiVoiceCatalog.optionsFor(state.providerKind).forEach { voice ->
                VoiceOverlayOption(
                    tag = "ai_profile_voice_${voice}",
                    label = if (voice == draft.voice) "$voice（当前）" else voice,
                    selected = voice == draft.voice,
                    onSelect = {
                        onDraftChange(draft.copy(voice = voice))
                        voiceOverlayVisible = false
                    },
                )
            }
        }
    }
}

@Composable
private fun VoiceOverlayOption(
    tag: String,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = AppType.Body,
            color = if (selected) DomainColors.AiSpeech.deep else AppPalette.TextPrimary,
        )
    }
}

/** 玻璃卡分节容器：对齐 SpeechSettingsScreen 的 PreviewCard 写法。 */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = AppShape.Card,
        color = AppPalette.GlassFill,
        border = BorderStroke(1.dp, AppPalette.GlassHighlight),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    tag: String,
    hint: String? = null,
    numeric: Boolean = false,
    masked: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = hint?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics { contentDescription = label },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = AppPalette.Surface,
            unfocusedContainerColor = AppPalette.Surface,
            focusedIndicatorColor = DomainColors.AiSpeech.base,
            unfocusedIndicatorColor = AppPalette.Separator,
            cursorColor = DomainColors.AiSpeech.base,
            focusedLabelColor = DomainColors.AiSpeech.deep,
            unfocusedLabelColor = AppPalette.TextSecondary,
            focusedTextColor = AppPalette.TextPrimary,
            unfocusedTextColor = AppPalette.TextPrimary,
        ),
    )
}

@Composable
private fun CapabilityToggle(capability: AiCapability, selected: Boolean, onToggle: (Boolean) -> Unit) {
    val background = if (selected) DomainColors.AiSpeech.base.copy(alpha = 0.12f) else AppPalette.Surface
    val border = if (selected) DomainColors.AiSpeech.base else AppPalette.Separator
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, AppShape.Button)
            .background(background, AppShape.Button)
            .toggleable(value = selected, onValueChange = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("ai_profile_capability_${capability.name.lowercase()}")
            .semantics { contentDescription = "${capability.label}能力" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(capability.label, style = AppType.Title)
            Text(
                text = if (selected) "已启用" else "未启用",
                style = AppType.Footnote,
            )
        }
    }
}

private fun hostOf(endpoint: String): String =
    runCatching { java.net.URI(endpoint).host }.getOrNull() ?: endpoint

private fun capabilitiesLabel(capabilities: Set<AiCapability>): String =
    if (capabilities.isEmpty()) {
        "未声明能力"
    } else {
        capabilities.sortedBy { it.ordinal }.joinToString(" / ") { it.label }
    }

private val AiCapability.label: String
    get() = when (this) {
        AiCapability.Text -> "文本"
        AiCapability.Vision -> "读图"
        AiCapability.Speech -> "语音"
        AiCapability.ImageGeneration -> "生图"
    }
