package com.example.englishlearning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiVoiceCatalog
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.EntryRow
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.GlassOverlay
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.components.glass.frosted
import com.example.englishlearning.ui.components.hairlineTop
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.LocalIsDark
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * 「设置 · AI」下的 AI 服务管理页（二级全屏层）。
 *
 * 同一个 composable 承担列表与编辑两态：`editor == null` 时是列表，否则是编辑页。这样返回键
 * 只有一条链路——编辑态下先关编辑页、列表态下才退出整层，不需要在 [AppScreen] 里再排一次顺序。
 *
 * 密钥输入框只写不回显：编辑已有配置时永远显示「已设置密钥 / 还没有设置密钥」，不把已保存的值
 * 填进输入框。
 *
 * 批 4 Task 3（版式重做，spec §5「AI 服务」行）：整屏按「列表 = 页头 + 裸行」/「编辑 = 页头 +
 * 3 枚软卡 + 吸底保存」重排。**数据与安全零改动**——字段顺序、tag、校验文案一字不动，这一屏只搬外观；
 * 危险动作（清除密钥 / 删除配置）不再点了就执行：**屏内零对话框**，两枚按钮只把请求交给宿主
 * `AppScreen`，由宿主的 `ConfirmDestructiveDialog` 统一确认（判据见 `AiProfileLayoutContractTest`
 * 与 `AppScreenOverlayContainerContractTest` 的守卫站点）。
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
    onRequestDeleteProfile: (String) -> Unit,
    onRequestDeleteKey: (String) -> Unit,
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
            onRequestDeleteProfile = onRequestDeleteProfile,
            onRequestDeleteKey = onRequestDeleteKey,
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
        // 旧「← 返回上一层」药片下线：返回位归共享页头（contentDescription 固定「返回」）。
        // 这一层有两个入口（「设置 · AI 服务与密钥」与「AI 助学」功能页的「去配置 AI 服务」），
        // 页头那枚返回位不写死具体 tab，所以从 AI 助学进来时也不会指错地方。
        AppScreenHeader(
            title = "AI 服务",
            subtitle = "可以配置多套 OpenAI 兼容服务。密钥保存在本机系统安全区，不写进数据库、日志或备份。",
            onBack = onBack,
        )

        when (state) {
            AiProfileListUiState.Loading -> Text("正在读取本地配置…", style = AppType.Body, color = appColors().text2)
            AiProfileListUiState.Unavailable -> Text(
                text = "本机配置暂时读不出来，稍后再试。",
                style = AppType.Body,
                color = appColors().text2,
                modifier = Modifier.testTag("ai_profiles_unavailable"),
            )
            is AiProfileListUiState.Ready -> {
                state.message?.let {
                    Text(it, style = AppType.Body, color = appDomain(AppDomain.Danger).deep, modifier = Modifier.testTag("ai_profiles_message"))
                }
                if (state.items.isEmpty()) {
                    Text(
                        text = "还没有任何 AI 配置。添加一套之后，「AI 助学」里的功能才有可以调用的服务。",
                        style = AppType.Body,
                        color = appColors().text2,
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
            domain = AppDomain.AiSpeech,
            testTag = "ai_profiles_add",
            contentDescription = "新增 AI 配置",
        )
        // 原型 `sAiProfiles` 页末 `notice(…, 'info')`：把本地优先的下限讲清楚——没配密钥不等于不能用。
        // 它不是安慰句：背词、复习、听写、本机 OCR 都不经过 AI 服务（AGENTS「本地优先」）。
        InlineNotice(
            text = "没有配置密钥时，所有不依赖 AI 的功能照常可用：背词、复习、听写、本机 OCR。",
            kind = InlineNoticeKind.Info,
            modifier = Modifier.testTag("ai_profiles_offline_notice"),
        )
    }
}

/**
 * 列表行：裸行（标题 + 「模型 · 域名」副标题 + 密钥态尾部值）下面挂一条「能力 / 默认位」动作带。
 *
 * 旧版是一枚描边 `Surface` 卡片，一屏多行时就是「卡片汤」；批 4 Task 3 按 spec §4.3 改成裸行：
 * 容器归共享 [BareRow]（自带 ≥48dp 命中区、1dp 发丝线与 52dp 缩进），点行去编辑。
 * 默认位那两枚可点面（`设为文章默认 / 设为生图默认`）收在行下动作带里，不再包一层卡片。
 */
@Composable
private fun AiProfileRow(
    item: AiProfileListItem,
    onEdit: (String) -> Unit,
    onSetDefaultTextProfile: (String) -> Unit,
    onSetDefaultImageProfile: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BareRow(
            title = item.profile.displayName,
            subtitle = "${item.profile.model} · ${hostOf(item.profile.endpoint)}",
            trailing = if (item.hasKey) "已设置密钥" else "还没有设置密钥",
            trailingTag = "ai_profile_key_state_${item.profile.profileId}",
            modifier = Modifier
                .testTag("ai_profile_item_${item.profile.profileId}")
                .semantics { contentDescription = "编辑配置 ${item.profile.displayName}" },
            onClick = { onEdit(item.profile.profileId) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 15.dp, end = 15.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = capabilitiesLabel(item.profile.capabilities),
                style = AppType.Label,
                color = appColors().text2,
                modifier = Modifier.weight(1f),
            )
            when {
                item.isDefaultTextProfile -> Text(
                    text = "文章默认",
                    style = AppType.Label,
                    color = appDomain(AppDomain.AiSpeech).deep,
                    modifier = Modifier.testTag("ai_profile_default_${item.profile.profileId}"),
                )
                item.canBeDefaultTextProfile -> PillButton(
                    text = "设为文章默认",
                    onClick = { onSetDefaultTextProfile(item.profile.profileId) },
                    style = PillStyle.Text,
                    domain = AppDomain.AiSpeech,
                    modifier = Modifier.testTag("ai_profile_set_default_${item.profile.profileId}"),
                    contentDescription = "将 ${item.profile.displayName} 设为文章默认",
                )
            }
            when {
                item.isDefaultImageProfile -> Text(
                    text = "生图默认",
                    style = AppType.Label,
                    color = appDomain(AppDomain.AiSpeech).deep,
                    modifier = Modifier.testTag("ai_profile_image_default_${item.profile.profileId}"),
                )
                item.canBeDefaultImageProfile -> PillButton(
                    text = "设为生图默认",
                    onClick = { onSetDefaultImageProfile(item.profile.profileId) },
                    style = PillStyle.Text,
                    domain = AppDomain.AiSpeech,
                    modifier = Modifier.testTag("ai_profile_set_image_default_${item.profile.profileId}"),
                    contentDescription = "将 ${item.profile.displayName} 设为生图默认",
                )
            }
        }
    }
}

