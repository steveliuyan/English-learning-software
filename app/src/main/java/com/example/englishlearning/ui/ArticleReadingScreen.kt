package com.example.englishlearning.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleDisplayMode
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun formatImportedAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

/**
 * 阅读页。正文是**单个 `Text` 的 `AnnotatedString`**：高亮区间以薄荷强调色加粗，点击回调
 * `onOpenCard`。译文可见性只由 `translationExpanded` 决定——三种模式只差初始值（domain
 * 文档：译文在任何模式下都可折叠）。来源区（Task E）按 `ArticleSource` 三分支展示：
 * AI 只显示模型名；外刊显示署名与原文链接（**纯文本，绝不自动打开**）；导入显示时间与责任说明。
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
    onSetLearnedMarks: (Boolean) -> Unit = {},
    onCompleteReading: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("article_reading_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
            ) { Text("返回") }
            Button(
                onClick = onOpenPronunciationPlaceholder,
                modifier = Modifier.testTag("article_pronunciation"),
                colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
            ) { Text("发音") }
        }

        val current = state
        if (current == null) {
            Text("正在加载…", color = MintTextMuted)
            return@Column
        }

        // F3-01B：进文覆盖词弹窗——每篇文章首次打开显示一次。coveredLemmas 是文章自带
        // 的落库字段，弹窗是纯呈现：关闭状态按 articleId 记忆，切换文章重新弹。
        var coveragePopupDismissed by androidx.compose.runtime.saveable.rememberSaveable(current.article.articleId) {
            androidx.compose.runtime.mutableStateOf(false)
        }
        if (!coveragePopupDismissed && current.article.coveredLemmas.isNotEmpty()) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { coveragePopupDismissed = true },
                title = {
                    Text(
                        "本文覆盖你已背的 ${current.article.coveredLemmas.size} 个词",
                        modifier = Modifier.testTag("coverage_popup_count"),
                        fontWeight = FontWeight.Bold,
                        color = MintPrimaryDark,
                    )
                },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        current.article.coveredLemmas.forEach { lemma ->
                            Text(
                                lemma,
                                Modifier
                                    .fillMaxWidth()
                                    .testTag("coverage_chip_$lemma")
                                    .padding(vertical = 3.dp),
                                color = MintPrimaryDark,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { coveragePopupDismissed = true },
                        modifier = Modifier.testTag("coverage_popup_close"),
                    ) { Text("我知道了") }
                },
                modifier = Modifier.testTag("coverage_popup"),
            )
        }

        Text(
            current.article.title,
            modifier = Modifier.testTag("article_title"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MintPrimaryDark,
        )

        SourceHeader(current.article)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("英文优先", ArticleDisplayMode.ENGLISH_FIRST, "article_mode_english_first", current.mode, onModeChange)
            ModeChip("双语", ArticleDisplayMode.BILINGUAL, "article_mode_bilingual", current.mode, onModeChange)
            ModeChip("全文对照", ArticleDisplayMode.FULL_TRANSLATION, "article_mode_full_translation", current.mode, onModeChange)
        }

        // F3-01C：标记已背词开关。只影响高亮呈现，不动文章内容与未覆盖词 chips。
        Row(
            modifier = Modifier.fillMaxWidth().testTag("article_marks_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("标记已背的词", color = MintPrimaryDark)
            Switch(
                checked = current.showLearnedMarks,
                onCheckedChange = onSetLearnedMarks,
                modifier = Modifier.testTag("article_marks_switch"),
            )
        }

        HighlightedEnglish(
            englishText = current.article.englishText,
            highlights = current.highlights,
            cards = current.cards,
            onOpenCard = onOpenCard,
            onOpenDictionaryPlaceholder = onOpenDictionaryPlaceholder,
        )

        if (current.article.chineseText.isEmpty()) {
            Text(
                "该来源没有中文翻译",
                modifier = Modifier.testTag("article_no_translation_notice"),
                color = MintTextMuted,
            )
        }
        TextButton(
            onClick = onToggleTranslation,
            enabled = current.article.chineseText.isNotEmpty(),
            modifier = Modifier.testTag("article_translation_toggle"),
        ) { Text(if (current.translationExpanded) "收起译文" else "显示译文") }
        if (current.translationExpanded) {
            Text(
                current.article.chineseText,
                modifier = Modifier
                    .background(MintSurface, RoundedCornerShape(14.dp))
                    .padding(14.dp)
                    .testTag("article_translation"),
                color = MintPrimaryDark,
            )
        }

        // F3-02：完成阅读。落库成功后按钮转为「已完成」并禁用；幂等由 articleId 主键保证。
        Button(
            onClick = onCompleteReading,
            enabled = !current.completed,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("article_complete_reading"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (current.completed) MintSurface else MintPrimary,
                contentColor = MintPrimaryDark,
            ),
        ) { Text(if (current.completed) "已完成阅读" else "完成阅读", fontWeight = FontWeight.Bold) }

        if (current.uncoveredLemmas.isNotEmpty()) {
            Text("未覆盖词", fontWeight = FontWeight.Bold, color = MintPrimaryDark, modifier = Modifier.testTag("article_uncovered"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                current.uncoveredLemmas.forEach { lemma ->
                    val card = current.cards.firstOrNull { it.lemma == lemma || it.cardId == lemma }
                    AssistChip(
                        onClick = {
                            if (card != null) onOpenCard(card) else onOpenDictionaryPlaceholder()
                        },
                        label = { Text(lemma) },
                        modifier = Modifier.testTag("article_uncovered_$lemma"),
                    )
                }
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

@Composable
private fun HighlightedEnglish(
    englishText: String,
    highlights: List<com.example.englishlearning.reading.WordHighlight>,
    cards: List<WordCard>,
    onOpenCard: (WordCard) -> Unit,
    onOpenDictionaryPlaceholder: () -> Unit,
) {
    val annotated = buildAnnotatedString {
        var cursor = 0
        for (highlight in highlights.sortedBy { it.start }) {
            if (highlight.start < cursor) continue // 与派生器的贪心去重双保险
            append(englishText, cursor, highlight.start)
            withStyle(SpanStyle(color = MintPrimary, fontWeight = FontWeight.Bold)) {
                append(englishText, highlight.start, highlight.end)
            }
            cursor = highlight.end
        }
        append(englishText, cursor, englishText.length)
    }
    androidx.compose.foundation.text.ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF17342A)),
        modifier = Modifier.testTag("article_english"),
        onClick = { offset ->
            val hit = highlights.firstOrNull { offset in it.start until it.end } ?: return@ClickableText
            val card = cards.firstOrNull { it.cardId == hit.cardId || it.lemma == hit.lemma }
            if (card != null) onOpenCard(card) else onOpenDictionaryPlaceholder()
        },
    )
}

/**
 * 来源区（Task E）。AC2-07：来源标识不得包含 Key / Endpoint——AI 分支只渲染 `modelName`，
 * `parameterSummary` 是纯审计字段，永远不进界面。
 */
