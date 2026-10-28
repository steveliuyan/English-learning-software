package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.UserAction
import com.example.englishlearning.ai.toUserAction
import com.example.englishlearning.reading.NotConfiguredReason
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
import com.example.englishlearning.ui.components.AppGlyph
import com.example.englishlearning.ui.components.AppGlyphLayer
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.ConfirmDestructiveDialog
import com.example.englishlearning.ui.components.EntryIconBadge
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.OutboundConfirmDialog
import com.example.englishlearning.ui.components.PayloadKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTint
import com.example.englishlearning.ui.theme.MintTextMuted

sealed interface ReadingAccessUiState {
    data object Loading : ReadingAccessUiState
    /**
     * 计划未完成。只挡「今天生成新文章」，所以仍带着已读到的 [history]：
     * 历史文章与解锁无关，任何时候都可重读。
     */
    data class Locked(val reason: String, val history: List<Article> = emptyList()) : ReadingAccessUiState
    data class Ready(
        val preference: ReadingPreference,
        val history: List<Article>,
        val todayArticle: Article? = null,
        val generation: GenerationUiState = GenerationUiState.Idle,
        val import: ImportUiState = ImportUiState(),
        val feed: FeedUiState = FeedUiState.Idle,
    ) : ReadingAccessUiState {
        val historyCount: Int get() = history.size
    }
    data object Unavailable : ReadingAccessUiState
}

/**
 * 该状态下可以重读的本地历史文章。
 *
 * 阅读历史与解锁状态**正交**：锁定只限制「当天新文章的生成入口」，历史文章始终可重读
 * （AGENTS.md：历史已解锁文章始终可重读）。因此这里是一个**全量**访问器——每种状态都必须
 * 表态，调用点不能再用 `as? Ready` 把锁定态的 history 悄悄丢成空列表。旧写法正是那样：
 * 2026-10-05 真机走查里今日计划未完成（Locked），点「查看本地阅读历史」恒显示
 * 「暂无本地文章」，而同一个库中确有 2026-09-28 的文章。
 */
val ReadingAccessUiState.historyOrEmpty: List<Article>
    get() = when (this) {
        is ReadingAccessUiState.Ready -> history
        is ReadingAccessUiState.Locked -> history
        ReadingAccessUiState.Loading, ReadingAccessUiState.Unavailable -> emptyList()
    }

/**
 * 「阅读」栏的根页面。一级 tab，出口交给底部导航与页内二级层，所以**没有**自带的返回按钮。
 *
 * 版面按裁决⑤ 重排（spec §6 表格 ⑤ 行），版式重做批 1 Task 7 再按 §3.4 收口：
 *  ① 主任务（今日文章）上提第一屏——今日文章收进一枚具名卡（`reading_today_article`），
 *    卡里只放「这是今天的文章 + 阅读 CTA」；无文章时同一位置换成生成 CTA；
 *  ② 「换一篇」搬进共享页头 `action` 槽（`reading_regenerate`），点开先过共享二次确认——
 *    它会替换掉当天那篇文章，不是一次无痕刷新；
 *  ③ 外刊 Feed / 粘贴 / 历史三枚入口收进同一个「更多」组（`reading_more_group`），不再平铺大按钮；
 *  ④ 锁定态一句说明（`reading_access_locked_types`）替代四枚 disabled 类型按钮：锁定只挡「今天生成」，
 *    类型选择属于生成前参数，没有生成入口时不该占版面；历史入口照常保留。
 *
 * Ready 态的三个来源入口（AI 生成 / 外刊选取 / 粘贴文章）与生成状态都由
 * [ReadingAccessViewModel] 编排；本文件只做呈现，不持有任何业务判断。
 */
