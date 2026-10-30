package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.reading.FeedItem
import com.example.englishlearning.reading.FetchFailure
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.QuietCard
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import java.time.Instant
import java.time.ZoneId

/**
 * 外刊选读二级层（裁决⑤ 第二件；批 3 Task 1 换骨架）。
 *
 * 原来它是 `ReadingAccessScreen` 里的一段内联区：Feed 一旦 `Ready`，几十枚标题按钮就把整页撑成
 * 一页流水账，主任务（今日文章）被挤出首屏。现在主页只留 `reading_more_feed` 这一枚入口，
 * 列表与抓取归本层，返回交给宿主（`AppScreen.kt` 的状态位 + `BackHandler`）。
 *
 * **四枚 Feed 状态与 feed_item 下标的语义逐字照搬**（`Idle` 一档在修复轮 1 F-5② 由「不渲染任何东西」
 * 改为一行文案级兜底 `feed_idle`，因为宿主会在状态非 Ready 时把本层停在 Idle，旧形状等于一整页空白；
 * 宿主在进入本层时已触发一次 `openFeed()`，正常路径第一帧起就是 Loading），包括「来源：+ feed.sourceDisplayName」
 * 这枚来源署名的权威口径——本层只换版面层次，不改取源文案。
 *
 * ## 批 3 Task 1 换掉的三件事（spec §6 逐屏表「外刊选读」行）
 *
 * 1. **索引页用懒列表**：整屏 `Column + verticalScroll + forEach` 改成「页头 + `LazyColumn`」。
 *    后者按需组合条目（外刊一次几十条），且滚动面只限页头之下——页头不跟着滚。
 * 2. **条目是裸行**：每条从 M3 实底 `Button` + `RoundedCornerShape(18.dp)` 改成 `BareRow`
 *    （标题两行 + 摘要副标题 + 日期尾值）。几十枚实底按钮是「卡片汤」的极端版：每条都在喊「点我」，
 *    主次全无；裸行靠 1dp 发丝线表达「同组」，圆角归三档容器、屏侧不再自造。
 * 3. **条目补日期 + 失败态可重试**：日期复用既有 `wordListDateLabel(instant, zoneId)`，
 *    时区由 `zoneId` 形参传入（不偷读系统时区）；两种取源失败各补一枚「重试」。
 *    重试接宿主现成的 `openFeed()`——屏只多收一枚 `onRetry` 回调，**不新增数据通路、不动 ViewModel**。
 *
 * **只补日期、不补篇幅**：`FeedItem`（`reading/ArticleFeedParser.kt:7-12`）只有 `title` /
 * `articleUrl` / `publishedAtEpochMillis` / `summary`，没有篇幅字段；补篇幅要么改数据形状
 * （撞「数据层零改动」），要么按 `summary.length` 糊一个假字数，两条都不做。
 *
 * 取墨一律走 `appColors()` 而不是 `Mint*` 别名：`MintTextMuted` 就是 `appColors().text2`、
 * `MintPrimaryDark` 就是 `appColors().text1`、`colorScheme.error` 与 `appColors().error` 同一套装配值，
 * 所以搬家后浅色侧逐值不变，深色侧才真正跟着换套——本文件不留一枚 `AppPalette` / `DomainColors`
 * 静态读点，也不带裸 hex（两张棘轮当场可核）。
 *
 * 来源署名仍取 `colors.text1`（而非令牌表里更轻的 `text2`）：`ReadingFeedScreenTest` 的两条像素腿
 * 逐值钉住「浅套 / 深套 text1」，那是本层唯一的深色档证据；把它降档会连带改掉那两条期望值，
 * 而此处的立论（署名是来源归属，属正文级信息）也不要求降档。
 *
 * ## 批 2 Task 21-A：补上原型「来源说明」那一节
 *
 * 原型 `sReadFeed`（`app.js:779-795`）在列表尾部还有 `section('来源说明', .card-quiet)`。本轮把它补上，
 * 但只写本仓读得到的那件事：来源名 + 「按其来源许可仅供学习使用」+ 指向既有的「开源许可与来源」页。
 * 三处不搬：① 条目副案的 `section · length · words 词`（`FeedItem` 没有这三枚字段，改数据形状撞
 * 「数据层零改动」、按摘要长度糊字数就是编造）；② `.meta`「正文不含广告」（读不到来源页，无法证实）；
 * ③ 「VOA … 公共领域」写死（来源可配置，换来源即假话）。判据见 `ReadingFeedScreenContractTest`。
 */
