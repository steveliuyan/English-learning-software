package com.example.englishlearning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleDisplayMode
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.ui.components.AppGlyph
import com.example.englishlearning.ui.components.AppGlyphLayer
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.hairlineTop
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun formatImportedAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

/**
 * 阅读页。正文是**单个 `Text` 的 `AnnotatedString`**：高亮区间以阅读域深色加粗，点击回调
 * `onOpenCard`。译文可见性只由 `translationExpanded` 决定——三种模式只差初始值（domain
 * 文档：译文在任何模式下都可折叠）。
 *
 * ## 版式（批 3 Task 3，spec §6 逐屏表「文章阅读」行）
 *
 * 换装前这屏是批 1 之前的形态，四类病灶各占一处：
 *
 * 1. **两枚自写页头按钮**：「返回」与「发音」都是 M3 实底 `Button`，返回位连 `testTag` 都没有，
 *    屏标题是 `headlineSmall`。现在返回位归共享 [AppScreenHeader]，发音收进它的 `action` 槽
 *    （40dp 视觉容器 + 48dp 可点区，与返回位同族几何），标题用页头的 `AppType.Title`。
 *    正文标题 `article_title` **仍留在正文侧**（Task 22-K 起是 `AppType.ArticleTitle`，原型 `.article-title` 的 30sp 衬线）：它是文章内容本身，
 *    connected 按这个 tag 取样，也是长标题唯一能完整显示的地方——页头两行 `Clip` 只承担
 *    「滚动后我还知道在读哪篇」。
 * 2. **主操作埋在流末尾**：「完成阅读」排在正文与未覆盖词之后，文章越长越够不着。现在它是
 *    屏底吸底条里唯一的主 CTA（`article_complete_action_bar` + 发丝线），正文滚动面在它上面。
 * 3. **覆盖词整块挡正文**：进文第一帧弹一枚居中 `GlassDialog`，用户先得点「我知道了」才看得到文章。
 *    现在改成底部面板（`ModalBottomSheet` + `skipPartiallyExpanded`）：从下沿升起、可下滑收起，
 *    正文留在上半屏；面板自绘标题行（含「我知道了」），词表超长时面板内滚动，标题行始终可见。
 *    首次进文仍自动展开一次（F3-01B 的行为不变），收起后由正文里的 `article_coverage_entry` 裸行
 *    再次打开——这条入口是**已有数据的重新呈现**，不是新数据通路。
 * 4. **译文控件分裂成两处**：「全文对照」chip 只改偏好、「显示译文」按钮只翻展开态，用户要看全译
 *    得点两处。现在模式行只剩两枚 chip（英文优先 / 双语），译文开关与它们同一行、同一容器；
 *    `FULL_TRANSLATION` 折叠成「双语 + 译文已展开」的呈现等价，**ViewModel 语义零改动**。
 *
 * 来源区（Task E）按 `ArticleSource` 三分支展示：AI 只显示模型名；外刊显示署名与原文链接
 * （**纯文本，绝不自动打开**）；导入显示时间与责任说明。四枚来源 tag 各恰一处（三分支原先
 * 各写一遍 `article_source_name`，connected 按 tag 取节点会撞多份），收成两行：站名 + 行尾域名、
 * 链接标签 + URL。域名与 URL 都不删——前者是授权署名，后者是「不会自动打开」这条安全能力的
 * 唯一可见处。
 *
 * 取墨一律走 `appColors()` / `appDomain(...)` 而不是 `Mint*` 静态桥：浅色侧逐值不变，深色侧才
 * 真正跟着换套。本文件不留一枚 `AppPalette` / `DomainColors` 静态读点，也不带裸 hex，
 * 屏侧不自造圆角（三档容器与 `AppShape` 承载）。
 *
 * **不许动的既有锚**（换了就是数据/语义变更）：`style = AppType.Body.copy(color = appColors().text1)`
 * 那一行逐字保留（`ReadingHomeLayoutContractTest` 精确子串钉住），`rememberScrollState(articleScrollPosition)`
 * 与 `snapshotFlow { scrollState.value }.collect(onArticleScrollPositionChange)` 逐字保留
 * （`SearchCloseReadingStateRetentionTest` 钉「搜索关闭后阅读位置不丢」），正文仍是
 * `verticalScroll(scrollState)` 而非懒列表（滚动位置要保持「像素值」可回传）。
 */
