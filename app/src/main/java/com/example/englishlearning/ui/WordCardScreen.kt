package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.ui.components.AppGlyph
import com.example.englishlearning.ui.components.AppGlyphLayer
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.SpeakerButton
import com.example.englishlearning.ui.components.SpeakerState
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
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
    val colors = appColors()
    val learn = appDomain(AppDomain.Learn)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("word_card_screen"),
    ) {
        when (state) {
            WordCardUiState.Loading -> WordCardStateBody {
                CircularProgressIndicator(
                    color = learn.base,
                    modifier = Modifier.size(48.dp).testTag("word_card_loading"),
                )
                Text("正在准备今天的词卡…", style = AppType.Body)
            }

            WordCardUiState.MissingSetup -> WordCardStateBody {
                Text(
                    text = "先选择词书并设置每日目标",
                    style = AppType.Body,
                    modifier = Modifier.testTag("word_card_missing_setup"),
                )
            }

            WordCardUiState.Unavailable -> WordCardStateBody {
                Text(
                    text = "词卡暂时无法读取，请稍后重试",
                    style = AppType.Body,
                    modifier = Modifier.testTag("word_card_unavailable"),
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .testTag("word_card_retry")
                        .semantics { contentDescription = "重试" },
                    shape = AppShape.Button,
                    colors = ButtonDefaults.buttonColors(containerColor = learn.base, contentColor = colors.onBrand),
                ) { Text("重试", style = AppType.Label, fontWeight = FontWeight.Bold) }
            }

            WordCardUiState.NoCards -> WordCardStateBody {
                Text(
                    text = "今天暂无学习任务",
                    style = AppType.Body,
                    modifier = Modifier.testTag("word_card_no_cards"),
                )
                BackToPlanButton(onBackToPlan)
            }

            is WordCardUiState.ReviewCompleted -> WordCardStateBody {
                Text(
                    text = "到期复习已完成",
                    style = AppType.Title,
                    color = appDomain(AppDomain.Review).deep,
                    modifier = Modifier.testTag("word_card_review_completed"),
                )
                Text(
                    text = "已完成复习 ${state.reviewCompleted}/${state.reviewTotal}，接下来学习 ${state.newTotal} 个新增词",
                    style = AppType.Body,
                    modifier = Modifier.testTag("word_card_phase_transition"),
                )
                Button(
                    onClick = onStartNewPhase,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .testTag("word_card_start_new_phase")
                        .semantics { contentDescription = "开始学习新增词" },
                    shape = AppShape.Button,
                    colors = ButtonDefaults.buttonColors(containerColor = learn.base, contentColor = colors.onBrand),
                ) { Text("开始学习新增词", style = AppType.Label, fontWeight = FontWeight.Bold) }
                BackToPlanButton(onBackToPlan)
            }

            is WordCardUiState.AllDone -> WordCardStateBody {
                Text(
                    text = "今天的词卡都提交完了",
                    style = AppType.Title,
                    modifier = Modifier.testTag("word_card_all_done"),
                )
                Text("共完成 ${state.completedCount} / ${state.total} 张", style = AppType.Body)
                Text(
                    text = "复习 ${state.reviewCompleted}/${state.reviewTotal} · 新增 ${state.newCompleted}/${state.newTotal}",
                    style = AppType.Body,
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
                // 顶栏（spec §5）：× 关闭 + 计数 + 进度条，固定不滚 —— 旧版把这三件混在页头与滚动流里，
                // 大字号下要滚到底才知道自己在第几张。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // × 与 AllDone 态的「返回今日计划」是同一个动作、两枚 tag：本屏唯一出口是回计划，
                        // 顶部这枚只是把「怎么出去」放到拇指够不到的左上角常驻位。
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(AppShape.Icon)
                                .clickable(onClick = onBackToPlan)
                                .testTag("word_card_close")
                                .semantics { contentDescription = "返回今日计划" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(AppShape.Icon)
                                    .background(colors.tint),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppGlyph(AppIcon.Close, colors.text1, 20.dp, layer = AppGlyphLayer.StrokeOnly)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "第 ${state.position} / ${state.total} 张",
                            style = AppType.Footnote,
                            modifier = Modifier
                                .testTag("word_card_progress")
                                .semantics { contentDescription = "第 ${state.position} 张，共 ${state.total} 张" },
                        )
                    }
                    LinearProgressIndicator(
                        progress = { wordCardProgressFraction(state.position, state.total) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(AppShape.Pill)
                            .testTag("word_card_progress_bar"),
                        color = learn.base,
                        trackColor = colors.tint,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SoftCard(
                        modifier = Modifier.testTag("word_card_content"),
                        contentPadding = PaddingValues(18.dp),
                    ) {
                        Text(
                            text = state.card.lemma,
                            style = AppType.Display,
                            modifier = Modifier
                                .testTag("word_card_lemma")
                                .semantics { contentDescription = state.card.lemma },
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // IPA 与词性是主词的注脚，合成一列让它们与词面左沿对齐；喇叭占行尾，
                            // 与 44sp 主词同一视觉行（旧版喇叭单独一行，等于把「怎么念」排到词义之后）。
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = state.card.ipa,
                                    style = AppType.Footnote,
                                    modifier = Modifier
                                        .testTag("word_card_ipa")
                                        .semantics { contentDescription = "音标 ${state.card.ipa}" },
                                )
                                Text(
                                    text = state.card.partOfSpeech,
                                    style = AppType.Label,
                                    color = learn.deep,
                                    modifier = Modifier
                                        .testTag("word_card_part_of_speech")
                                        .semantics { contentDescription = "词性 ${state.card.partOfSpeech}" },
                                )
                            }
                            // Task 15：私有喇叭药片换成共享药片组件（F5-03「喇叭药片放大」的屏侧落地）。
                            // 48dp 触控下限、底色与三态朗读文案都由组件交付，屏侧只给 tag 与动作。
                            SpeakerButton(
                                state = SpeakerState.Idle,
                                onClick = { onSpeak(state.card.lemma) },
                                modifier = Modifier.testTag("word_card_speak"),
                            )
                        }
                        if (state.meaningRevealed) {
                            Text(
                                text = state.card.meaningZh,
                                style = AppType.Title,
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
                            // 浅底表单式按钮：揭晓不是这一屏的主操作（主操作是底部三档评分），
                            // 用实心域色会把拇指从评分条吸走。
                            Button(
                                onClick = onReveal,
                                enabled = !state.submitting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("word_card_reveal")
                                    .semantics { contentDescription = "显示释义" },
                                shape = AppShape.Button,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.tint,
                                    contentColor = colors.onMint,
                                    disabledContainerColor = colors.tint2,
                                    disabledContentColor = colors.text3,
                                ),
                            ) { Text("显示释义", style = AppType.Label, fontWeight = FontWeight.Bold) }
                        }
                        // F3-02 播控组：只在 Playing / Paused 窗口呈现（决策 §2）。
                        // 重播 = 再点上面那个喇叭（系统 TTS 走 QUEUE_FLUSH 天然打断，音频路径重走播放）。
                        // 这一组**必须在 word_card_status_live_region 容器之外**：容器只装瞬时状态文字
                        // （T6 修好的常驻播报区，WordCardRevealFocusTest 断它的子节点集合），
                        // 把按钮塞进去会让读屏把按钮念成状态变化的一部分。
                        val speakingOrPaused =
                            pronunciationStatus == PronunciationStatus.Playing ||
                            pronunciationStatus == PronunciationStatus.Paused
                        if (speakingOrPaused) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (pauseResumeSupported) {
                                    // 暂停-继续同样走共享药片：三态文案由组件的可见文字承载
                                    // （clickable 会把子孙合并进同一枚语义节点），屏侧不再另写一份朗读文案。
                                    SpeakerButton(
                                        state = wordCardSpeakerState(pronunciationStatus),
                                        onClick = { onPauseResume() },
                                        modifier = Modifier.testTag("word_card_pronunciation_toggle"),
                                    )
                                }
                                TextButton(
                                    onClick = onStopPronunciation,
                                    modifier = Modifier
                                        .testTag("word_card_pronunciation_stop")
                                        .semantics { contentDescription = "停止播放发音" },
                                    colors = ButtonDefaults.textButtonColors(contentColor = colors.text1),
                                ) { Text("停止") }
                            }
                        }
                        state.card.example?.let { example ->
                            Text(
                                text = example,
                                style = AppType.Body,
                                modifier = Modifier
                                    .testTag("word_card_example")
                                    .semantics { contentDescription = "例句 $example" },
                            )
                        }
                        if (state.meaningRevealed && state.card.inflections.isNotEmpty()) {
                            val inflections = state.card.inflections.joinToString("、")
                            Text(
                                text = "词形变化：$inflections",
                                style = AppType.Footnote,
                                modifier = Modifier
                                    .testTag("word_card_inflections")
                                    .semantics { contentDescription = "词形变化 $inflections" },
                            )
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
                    Column(
                        modifier = Modifier
                            .testTag("word_card_status_live_region")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (state.submitting) {
                            Text(
                                text = "正在保存…",
                                style = AppType.Footnote,
                                modifier = Modifier.testTag("word_card_submitting"),
                            )
                        }
                        pronunciationMessage?.let { message ->
                            Text(
                                text = message,
                                style = AppType.Footnote,
                                modifier = Modifier
                                    .testTag("word_card_pronunciation_message")
                                    .semantics { contentDescription = message },
                            )
                        }
                    }
                }
                // 吸底评分条：三档反馈不再排在滚动流末尾（旧版大字号下要滚过整张卡才够得着），
                // 「不认识 / 模糊 / 认识」是这一屏唯一的主操作，永远在拇指下方。
                WordCardFeedbackBar(state = state, onSubmit = onSubmit)
            }
        }
    }
}

