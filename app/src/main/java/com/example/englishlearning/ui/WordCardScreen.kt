package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint

/**
 * Word card screen (spec F1-03, recall-reveal-grade flow per decision 2026-10-14).
 *
 * The card front shows lemma, IPA and part of speech (plus the English example when the content
 * source provides it, as a recall context clue); the Chinese meaning and inflections stay hidden
 * until the user taps 显示释义. Grading with the meaning visible would measure recognition
 * instead of recall and inflate FSRS intervals, so the reveal is an explicit action. The three
 * fixed feedback actions stay available before and after reveal, and there is deliberately no
 * undo: V1 must not let a submitted rating be silently replaced, or the schedule becomes ambiguous.
 */
@Composable
fun WordCardScreen(
    state: WordCardUiState,
    onSubmit: (CardFeedback) -> Unit = {},
    onReveal: () -> Unit = {},
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
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = AppPalette.OnMintPrimary),
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
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = AppPalette.OnMintPrimary),
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
                // F5-03 焦点迁移：释义节点是随 meaningRevealed 翻真才进组合的新节点，
                // 不主动把焦点移过去，「显示释义」按钮离开组合后焦点就落空（设备 RED 实测：
                // 揭晓后获焦节点集合为空），读屏/键盘用户要从卡片顶部重新念，跳过刚揭晓的释义。
                // wasRevealed 用来区分「本次揭晓」与「重进组合时已是揭晓态」，后者不抢焦点。
                val meaningFocusRequester = remember { FocusRequester() }
                var wasRevealed by remember { mutableStateOf(state.meaningRevealed) }
                LaunchedEffect(state.meaningRevealed) {
                    if (state.meaningRevealed && !wasRevealed) {
                        // 释义节点可能已随提交/切卡离开组合：丢焦点不是学习流程的失败，禁止让它崩。
                        runCatching { meaningFocusRequester.requestFocus() }
                    }
                    wasRevealed = state.meaningRevealed
                }
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
                        if (state.meaningRevealed) {
                            Text(
                                text = state.card.meaningZh,
                                style = MaterialTheme.typography.titleMedium,
                                color = MintPrimaryDark,
                                // focusable() 不是可选项：SemanticsProperties.Focused（TalkBack 播报
                                // 焦点、测试的 isFocused() 都读它）只由 FocusableNode 写入，光挂
                                // focusRequester 的纯文本节点拿到焦点也不进语义树，等于假通过。
                                modifier = Modifier
                                    .focusRequester(meaningFocusRequester)
                                    .focusable()
                                    .testTag("word_card_meaning")
                                    .semantics { contentDescription = "释义 ${state.card.meaningZh}" },
                            )
                        } else {
                            // Recall-first (F1-03): the meaning is only exposed by an explicit action.
                            OutlinedButton(
                                onClick = onReveal,
                                enabled = !state.submitting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("word_card_reveal")
                                    .semantics { contentDescription = "显示释义" },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = MintPrimaryDark,
                                ),
                            ) { Text("显示释义", fontWeight = FontWeight.Bold) }
                        }
                        WordCardSpeakerButton(
                            lemma = state.card.lemma,
                            modifier = Modifier
                                .testTag("word_card_speak")
                                .clickable { onSpeak(state.card.lemma) },
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
                        if (state.meaningRevealed && state.card.inflections.isNotEmpty()) {
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
                    // 瞬时状态：只在这句话出现/消失时播报一次（Polite），不打断用户当前朗读。
                    Text(
                        "正在保存…",
                        color = MintTextMuted,
                        modifier = Modifier
                            .testTag("word_card_submitting")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                pronunciationMessage?.let { message ->
                    Text(
                        text = message,
                        color = MintTextMuted,
                        modifier = Modifier
                            .testTag("word_card_pronunciation_message")
                            // 同上：发音排队/播放/失败提示都是不打断当前朗读的瞬时状态。
                            .semantics {
                                contentDescription = message
                                liveRegion = LiveRegionMode.Polite
                            },
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
    // 「认识」档是浅薄荷底，白字只有 ≈2.1:1（决策 §3 禁令2），统一改深字 token。
    val content = if (feedback == CardFeedback.Known) AppPalette.OnMintPrimary else MintPrimaryDark
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
        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = AppPalette.OnMintPrimary),
    ) { Text("返回今日计划", fontWeight = FontWeight.Bold) }
}

/** 学习词卡的发音按钮：小圆片底 + Material 风格自绘喇叭，与详情页发音按钮同族。 */
@Composable
private fun WordCardSpeakerButton(lemma: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MintTint, RoundedCornerShape(50))
            .padding(6.dp)
            .semantics { contentDescription = "播放 $lemma 发音" },
    ) {
        DetailSpeakerIcon()
    }
}

/** Material `volume_up` 比例的自绘喇叭（详情页 AccentSpeakerButton 同款几何，20dp）。 */
@Composable
private fun DetailSpeakerIcon() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val boxLeft = w * 0.14f
        val boxRight = w * 0.34f
        val boxTop = h * 0.40f
        val boxBottom = h * 0.60f
        drawRect(
            color = MintPrimaryDark,
            topLeft = androidx.compose.ui.geometry.Offset(boxLeft, boxTop),
            size = androidx.compose.ui.geometry.Size(boxRight - boxLeft, boxBottom - boxTop),
        )
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(boxRight, boxTop)
                lineTo(w * 0.52f, h * 0.24f)
                lineTo(w * 0.52f, h * 0.76f)
                lineTo(boxRight, boxBottom)
                close()
            },
            color = MintPrimaryDark,
        )
        drawArc(
            color = MintPrimaryDark,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.32f),
            size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.36f),
        )
        drawArc(
            color = MintPrimaryDark,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.56f, h * 0.20f),
            size = androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.60f),
        )
    }
}