@Composable
fun ArticleReadingScreen(
    state: ArticleReadingUiState?,
    onBack: () -> Unit,
    onOpenCard: (WordCard) -> Unit,
    onModeChange: (ArticleDisplayMode) -> Unit,
    onToggleTranslation: () -> Unit,
    onOpenDictionaryPlaceholder: () -> Unit,
    onOpenPronunciationPlaceholder: () -> Unit,
    onSpeakSentence: (String) -> Unit = { onOpenPronunciationPlaceholder() },
    // F3-02 四控（裁决 B2）：状态与文案由宿主给，暂停/继续的呈现只看引擎选择、不看 provider 返回值。
    pronunciationMessage: String? = null,
    pronunciationStatus: PronunciationStatus = PronunciationStatus.Idle,
    pauseResumeSupported: Boolean = false,
    onPauseResume: () -> Unit = {},
    onStopPronunciation: () -> Unit = {},
    onOpenDictionary: (String) -> Unit = { onOpenDictionaryPlaceholder() },
    onSetLearnedMarks: (Boolean) -> Unit = {},
    onCompleteReading: () -> Unit = {},
    modifier: Modifier = Modifier,
    articleScrollPosition: Int = 0,
    onArticleScrollPositionChange: (Int) -> Unit = {},
) {
    val colors = appColors()
    val scrollState = rememberScrollState(articleScrollPosition)
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect(onArticleScrollPositionChange)
    }
    val current = state
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("article_reading_screen"),
    ) {
        AppScreenHeader(
            title = current?.article?.title ?: "文章阅读",
            onBack = onBack,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            // 页头右侧的发音位：40dp 视觉容器 + 48dp 可点区，与共享页头的返回位同族几何（R-9.1）。
            // 不用实底 `Button`：这一枚是页头行的附属动作，不是主操作——一屏只有一枚实底主操作
            // （屏底「完成阅读」）。`contentDescription` 取「播放发音」而不是「发音」，TalkBack
            // 念出来要能听出这是动作；再点即重播（系统 TTS QUEUE_FLUSH / 音频路径重走播放），
            // 不另设重播按钮。写在 `action` 槽调用窗口内而不是抽成私有函数：页头这一行只有
            // 返回位 + 发音两枚元素，抽出去只会让「页头到底承载了什么」在源码上分成两处。
            action = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(AppShape.Icon)
                        .clickable(onClick = { current?.let { onSpeakSentence(it.article.englishText) } })
                        .testTag("article_pronunciation")
                        .semantics { contentDescription = "播放发音" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(AppShape.Icon)
                            .background(colors.surface)
                            .border(1.dp, colors.outlineSoft, AppShape.Icon),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppGlyph(icon = AppIcon.Speaker, tint = colors.text2, size = 22.dp)
                    }
                }
            },
        )
        // F3-02 整篇朗读的状态文字与播控组**必须**位于空态早退之后（Task 9 评审 I-2）：
        // 空态（「正在加载…」）不得组合出消息文字与播控行。
        if (current == null) {
            Text(
                text = "正在加载…",
                style = AppType.Body,
                color = colors.text2,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        } else {
            // F3-01B：进文覆盖词面板——每篇文章首次打开自动展开一次。coveredLemmas 是文章自带的
            // 落库字段，面板是纯呈现：开关状态按 articleId 记忆，切换文章重新展开。
            var coverageOpen by rememberSaveable(current.article.articleId) {
                mutableStateOf(current.article.coveredLemmas.isNotEmpty())
            }
            // 原型 `sReadTranslate` 的 `bar-action`「只看中文」：本仓不新开页（Task 21-D 裁决），
            // 落成屏内一个开关。按 articleId 记忆，换文章回到默认（先看英文）。
            var chineseOnly by rememberSaveable(current.article.articleId) { mutableStateOf(false) }
            val translationVisible = current.translationExpanded || chineseOnly
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ArticleHeadlineAndSpeech(
                    article = current.article,
                    pronunciationMessage = pronunciationMessage,
                    pronunciationStatus = pronunciationStatus,
                    pauseResumeSupported = pauseResumeSupported,
                    onPauseResume = onPauseResume,
                    onStopPronunciation = onStopPronunciation,
                )
                ArticleSourceBlock(current.article)
                ArticleModeRow(
                    mode = current.mode,
                    translationExpanded = current.translationExpanded,
                    translationAvailable = current.article.chineseText.isNotEmpty(),
                    chineseOnly = chineseOnly,
                    // 模式 chip 是唯一真状态：切它必须退出中文态，否则一个隐藏开关会盖住屏上的选择。
                    onModeChange = { mode ->
                        chineseOnly = false
                        onModeChange(mode)
                    },
                    onToggleTranslation = onToggleTranslation,
                    onToggleChineseOnly = {
                        val next = !chineseOnly
                        chineseOnly = next
                        // 只开中文却没展开译文，屏幕上会只剩标题——顺手把译文展开。
                        if (next && !current.translationExpanded) onToggleTranslation()
                    },
                )
                // 中文态下英文侧整块不组合：原型对照页只有英文与中文两段，没有标注行、
                // 未覆盖词与覆盖入口；留着它们等于在「只看中文」里塞一堆英文控件。
                if (!chineseOnly) {
                    ArticleMarksRow(
                        checked = current.showLearnedMarks,
                        onSetLearnedMarks = onSetLearnedMarks,
                    )
                    HighlightedEnglish(
                        englishText = current.article.englishText,
                        highlights = current.highlights,
                        cards = current.cards,
                        onOpenCard = onOpenCard,
                        onOpenDictionary = onOpenDictionary,
                    )
                }
                if (current.article.chineseText.isEmpty()) {
                    Text(
                        text = "该来源没有中文翻译",
                        style = AppType.Footnote,
                        color = colors.text2,
                        modifier = Modifier.testTag("article_no_translation_notice"),
                    )
                }
                if (translationVisible) {
                    // 原型 `.prose-zh`：无衬线 13px / 行高 1.75、次要墨，没有容器——
                    // 字号与字体本身就把译文和衬线正文分开，不必再套一层卡。
                    Text(
                        text = current.article.chineseText,
                        style = AppType.ProseZh,
                        color = colors.text2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("article_translation"),
                    )
                    // 原型对照页末尾的 `notice(…, 'info', 'warning')`。「本机 AI 配置生成」只对
                    // AI 文章成立，导入文章的译文随包进来，所以按来源分三支写（共享件只有 Info 档，取 Info）。
                    InlineNotice(
                        text = when (current.article.source) {
                            is ArticleSource.AiGenerated ->
                                "译文由你配置的 AI 服务生成，仅作辅助理解；正式引用请以英文原文为准。"
                            is ArticleSource.UserImported ->
                                "译文随文章一起导入，仅作辅助理解；正式引用请以英文原文为准。"
                            is ArticleSource.WebFetched ->
                                "译文仅作辅助理解；正式引用请以英文原文为准。"
                        },
                        kind = InlineNoticeKind.Info,
                        modifier = Modifier.testTag("article_translation_caution"),
                    )
                }
                if (!chineseOnly && current.uncoveredLemmas.isNotEmpty()) {
                    Text(
                        text = "未覆盖词",
                        style = AppType.Title,
                        color = colors.text1,
                        modifier = Modifier.testTag("article_uncovered"),
                    )
                    // F5-03 / Task 11：未覆盖词 chip 数量不定，横向排布在动态字体下必然溢出 → 改 FlowRow 换行。
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        current.uncoveredLemmas.forEach { lemma ->
                            val card = current.cards.firstOrNull { it.lemma == lemma || it.cardId == lemma }
                            AssistChip(
                                onClick = {
                                    if (card != null) onOpenCard(card) else onOpenDictionary(lemma)
                                },
                                label = { Text(lemma) },
                                modifier = Modifier.testTag("article_uncovered_$lemma"),
                            )
                        }
                    }
                }
                if (!chineseOnly && current.article.coveredLemmas.isNotEmpty()) {
                    CoverageEntryRow(
                        count = current.article.coveredLemmas.size,
                        onClick = { coverageOpen = true },
                    )
                }
            }
            ArticleCompleteBar(
                completed = current.completed,
                bilingualAvailable = current.article.chineseText.isNotEmpty(),
                onOpenBilingual = {
                    // 页脚第二枚：切到双语并把译文展开（已展开时不重复 toggle，否则会收起）。
                    if (current.mode != ArticleDisplayMode.BILINGUAL) onModeChange(ArticleDisplayMode.BILINGUAL)
                    chineseOnly = false
                    if (!current.translationExpanded) onToggleTranslation()
                },
                onCompleteReading = onCompleteReading,
            )
            if (coverageOpen && current.article.coveredLemmas.isNotEmpty()) {
                CoverageSheet(
                    lemmas = current.article.coveredLemmas,
                    onDismiss = { coverageOpen = false },
                )
            }
        }
    }
}