/**
 * 编辑页：13 个真控件按「服务与模型 / 语音 / 高级与密钥」归 3 枚 [SoftCard]，外加第 4 枚
 * 「未接入的选项」（批 3 按裁决 B 从原型补回：四枚禁用行，标注「暂未接入」，见 [UnsupportedOption]），
 * 保存固定吸底。
 *
 * 第 4 枚卡**只装禁用行**：它不持有任何可写入草稿的控件，13 个真控件的分组归属不受它影响。
 *
 * 保存按钮原来跟着表单滚到底部（长表单里最后才看见），现行版式把它从流里搬到页底的吸底条：
 * 滚动面只到吸底条上沿为止，两者互不遮挡（`ai_profile_save` 全文件仍恰 1 处，流内没有第二枚保存入口）。
 */
@Composable
private fun AiProfileEditor(
    state: AiProfileEditorUiState,
    onDraftChange: (AiProfileDraft) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onRequestDeleteProfile: (String) -> Unit,
    onRequestDeleteKey: (String) -> Unit,
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
                .background(MintBackground),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .frosted(voiceOverlayVisible)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .testTag("ai_profile_editor"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 旧「← 返回 AI 服务」药片下线：返回位归共享页头，接的是「关编辑页」那条链路。
                AppScreenHeader(
                    title = if (state.profileId == null) "新增 AI 配置" else "编辑 AI 配置",
                    onBack = onClose,
                )

                // ---- 第 1 组：服务与模型（4 个字段 + 4 枚能力开关）----
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    Text("服务与模型", style = AppType.Title, color = appColors().text1)
                    Text(
                        text = "填 OpenAI 兼容服务的地址与模型名，并声明这套服务支持的模态；生成文章至少要有「文本」。",
                        style = AppType.Footnote,
                        color = appColors().text2,
                    )
                    EditorField(
                        label = "名称",
                        value = draft.displayName,
                        tag = "ai_profile_name",
                        onValueChange = { onDraftChange(draft.copy(displayName = it)) },
                    )
                    EditorField(
                        label = "网站地址",
                        value = draft.websiteUrl,
                        tag = "ai_profile_website",
                        hint = "https://mimo.mi.com",
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
                    Text("能力", style = AppType.Title, color = appColors().text1)
                    // 四枚能力开关逐枚点名（不遍历 `AiCapability.entries`）：tag 是 connected 侧像素取样带与
                    // 手势锚（`GlassPanelThemeInkTest` 读 `ai_profile_capability_text` / `_vision`），
                    // 由枚举名插值出来时字面在源码里不存在，契约与连通测试都取不到锚点。
                    CapabilityToggle(
                        capability = AiCapability.Text,
                        tag = "ai_profile_capability_text",
                        selected = AiCapability.Text in draft.capabilities,
                        onToggle = { enabled ->
                            val next = if (enabled) draft.capabilities + AiCapability.Text else draft.capabilities - AiCapability.Text
                            onDraftChange(draft.copy(capabilities = next))
                        },
                    )
                    CapabilityToggle(
                        capability = AiCapability.Vision,
                        tag = "ai_profile_capability_vision",
                        selected = AiCapability.Vision in draft.capabilities,
                        onToggle = { enabled ->
                            val next = if (enabled) draft.capabilities + AiCapability.Vision else draft.capabilities - AiCapability.Vision
                            onDraftChange(draft.copy(capabilities = next))
                        },
                    )
                    CapabilityToggle(
                        capability = AiCapability.Speech,
                        tag = "ai_profile_capability_speech",
                        selected = AiCapability.Speech in draft.capabilities,
                        onToggle = { enabled ->
                            val next = if (enabled) draft.capabilities + AiCapability.Speech else draft.capabilities - AiCapability.Speech
                            onDraftChange(draft.copy(capabilities = next))
                        },
                    )
                    CapabilityToggle(
                        capability = AiCapability.ImageGeneration,
                        tag = "ai_profile_capability_image",
                        selected = AiCapability.ImageGeneration in draft.capabilities,
                        onToggle = { enabled ->
                            val next = if (enabled) {
                                draft.capabilities + AiCapability.ImageGeneration
                            } else {
                                draft.capabilities - AiCapability.ImageGeneration
                            }
                            onDraftChange(draft.copy(capabilities = next))
                        },
                    )
                }

                // ---- 第 2 组：语音角色（只在声明了 Speech 能力时出现）----
                if (AiCapability.Speech in draft.capabilities) {
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        Text("语音角色", style = AppType.Title, color = appColors().text1)
                        Text(
                            text = "留空 = 自动：MiMo 按语言选冰糖/Mia，OpenAI 兼容走服务默认。",
                            style = AppType.Footnote,
                            color = appColors().text2,
                        )
                        PillButton(
                            text = if (draft.voice.isBlank()) "自动（推荐）  ▾" else "${draft.voice}  ▾",
                            onClick = { voiceOverlayVisible = true },
                            style = PillStyle.Secondary,
                            domain = AppDomain.AiSpeech,
                            testTag = "ai_profile_voice",
                            contentDescription = "选择语音角色",
                        )
                    }
                }

                // ---- 第 3 组：高级与密钥 ----
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    Text("高级参数", style = AppType.Title, color = appColors().text1)
                    Text(
                        text = "可选范围：temperature 0–2、top_p 0–1、max_tokens 1–4096、超时 5–120 秒。",
                        style = AppType.Footnote,
                        color = appColors().text2,
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
                    Text("密钥", style = AppType.Title, color = appColors().text1)
                    Text(
                        text = if (state.hasStoredKey) "已设置密钥" else "还没有设置密钥",
                        style = AppType.Body,
                        // 批 2 终审修复席 C-1（控制者裁决）：本行 `style = AppType.Body` 是正文档。当时浅套 `AiSpeech.deep`
                        // （#188F76）压浅套玻璃合成底（#FEFFFE）只有 4.006:1，不达 4.5 正文线，不许按装饰档放行。
                        // 按房内先例 `AppBottomBar.kt:95` 的形状按套钉死：浅套正文档取 `appColors().onMint`
                        // （#174C44，压同底 9.727:1），深套沿用 `deep`（压深套容器 ≥4.5，由 GlassPanelThemeInkTest 深套腿当场钉）。
                        // 浅套观感有变（薄荷绿墨→深墨）属修 AA 失败，列入 18-ii 目检。
                        // 2026-10-27 轻松档补记：deep 已换到 #1A7A63，压同底升到 5.227 本可独用；分档**不回退**——
                        // 全房一致口径（浅套 AiSpeech 文本一律 onMint）比「刚好过线」优先，且 5.227 的余量只有 0.73。
                        // 批 4 Task 3：容器的玻璃分节卡换成不透明 `SoftCard`，本行的取墨决定一字不动（判据由
                        // `AiProfileLayoutContractTest` 逐字钉住）。
                        color = if (!LocalIsDark.current) appColors().onMint else appDomain(AppDomain.AiSpeech).deep,
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
                        // 批 4 Task 3：清除密钥是不可撤销动作，屏内不再直接执行——只把请求发给宿主确认框。
                        PillButton(
                            text = "清除已保存的密钥",
                            onClick = { onRequestDeleteKey(state.profileId) },
                            style = PillStyle.Text,
                            domain = AppDomain.Danger,
                            testTag = "ai_profile_key_clear",
                            contentDescription = "清除已保存的密钥",
                        )
                    }
                }

                // ---- 第 4 组：原型有、V1 协议不放行的选项（裁决 B：画出来、禁用、标「暂未接入」）----
                // 原型的编辑页还有流式返回、音色细化、生图参数与「连接测试」四段；它们要么超出
                // V1 高级参数白名单（`temperature/top_p/max_tokens/timeout_seconds` 与受控模板），
                // 要么需要本仓还没有的探活端点。既不能照搬成真控件（会让用户以为可调），
                // 也不能整块消失（原型的版面是产品承诺的形状）——所以逐项画出来、真的禁用、统一标注。
                SoftCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_profile_unsupported_card"),
                ) {
                    Text("未接入的选项", style = AppType.Title, color = appColors().text1)
                    Text(
                        text = "这几项在 V1 的请求白名单之外，先把位置留出来；接通后在这里放开。",
                        style = AppType.Footnote,
                        color = appColors().text2,
                    )
                    UnsupportedOption(
                        icon = AppIcon.Spark,
                        title = "流式返回",
                        subtitle = "边生成边显示；V1 只支持一次性返回",
                        tag = "ai_profile_unsupported_stream",
                    )
                    UnsupportedOption(
                        icon = AppIcon.Speaker,
                        title = "音色细化参数",
                        subtitle = "语速、音高、输出格式；V1 只选音色角色",
                        tag = "ai_profile_unsupported_voice",
                    )
                    UnsupportedOption(
                        icon = AppIcon.Image,
                        title = "生图参数",
                        subtitle = "画面比例、风格预设、张数与参考图",
                        tag = "ai_profile_unsupported_image",
                    )
                    UnsupportedOption(
                        icon = AppIcon.Info,
                        title = "连接测试",
                        subtitle = "发一次探活请求验证地址与密钥；V1 还没有探活端点",
                        tag = "ai_profile_unsupported_connection_test",
                    )
                }

                state.fieldError?.let {
                    Text(
                        text = it.message,
                        style = AppType.Body,
                        color = appDomain(AppDomain.Danger).deep,
                        modifier = Modifier.testTag("ai_profile_field_error"),
                    )
                }
                state.message?.let {
                    Text(it, style = AppType.Body, color = appDomain(AppDomain.Danger).deep, modifier = Modifier.testTag("ai_profile_message"))
                }

                if (state.profileId != null) {
                    // 删除配置同上一枚：危险动作只发请求，确认框归宿主 AppScreen。
                    PillButton(
                        text = "删除这套配置",
                        onClick = { onRequestDeleteProfile(state.profileId) },
                        modifier = Modifier.fillMaxWidth(),
                        style = PillStyle.Text,
                        domain = AppDomain.Danger,
                        testTag = "ai_profile_delete",
                        contentDescription = "删除这套配置",
                    )
                }
            }

            // 吸底保存条：滚动面之上的独立一档，`ai_profile_save` 全文件只此一处。
            // 槽色与屏体软卡同为 `colors.surface`，故槽顶压一条发丝线（批 5 遗留 7 收口，
            // 与 `BackupExportScreen.kt:221` 同形）：2× 字号下表单卡片正好断在槽沿上，没有边界线
            // 会被读成「卡片内容被截断」。
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = appColors().surface,
                    modifier = hairlineTop(appColors().outline)
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .testTag("ai_profile_save_bar"),
                ) {
                    PillButton(
                        text = if (state.saving) "保存中…" else "保存",
                        onClick = onSave,
                        enabled = !state.saving,
                        testTag = "ai_profile_save",
                        contentDescription = "保存 AI 配置",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        style = PillStyle.Primary,
                        domain = AppDomain.AiSpeech,
                    )
                }
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