/**
 * 非 Ready 五态的共用壳：留白 + 可滚（大字号下 Loading 文案与重试按钮也要能滚出来）。
 *
 * 抽出来的理由不是复用好看，而是根 `Column` 已改成**不滚**的骨架（顶部固定、内容区自己滚、
 * 评分条吸底），六态各写一遍滚动容器会出现「某一态没滚」这种只在 2.0 字号下暴露的漏。
 */
@Composable
private fun WordCardStateBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/**
 * 吸底评分条（spec §5：三枚反馈按钮固定底部）。
 *
 * 三枚等宽、语义各一：认识 = 学习域实心（本屏唯一主操作），模糊 = 浅薄荷底，不认识 = 白面。
 * 不做描边——描边只留给输入框与危险动作（spec §4.1），三档评分的层级由底色承担。
 */
@Composable
private fun WordCardFeedbackBar(
    state: WordCardUiState.Ready,
    onSubmit: (CardFeedback) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("word_card_feedback_bar")
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FeedbackButton(state, CardFeedback.Unknown, "不认识", "word_card_feedback_unknown", onSubmit, Modifier.weight(1f))
        FeedbackButton(state, CardFeedback.Fuzzy, "模糊", "word_card_feedback_fuzzy", onSubmit, Modifier.weight(1f))
        FeedbackButton(state, CardFeedback.Known, "认识", "word_card_feedback_known", onSubmit, Modifier.weight(1f))
    }
}