/**
 * 正文标题 + 朗读状态活区 + 播控行。
 *
 * F5-03 决策 §4（Task 9 修复轮 I-4，controller 裁决）：live region 必须常驻，靠子节点进出触发播报
 * （Task 6 判例 `WordCardScreen.word_card_status_live_region`）：
 * ①tag `article_pronunciation_message` 留在瞬时 Text 上（tag 口径不动，既有引用不破）；
 * ②liveRegion 只在容器上，瞬时子节点不得再带；③播控 Row 保持在容器之外（B1 口径）。
 *
 * 决策 §2：暂停/继续只在 Playing/Paused 窗口呈现，呈现条件按「选中引擎 ≠ SystemTts」收窄（裁决 B2）；
 * 停止在系统 TTS 下同样可用，照常给。两枚都是 `TextButton`：播控是附属动作，不与吸底主操作抢实底。
 */
@Composable
private fun ArticleHeadlineAndSpeech(
    article: Article,
    pronunciationMessage: String?,
    pronunciationStatus: PronunciationStatus,
    pauseResumeSupported: Boolean,
    onPauseResume: () -> Unit,
    onStopPronunciation: () -> Unit,
) {
    val colors = appColors()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 原型 `.article-title`：衬线 30px / 行高 1.18（`AppType.ArticleTitle` 逐值取自这张样式表）。
        // 旧实现是 24sp 的 `Headline`——正文标题是文章内容本身，不是屏头，档位跟着原型走。
        Text(
            text = article.title,
            style = AppType.ArticleTitle,
            color = colors.text1,
            modifier = Modifier.testTag("article_title"),
        )
        // 原型 `.article-meta`（`app.js:748`）：`source · 约 214 词 · 5 分钟`。
        // 这里只搬读得到的那一枚——词数按 `englishText` 空白切分真算；版本号取 `Article.version`。
        // 「5 分钟」不画：本仓没有阅读速度口径，按固定词/分钟换算就是编造。
        Text(
            text = "约 ${article.englishText.split(Regex("\\s+")).count { it.isNotBlank() }} 词 · 第 ${article.version} 版",
            style = AppType.Meta,
            color = colors.text2,
            modifier = Modifier.testTag("article_meta"),
        )
        Column(
            modifier = Modifier
                .testTag("article_status_live_region")
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            pronunciationMessage?.let { message ->
                Text(
                    text = message,
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier
                        .testTag("article_pronunciation_message")
                        .semantics { contentDescription = message },
                )
            }
        }
        val speakingOrPaused =
            pronunciationStatus == PronunciationStatus.Playing || pronunciationStatus == PronunciationStatus.Paused
        if (speakingOrPaused) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (pauseResumeSupported) {
                    TextButton(
                        onClick = onPauseResume,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("article_pronunciation_toggle")
                            .semantics {
                                contentDescription =
                                    if (pronunciationStatus == PronunciationStatus.Paused) "继续播放发音" else "暂停发音"
                            },
                    ) {
                        Text(
                            text = if (pronunciationStatus == PronunciationStatus.Paused) "继续" else "暂停",
                            style = AppType.Label,
                            color = colors.text2,
                        )
                    }
                }
                TextButton(
                    onClick = onStopPronunciation,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("article_pronunciation_stop")
                        .semantics { contentDescription = "停止播放发音" },
                ) {
                    Text(text = "停止", style = AppType.Label, color = colors.text2)
                }
            }
        }
    }
}

