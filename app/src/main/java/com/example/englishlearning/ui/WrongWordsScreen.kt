package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted

/**
 * F4-01 决策⑤错词页：列出「最近一次反馈为不认识」的词（顺序由仓储派生，挽回后自动消失）。
 * UI-only：点行由宿主交给只读词卡详情（复用学习记录详情宿主）。
 */
@Composable
fun WrongWordsScreen(
    state: WrongWordsUiState,
    onBack: () -> Unit,
    onOpenCard: (WordCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("wrong_words_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "错词本",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MintPrimaryDark,
                modifier = Modifier.weight(1f),
            )
            Text(
                "返回",
                color = MintPrimaryDark,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .testTag("wrong_words_back")
                    .semantics { contentDescription = "返回" }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onBack)
                    .padding(10.dp),
            )
        }
        when (state) {
            WrongWordsUiState.Loading -> Text("正在统计错词…", color = MintTextMuted)
            WrongWordsUiState.Unavailable -> Text(
                "错词暂时无法读取，请稍后重试",
                color = MintTextMuted,
                modifier = Modifier.testTag("wrong_words_unavailable"),
            )
            is WrongWordsUiState.Ready -> {
                if (state.cards.isEmpty()) {
                    Text(
                        "当前没有待纠正的错词。复习中标记「不认识」的词会出现在这里，重新答对后自动消失。",
                        color = MintTextMuted,
                        modifier = Modifier.testTag("wrong_words_empty"),
                    )
                } else {
                    Text(
                        "最近一次标记为「不认识」的词，按到期先后排列。",
                        color = MintTextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    state.cards.forEachIndexed { index, card ->
                        WrongWordRow(index = index, card = card, onOpen = { onOpenCard(card) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WrongWordRow(index: Int, card: WordCard, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("wrong_words_card_$index")
            .semantics { contentDescription = "错词 ${card.lemma}" }
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(card.lemma, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            val meaning = card.meaningZh.ifBlank { card.senses.firstOrNull()?.meaningZh.orEmpty() }
            if (meaning.isNotBlank()) {
                Text(meaning, color = MintTextMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
