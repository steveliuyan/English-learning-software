package com.example.englishlearning.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.UserAction
import com.example.englishlearning.ai.toUserAction
import com.example.englishlearning.reading.FetchFailure
import com.example.englishlearning.reading.FeedItem
import com.example.englishlearning.reading.NotConfiguredReason
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
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

sealed interface ReadingAccessUiState {
    data object Loading : ReadingAccessUiState
    data class Locked(val reason: String) : ReadingAccessUiState
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
 * 「阅读」栏的根页面。一级 tab，出口交给底部导航与页内二级层，所以**没有**自带的返回按钮。
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
    onFetchFeedItem: (FeedItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
                HistoryButton(onOpenHistory)
            }
            is ReadingAccessUiState.Ready -> ReadyContent(
                state = state,
                onSelectType = onSelectType,
                onOpenHistory = onOpenHistory,
                onGenerate = onGenerate,
                onRegenerate = onRegenerate,
                onOpenTodayArticle = onOpenTodayArticle,
                onConfirmOutbound = onConfirmOutbound,
                onOpenAiSettings = onOpenAiSettings,
                onOpenImport = onOpenImport,
                onOpenFeed = onOpenFeed,
                onFetchFeedItem = onFetchFeedItem,
            )
        }
    }
}

@Composable
private fun ReadyContent(
    state: ReadingAccessUiState.Ready,
    onSelectType: (ArticleType) -> Unit,
    onOpenHistory: () -> Unit,
    onGenerate: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenTodayArticle: () -> Unit,
    onConfirmOutbound: (Boolean) -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenFeed: () -> Unit,
    onFetchFeedItem: (FeedItem) -> Unit,
) {
    AccessCard(
        "文章已解锁",
        "三种来源任选：AI 生成、外刊选取，或粘贴你自己的文章。",
        "reading_access_unlocked",
    )

    SourceEntryButton("AI 生成文章", "reading_source_ai", "用 AI 生成今天的文章", onGenerate)
    Text(
        "每次生成都会真实调用你配置的 AI 服务并可能产生费用",
        style = MaterialTheme.typography.bodySmall,
        color = MintTextMuted,
        modifier = Modifier
            .padding(top = 0.dp)
            .testTag("generation_cost_hint")
            .semantics { contentDescription = "生成费用提示" },
    )
    SourceEntryButton("从外刊选取", "reading_source_feed", "从已核验的外刊来源选取", onOpenFeed)
    SourceEntryButton("粘贴文章", "reading_source_import", "粘贴你自己的文章", onOpenImport)

    when (val generation = state.generation) {
        GenerationUiState.Generating -> Text(
            "正在生成…",
            color = MintTextMuted,
            modifier = Modifier.testTag("generation_status"),
        )
        GenerationUiState.StorageFailed -> Text(
            "本地存储暂时不可用，文章没有保存。",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("generation_storage_failed"),
        )
        is GenerationUiState.Failed -> GenerationFailureBanner(generation.failure, onOpenAiSettings)
        is GenerationUiState.NotConfigured -> NotConfiguredBanner(generation.reason, onOpenAiSettings)
        is GenerationUiState.NeedsConfirmation -> OutboundConfirmationDialog(generation.host, onConfirmOutbound)
        GenerationUiState.Idle -> Unit
    }

    if (state.todayArticle != null) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onOpenTodayArticle,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("reading_open_today")
                    .semantics { contentDescription = "阅读今天的文章" },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
            ) { Text("阅读今天的文章", fontWeight = FontWeight.Bold) }
            Button(
                onClick = onRegenerate,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("reading_regenerate")
                    .semantics { contentDescription = "换一篇" },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
            ) { Text("换一篇", fontWeight = FontWeight.Bold) }
        }
    }

    FeedSection(state.feed, onFetchFeedItem)

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
    HistoryButton(onOpenHistory, state.historyCount)
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
                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
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
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
                ) { Text(UserAction.ConfigureProfile.label, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun OutboundConfirmationDialog(host: String, onConfirmOutbound: (Boolean) -> Unit) {
    GlassDialog(onDismiss = { onConfirmOutbound(false) }) {
        Text("确认发送请求", style = AppType.Headline)
        Text(
            "你的文本与该服务的密钥将发送给 $host，并可能产生费用。",
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("outbound_confirmation_text"),
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
                onClick = { onConfirmOutbound(false) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Secondary,
                accent = DomainColors.Reading,
                testTag = "outbound_cancel",
            )
            PillButton(
                text = "确认发送",
                onClick = { onConfirmOutbound(true) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Primary,
                accent = DomainColors.Reading,
                testTag = "outbound_confirm",
            )
        }
    }
}

/** 外刊列表区。Idle 不渲染任何东西；空态与失败态都显式给出文字。 */
@Composable
private fun FeedSection(feed: FeedUiState, onFetchFeedItem: (FeedItem) -> Unit) {
    when (feed) {
        FeedUiState.Idle -> Unit
        FeedUiState.Loading -> Text("正在获取外刊列表…", color = MintTextMuted, modifier = Modifier.testTag("feed_loading"))
        FeedUiState.Empty -> Text(
            "这个来源暂时没有可读的文章，稍后再来看看。",
            color = MintTextMuted,
            modifier = Modifier.testTag("feed_empty"),
        )
        FeedUiState.Unreachable -> Text(
            "外刊来源暂时不可用，请检查网络后重试。",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("feed_error"),
        )
        is FeedUiState.FetchFailed -> Text(
            "抓取失败：${feed.failure.label}",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("feed_fetch_failed"),
        )
        is FeedUiState.Ready -> {
            Text("来源：${feed.sourceDisplayName}", color = MintPrimaryDark, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("feed_source"))
            feed.items.forEachIndexed { index, item ->
                Button(
                    onClick = { onFetchFeedItem(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("feed_item_$index")
                        .semantics { contentDescription = "阅读外刊文章 ${item.title}" },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
                ) { Text(item.title, maxLines = 2) }
            }
        }
    }
}

private val FetchFailure.label: String
    get() = when (this) {
        FetchFailure.NetworkUnavailable -> "网络不可用"
        FetchFailure.SourceUnreachable -> "来源暂时不可用"
        FetchFailure.BodyNotFound -> "页面里找不到正文，链接可能已失效"
        FetchFailure.BodyTooShort -> "正文太短"
        FetchFailure.QualityRejected -> "内容未通过质量检查"
    }

@Composable
private fun SourceEntryButton(title: String, tag: String, description: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag(tag).semantics { contentDescription = description },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
    ) { Text(title, fontWeight = FontWeight.Bold) }
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
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("reading_type_${type.name.lowercase()}").semantics { contentDescription = "${type.label}文章" },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MintPrimary,
            contentColor = Color.White,
            disabledContainerColor = MintTint,
            disabledContentColor = MintPrimaryDark,
        ),
    ) { Text(type.label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun HistoryButton(onOpenHistory: () -> Unit, historyCount: Int? = null) {
    Button(
        onClick = onOpenHistory,
        modifier = Modifier.fillMaxWidth().height(54.dp).border(1.dp, MintOutline, RoundedCornerShape(18.dp)).testTag("reading_history_entry").semantics { contentDescription = "本地阅读历史" },
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