/**
 * 来源块两行（Task E 的四行收口）。AC2-07：来源标识不得包含 Key / Endpoint——AI 分支只渲染
 * `modelName`，`parameterSummary` 是纯审计字段，永远不进界面。
 *
 * 三分支**只挑文案，不各写一遍块**：原来每支各写一枚 `article_source_name`，全文件三份同名
 * 字面量，connected 侧 `onNodeWithTag` 会撞多个节点（`ArticleReadingScreenTest` 的
 * AI / 外刊 / 导入三条用例正是这么取的）。收成「先算文案、再渲染两行」之后四枚 tag 各恰一处。
 *
 * 行尾域名取 `text2` 而不是令牌表里更轻的 `text3`：`text3` 压 `background` 实测 3.17:1，
 * 未达正文线 4.5——域名是授权署名（`attributionText`），属正文级信息，不是装饰。
 * 链接仍是**纯文本展示**，不挂任何点击动作：原文链接绝不自动打开（spec F2-06），
 * 但 URL 必须可见——它是这条安全能力的唯一可见处。
 */
@Composable
private fun ArticleSourceBlock(article: Article) {
    val colors = appColors()
    val source = article.source
    val name = when (source) {
        is ArticleSource.AiGenerated -> "AI 生成 · ${source.modelName}"
        is ArticleSource.WebFetched -> source.displayName
        ArticleSource.UserImported -> "手动导入 · ${formatImportedAt(article.generatedAtEpochMillis)}"
    }
    val attribution = (source as? ArticleSource.WebFetched)?.attributionText
    val articleUrl = (source as? ArticleSource.WebFetched)?.articleUrl
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = name,
                style = AppType.Title,
                color = colors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("article_source_name"),
            )
            if (attribution != null) {
                Text(
                    text = attribution,
                    style = AppType.Footnote,
                    color = colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("article_attribution"),
                )
            }
        }
        if (articleUrl != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "原文链接（不会自动打开）",
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.testTag("article_source_url_label"),
                )
                Text(
                    text = articleUrl,
                    style = AppType.Footnote,
                    color = colors.text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("article_source_link"),
                )
            }
        }
        if (source is ArticleSource.UserImported) {
            Text(
                text = "内容由你自行提供，请确保你有权使用。",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("article_import_responsibility"),
            )
        }
    }
}