@Composable
fun ReadingFeedScreen(
    feed: FeedUiState,
    onBack: () -> Unit,
    onFetchFeedItem: (FeedItem) -> Unit,
    onRetry: () -> Unit = {},
    zoneId: ZoneId = ZoneId.systemDefault(),
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("reading_feed_screen"),
    ) {
        AppScreenHeader(
            title = "外刊选读",
            onBack = onBack,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            when (feed) {
                is FeedUiState.Ready -> {
                    item {
                        Text(
                            text = "来源：${feed.sourceDisplayName}",
                            style = AppType.Footnote,
                            color = colors.text1,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                                .testTag("feed_source"),
                        )
                    }
                    itemsIndexed(feed.items) { index, item ->
                        BareRow(
                            title = item.title,
                            subtitle = item.summary.takeIf { it.isNotBlank() },
                            trailing = wordListDateLabel(
                                Instant.ofEpochMilli(item.publishedAtEpochMillis),
                                zoneId,
                            ),
                            titleMaxLines = 2,
                            topDivider = index > 0,
                            onClick = { onFetchFeedItem(item) },
                            modifier = Modifier
                                .testTag("feed_item_$index")
                                .semantics { contentDescription = "阅读外刊文章 ${item.title}" },
                        )
                    }
                    // 原型 `section('来源说明', .card-quiet)`（`app.js:792-794`）：只写本仓读得到的
                    // 那一件事——来源名与它自己的许可口径，并把来源台账指向既有的「开源许可与来源」页。
                    // 不写死 VOA / 「公共领域」（来源是可配置的），也不断言「正文不含广告」（读不到来源页）。
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            SectionHeading(text = "来源说明")
                            QuietCard(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .testTag("feed_source_notice"),
                            ) {
                                Text(
                                    text = "本屏文章来自 ${feed.sourceDisplayName}，" +
                                        "按其来源许可仅供学习使用；App 内的来源台账见「我的 · 开源许可与来源」。",
                                    style = AppType.Caption,
                                    color = colors.text3,
                                )
                            }
                        }
                    }
                }

                else -> item {
                    FeedStatusNotice(feed = feed, onRetry = onRetry)
                }
            }
        }
    }
}

/**
 * 非 `Ready` 五态共用的正文块（`Idle` / `Loading` / `Empty` / `Unreachable` / `FetchFailed`）。
 *
 * 两枚失败态各带一枚「重试」：此前只有一句文案，用户只能退回主页再进来一次。重试走 `TextButton`
 * 而不是实底按钮——它是失败态的次要动作，不与主操作抢实底（同族口径见 `VocabularyRelearnScreen`）。
 * 文案逐字不变：connected 侧按全等文案断言，换骨架不许顺手改字。
 */
@Composable
private fun FeedStatusNotice(feed: FeedUiState, onRetry: () -> Unit) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (feed) {
            // `Idle` 是修复轮 1 F-5② 的文案级兜底：宿主非 Ready 时把本层停在 Idle，
            // 旧的 `Idle -> Unit` 会让本层只剩页头。措辞沿用「正在准备」这一族但与 `feed_loading`
            // 逐字不同，两枚 tag 必须能分别点名。
            FeedUiState.Idle -> Text(
                text = "外刊列表正在准备，请稍候。",
                style = AppType.Body,
                color = colors.text2,
                modifier = Modifier.testTag("feed_idle"),
            )

            FeedUiState.Loading -> Text(
                text = "正在获取外刊列表…",
                style = AppType.Body,
                color = colors.text2,
                modifier = Modifier.testTag("feed_loading"),
            )

            FeedUiState.Empty -> Text(
                text = "这个来源暂时没有可读的文章，稍后再来看看。",
                style = AppType.Body,
                color = colors.text2,
                modifier = Modifier.testTag("feed_empty"),
            )

            FeedUiState.Unreachable -> {
                Text(
                    text = "外刊来源暂时不可用，请检查网络后重试。",
                    style = AppType.Body,
                    color = colors.error,
                    modifier = Modifier.testTag("feed_error"),
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("feed_retry"),
                ) {
                    Text("重试", style = AppType.Label, color = colors.text1)
                }
            }

            is FeedUiState.FetchFailed -> {
                Text(
                    text = "抓取失败：${feed.failure.label}",
                    style = AppType.Body,
                    color = colors.error,
                    modifier = Modifier.testTag("feed_fetch_failed"),
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("feed_retry"),
                ) {
                    Text("重试", style = AppType.Label, color = colors.text1)
                }
            }

            // `Ready` 由屏本体直接铺列表，不会走到这里：分支留着是为了让 `when` 穷尽
            // （少写这一支就得写 `else`，而 `else` 会在将来新增状态时静默吞掉它）。
            is FeedUiState.Ready -> Unit
        }
    }
}

/**
 * 抓取失败的常量文案（随 `FeedSection` 一起从主页搬来）。
 *
 * 逐条来自 `FetchFailure` 枚举，不拼自由文本：这条面是「来源侧原因」而不是「AI 泄露面」，
 * 但同样不许把原始响应体塞进界面，所以保持常量表形状。
 */
private val FetchFailure.label: String
    get() = when (this) {
        FetchFailure.NetworkUnavailable -> "网络不可用"
        FetchFailure.SourceUnreachable -> "来源暂时不可用"
        FetchFailure.BodyNotFound -> "页面里找不到正文，链接可能已失效"
        FetchFailure.BodyTooShort -> "正文太短"
        FetchFailure.QualityRejected -> "内容未通过质量检查"
    }