@Composable
private fun FeedbackButton(
    state: WordCardUiState.Ready,
    feedback: CardFeedback,
    label: String,
    tag: String,
    onSubmit: (CardFeedback) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val learn = appDomain(AppDomain.Learn)
    // 「认识」档是浅薄荷底，白字只有 ≈2.1:1（决策 §3 禁令2），统一改深字 token。
    val container = when (feedback) {
        CardFeedback.Known -> learn.base
        CardFeedback.Fuzzy -> colors.tint
        CardFeedback.Unknown -> colors.surface
    }
    val content = when (feedback) {
        CardFeedback.Known -> colors.onBrand
        CardFeedback.Fuzzy -> colors.onMint
        CardFeedback.Unknown -> colors.text1
    }
    Button(
        onClick = { onSubmit(feedback) },
        enabled = !state.submitting,
        modifier = modifier
            .heightIn(min = 54.dp)
            .testTag(tag)
            .semantics { contentDescription = label },
        shape = AppShape.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = colors.tint2,
            disabledContentColor = colors.text3,
        ),
    ) { Text(label, style = AppType.Label, color = content, fontWeight = FontWeight.Bold) }
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
        shape = AppShape.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = appDomain(AppDomain.Learn).base,
            contentColor = appColors().onBrand,
        ),
    ) { Text("返回今日计划", style = AppType.Label, fontWeight = FontWeight.Bold) }
}

/**
 * 学完的卡片数 / 总数，夹进 0..1。
 *
 * 不画「当前第几张」的比例：第 1 张还没提交时进度条就已经走掉一格，会把「还剩多少」说多半格。
 * 抽成 `internal` 纯函数是为了让这档算术能在 JVM 侧点名（total = 0、越界、末张各一例）。
 */
internal fun wordCardProgressFraction(position: Int, total: Int): Float {
    if (total <= 0) return 0f
    val done = (position - 1).coerceAtLeast(0)
    return (done.toFloat() / total).coerceIn(0f, 1f)
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