/**
 * 模式行：两枚 chip + 译文开关，同一行同一容器。
 *
 * 第三枚「全文对照」chip 撤掉（`article_mode_full_translation` 归零）：它只改偏好里的初始展开值，
 * 而「双语 + 显示译文」已经能表达同一件事——两个控件表达一个状态，用户得点两处才看得到全译。
 * `FULL_TRANSLATION` 折叠成「双语 chip 选中 + 译文已展开」的**呈现等价**，VM 语义不动：
 * 屏侧只在选中态的比对上把它读成双语，chip 也仍然只发前两档偏好。
 *
 * 横向可滚动保住 F5-03：特大字号下两枚 chip + 一枚文字按钮会超出屏宽。
 * 译文开关是 `TextButton`：次要动作不与吸底主操作抢实底；无译文时禁用并降墨到 `text3`。
 */
@Composable
private fun ArticleModeRow(
    mode: ArticleDisplayMode,
    translationExpanded: Boolean,
    translationAvailable: Boolean,
    chineseOnly: Boolean,
    onModeChange: (ArticleDisplayMode) -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleChineseOnly: () -> Unit,
) {
    val colors = appColors()
    val presentedMode = if (mode == ArticleDisplayMode.FULL_TRANSLATION) ArticleDisplayMode.BILINGUAL else mode
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeChip("英文优先", ArticleDisplayMode.ENGLISH_FIRST, "article_mode_english_first", presentedMode, onModeChange)
        ModeChip("双语", ArticleDisplayMode.BILINGUAL, "article_mode_bilingual", presentedMode, onModeChange)
        TextButton(
            onClick = onToggleTranslation,
            enabled = translationAvailable,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("article_translation_toggle"),
        ) {
            Text(
                text = if (translationExpanded) "收起译文" else "显示译文",
                style = AppType.Label,
                color = if (translationAvailable) colors.text1 else colors.text3,
            )
        }
        // 原型 `sReadTranslate` 的页头 `bar-action`「只看中文」。落点从页头改到这一行：页头动作位
        // 已被发音占用，2× 字号下再塞一枚文字动作会把标题挤成竖条；模式与译文开关都在这一行，
        // 「只看中文」本来就是它们三者的第三种读法。没有译文时禁用（点了只会看到空屏）。
        TextButton(
            onClick = onToggleChineseOnly,
            enabled = translationAvailable,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("article_chinese_only_toggle"),
        ) {
            Text(
                text = if (chineseOnly) "看回英文" else "只看中文",
                style = AppType.Label,
                color = if (translationAvailable) colors.text1 else colors.text3,
            )
        }
    }
}

