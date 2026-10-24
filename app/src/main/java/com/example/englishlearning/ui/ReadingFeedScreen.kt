package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishlearning.reading.FetchFailure
import com.example.englishlearning.reading.FeedItem
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 外刊选读二级层（裁决⑤ 第二件）。
 *
 * 原来它是 `ReadingAccessScreen` 里的一段内联区：Feed 一旦 `Ready`，几十枚标题按钮就把整页撑成
 * 一页流水账，主任务（今日文章）被挤出首屏。现在主页只留 `reading_source_feed` 这一枚入口，
 * 列表与抓取归本层，返回交给宿主（`AppScreen.kt` 的状态位 + `BackHandler`）。
 *
 * **四枚 Feed 状态与 feed_item 下标的语义逐字照搬**（Idle 依旧不渲染任何东西，宿主在进入本层时
 * 已经触发一次 `openFeed()`，所以第一帧起就是 Loading），包括「来源：+ feed.sourceDisplayName」
 * 这枚来源署名的权威口径——本层只换版面层次，不改取源文案，也不新增重试动作
 * （取源与 `FeedItem` 数据形状归 `ReadingAccessViewModel`，多源是 UI 轮之后的独立小批）。
 *
 * 取墨一律走 `appColors()` 而不是 `Mint*` 别名：`MintTextMuted` 就是 `appColors().text2`、
 * `MintPrimaryDark` 就是 `appColors().text1`、`colorScheme.error` 与 `appColors().error` 同一套装配值，
 * 所以搬家后浅色侧逐值不变，深色侧才真正跟着换套——新文件不留一枚 `AppPalette` / `DomainColors`
 * 静态读点，也不带裸 hex（两张棘轮当场可核）。
 */
@Composable
fun ReadingFeedScreen(
    feed: FeedUiState,
    onBack: () -> Unit,
    onFetchFeedItem: (FeedItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("reading_feed_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(title = "外刊选读", onBack = onBack)
        when (feed) {
            FeedUiState.Idle -> Unit
            FeedUiState.Loading -> Text(
                "正在获取外刊列表…",
                style = AppType.Body,
                color = colors.text2,
                modifier = Modifier.testTag("feed_loading"),
            )
            FeedUiState.Empty -> Text(
                "这个来源暂时没有可读的文章，稍后再来看看。",
                style = AppType.Body,
                color = colors.text2,
                modifier = Modifier.testTag("feed_empty"),
            )
            FeedUiState.Unreachable -> Text(
                "外刊来源暂时不可用，请检查网络后重试。",
                style = AppType.Body,
                color = colors.error,
                modifier = Modifier.testTag("feed_error"),
            )
            is FeedUiState.FetchFailed -> Text(
                "抓取失败：${feed.failure.label}",
                style = AppType.Body,
                color = colors.error,
                modifier = Modifier.testTag("feed_fetch_failed"),
            )
            is FeedUiState.Ready -> {
                Text(
                    "来源：${feed.sourceDisplayName}",
                    style = AppType.Body,
                    color = colors.text1,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("feed_source"),
                )
                feed.items.forEachIndexed { index, item ->
                    Button(
                        onClick = { onFetchFeedItem(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .testTag("feed_item_$index")
                            .semantics { contentDescription = "阅读外刊文章 ${item.title}" },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surface,
                            contentColor = colors.text1,
                        ),
                    ) { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
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
