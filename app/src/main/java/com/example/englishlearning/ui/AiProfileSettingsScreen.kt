package com.example.englishlearning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint

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
            onBack = onBack,
        )
    }
}

@Composable
private fun AiProfileList(
    state: AiProfileListUiState,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
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
        TextButton(
            onClick = onBack,
            colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark),
            // 这一层有两个入口：「设置 · AI 服务与密钥」和「AI 学」功能页的「去配置 AI 服务」。
            // 从后者进来时底部选中的仍是 AI 学栏，写死「返回设置」就会指错地方，所以用中性的表述。
            modifier = Modifier.semantics { contentDescription = "返回上一层" },
        ) { Text("← 返回上一层", fontWeight = FontWeight.Bold) }

        Text("AI 服务", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text(
            text = "可以配置多套 OpenAI 兼容服务。密钥保存在本机系统安全区，不写进数据库、日志或备份。",
            style = MaterialTheme.typography.bodySmall,
            color = MintTextMuted,
        )

        when (state) {
            AiProfileListUiState.Loading -> Text("正在读取本地配置…", color = MintTextMuted)
            AiProfileListUiState.Unavailable -> Text(
                text = "本机配置暂时读不出来，稍后再试。",
                color = MintTextMuted,
                modifier = Modifier.testTag("ai_profiles_unavailable"),
            )
            is AiProfileListUiState.Ready -> {
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai_profiles_message"))
                }
                if (state.items.isEmpty()) {
                    Text(
                        text = "还没有任何 AI 配置。添加一套之后，「AI 学」里的功能才有可以调用的服务。",
                        color = MintTextMuted,
                        modifier = Modifier.testTag("ai_profiles_empty"),
                    )
                }
                state.items.forEach { item ->
                    AiProfileRow(item = item, onEdit = onEdit)
                }
            }
        }

        Button(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("ai_profiles_add")
                .semantics { contentDescription = "新增 AI 配置" },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
        ) { Text("新增配置", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun AiProfileRow(item: AiProfileListItem, onEdit: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(22.dp))
            .testTag("ai_profile_item_${item.profile.profileId}")
            .clickable { onEdit(item.profile.profileId) }
            .semantics { contentDescription = "编辑配置 ${item.profile.displayName}" },
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.profile.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            Text(
                text = "${item.profile.model} · ${hostOf(item.profile.endpoint)}",
                style = MaterialTheme.typography.bodySmall,
                color = MintTextMuted,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (item.hasKey) "已设置密钥" else "还没有设置密钥",
                    style = MaterialTheme.typography.labelSmall,
                    color = MintPrimaryDark,
                    modifier = Modifier
                        .background(MintTint, RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                        .testTag("ai_profile_key_state_${item.profile.profileId}"),
                )
                Text(
                    text = capabilitiesLabel(item.profile.capabilities),
                    style = MaterialTheme.typography.labelSmall,
                    color = MintTextMuted,
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("ai_profile_editor"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(
            onClick = onClose,
            colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark),
            modifier = Modifier.semantics { contentDescription = "返回 AI 服务" },
        ) { Text("← 返回 AI 服务", fontWeight = FontWeight.Bold) }

        Text(
            text = if (state.profileId == null) "新增 AI 配置" else "编辑 AI 配置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MintPrimaryDark,
        )

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

        Text("能力", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text("声明这套服务支持的模态；生成文章至少要有「文本」。", style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

        Text("高级参数", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text(
            text = "可选范围：temperature 0–2、top_p 0–1、max_tokens 1–4096、超时 5–120 秒。",
            style = MaterialTheme.typography.bodySmall,
            color = MintTextMuted,
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

        Text("密钥", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text(
            text = if (state.hasStoredKey) "已设置密钥" else "还没有设置密钥",
            style = MaterialTheme.typography.bodyMedium,
            color = MintPrimaryDark,
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
            TextButton(
                onClick = { onDeleteKey(state.profileId) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("ai_profile_key_clear").semantics { contentDescription = "清除已保存的密钥" },
            ) { Text("清除已保存的密钥") }
        }

        state.fieldError?.let {
            Text(
                text = it.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("ai_profile_field_error"),
            )
        }
        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai_profile_message"))
        }

        Button(
            onClick = onSave,
            enabled = !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("ai_profile_save")
                .semantics { contentDescription = "保存 AI 配置" },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MintPrimary,
                contentColor = Color.White,
                disabledContainerColor = MintTint,
                disabledContentColor = MintTextMuted,
            ),
        ) { Text(if (state.saving) "保存中…" else "保存", fontWeight = FontWeight.Bold) }

        if (state.profileId != null) {
            TextButton(
                onClick = { onDeleteProfile(state.profileId) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_profile_delete")
                    .semantics { contentDescription = "删除这套配置" },
            ) { Text("删除这套配置", fontWeight = FontWeight.Bold) }
        }
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
            focusedContainerColor = MintSurface,
            unfocusedContainerColor = MintSurface,
            focusedIndicatorColor = MintPrimary,
            unfocusedIndicatorColor = MintOutline,
            cursorColor = MintPrimary,
            focusedLabelColor = MintPrimaryDark,
            unfocusedLabelColor = MintTextMuted,
            focusedTextColor = MintPrimaryDark,
            unfocusedTextColor = MintPrimaryDark,
        ),
    )
}

@Composable
private fun CapabilityToggle(capability: AiCapability, selected: Boolean, onToggle: (Boolean) -> Unit) {
    val background = if (selected) MintTint else MintSurface
    val border = if (selected) MintPrimary else MintOutline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .background(background, RoundedCornerShape(16.dp))
            .toggleable(value = selected, onValueChange = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("ai_profile_capability_${capability.name.lowercase()}")
            .semantics { contentDescription = "${capability.label}能力" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(capability.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            Text(
                text = if (selected) "已启用" else "未启用",
                style = MaterialTheme.typography.bodySmall,
                color = MintTextMuted,
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
        AiCapability.Vision -> "图片"
        AiCapability.Speech -> "语音"
    }
