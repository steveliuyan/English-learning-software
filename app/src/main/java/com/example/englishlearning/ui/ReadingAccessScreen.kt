package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.UserAction
import com.example.englishlearning.ai.toUserAction
import com.example.englishlearning.reading.NotConfiguredReason
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
import com.example.englishlearning.ui.components.AppCard
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
import com.example.englishlearning.ui.components.QuietCard
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

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
 *
 * ## 令牌与动作档（Task 22-L 收口）
 *
 * 本屏是最后一批还画在 `Mint*` 旧桥上的页面之一：底色写 `MintBackground`、卡片铺 `MintSurface`/
 * `MintTint`、按钮走 M3 `Button` + `ButtonDefaults`、圆角是屏内自造的 `RoundedCornerShape(18.dp)`。
 * Task 22-L 把这套全部换掉：
 *  - 底色 `colors.background`，卡面 `AppCard`（状态卡 / 失败横幅）与 `SoftCard`（更多组 / 生成前参数），
 *    今日文章卡保留 12% 域色叠层但圆角取 `AppShape.Card`；
 *  - 动作全部归共享 `PillButton` 三档：主色恰一枚（今日文章卡里那枚 CTA），次级四枚
 *    （参数开关 / 类型行 / 历史入口 / 失败横幅「去配置」），文字档一枚（页头「换一篇」）。
 *    主色那对「亮薄荷容器 + 主题无关深墨」的逐值复算归共享纯表 `pillButtonInk` 与 `PillButtonInkTest`，
 *    屏侧只回答「用哪一档、用几枚」（`ReadingHomeLayoutContractTest` 第 6 节）。
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
    /**
     * 「本周阅读」统计卡的窗口基准日（本地时区的今天）。
     *
     * 由调用方注入而不是屏内读钟：本仓所有「今天」的口径都由宿主层给出（今日计划按本地 0 点生成），
     * 屏侧再读一次系统时钟就是第二份时间权威，且会让 connected 用例不可确定。
     */
    todayLocalDate: java.time.LocalDate = java.time.LocalDate.now(),
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
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("reading_access_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 一级 tab 页头：无返回键；「换一篇」只在今天已有文章时进 action 槽——
        // 没文章可换时那枚按钮不该在场（也就不必回答「换掉的是什么」）。
        //
        // 副标题取原型 `head()` 的 `sub`（`app.js:706`），末句按本仓落点改写：原型写「可以在设置里固定」，
        // 但本仓的类型与长度不在「设置」屏，而在本屏下方那张「生成前参数」展开卡（`ReadingPreference`）。
        // 前半句「文章由今日学过的词生成」成立：生成请求的 `targetCards` 就是今日计划里已完成的词卡。
        // 原型同一处的 `eyebrow`（`planDate · type`）**不搬**：本仓日期眉标由今日学习页的计划快照
        // （`localDateLabel`）提供，是唯一日期口径；阅读屏只有注入的 `todayLocalDate`，屏内再格式化一份
        // 就是第二套日期样式，而一级 tab 也不重复「今天是哪天」。
        AppScreenHeader(
            title = "每日阅读",
            subtitle = "文章由今日学过的词生成；长度与题材在下方「生成前参数」里设置。",
            hero = true,
            badge = { EntryIconBadge(icon = AppIcon.Newspaper, domain = AppDomain.Reading) },
            action = if (state is ReadingAccessUiState.Ready && state.todayArticle != null) {
                {
                    // 页头动作是「纯文字动作」而不是第二枚带底的按钮（原型 `.bar-action` 口径）：
                    // 实底按钮会把「一屏一个主操作」的重量抢走，主色留在今日文章卡里那枚 CTA 上。
                    PillButton(
                        text = "换一篇",
                        onClick = { regenerateConfirm = true },
                        style = PillStyle.Text,
                        domain = AppDomain.Reading,
                        testTag = "reading_regenerate",
                    )
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
                todayLocalDate = todayLocalDate,
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
    todayLocalDate: java.time.LocalDate,
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
                .clip(AppShape.Card)
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
            // 原型今日文章卡上的两枚 chip（`app.js:717-719`）：「覆盖 N 词」与版本号。
            // 两枚都取 `Article` 自己的真字段（`coveredLemmas.size` / `version`）；
            // 第三枚「未读 / 已读」**不画**——`Article` 没有阅读态字段，推断就是编造
            // （与阅读历史屏同一条口径，Task 21-B 台账第 3 行）。
            Text(
                text = "覆盖 ${state.todayArticle.coveredLemmas.size} 词 · 第 ${state.todayArticle.version} 版",
                style = AppType.Label,
                color = colors.text2,
                modifier = Modifier.testTag("reading_today_meta"),
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
        // 偏好区收进共享软卡（旧实现是 `Card(containerColor = MintTint)` + 自造 18dp 圆角）。
        // 文案仍是一句现状 + 当前取值，读的是 `ReadingPreference` 的真字段，不是稿面常量。
        SoftCard(modifier = Modifier.testTag("reading_preference")) {
            Text(
                text = "默认类型：${state.preference.defaultArticleType.label} · 长度：${state.preference.explicitLengthTier?.label ?: "按词书默认"}",
                style = AppType.Body,
                color = colors.text1,
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

    // —— 原型 `section('本周阅读', .card-quiet)`（`app.js:730-735`）：只搬本仓能派生的事实。
    // 同族开源项目（LWT / LinguaCafe）的阅读侧也只报统计、不设周目标，所以「目标是每周 5 篇，还差 2 篇」
    // 那句不写（本仓没有每周篇数配置）。窗口 = [today-6, today]，基准日由宿主注入（本地时区的今天）；
    // 字数按 `englishText` 的空白切分计数——两枚都是真数，读不到日期的行直接不计入。
    val weekStart = todayLocalDate.minusDays(6)
    val weekArticles = state.history.filter { article ->
        val date = runCatching { java.time.LocalDate.parse(article.localDate) }.getOrNull()
        date != null && !date.isBefore(weekStart) && !date.isAfter(todayLocalDate)
    }
    val weekWords = weekArticles.sumOf { article ->
        article.englishText.split(Regex("\\s+")).count { it.isNotBlank() }
    }
    SectionHeading(text = "本周阅读")
    QuietCard(modifier = Modifier.testTag("reading_week_card")) {
        Text(
            text = "近 7 天：生成 ${weekArticles.size} 篇 · 共 $weekWords 词",
            style = AppType.Body,
            color = colors.text1,
            modifier = Modifier.testTag("reading_week_stats"),
        )
    }
}

/**
 * 主 CTA 一档：共享 `PillButton` 的主色档（`PillStyle.Primary` + 品牌域 `AiSpeech`）。
 *
 * 为什么域取 `AiSpeech` 而不是 `Reading`：原型 `head()` 的主按钮就是品牌薄荷那一档，
 * 本仓的 `PillStyle.Primary + AiSpeech` 正是它的同义（品牌渐变 + **主题无关**深墨 `set.onBrand`）。
 * 其余六域的 Primary 是 `palette.deep` 纯色档，那是另一种视觉重量（深底白字），不是本屏那枚。
 *
 * 墨色配平**不在本屏**：旧实现把 `MintPrimary` 容器与 `appColors().onBrand` 前景写在屏内两行，
 * 深套 `onMint` 压亮薄荷只有 1.49:1 的坑因此要靠本屏注释防；Task 22-L 起这对配平归共享纯表
 * `pillButtonInk`，逐值复算在 `PillButtonInkTest`，本屏只声明「一屏一枚主色」（第 6 节契约）。
 *
 * 批 1 Task 7 起本屏只剩**这一枚**主色按钮（今日文章卡里的阅读 CTA，或无文章时的生成 CTA）：
 * 类型行、页头动作与两张横幅的动作都已降为次级 / 文字档，「一屏一枚薄荷主色」因此既是运行时事实、
 * 也能在源码里按站点数点出来。
 */
@Composable
private fun PrimaryCtaButton(title: String, tag: String, description: String, onClick: () -> Unit) {
    PillButton(
        text = title,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        style = PillStyle.Primary,
        domain = AppDomain.AiSpeech,
        testTag = tag,
        contentDescription = description,
    )
}

/** 「生成前参数」开关：偏好区不再常驻长列，随这一次动作出现（裁决⑤ 第三件）。 */
@Composable
private fun GenerationParamsToggle(expanded: Boolean, onClick: () -> Unit) {
    PillButton(
        text = if (expanded) "收起生成前参数" else "生成前参数：类型与长度",
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        style = PillStyle.Secondary,
        domain = AppDomain.Reading,
        testTag = "reading_generation_params_toggle",
        contentDescription = if (expanded) "收起生成前参数" else "展开生成前参数",
    )
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
    GenerationFailureCard(message = message, actionLabel = "去配置", onOpenAiSettings = onOpenAiSettings)
}

@Composable
private fun GenerationFailureBanner(failure: AiFailure, onOpenAiSettings: () -> Unit) {
    GenerationFailureCard(
        message = failure.uiText.message,
        actionLabel = if (failure.toUserAction() == UserAction.ConfigureProfile) {
            UserAction.ConfigureProfile.label
        } else {
            null
        },
        onOpenAiSettings = onOpenAiSettings,
    )
}

/**
 * 两张横幅共用的承载面：`AppCard`（surface 底 + 1dp 描边 + 共享圆角）装一句常量文案与一枚次级出口。
 *
 * 不是共享 `InlineNotice`：提示条的动作槽把 tag 固定成 `inline_notice_action`（见该组件 KDoc），
 * 而本屏的出口锚 `generation_failure_action` 是 connected 用例点名的取样点；且这里要的是
 * 「一句现状 + 一枚去配置」的可点击出口，不是提示条那种行内链接。承载面仍归共享卡，不自造第二份几何。
 *
 * [actionLabel] 为 `null` 时只画一句原因（`AiFailure` 的处置动作不是「去配置」时那枚按钮点不出路，
 * 画出来就是假出口）。`contentDescription = "生成失败原因"` 留在原因句上，语义与既有接线一字未改。
 */
@Composable
private fun GenerationFailureCard(message: String, actionLabel: String?, onOpenAiSettings: () -> Unit) {
    val colors = appColors()
    AppCard(modifier = Modifier.testTag("generation_failure")) {
        Text(
            text = message,
            style = AppType.Body,
            color = colors.text1,
            modifier = Modifier.semantics { contentDescription = "生成失败原因" },
        )
        if (actionLabel != null) {
            PillButton(
                text = actionLabel,
                onClick = onOpenAiSettings,
                modifier = Modifier.fillMaxWidth(),
                style = PillStyle.Secondary,
                domain = AppDomain.Reading,
                testTag = "generation_failure_action",
            )
        }
    }
}

@Composable
private fun AccessCard(title: String, message: String, tag: String) {
    val colors = appColors()
    AppCard(modifier = Modifier.testTag(tag)) {
        Text(title, style = AppType.Label, color = colors.text1)
        Text(message, style = AppType.Footnote, color = colors.text2)
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
    PillButton(
        text = type.label,
        onClick = { onSelectType(type) },
        modifier = Modifier.fillMaxWidth(),
        style = PillStyle.Secondary,
        domain = AppDomain.Reading,
        testTag = "reading_type_${type.name.lowercase()}",
        contentDescription = "${type.label}文章",
    )
}

@Composable
private fun HistoryButton(onOpenHistory: () -> Unit, historyCount: Int? = null) {
    PillButton(
        text = if (historyCount == null) "查看本地阅读历史" else "查看本地阅读历史（$historyCount）",
        onClick = onOpenHistory,
        modifier = Modifier.fillMaxWidth(),
        style = PillStyle.Secondary,
        domain = AppDomain.Reading,
        testTag = "reading_history_entry",
        contentDescription = "本地阅读历史",
    )
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