@Composable
fun ReadingAccessScreen(
    state: ReadingAccessUiState,
    onSelectType: (ArticleType) -> Unit,
    onOpenHistory: () -> Unit,
    onGenerate: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    onOpenTodayArticle: () -> Unit = {},
    onConfirmOutbound: (Boolean) -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenFeed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    // 生成前参数的展开态是本屏的呈现状态，不进 ViewModel：它不改变任何持久化取值，
    // 只决定偏好区这一支此刻画不画（brief §二.3「偏好写入路径不改」）。
    var generationParamsExpanded by remember { mutableStateOf(false) }
    // 「换一篇」的二次确认态同理：它只决定对话框画不画，生成动作仍由宿主转给 ViewModel。
    var regenerateConfirm by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("reading_access_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 一级 tab 页头：无返回键；「换一篇」只在今天已有文章时进 action 槽——
        // 没文章可换时那枚按钮不该在场（也就不必回答「换掉的是什么」）。
        AppScreenHeader(
            title = "每日阅读",
            hero = true,
            badge = { EntryIconBadge(icon = AppIcon.Newspaper, domain = AppDomain.Reading) },
            action = if (state is ReadingAccessUiState.Ready && state.todayArticle != null) {
                {
                    TextButton(
                        onClick = { regenerateConfirm = true },
                        modifier = Modifier.testTag("reading_regenerate"),
                    ) {
                        Text("换一篇", style = AppType.Body, color = colors.text2)
                    }
                }
            } else {
                null
            },
        )
        when (state) {
            ReadingAccessUiState.Loading -> Text("正在读取本地阅读状态…", style = AppType.Footnote, color = colors.text2)
            ReadingAccessUiState.Unavailable -> Text("本地阅读状态暂时无法读取", style = AppType.Footnote, color = colors.text2)
            is ReadingAccessUiState.Locked -> {
                AccessCard("文章尚未解锁", state.reason, "reading_access_locked_reason")
                // 锁定态删掉了四枚 disabled 类型按钮：类型是「生成前参数」，而锁定态根本没有生成入口。
                // 一句话把「为什么锁、什么时候能生成、历史还能不能读」讲清，比四枚点不动的按钮更有用。
                Text(
                    "今日计划完成后可生成新文章；已解锁的历史文章随时可读。",
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.testTag("reading_access_locked_types"),
                )
                // 条数照常显示：锁定态的历史入口与解锁态是同一件事，没有理由藏起来。
                HistoryButton(onOpenHistory, state.history.size)
            }
            is ReadingAccessUiState.Ready -> ReadyContent(
                state = state,
                generationParamsExpanded = generationParamsExpanded,
                onToggleGenerationParams = { generationParamsExpanded = !generationParamsExpanded },
                onSelectType = onSelectType,
                onOpenHistory = onOpenHistory,
                onGenerate = onGenerate,
                onOpenTodayArticle = onOpenTodayArticle,
                onConfirmOutbound = onConfirmOutbound,
                onOpenAiSettings = onOpenAiSettings,
                onOpenImport = onOpenImport,
                onOpenFeed = onOpenFeed,
            )
        }
        ConfirmDestructiveDialog(
            state = regenerateConfirm,
            title = "换一篇今天的文章？",
            cascadeLines = listOf("当前这篇文章会被新生成的文章替换"),
            keepLines = listOf("已解锁的历史文章与阅读记录不受影响"),
            irreversible = false,
            confirmLabel = "换一篇",
            onConfirm = {
                regenerateConfirm = false
                onRegenerate()
            },
            onDismiss = { regenerateConfirm = false },
        )
    }
}

