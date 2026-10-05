package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import com.example.englishlearning.ui.theme.DomainColors
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
    onSpeak: (String) -> Unit = {},
    pronunciationMessage: String? = null,
    onRetry: () -> Unit = {},
    onStartNewPhase: () -> Unit = {},
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

            is WordCardUiState.ReviewCompleted -> {
                Text(
                    text = "到期复习已完成",
                    color = DomainColors.Review.deep,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("word_card_review_completed"),
                )
                Text(
                    "已完成复习 ${state.reviewCompleted}/${state.reviewTotal}，接下来学习 ${state.newTotal} 个新增词",
                    color = MintTextMuted,
                    modifier = Modifier.testTag("word_card_phase_transition"),
                )
                Button(
                    onClick = onStartNewPhase,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("word_card_start_new_phase")
                        .semantics { contentDescription = "开始学习新增词" },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
                ) { Text("开始学习新增词", fontWeight = FontWeight.Bold) }
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
                Text(
                    "复习 ${state.reviewCompleted}/${state.reviewTotal} · 新增 ${state.newCompleted}/${state.newTotal}",
                    color = MintTextMuted,
                    modifier = Modifier.testTag("word_card_completion_breakdown"),
                )
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
                Text(
                    text = if (state.isReview) {
                        "到期复习 ${state.taskCompletedCount}/${state.taskTotal}"
                    } else {
                        "今日新增 ${state.taskCompletedCount}/${state.taskTotal}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.isReview) DomainColors.Review.deep else DomainColors.Learn.deep,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .testTag("word_card_task_type")
                        .semantics { contentDescription = if (state.isReview) "到期复习" else "今日新增" },
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
                        Canvas(
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("word_card_speak")
                                .semantics { contentDescription = "播放 ${state.card.lemma} 发音" }
                                .clickable { onSpeak(state.card.lemma) },
                        ) {
                            val left = size.width * 0.18f
                            val right = size.width * 0.48f
                            val top = size.height * 0.34f
                            val bottom = size.height * 0.66f
                            drawRect(
                                color = MintPrimary,
                                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                            )
                            drawPath(
                                path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(right, top)
                                    lineTo(size.width * 0.72f, size.height * 0.22f)
                                    lineTo(size.width * 0.72f, size.height * 0.78f)
                                    lineTo(right, bottom)
                                    close()
                                },
                                color = MintPrimary,
                            )
                            drawArc(
                                color = MintPrimary,
                                startAngle = -42f,
                                sweepAngle = 84f,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.54f, size.height * 0.28f),
                                size = androidx.compose.ui.geometry.Size(size.width * 0.34f, size.height * 0.44f),
                            )
                            drawArc(
                                color = MintPrimary,
                                startAngle = -38f,
                                sweepAngle = 76f,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.60f, size.height * 0.14f),
                                size = androidx.compose.ui.geometry.Size(size.width * 0.42f, size.height * 0.72f),
                            )
                        }
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
                pronunciationMessage?.let { message ->
                    Text(
                        text = message,
                        color = MintTextMuted,
                        modifier = Modifier
                            .testTag("word_card_pronunciation_message")
                            .semantics { contentDescription = message },
                    )
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