/**
 * 未接入控件的**唯一配方**（裁决 B）：一行禁用入口行，尾部值统一「暂未接入」。
 *
 * `enabled = false` 走共享 [EntryRow] 的禁用态——链上不挂 `clickable`、补 `disabled()` 语义，
 * 所以它不是「能点却不作为」的假入口，TalkBack 也会读出「不可用」。`onClick` 是 [EntryRow] 的
 * 必填形参，这里给空体：禁用态下它永远不会被调用（不是守卫，是形状要求）。
 * `showChevron = false`：chevron 是「点得进去」的暗示，禁用行不画。
 *
 * 四枚行共用一处实现，不要在调用点逐枚手搓：一处改动（例如将来接通后放开某项）只改这里。
 */
@Composable
private fun UnsupportedOption(
    icon: AppIcon,
    title: String,
    subtitle: String,
    tag: String,
) {
    EntryRow(
        icon = icon,
        domain = AppDomain.AiSpeech,
        title = title,
        subtitle = subtitle,
        trailing = "暂未接入",
        showChevron = false,
        enabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        onClick = {},
    )
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
            // 容器与前景已于批 2 终审修复席 B 同批迁：`GlassOverlay` 面板底现在是 `appColors().surfaceGlass`
            // （浅套逐值 = 旧的 `AppPalette.GlassFill`，深色档才换成套深玻璃），所以这一行的墨必须跟着按套取值，
            // 否则深色档就是「浅墨压白玻璃 → 深墨压深玻璃」两侧各错一次（Task 12.5 修复轮 C-2 当年回退的前提
            // 「面板未迁」已随本批消失；登记见 DarkTokenConsumptionRatchetTest 的 GlassFillBackdropDebtLedger 收口）。
            // 批 2 终审修复席 C/C-1（控制者裁决，B 席 concerns ①）：本行是 `style = AppType.Body` 正文档 ⇒ 阈值就是 ≥4.5，
            // 不许按装饰档 3.1 放行。本席现算（WCAG 2.1，浅玻璃实底 = surfaceGlass #EFFFFFFF src-over 页底 #F6F9F6 = #FEFFFE，
            // 深玻璃实底 = #E617201C src-over #111A16 = #161F1B）：修复前的浅套域色墨 #188F76 压 #FEFFFE 只有
            // **4.006:1**，不达正文线；C-1 席按 `AppBottomBar.kt:95` 的形状按套归位后
            // 浅套取 `appColors().onMint`：#174C44 压 #FEFFFE = **9.727:1**，
            // 深套沿用 `appDomain(AppDomain.AiSpeech).deep`：#6FD9B6 压 #161F1B = **9.853:1**（2026-10-27 轻松档现算），
            // 两套都远高于正文线，且浅档取的是房内既有 token：不新增静态色、不动文案与 testTag。
            // 浅套这一处观感有变（选中行由薄荷绿墨变深墨），属修 AA 失败而非回归，已列入 18-ii 用户目检；
            // 判据见 GlassPanelInkPairingContractTest 的现算闸（诱饵已换成 text-3 与 #1E987C 两枚装饰档墨）
            // 与 GlassPanelThemeInkTest 两套像素腿。
            // 2026-10-27 轻松档补记：浅套 deep 换到 #1A7A63 后压 #FEFFFE 是 5.227，已过 4.5；分档仍不回退（全房一致）。
            color = if (selected && !LocalIsDark.current) appColors().onMint else if (selected) appDomain(AppDomain.AiSpeech).deep else appColors().text1,
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
            focusedContainerColor = appColors().surface,
            unfocusedContainerColor = appColors().surface,
            focusedIndicatorColor = appDomain(AppDomain.AiSpeech).base,
            unfocusedIndicatorColor = appColors().outlineSoft,
            cursorColor = appDomain(AppDomain.AiSpeech).base,
            focusedLabelColor = appDomain(AppDomain.AiSpeech).deep,
            unfocusedLabelColor = appColors().text2,
            focusedTextColor = appColors().text1,
            unfocusedTextColor = appColors().text1,
        ),
    )
}

@Composable
private fun CapabilityToggle(capability: AiCapability, tag: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
    // 批 2 终审修复席 B：容器与前景同批迁（Task 12.5 修复轮 C-1 第二半的前提已消失）。
    // 批 4 Task 3 把外层的玻璃分节卡换成不透明 `SoftCard`，本行的取墨决定一字不动：选中档填充与
    // 未选中档的底/描边照旧按套取值（浅套逐值不变，深色档得到「套内 surface + 浅墨 + 亮域色」这一对）。
    val background = if (selected) appDomain(AppDomain.AiSpeech).base.copy(alpha = 0.12f) else appColors().surface
    val border = if (selected) appDomain(AppDomain.AiSpeech).base else appColors().outlineSoft
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, AppShape.Button)
            .background(background, AppShape.Button)
            .toggleable(value = selected, onValueChange = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(tag)
            .semantics { contentDescription = "${capability.label}能力" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(capability.label, style = AppType.Title, color = appColors().text1)
            Text(
                text = if (selected) "已启用" else "未启用",
                style = AppType.Footnote,
                color = appColors().text2,
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
