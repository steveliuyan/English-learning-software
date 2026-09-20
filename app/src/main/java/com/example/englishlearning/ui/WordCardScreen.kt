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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint

/**
 * Word card screen (spec F1-03).
 *
 * Shows one plan card at a time with its lemma, IPA, part of speech and Chinese meaning;
 * the example sentence and inflections only appear when the content source provides them.
 * Exactly three fixed feedback actions are offered and there is deliberately no undo:
 * V1 must not let a submitted rating be silently replaced, or the schedule becomes ambiguous.
 */
@Composable
fun WordCardScreen(
    state: WordCardUiState,
    onSubmit: (CardFeedback) -> Unit = {},
    onRetry: () -> Unit = {},
    onBackToPlan: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("word_card_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "词卡学习",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MintPrimaryDark,
            modifier = Modifier.semantics { contentDescription = "词卡学习" },
        )
        when (state) {
            WordCardUiState.Loading -> {
                CircularProgressIndicator(
                    color = MintPrimary,
                    modifier = Modifier.size(48.dp).testTag("word_card_loading"),
                )
                Text("正在准备今天的词卡…", color = MintTextMuted)
            }

            WordCardUiState.MissingSetup ->
                Text("先选择词书并设置每日目标", color = MintTextMuted, modifier = Modifier.testTag("word_card_missing_setup"))

            WordCardUiState.Unavailable -> {
                Text("词卡暂时无法读取，请稍后重试", color = MintTextMuted, modifier = Modifier.testTag("word_card_unavailable"))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.testTag("word_card_retry").semantics { contentDescription = "重试" },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
                ) { Text("重试") }
            }

            WordCardUiState.NoCards -> {
                Text("今天暂无学习任务", color = MintTextMuted, modifier = Modifier.testTag("word_card_no_cards"))
                BackToPlanButton(onBackToPlan)
            }

            is WordCardUiState.AllDone -> {
                Text(
                    text = "今天的词卡都提交完了",
                    color = MintPrimaryDark,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("word_card_all_done"),
                )
                Text("共完成 ${state.completedCount} / ${state.total} 张", color = MintTextMuted)
                BackToPlanButton(onBackToPlan)
            }

            is WordCardUiState.Ready -> {
                Text(
                    text = "第 ${state.position} / ${state.total} 张",
                    style = MaterialTheme.typography.bodySmall,
                    color = MintTextMuted,
                    modifier = Modifier
                        .testTag("word_card_progress")
                        .semantics { contentDescription = "第 ${state.position} 张，共 ${state.total} 张" },
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MintSurface),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MintOutline, RoundedCornerShape(24.dp))
                        .testTag("word_card_content"),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = state.card.lemma,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MintPrimaryDark,
                            modifier = Modifier
                                .testTag("word_card_lemma")
                                .semantics { contentDescription = state.card.lemma },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = state.card.ipa,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MintTextMuted,
                                modifier = Modifier
                                    .testTag("word_card_ipa")
                                    .semantics { contentDescription = "音标 ${state.card.ipa}" },
                            )
                            Text(
                                text = state.card.partOfSpeech,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MintPrimary,
                                modifier = Modifier
                                    .testTag("word_card_part_of_speech")
                                    .semantics { contentDescription = "词性 ${state.card.partOfSpeech}" },
                            )
                        }
                        Text(
                            text = state.card.meaningZh,
                            style = MaterialTheme.typography.titleMedium,
                            color = MintPrimaryDark,
                            modifier = Modifier
                                .testTag("word_card_meaning")
                                .semantics { contentDescription = "释义 ${state.card.meaningZh}" },
                        )
                        state.card.example?.let { example ->
                            Text(
                                text = example,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MintTextMuted,
                                modifier = Modifier
                                    .testTag("word_card_example")
                                    .semantics { contentDescription = "例句 $example" },
                            )
                        }
                        if (state.card.inflections.isNotEmpty()) {
                            val inflections = state.card.inflections.joinToString("、")
                            Text(
                                text = "词形变化：$inflections",
                                style = MaterialTheme.typography.bodySmall,
                                color = MintTextMuted,
                                modifier = Modifier
                                    .testTag("word_card_inflections")
                                    .semantics { contentDescription = "词形变化 $inflections" },
                            )
                        }
                    }
                }
                state.message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("word_card_message").semantics { contentDescription = it },
                    )
                }
                if (state.submitting) {
                    Text("正在保存…", color = MintTextMuted, modifier = Modifier.testTag("word_card_submitting"))
                }
                FeedbackButton(state, CardFeedback.Unknown, "不认识", "word_card_feedback_unknown", onSubmit)
                FeedbackButton(state, CardFeedback.Fuzzy, "模糊", "word_card_feedback_fuzzy", onSubmit)
                FeedbackButton(state, CardFeedback.Known, "认识", "word_card_feedback_known", onSubmit)
            }
        }
    }
}

@Composable
private fun FeedbackButton(
    state: WordCardUiState.Ready,
    feedback: CardFeedback,
    label: String,
    tag: String,
    onSubmit: (CardFeedback) -> Unit,
) {
    val container = if (feedback == CardFeedback.Known) MintPrimary else MintSurface
    val content = if (feedback == CardFeedback.Known) Color.White else MintPrimaryDark
    Button(
        onClick = { onSubmit(feedback) },
        enabled = !state.submitting,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .border(1.dp, if (feedback == CardFeedback.Known) MintPrimary else MintOutline, RoundedCornerShape(18.dp))
            .testTag(tag)
            .semantics { contentDescription = label },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = MintTint,
            disabledContentColor = MintPrimaryDark,
        ),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BackToPlanButton(onBackToPlan: () -> Unit) {
    Button(
        onClick = onBackToPlan,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("word_card_back_to_plan")
            .semantics { contentDescription = "返回今日计划" },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
    ) { Text("返回今日计划", fontWeight = FontWeight.Bold) }
}