/**
 * 标注行：F3-01C 的「标记已背的词」开关。只影响高亮呈现，不动文章内容与未覆盖词 chips。
 *
 * 开关的 `contentDescription` 与 `testTag` 必须**相邻**（`AccessibilitySwitchLabelContractTest`
 * 规则1：`testTag` 之后 80 字符内必须出现 `contentDescription`），紧凑书写优先于放宽窗口。
 */
@Composable
private fun ArticleMarksRow(
    checked: Boolean,
    onSetLearnedMarks: (Boolean) -> Unit,
) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("article_marks_row"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "标记已背的词",
            style = AppType.Body,
            color = colors.text1,
        )
        Switch(
            checked = checked,
            onCheckedChange = onSetLearnedMarks,
            modifier = Modifier.testTag("article_marks_switch").semantics { contentDescription = "标记已背的词" },
        )
    }
}

/**
 * 覆盖词入口行：面板收起之后仍能把这些词调出来。
 *
 * 原来是一枚居中对话框、关掉就再也看不到（每篇文章只弹一次）。改成底部面板后，关掉同样不再自弹，
 * 所以正文侧留一条弱行作为重新打开的入口——这是**已有数据的重新呈现**（`coveredLemmas` 是文章
 * 自带的落库字段），不新增数据通路、不改 ViewModel。
 */
@Composable
private fun CoverageEntryRow(
    count: Int,
    onClick: () -> Unit,
) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(AppShape.Inline)
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("article_coverage_entry"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "本文覆盖你已背的 $count 个词",
            style = AppType.Body,
            color = colors.text1,
            modifier = Modifier.weight(1f),
        )
        AppGlyph(icon = AppIcon.ChevronRight, tint = colors.text2, size = 16.dp, layer = AppGlyphLayer.StrokeOnly)
    }
}