@Composable
private fun ReadyContent(
    state: ReadingAccessUiState.Ready,
    generationParamsExpanded: Boolean,
    onToggleGenerationParams: () -> Unit,
    onSelectType: (ArticleType) -> Unit,
    onOpenHistory: () -> Unit,
    onGenerate: () -> Unit,
    onOpenTodayArticle: () -> Unit,
    onConfirmOutbound: (Boolean) -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenFeed: () -> Unit,
) {
    val colors = appColors()
    // —— 主任务区（裁决⑤ 第一件 + §3.4）：紧跟页头，第一屏不滚动即可点。
    // 主 CTA 恰一枚：无当日文章时这一格是「生成今天的文章」，已有当日文章时同一位置换成「阅读今天的文章」。
    // 「换一篇」已搬进页头 action，`reading_source_ai` 与 `reading_regenerate` 因此永不并列。
    if (state.todayArticle != null) {
        // 今日文章卡：Reading 域 12% 底压在 `surface` 上（§4.4 一屏一域色），只讲「这是哪一篇」与「去读」。
        val readingPalette = appDomain(AppDomain.Reading)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .background(readingPalette.base.copy(alpha = 0.12f))
                .padding(16.dp)
                .testTag("reading_today_article"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("今日文章", style = AppType.Label, color = readingPalette.deep)
            Text(
                state.todayArticle.title,
                style = AppType.Title,
                color = colors.text1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.todayArticle.chineseText.lineSequence().firstOrNull().orEmpty(),
                style = AppType.Footnote,
                color = colors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${state.todayArticle.articleType.label} · ${state.todayArticle.lengthTier.label}",
                style = AppType.Label,
                color = colors.text2,
            )
            PrimaryCtaButton("阅读今天的文章", "reading_open_today", "阅读今天的文章", onOpenTodayArticle)
        }
    } else {
        Text("今天还没有生成文章", style = AppType.Title, color = colors.text1)
        PrimaryCtaButton("生成今天的文章", "reading_source_ai", "用 AI 生成今天的文章", onGenerate)
    }
    Text(
        "每次生成都会真实调用你配置的 AI 服务并可能产生费用",
        style = AppType.Footnote,
        color = colors.text2,
        modifier = Modifier
            .padding(top = 0.dp)
            .testTag("generation_cost_hint")
            .semantics { contentDescription = "生成费用提示" },
    )

    AccessCard(
        "文章已解锁",
        "三种来源任选：AI 生成、外刊选取，或粘贴你自己的文章。",
        "reading_access_unlocked",
    )

    when (val generation = state.generation) {
        GenerationUiState.Generating -> Text(
            "正在生成…",
            style = AppType.Footnote,
            color = colors.text2,
            modifier = Modifier.testTag("generation_status"),
        )
        GenerationUiState.StorageFailed -> Box(Modifier.testTag("generation_storage_failed")) {
            // Task 26 Step 3：错误行收口到共享 `InlineNotice`；标签留在外层（组件的 `inline_notice_error`
            // 是后设的，同一 modifier 会被覆盖）。
            InlineNotice("本地存储暂时不可用，文章没有保存。", InlineNoticeKind.Error)
        }
        is GenerationUiState.Failed -> GenerationFailureBanner(generation.failure, onOpenAiSettings)
        is GenerationUiState.NotConfigured -> NotConfiguredBanner(generation.reason, onOpenAiSettings)
        // 出站确认走共享件（本席是它第一枚生产接线）：载荷档位显式给 `PayloadKind.Text`。
        // 屏侧锚 `outbound_confirmation_text` 随接线收窄为「本屏出站确认槽位」，
        // 正文节点的权威锚是共享件自带的 `outbound_confirm_body`；两枚锚由同一枚 connected 用例点名。
        is GenerationUiState.NeedsConfirmation -> Box(Modifier.testTag("outbound_confirmation_text")) {
            OutboundConfirmDialog(
                endpointHost = generation.host,
                payloadKind = PayloadKind.Text,
                onConfirm = { onConfirmOutbound(true) },
                onDismiss = { onConfirmOutbound(false) },
            )
        }
        GenerationUiState.Idle -> Unit
    }

    GenerationParamsToggle(expanded = generationParamsExpanded, onClick = onToggleGenerationParams)
    if (generationParamsExpanded) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MintTint),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().testTag("reading_preference"),
        ) {
            Text(
                "默认类型：${state.preference.defaultArticleType.label} · 长度：${state.preference.explicitLengthTier?.label ?: "按词书默认"}",
                color = MintPrimaryDark,
                modifier = Modifier.padding(18.dp),
            )
        }
        ArticleType.entries.forEach { type ->
            TypeButton(type, onSelectType)
        }
    }

    // —— 「更多」组：外刊 / 粘贴 / 历史三枚入口同住一张软卡（§3.4 第 3 块）。
    // 三枚都是导航动作、同一层级，所以用裸行而不是三条各自抢眼的按钮；历史条数落在副标题里。
    SoftCard(
        modifier = Modifier.testTag("reading_more_group"),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        BareRow(
            title = "外刊选读",
            subtitle = "从已核验的来源挑一篇",
            leading = {
                AppGlyph(
                    icon = AppIcon.Newspaper,
                    tint = appDomain(AppDomain.Reading).deep,
                    size = 20.dp,
                    layer = AppGlyphLayer.StrokeOnly,
                )
            },
            modifier = Modifier.testTag("reading_more_feed"),
            onClick = onOpenFeed,
        )
        BareRow(
            title = "粘贴导入",
            subtitle = "用你自己的文章学",
            leading = {
                AppGlyph(
                    icon = AppIcon.Document,
                    tint = appDomain(AppDomain.Reading).deep,
                    size = 20.dp,
                    layer = AppGlyphLayer.StrokeOnly,
                )
            },
            topDivider = true,
            modifier = Modifier.testTag("reading_more_import"),
            onClick = onOpenImport,
        )
        BareRow(
            title = "阅读历史",
            subtitle = "共 ${state.historyCount} 篇",
            leading = {
                AppGlyph(
                    icon = AppIcon.List,
                    tint = appDomain(AppDomain.Reading).deep,
                    size = 20.dp,
                    layer = AppGlyphLayer.StrokeOnly,
                )
            },
            topDivider = true,
            modifier = Modifier.testTag("reading_more_history"),
            onClick = onOpenHistory,
        )
    }
}

/**
 * 主 CTA 一档：`MintPrimary` 容器 + `appColors().onBrand` 前景。
 *
 * 容器 `MintPrimary` 是 `LocalDomainColors` 的组合期读点，两套都落在**亮**薄荷上
 * （浅 #2EC99C / 深 #35E0AC），所以前景必须**与套无关**：
 *  - 不能用 `appColors().onMint`——它随套翻转，深套取值是浅墨 #DFF6EC，压 #35E0AC 只有 1.49:1；
 *  - `onBrand` 明暗两套同值 #04241C（裁决 3「品牌渐变用深字」那枚 token），
 *    压浅套 7.81:1 / 深套 9.74:1，两套都过正文线。
 * 亮度复算与这条判据见 `ReadingHomeLayoutContractTest` 的姊妹用例
 * `[the mint primary pair stays dark ink on both theme sets]`。批 1 Task 7 起本屏只剩**这一枚**主色按钮
 * （今日文章卡里的阅读 CTA，或无文章时的生成 CTA）：类型行与两张横幅的动作已降为次级档，
 * 「一屏一枚薄荷主色」因此既是运行时事实、也能在源码里按站点数点出来。
 */
