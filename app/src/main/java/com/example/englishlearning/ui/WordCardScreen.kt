package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SpeakerButton
import com.example.englishlearning.ui.components.SpeakerState
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

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
    // F3-02 四控：呈现条件由宿主给的状态决定，动作一律回调出去（UI 不碰播放器）。
    pronunciationStatus: PronunciationStatus = PronunciationStatus.Idle,
    onPauseResume: () -> Unit = {},
    onStopPronunciation: () -> Unit = {},
    // 决策 §2（Task 7 取证 / Task 9 裁决 B2）：系统 TTS 无公开 pause()/resume()，
    // 因此「暂停/继续」只能由宿主的**引擎选择**决定呈现，不能靠 pausePlayback() 的返回值
    // （返回值决定会让系统 TTS 下点暂停落到 Idle，按钮组消失而朗读继续）。
    pauseResumeSupported: Boolean = false,
    onRetry: () -> Unit = {},
    onStartNewPhase: () -> Unit = {},
    onBackToPlan: () -> Unit = {},
) {
    // F5-03 移交账「退出学习不停播」的还账点：本屏离开组合（退出学习浮层、切走学习流、宿主销毁整屏）
    // 时把停播恰好委托给宿主一次。三个要件缺一不可，都被 `WordCardScreenContractTest` 钉住：
    //  - 挂在 `when (state)` **之外**：停播只跟「整屏离开组合」绑定，切卡 / 揭晓 / 提交翻态都不许误停播；
    //  - [rememberUpdatedState]：离开组合那一刻要拿到宿主**最后一版**回调（代次自增与状态清零都在闭包里，
    //    拿首次组合那版等于漏清），所以不能把 `onStopPronunciation` 直接闭进 `remember`；
    //  - [WordCardExitStopGuard]：转发至多一次，并让「收到过停播」这件事在 JVM 侧有可计数的落点。
    // 屏侧绝不直接对播放器下手（既有红线）——走的就是「停止」按钮那同一个回调。
    val stopIntent = rememberUpdatedState(onStopPronunciation)
    val exitStopGuard = remember { WordCardExitStopGuard { stopIntent.value() } }
    DisposableEffect(exitStopGuard) {
        onDispose { exitStopGuard.onDispose() }
    }
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
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = appColors().onBrand),
                ) { Text("重试") }
            }

            WordCardUiState.NoCards -> {
                Text("今天暂无学习任务", color = MintTextMuted, modifier = Modifier.testTag("word_card_no_cards"))
                BackToPlanButton(onBackToPlan)
            }

            is WordCardUiState.ReviewCompleted -> {
                Text(
                    text = "到期复习已完成",
                    color = appDomain(AppDomain.Review).deep,
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
                        .heightIn(min = 54.dp)
                        .testTag("word_card_start_new_phase")
                        .semantics { contentDescription = "开始学习新增词" },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = appColors().onBrand),
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
                        // Task 15：私有喇叭药片换成共享药片组件（F5-03「喇叭药片放大」的屏侧落地）。
                        // 48dp 触控下限、底色与三态朗读文案都由组件交付，屏侧只给 tag 与动作。
                        SpeakerButton(
                            state = SpeakerState.Idle,
                            onClick = { onSpeak(state.card.lemma) },
                            modifier = Modifier.testTag("word_card_speak"),
                        )
                        // F3-02 播控组：只在 Playing / Paused 窗口呈现（决策 §2）。
                        // 重播 = 再点上面那个喇叭（系统 TTS 走 QUEUE_FLUSH 天然打断，音频路径重走播放）。
                        // 这一组**必须在 word_card_status_live_region 容器之外**：容器只装瞬时状态文字
                        // （T6 修好的常驻播报区，WordCardRevealFocusTest 断它的子节点集合），
                        // 把按钮塞进去会让读屏把按钮念成状态变化的一部分。
                        val speakingOrPaused =
                            pronunciationStatus == PronunciationStatus.Playing ||
                            pronunciationStatus == PronunciationStatus.Paused
                        if (speakingOrPaused) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (pauseResumeSupported) {
                                    // 暂停-继续同样走共享药片：三态文案由组件的可见文字承载
                                    // （clickable 会把子孙合并进同一枚语义节点），屏侧不再另写一份朗读文案。
                                    SpeakerButton(
                                        state = wordCardSpeakerState(pronunciationStatus),
                                        onClick = { onPauseResume() },
                                        modifier = Modifier.testTag("word_card_pronunciation_toggle"),
                                    )
                                }
                                OutlinedButton(
                                    onClick = onStopPronunciation,
                                    modifier = Modifier
                                        .testTag("word_card_pronunciation_stop")
                                        .semantics { contentDescription = "停止播放发音" },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MintPrimaryDark),
                                ) { Text("停止") }
                            }
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
                    // Task 26 Step 3：错误行收口到共享 `InlineNotice`。旧锚 `word_card_message` 留在外层 Box
                    // （组件的 `inline_notice_error` 是后设的，同一 modifier 会被覆盖）；朗读语义仍由
                    // `contentDescription` 供出，`mergeDescendants` 让整条提示成为一个可朗读节点（与原单枚 Text 同形）。
                    Box(Modifier.testTag("word_card_message")) {
                        InlineNotice(
                            text = it,
                            kind = InlineNoticeKind.Error,
                            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = it },
                        )
                    }
                }
                // F5-03：live region 必须是常驻容器——瞬时消息作为子节点进出组合，容器本身留在树里，
                // 否则 TalkBack 没有可监听的区域（决策 §4 要求「不打断当前朗读的瞬时播报」）。
                // 代价：外层 Column 的 spacedBy(14.dp) 会在无消息时也给容器留一份间距，本屏归 UI 重设计轮改版。
                Column(
                    modifier = Modifier
                        .testTag("word_card_status_live_region")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (state.submitting) {
                        Text(
                            "正在保存…",
                            color = MintTextMuted,
                            modifier = Modifier.testTag("word_card_submitting"),
                        )
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
    val content = if (feedback == CardFeedback.Known) appColors().onBrand else MintPrimaryDark
    Button(
        onClick = { onSubmit(feedback) },
        enabled = !state.submitting,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
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
            .heightIn(min = 54.dp)
            .testTag("word_card_back_to_plan")
            .semantics { contentDescription = "返回今日计划" },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = appColors().onBrand),
    ) { Text("返回今日计划", fontWeight = FontWeight.Bold) }
}