/**
 * 覆盖词底部面板（F3-01B 的呈现改造）。
 *
 * 换掉的是居中 `GlassDialog`：它进文第一帧就把正文整块挡住，用户得先点「我知道了」才看得到文章。
 * 底部面板从下沿升起、可下滑收起，正文留在上半屏；`skipPartiallyExpanded = true` 让它一次升到
 * 展开位而不是半展开（半展开会把正文压成一条缝）。
 *
 * 标题行（计数 + 「我知道了」）排在词表**之上**并留在滚动面之外：词表长时按钮仍随手可点，
 * connected 那几处「关掉面板后正文才可读」的用例不必先滚到底。
 * 容器取 `appColors().surface`、面板内每一处前景显式给 `appColors()` 档——容器与墨同源，
 * 深浅两套各自成立（原口径由 `GlassPanelInkPairingContractTest` 钉在 `GlassDialog` 族上；
 * 本屏退出该族后同一口径由 `ArticleReadingLayoutContractTest` 第 3 条接手）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverageSheet(
    lemmas: List<String>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = appColors().surface,
        modifier = Modifier.testTag("coverage_popup"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "本文覆盖你已背的 ${lemmas.size} 个词",
                    style = AppType.Headline,
                    color = appColors().text1,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("coverage_popup_count"),
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("coverage_popup_close"),
                ) {
                    Text(text = "我知道了", style = AppType.Label, color = appColors().text2)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp),
            ) {
                lemmas.forEach { lemma ->
                    Text(
                        text = lemma,
                        style = AppType.Body,
                        color = appColors().text1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("coverage_chip_$lemma")
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * 吸底操作条：本屏唯一的主 CTA。
 *
 * 「完成阅读」原来排在正文、未覆盖词之后——文章越长越点不到，而它恰恰是这一屏唯一的主操作。
 * 现在它是页头与正文之外的第三条带：条底铺页底色 `background`（不是 `surface`），靠顶部 1dp
 * 发丝线与滚动正文分层，正文滚动面在它上面，滚动时不动。
 *
 * 高度取 `heightIn(min = 50.dp)`：spec §4.5 钉主 CTA 全宽 50–52dp，计划写的 48dp 是触控下限，
 * 这里取两者交集的下沿。实底取 `strongSurface` + `onStrong`（房内主操作档，同 `WorksheetPreviewScreen`），
 * 禁用态落 `tint3` + `text3`（已完成阅读后按钮转为「已完成阅读」并禁用，幂等由 articleId 主键保证）。
 */
@Composable
private fun ArticleCompleteBar(
    completed: Boolean,
    bilingualAvailable: Boolean,
    onOpenBilingual: () -> Unit,
    onCompleteReading: () -> Unit,
) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .testTag("article_complete_action_bar")
            .then(hairlineTop(colors.outlineSoft))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // 原型 `.foot` 是两枚并列：`btn-secondary`「双语」+ `btn-primary`「读完了」。
        // 本仓不新开对照页（Task 21-D 裁决），第二枚的落点因此是「切到双语 + 展开译文」。
        // 没有译文时整枚不画：一枚点了没反应的按钮比没有更糟。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (bilingualAvailable) {
                OutlinedButton(
                    onClick = onOpenBilingual,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("article_open_bilingual"),
                    shape = AppShape.Button,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text1),
                    border = BorderStroke(1.dp, colors.outlineSoft),
                ) {
                    Text(text = "双语", style = AppType.Label, color = colors.text1)
                }
            }
            Button(
                onClick = onCompleteReading,
                enabled = !completed,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("article_complete_reading"),
                shape = AppShape.Button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.strongSurface,
                    contentColor = colors.onStrong,
                    disabledContainerColor = colors.tint3,
                    disabledContentColor = colors.text3,
                ),
            ) {
                Text(
                    text = if (completed) "已完成阅读" else "完成阅读",
                    style = AppType.Label,
                    color = if (completed) colors.text3 else colors.onStrong,
                )
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    mode: ArticleDisplayMode,
    tag: String,
    selected: ArticleDisplayMode,
    onModeChange: (ArticleDisplayMode) -> Unit,
) {
    FilterChip(
        selected = selected == mode,
        onClick = { onModeChange(mode) },
        label = { Text(label) },
        modifier = Modifier.testTag(tag),
    )
}

/**
 * 英文正文：单节点 `AnnotatedString`，高亮区间加粗并用阅读域深色墨。
 *
 * 高亮墨取 `appDomain(AppDomain.Reading).deep` 而不是静态薄荷桥：浅套 9C3B4F 压背景 ≈5.9:1、
 * 深套 EDA8B4 压深底更高——域色两套都由 `appColors()` 家族供给，屏侧不再出现第二套色源。
 */