@Composable
private fun SourceHeader(article: Article) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (val source = article.source) {
            is ArticleSource.AiGenerated -> {
                Text(
                    "AI 生成 · ${source.modelName}",
                    modifier = Modifier.testTag("article_source_name"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MintTextMuted,
                )
            }
            is ArticleSource.WebFetched -> {
                Text(
                    source.displayName,
                    modifier = Modifier.testTag("article_source_name"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MintTextMuted,
                )
                Text(
                    source.attributionText,
                    modifier = Modifier.testTag("article_attribution"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MintTextMuted,
                )
                // 链接是纯文本展示，不挂任何点击动作：原文链接绝不自动打开（spec F2-06）。
                Text(
                    "原文链接（不会自动打开）",
                    modifier = Modifier.testTag("article_source_url_label"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MintTextMuted,
                )
                Text(
                    source.articleUrl,
                    modifier = Modifier.testTag("article_source_link"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MintPrimaryDark,
                )
            }
            ArticleSource.UserImported -> {
                Text(
                    "手动导入 · ${formatImportedAt(article.generatedAtEpochMillis)}",
                    modifier = Modifier.testTag("article_source_name"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MintTextMuted,
                )
                Text(
                    "内容由你自行提供，请确保你有权使用。",
                    modifier = Modifier.testTag("article_import_responsibility"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MintTextMuted,
                )
            }
        }
    }
}