/**
 * 宿主呈现态 → 药片三态（Task 15 Step 2「文案一处定义」的屏侧映射）。
 *
 * 播控组只在 Playing / Paused 窗口里长出来（决策 §2），所以这里只有两档有意义：Paused 才是 Paused
 * （点下去是「继续」），其余一律按 Playing 处理（点下去是「暂停」）。文案与字形仍由组件那侧分派，
 * 本函数不写第三套 when——它只回答「宿主这一档对应组件哪一态」。
 * 抽成 `internal` 纯函数是为了让这档映射能在 JVM 侧逐态点名（connected 读不到 `SpeakerState`）。
 */
internal fun wordCardSpeakerState(status: PronunciationStatus): SpeakerState =
    if (status == PronunciationStatus.Paused) SpeakerState.Paused else SpeakerState.Playing

/**
 * 退出停播守卫（F5-03 移交账「退出学习不停播」的还账本体）。
 *
 * 为什么是一枚独立的类、而不是在 dispose 回调里直接写那句 lambda：本仓 JVM 单测没有 Compose 渲染能力
 * （`ui-test-junit4` 只在 androidTestImplementation），组合期与销毁时机在 JVM 里根本跑不出来，
 * 「播放器收到过停播、而且每轮组合只收一次」这条断言就没地方落。守卫本体是无组合依赖的纯 Kotlin，
 * 假播放器能在 JVM 侧把次数钉死（`WordCardScreenContractTest`）；「接线确实被触发」另由 connected 两侧证：
 * 屏侧 `WordCardScreenTest.leavingTheScreenForwardsTheStopActionExactlyOnce`（含重进屏幕的第二轮组合）、
 * 宿主侧 `AppScreenTest.leavingLearningStopsTheStillPlayingAudioAtTheProvider`（真实退出导航打到播放器上）。
 *
 * 幂等位挡的是「同一枚守卫被 dispose 两次」（测试里替换组合、宿主 teardown 与返回键退出撞进同一次运行）。
 * 每次进入学习流都新建一枚守卫，所以重进屏幕后的那次离开照常补一次停播——这点也被上面那条用例钉住。
 */
internal class WordCardExitStopGuard(private val onStop: () -> Unit) {
    private var stopped = false

    fun onDispose() {
        if (stopped) return
        stopped = true
        onStop()
    }
}
