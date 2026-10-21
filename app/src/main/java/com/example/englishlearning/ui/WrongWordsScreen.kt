package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.example.englishlearning.ui.components.WordRow
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
        // F5-03 / Task 11（§E.2，Task 10 评审 I-4）：头部 Row 带 `fillMaxWidth()` 在前的 `horizontalScroll`。
        // 取证（Task 11 评审 I-1）：该顺序**不产生滚动范围**，本轮未据此宣称「返回链可滚进视口」已成立；
        // 留着它只钉住修饰符顺序（反序会把 weight(1f) 标题挤成 0 宽）。真实修法归 UI 重设计轮（L-T11-1），
        // 逐位取证见 CheckInScreen.kt 的同处注释。
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
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
        // Task 20-B（计划 Step 4）：卡内那枚私有 `Column` + 两枚 `Text` 换成共享 [WordRow]。
        // 卡面（圆角/描边/底色/testTag/朗读定语/clickable）原样留在调用方；**不传 onClick**，
        // 免得与已 `clickable` 的卡形成嵌套触控目标。释义档改走共享 `wordListMeaning`
        // （原来行内手写的 `meaningZh.ifBlank { senses… }` 与它逐字同义，且空释义时它给 null，
        // 组件据此不渲染释义槽——与旧的 `if (meaning.isNotBlank())` 同一行为）。
        WordRow(
            lemma = card.lemma,
            gloss = wordListMeaning(card),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