@Composable
private fun PrimaryCtaButton(title: String, tag: String, description: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag(tag).semantics { contentDescription = description },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = appColors().onBrand),
    ) { Text(title, fontWeight = FontWeight.Bold) }
}

/** 「生成前参数」开关：偏好区不再常驻长列，随这一次动作出现（裁决⑤ 第三件）。 */
@Composable
private fun GenerationParamsToggle(expanded: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("reading_generation_params_toggle")
            .semantics { contentDescription = if (expanded) "收起生成前参数" else "展开生成前参数" },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
    ) {
        Text(if (expanded) "收起生成前参数" else "生成前参数：类型与长度", fontWeight = FontWeight.Bold)
    }
}

/** 生成失败横幅：文案与按钮标签全部来自常量（AiFailure 的泄露防线），不发自由文本。 */
@Composable
private fun NotConfiguredBanner(reason: NotConfiguredReason, onOpenAiSettings: () -> Unit) {
    val message = when (reason) {
        NotConfiguredReason.NoDefaultProfile -> "请先选择文章默认 AI 服务。"
        NotConfiguredReason.DefaultProfileUnavailable -> "文章默认 AI 服务已不可用，请重新选择。"
        NotConfiguredReason.NoProfile,
        NotConfiguredReason.NoKey,
        NotConfiguredReason.ProfileUnreadable,
        NotConfiguredReason.InvalidEndpoint,
        -> "本机 AI 配置暂时不可用，请检查设置。"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MintOutline, RoundedCornerShape(18.dp)).testTag("generation_failure"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MintPrimaryDark, modifier = Modifier.semantics { contentDescription = "生成失败原因" })
            Button(
                onClick = onOpenAiSettings,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .border(1.dp, MintOutline, RoundedCornerShape(14.dp))
                    .testTag("generation_failure_action"),
                colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
            ) { Text("去配置", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun GenerationFailureBanner(failure: AiFailure, onOpenAiSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MintOutline, RoundedCornerShape(18.dp)).testTag("generation_failure"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(failure.uiText.message, color = MintPrimaryDark, modifier = Modifier.semantics { contentDescription = "生成失败原因" })
            if (failure.toUserAction() == UserAction.ConfigureProfile) {
                Button(
                    onClick = onOpenAiSettings,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .border(1.dp, MintOutline, RoundedCornerShape(14.dp))
                        .testTag("generation_failure_action"),
                    colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
                ) { Text(UserAction.ConfigureProfile.label, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun AccessCard(title: String, message: String, tag: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MintOutline, RoundedCornerShape(24.dp)).testTag(tag),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            Text(message, color = MintTextMuted)
        }
    }
}

/**
 * 类型行（生成前参数展开区）：四枚同为**次级档**——薄荷底留给主 CTA 一枚。
 *
 * 批 1 Task 7 之前它带 `enabled` 参数（锁定态铺四枚 disabled 行）；锁定态改用一句说明后，
 * 本函数只在展开区出现，`enabled` 随之退役。
 */
@Composable
private fun TypeButton(type: ArticleType, onSelectType: (ArticleType) -> Unit) {
    Button(
        onClick = { onSelectType(type) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("reading_type_${type.name.lowercase()}")
            .semantics { contentDescription = "${type.label}文章" },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
    ) { Text(type.label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun HistoryButton(onOpenHistory: () -> Unit, historyCount: Int? = null) {
    Button(
        onClick = onOpenHistory,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).border(1.dp, MintOutline, RoundedCornerShape(18.dp)).testTag("reading_history_entry").semantics { contentDescription = "本地阅读历史" },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
    ) { Text(if (historyCount == null) "查看本地阅读历史" else "查看本地阅读历史（$historyCount）", fontWeight = FontWeight.Bold) }
}

private val ArticleType.label: String
    get() = when (this) {
        ArticleType.NEWS -> "新闻"
        ArticleType.STORY -> "故事"
        ArticleType.SCIENCE -> "科普"
        ArticleType.WORKPLACE -> "职场"
    }

private val com.example.englishlearning.reading.domain.ArticleLengthTier.label: String
    get() = when (this) {
        com.example.englishlearning.reading.domain.ArticleLengthTier.SHORT -> "短篇"
        com.example.englishlearning.reading.domain.ArticleLengthTier.STANDARD -> "标准"
        com.example.englishlearning.reading.domain.ArticleLengthTier.LONG -> "长篇"
    }