@Composable
private fun HighlightedEnglish(
    englishText: String,
    highlights: List<com.example.englishlearning.reading.WordHighlight>,
    cards: List<WordCard>,
    onOpenCard: (WordCard) -> Unit,
    onOpenDictionary: (String) -> Unit,
) {
    val highlightInk = appDomain(AppDomain.Reading).deep
    val annotated = buildAnnotatedString {
        var cursor = 0
        for (highlight in highlights.sortedBy { it.start }) {
            if (highlight.start < cursor) continue // 与派生器的贪心去重双保险
            append(englishText, cursor, highlight.start)
            withStyle(SpanStyle(color = highlightInk, fontWeight = FontWeight.Bold)) {
                append(englishText, highlight.start, highlight.end)
            }
            // Task 26 Step 1（F5-03 移交账）：词区间以字符串注解留在正文上，供语义层与判据
            // 逐词取回（整段只有一个合并节点，读屏无法按字切分，靠区间 + 自定义动作逐词可达）。
            addStringAnnotation(
                tag = WORD_ANNOTATION_TAG,
                annotation = highlight.lemma,
                start = highlight.start,
                end = highlight.end,
            )
            cursor = highlight.end
        }
        append(englishText, cursor, englishText.length)
    }

    // 点按与读屏自定义动作共用同一条落点解析：有词卡开词卡，否则走词典。
    fun openHighlight(highlight: com.example.englishlearning.reading.WordHighlight) {
        val card = cards.firstOrNull { it.cardId == highlight.cardId || it.lemma == highlight.lemma }
        if (card != null) onOpenCard(card) else onOpenDictionary(highlight.matched.ifBlank { highlight.lemma })
    }

    // 逐词动作（TalkBack：聚焦正文后打开「操作」逐个词选择）。同一 lemma 在正文里出现多次时
    // 落点相同，去重后只留一枚，避免读屏动作列表里出现同名重复项。
    val wordActions = highlights.sortedBy { it.start }.distinctBy { it.lemma }.map { highlight ->
        val card = cards.firstOrNull { it.cardId == highlight.cardId || it.lemma == highlight.lemma }
        val label = if (card != null) {
            "打开词卡：${highlight.lemma}"
        } else {
            "查词：${highlight.matched.ifBlank { highlight.lemma }}"
        }
        CustomAccessibilityAction(label) {
            openHighlight(highlight)
            true
        }
    }
    androidx.compose.foundation.text.ClickableText(
        text = annotated,
        // Task 24（裁决⑤ 附带债）：这一行原先把浅套深墨以裸 hex 写死，深色档下英文正文直接不可读。
        // 落底是屏根 `Column` 的 `background`（读 `appColors()` 的组合期取值）——**容器随主题换套**，
        // 所以前景同样换套才成立：旧墨压深套底只有 1.38:1（正文线 4.5，连装饰线 3.1 都不过），
        // 换 `appColors().text1` 后浅/深两套实测 16.09 / 16.20。判据与复算见
        // `ReadingHomeLayoutContractTest` `[the english body ink clears the AA gate on both theme sets]`。
        // 形状按 R-22.3 归 `AppType.Body` 并显式给墨（房内配平先例：面板内前景必须逐枚给 `appColors()`，
        // 只借 `AppType` 会连着它自带的主题墨一起落下——这里显式给是为了让判据能逐字取样）。
        // 原型 `.prose`：衬线 17.5px / 行高 1.72 —— `AppType.Prose` 已是这两项的逐值档（17.5sp / 30sp），
        // 旧实现在 15sp 正文上手写 22.5sp 行高，等于给正文配了第二套行距。
        // `ReadingHomeLayoutContractTest` 按精确子串取样，形状仍保持「token + 显式 appColors().text1」。
        style = AppType.Prose.copy(color = appColors().text1),
        modifier = Modifier
            .testTag("article_english")
            .semantics { customActions = wordActions },
        onClick = { offset ->
            val hit = highlights.firstOrNull { offset in it.start until it.end } ?: return@ClickableText
            openHighlight(hit)
        },
    )
}

/** 正文逐词注解的 tag；判据侧按同一字面量取区间（`getStringAnnotations`）。 */
private const val WORD_ANNOTATION_TAG = "word"
