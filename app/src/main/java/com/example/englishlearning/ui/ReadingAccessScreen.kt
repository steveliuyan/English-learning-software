package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
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
    ) : ReadingAccessUiState {
        val historyCount: Int get() = history.size
    }
    data object Unavailable : ReadingAccessUiState
}

/**
 * 「阅读」栏的根页面。
 *
 * 它现在只作为一级 tab 使用，出口交给底部导航，所以**没有**自带的返回按钮——一级页面出现
 * 「返回上一层」本身就是层级错误，留着只会误导人。
 */
@Composable
fun ReadingAccessScreen(
    state: ReadingAccessUiState,
    onSelectType: (ArticleType) -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MintBackground).padding(horizontal = 20.dp, vertical = 24.dp).testTag("reading_access_screen"),
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
            is ReadingAccessUiState.Ready -> {
                AccessCard("文章已解锁", "选择文章类型；本阶段不会发起网络生成。", "reading_access_unlocked")
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
