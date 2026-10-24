package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.UserAction
import com.example.englishlearning.ai.toUserAction
import com.example.englishlearning.reading.NotConfiguredReason
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.OutboundConfirmDialog
import com.example.englishlearning.ui.components.PayloadKind
import com.example.englishlearning.ui.theme.appColors
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
 * 版面按裁决⑤ 重排（spec §6 表格 ⑤ 行）：
 *  ① 主任务（今日文章）上提第一屏——不滚动即可见那枚主 CTA；
 *  ② 「AI 生成」与「换一篇」**合并为一枚主 CTA**，两档按「今天有没有文章」各归其位，永不并列；
 *  ③ 外刊 Feed 改独立二级层（`ReadingFeedScreen`），本页只留通往它的入口；
 *  ④ 类型与长度偏好下沉「生成前参数」，随这一次动作出现，不再常驻长列。
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
    // 生成前参数的展开态是本屏的呈现状态，不进 ViewModel：它不改变任何持久化取值，
    // 只决定偏好区这一支此刻画不画（brief §二.3「偏好写入路径不改」）。
    var generationParamsExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("reading_access_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("每日阅读", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        when (state) {
            ReadingAccessUiState.Loading -> Text("正在读取本地阅读状态…", color = MintTextMuted)
            ReadingAccessUiState.Unavailable -> Text("本地阅读状态暂时无法读取", color = MintTextMuted)
            is ReadingAccessUiState.Locked -> {
                AccessCard("文章尚未解锁", state.reason, "reading_access_locked_reason")
                ArticleType.entries.forEach { type ->
                    TypeButton(type, false, onSelectType)
                }
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
                onRegenerate = onRegenerate,
                onOpenTodayArticle = onOpenTodayArticle,
                onConfirmOutbound = onConfirmOutbound,
                onOpenAiSettings = onOpenAiSettings,
                onOpenImport = onOpenImport,
                onOpenFeed = onOpenFeed,
            )
        }
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
    onRegenerate: () -> Unit,
    onOpenTodayArticle: () -> Unit,
    onConfirmOutbound: (Boolean) -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenFeed: () -> Unit,
) {
    // —— 主任务区（裁决⑤ 第一件 + 第四件）：紧跟页头，第一屏不滚动即可点。
    // 主 CTA 恰一枚：无当日文章时这一格是「生成今天的文章」，已有当日文章时同一位置换成「阅读今天的文章」，
    // 换一篇退为次级行。`reading_source_ai` 与 `reading_regenerate` 因此永不并列。
    if (state.todayArticle != null) {
        PrimaryCtaButton("阅读今天的文章", "reading_open_today", "阅读今天的文章", onOpenTodayArticle)
        SecondaryCtaButton("换一篇", "reading_regenerate", "换一篇", onRegenerate)
    } else {
        PrimaryCtaButton("生成今天的文章", "reading_source_ai", "用 AI 生成今天的文章", onGenerate)
    }
    Text(
        "每次生成都会真实调用你配置的 AI 服务并可能产生费用",
        style = MaterialTheme.typography.bodySmall,
        color = MintTextMuted,
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
            color = MintTextMuted,
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
            TypeButton(type, true, onSelectType)
        }
    }

    // —— 另外两枚来源：次级行。Feed 只在主页留入口，列表与抓取归二级层。
    SecondaryCtaButton("从外刊选取", "reading_source_feed", "从已核验的外刊来源选取", onOpenFeed)
    SecondaryCtaButton("粘贴文章", "reading_source_import", "粘贴你自己的文章", onOpenImport)

    HistoryButton(onOpenHistory, state.historyCount)
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
 * `[the mint primary pair stays dark ink on both theme sets]`；本文件四枚站点同一形状。
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

/** 次级档：与主 CTA 同几何、不同容器，保证一屏只有一枚薄荷主色按钮抢到主任务叙事。 */
@Composable
private fun SecondaryCtaButton(title: String, tag: String, description: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag(tag).semantics { contentDescription = description },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
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
                modifier = Modifier.testTag("generation_failure_action"),
                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = appColors().onBrand),
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
                    modifier = Modifier.testTag("generation_failure_action"),
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = appColors().onBrand),
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

@Composable
private fun TypeButton(type: ArticleType, enabled: Boolean, onSelectType: (ArticleType) -> Unit) {
    Button(
        onClick = { onSelectType(type) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("reading_type_${type.name.lowercase()}").semantics { contentDescription = "${type.label}文章" },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MintPrimary,
            contentColor = appColors().onBrand,
            disabledContainerColor = MintTint,
            disabledContentColor = MintPrimaryDark,
        ),
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
