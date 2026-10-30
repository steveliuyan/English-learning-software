package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * F4-01 决策 §8「看得见」：历史按「同日+同册+同类型+同长度档」分组——
 * 单版本组保持点行直开（F3-01A 行为不变）；多版本组点开展开版本列表，
 * 每版给「重读」与「删除此版」。唯一版本连删除入口都不给，「删不空」在
 * 入口侧就先立住一道；仓储事务里的判定是最后一道。
 *
 * ## 版式（批 3 Task 2）
 *
 * 页头归共享 [AppScreenHeader]（原来自写「返回」实底按钮 + `headlineSmall` 标题），
 * 滚动面收进页头之下的 [LazyColumn]——**本屏原来零滚动**，历史一多就顶出屏幕、页头跟着走。
 *
 * 三条卡壳全拆成裸行：单版本条目一行、多版本组行一行、展开后的逐版行无壳。
 * 主从两级靠缩进（展开行左缩进 28dp）+ 发丝线表达，不再一层套一层同色卡。
 * 「删除此版」只换墨色（`colors.error`）、不做红底：危险填充归确认框，列表入口不是。
 * 展开后每版的「重读」是本屏唯一的主操作，域色只出现这一处（`appDomain(AppDomain.Reading)`）。
 */
@Composable
fun ReadingHistoryScreen(
    history: List<Article>,
    onBack: () -> Unit,
    onOpenArticle: (Article) -> Unit = {},
    onRequestDeleteVersion: (Article) -> Unit = {},
    notice: String? = null,
) {
    val colors = appColors()
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).testTag("reading_history_screen"),
    ) {
        AppScreenHeader(
            title = "阅读历史",
            onBack = onBack,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        if (notice != null) {
            Text(
                text = notice,
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .testTag("reading_version_notice"),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        ) {
            if (history.isEmpty()) {
                item {
                    Text(
                        text = "暂无本地文章。生成后的文章会保存在这台设备上，可离线重读。",
                        style = AppType.Body,
                        color = colors.text2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                            .testTag("reading_history_empty"),
                    )
                }
            } else {
                // findHistory 已按 localDate DESC, generatedAt DESC, version DESC 排全部版本行；
                // 先按日期分节（原型 `section(h.date, …)`），再在节内按版本组分株：两层 `groupBy`
                // 都保持遭遇顺序，所以节与组内都仍是「新的在前」。
                val byDate = history.groupBy { it.localDate }
                byDate.forEach { (date, articlesOnThatDate) ->
                    item("date-$date") {
                        SectionHeading(text = date)
                    }
                    articlesOnThatDate.groupBy { it.versionGroupKey }.forEach { (key, versions) ->
                        item(key) {
                            if (versions.size == 1) {
                                val article = versions.single()
                                BareRow(
                                    title = article.title,
                                    subtitle = article.localDate,
                                    topDivider = true,
                                    onClick = { onOpenArticle(article) },
                                    modifier = Modifier.testTag("reading_history_item_${article.articleId}"),
                                )
                            } else {
                                VersionGroup(
                                    groupKey = key,
                                    versions = versions,
                                    onOpenArticle = onOpenArticle,
                                    onRequestDeleteVersion = onRequestDeleteVersion,
                                )
                            }
                        }
                    }
                }
                // 原型 `app.js:811` 的尾部提示：本仓这句成立（旧版本不被覆盖、都在这份列表里），逐字搬。
                item("notice") {
                    Text(
                        text = "同一天可以重新生成文章；旧版本不会被覆盖，都在这份列表里。",
                        style = AppType.Footnote,
                        color = colors.text2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .testTag("reading_history_notice"),
                    )
                }
            }
        }
    }
}

/**
 * 多版本组：组行点按展开/收起，展开后逐版一行。
 *
 * 展开态必须同时进语义（`stateDescription` 逐字保留，`AccessibilityStateSemanticsContractTest`
 * 连变量名 `expanded` 都钉）：只看卡面文案变化的话，TalkBack 用户听不到「已展开/已收起」。
 * 组行与展开行的展开态放在**同一个**组合作用域里：作用域拆到 `LazyColumn` 的 item 之外
 * 会让滚动回收把展开态一起带走。
 */
@Composable
private fun VersionGroup(
    groupKey: String,
    versions: List<Article>,
    onOpenArticle: (Article) -> Unit,
    onRequestDeleteVersion: (Article) -> Unit,
) {
    var expanded by remember(groupKey) { mutableStateOf(false) }
    val newest = versions.first()
    Column {
        BareRow(
            title = newest.title,
            subtitle = "共 ${versions.size} 个版本 · 点按${if (expanded) "收起" else "展开"}",
            topDivider = true,
            onClick = { expanded = !expanded },
            modifier = Modifier
                .testTag("reading_history_group_$groupKey")
                .semantics { stateDescription = if (expanded) "已展开" else "已收起" },
        )
        if (expanded) {
            versions.forEach { article ->
                VersionRow(
                    article = article,
                    onOpenArticle = onOpenArticle,
                    onRequestDeleteVersion = onRequestDeleteVersion,
                )
            }
        }
    }
}

/**
 * 展开后的单版一行（无卡片壳）：日期行 + 「重读」+「删除此版」。
 *
 * 两枚动作都是 `TextButton`（次要动作不抢实底）；「重读」是本屏唯一主操作，取阅读域色，
 * 「删除此版」只取危险墨色。`heightIn(min = 48.dp)` 保住 F5-03 的大触控目标。
 */
@Composable
private fun VersionRow(
    article: Article,
    onOpenArticle: (Article) -> Unit,
    onRequestDeleteVersion: (Article) -> Unit,
) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reading_version_row_${article.articleId}")
            .padding(start = 28.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "第 ${article.version} 版 · ${article.localDate}",
            style = AppType.Body,
            color = colors.text1,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { onOpenArticle(article) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("reading_version_reread_${article.articleId}"),
        ) {
            Text("重读", style = AppType.Label, color = appDomain(AppDomain.Reading).deep)
        }
        TextButton(
            onClick = { onRequestDeleteVersion(article) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("reading_version_delete_${article.articleId}"),
        ) {
            Text("删除此版", style = AppType.Label, color = colors.error)
        }
    }
}

private val Article.versionGroupKey: String
    get() = "${localDate}_${activeWordBookId}_${articleType.name}_${lengthTier.name}"
