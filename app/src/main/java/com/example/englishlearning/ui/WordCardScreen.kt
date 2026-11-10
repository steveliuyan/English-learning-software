package com.example.englishlearning.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.ui.components.AppGlyph
import com.example.englishlearning.ui.components.AppGlyphLayer
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SkeletonCard
import com.example.englishlearning.ui.components.SkeletonStatGrid
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.SpeakerButton
import com.example.englishlearning.ui.components.SpeakerState
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppTransition
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.LocalIsDark
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain
import kotlinx.coroutines.delay

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
    /**
     * 回执态的主操作（原型「下一个词 / 完成本轮」）。评分成功后来到这里，只有它推进画面——
     * 三档评分在回执态不再呈现，免得同一条回执上写出第二个学习事件。
     */
    onNext: () -> Unit = {},
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
                // 批 C-3（Spec 05 F5-05 / 决策 2026-11-07 决定 3 第一类「等数据」）：
                // 旧首帧是 48dp 转圈 + 一句「正在准备今天的词卡…」，回填时那一句话整块长成
                // 「一张词卡 + 底部三档评分」。这里镜像的是那**两块**的真实形状：
                //  ① 词卡：`SoftCard(contentPadding = 18.dp)` 里 ① 词头（`AppType.Display`，一行 ~52dp）
                //     ② 音标 + 词性 ③ 喇叭药片那一行 ④ 释义/例句若干行 —— 取 `bars = 4`、下限 180dp；
                //  ② 评分条：三枚 `weight(1f)` 等宽按钮、`spacedBy(10.dp)`、`heightIn(min = 54.dp)`，
                //     每枚两行（档位名 + 副注）——正是 `SkeletonStatGrid` 那一族形状，格数与行高取真值。
                // 顶栏那句「第 N / M 张」与进度条**不铺**：它们是计数，首帧写出来就是 F1-01 那枚假数字；
                // 让位给形状本身（族口径：只承诺横向起点对齐与档位，不逐像素复刻总高）。
                SkeletonCard(
                    modifier = Modifier.testTag("word_card_loading"),
                    bars = 4,
                    minHeight = 180.dp,
                )
                SkeletonStatGrid(cells = 3, rowHeight = 54.dp)
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

            is WordCardUiState.AllDone -> {
                // 批 1 Task 18：本轮收尾不再由本屏呈现。原型在末张卡推进后走的是 `go('learn-summary')`
                // （`assets/app.js:1688` / `:1701`），对应新建的 `TodaySummaryScreen`；宿主在 `showLearning`
                // 分支里按 `AllDone` 直接挂那一屏，所以本分支**在本仓的导航里到不了**。
                // 保留它是为了 `when (state)` 的穷尽性（漏分支等于给未来的接线留一个编译期空档），
                // 但绝不在此处再画一份收尾版面——同一条状态两处渲染，就是 Task 10 台账里
                // 「回执与评分同屏」那一族重复画面的回归。
                // 屏侧的退出停播守卫挂在 `when` 之外：本屏换成收尾屏时照样离开组合、照样补一次停播。
            }


            is WordCardUiState.Ready -> {
                // F5-03 焦点迁移：释义节点是随 meaningRevealed 翻真才进组合的新节点，
                // 不主动把焦点移过去，「显示释义」按钮离开组合后焦点就落空（设备 RED 实测：
                // 揭晓后获焦节点集合为空），读屏/键盘用户要从卡片顶部重新念，跳过刚揭晓的释义。
                // wasRevealed 用来区分「本次揭晓」与「重进组合时已是揭晓态」，后者不抢焦点。
                val receipt = state.receipt
                // 回执态一律按揭晓态画：回执若配着一张还藏着释义的卡，用户看不出自己刚才是凭印象
                // 还是凭释义选的。VM 在提交成功时已把 meaningRevealed 翻真，这里的 `||` 只是让
                // 宿主直接喂回执（测试/预览）时也不会出现「有回执、没释义」的半截卡。
                val revealed = state.meaningRevealed || receipt != null
                // QA3 A2.5：顶档（> 2.0）下唯一出口常驻吸底区，见文件末尾 `wordCardPinsRevealAction`。
                // 只在 Ready 分支读一次：降级的是这一屏的摆法，不是全局字号档。
                // QA3 A2.3 的换档判据同源于这一枚读数（`wordCardLemmaUsesCompactTier`）：
                // Ready 分支只读一次字号档，两处各自换算，不在分支里散写第二套 `> 2f`。
                val fontScale = LocalDensity.current.fontScale
                val pinRevealAction = wordCardPinsRevealAction(fontScale)
                val compactLemma = wordCardLemmaUsesCompactTier(fontScale)

                // 问题 H（m03128 真机复现）：回执态的「下一个词」与**下一张卡**的评分条落在同一处——
                // 「下一个词」实测在 (541,2187)，下一张卡的三档评分横跨 y≈2160–2198，正好压在「模糊」上。
                // 于是回执上连点两下（实测两击间隔 ~120ms）时，第二击写进的是下一张卡的评分：用户没看词
                // 就先记了一档，FSRS 排程被一条他根本没做过的判断改写。
                //
                // 挡法：**换卡之后**的头 WORD_CARD_ARM_AFTER_SWITCH_MS 毫秒里，评分与揭晓一律不响应。
                // 只挡「这一下输入」，不改外观（按钮不闪禁用色——那会把一次误触变成每张卡都看得见的抖动），
                // 窗口也短到任何「先读一眼再评」的停留都远在它之外。首次进组合不设窗口：那时没有任何上一击
                // 落在本屏上，加了只会让第一张卡白等，且 WordCardScreenTest 的即时点击全都不用改。
                val cardKey = state.card.cardId
                var cardSwitchArmed by remember { mutableStateOf(true) }
                var lastArmedCardKey by remember { mutableStateOf(cardKey) }
                LaunchedEffect(cardKey) {
                    if (lastArmedCardKey != cardKey) {
                        lastArmedCardKey = cardKey
                        cardSwitchArmed = false
                        delay(WORD_CARD_ARM_AFTER_SWITCH_MS)
                        cardSwitchArmed = true
                    }
                }
                val guardedSubmit: (CardFeedback) -> Unit = { if (cardSwitchArmed) onSubmit(it) }
                val guardedReveal: () -> Unit = { if (cardSwitchArmed) onReveal() }
                val meaningFocusRequester = remember { FocusRequester() }
                var wasRevealed by remember { mutableStateOf(state.meaningRevealed) }
                LaunchedEffect(state.meaningRevealed) {
                    if (state.meaningRevealed && !wasRevealed) {
                        // 释义节点可能已随提交/切卡离开组合：丢焦点不是学习流程的失败，禁止让它崩。
                        runCatching { meaningFocusRequester.requestFocus() }
                    }
                    wasRevealed = state.meaningRevealed
                }
                // 回执同理：它是提交成功后才出现的新节点，不迁焦点的话读屏用户只会听到释义，
                // 听不到「记成了哪一档、下次什么时候再见」。与释义焦点的先后由声明顺序决定——
                // 回执在后，读到的是回执（那才是这次操作的结果）。重进组合时已有回执不抢焦点。
                val receiptFocusRequester = remember { FocusRequester() }
                var hadReceipt by remember { mutableStateOf(receipt != null) }
                LaunchedEffect(receipt) {
                    if (receipt != null && !hadReceipt) {
                        runCatching { receiptFocusRequester.requestFocus() }
                    }
                    hadReceipt = receipt != null
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
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SoftCard(
                        modifier = Modifier.testTag("word_card_content"),
                        contentPadding = PaddingValues(18.dp),
                    ) {
                        Text(
                            text = state.card.lemma,
                            // QA3 A2.3：顶档（> 2.0）把词头从 Display 换到 StrongTitle。
                            // 排版本身没丢字（`GetTextLayoutResult` 的末行行尾 = 词头长度，实测 10/10），
                            // 丢的是**绘制**：这一档 Display 排到三行、真实占位 449dp，而可滚视口只剩
                            // 吸底区之上的 220dp，第三行落在视口之外——卡片被视口切在同一行上，
                            // 看上去就是「词头少了最后一个字母且没有省略号」。换小一档后两行 192dp，
                            // 整词进得了首帧视口（判据与阈值见文件末尾 `wordCardLemmaUsesCompactTier`）。
                            style = if (compactLemma) AppType.StrongTitle else AppType.Display,
                            modifier = Modifier
                                .testTag("word_card_lemma")
                                .semantics { contentDescription = state.card.lemma },
                        )
                        // QA3 A2.8：顶档（> 2.0）把喇叭药片另起一行。这一行的旧形是
                        // `Row { Column(weight(1f)){ 音标 ; 词性 } ; SpeakerButton }`，而药片的**可见文字**
                        // 与朗读文案同源（`SpeakerButton.kt` 的「一处定义」口径），3.0 档那四个字连字形长到
                        // 180dp，`weight(1f)` 那一侧只剩 89.6dp——`/prəˈɡresɪv/` 被切成 `/prə` `ˈgres` `ɪv/`
                        // 三截的音节（设备实拍 `.git/qa-scratch/qa3-a2-final/fs300-a23-card-scrolled.png`）。
                        // 换摆法不换内容：注脚与药片各只有一处渲染点（两个分支共用同一份），
                        // 判据 `pronunciationKeepsTheIpaOnOneLineAtTopFontScale`，阈值口径见文件末尾。
                        val pronunciationStacks = wordCardStacksPronunciationRow(fontScale)
                        val speakerPill: @Composable () -> Unit = {
                            // Task 15：私有喇叭药片换成共享药片组件（F5-03「喇叭药片放大」的屏侧落地）。
                            // 48dp 触控下限、底色与三态朗读文案都由组件交付，屏侧只给 tag 与动作。
                            // 调用窗口必须**只此一枚**（`WordCardScreenContractTest` 钉「SpeakerButton 的调用
                            // 窗口恰两枚」：待播 + 播控档各一枚），所以两个分支共用这份槽位而不是各写一遍。
                            SpeakerButton(
                                state = SpeakerState.Idle,
                                onClick = { onSpeak(state.card.lemma) },
                                modifier = Modifier.testTag("word_card_speak"),
                            )
                        }
                        if (pronunciationStacks) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                WordCardPronunciationFootnotes(
                                    ipa = state.card.ipa,
                                    partOfSpeech = state.card.partOfSpeech,
                                    partOfSpeechInk = learn.deep,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                speakerPill()
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // IPA 与词性是主词的注脚，合成一列让它们与词面左沿对齐；喇叭占行尾，
                                // 与 44sp 主词同一视觉行（旧版喇叭单独一行，等于把「怎么念」排到词义之后）。
                                WordCardPronunciationFootnotes(
                                    ipa = state.card.ipa,
                                    partOfSpeech = state.card.partOfSpeech,
                                    partOfSpeechInk = learn.deep,
                                    modifier = Modifier.weight(1f),
                                )
                                speakerPill()
                            }
                        }
                        if (revealed) {
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
                        } else if (!pinRevealAction) {
                            // Recall-first (F1-03): the meaning is only exposed by an explicit action.
                            // 组件本体抽在文件末尾的 `RevealMeaningButton`：形状与理由都归它自己那一段。
                            RevealMeaningButton(onReveal = guardedReveal, enabled = !state.submitting)
                        }
                        // QA3 A2.5：顶档未揭晓时这一格不画东西——出口那颗按钮改由吸底区常驻
                        // （见下面 `pinRevealAction` 那一处）。卡面少一枚按钮、但看得见，
                        // 比「按钮排在折叠线之下、首屏只剩评分」更接近这一屏真正给用户的选项。
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
                        if (revealed && state.card.inflections.isNotEmpty()) {
                            val inflections = state.card.inflections.joinToString("、")
                            Text(
                                text = "词形变化：$inflections",
                                style = AppType.Footnote,
                                modifier = Modifier
                                    .testTag("word_card_inflections")
                                    .semantics { contentDescription = "词形变化 $inflections" },
                            )
                        }
                        // 评分回执（原型 `recap()`）：跟着卡片一起读，而不是把卡翻走只剩一句 toast——
                        // 「记为哪一档 + 下次什么时候再见」要能被反复看。时间一律取调度器真写进去的那个。
                        if (receipt != null) {
                            WordCardReceipt(
                                receipt = receipt,
                                focusRequester = receiptFocusRequester,
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
                    // m03560（用户走查「这三个选项太下了…放在单词下面就是屏幕居中」）：评分区从「吸底槽」
                    // 改成卡面**下方的文档流块**——紧接在卡与状态行之后落位，剩下的空白留在它下面。
                    // 旧摆法是 `Column(weight(1f)) + 吸底槽`：内容区把剩余高度全吃掉，评分条被顶到屏幕最底，
                    // 握机时拇指够不着（1080×2340 实测「不认识」上沿 671dp，屏高 851dp）。
                    // 评分前/揭晓后是三档反馈，回执态换成唯一的主推进按钮（原型 `sSession` 的两种 foot）；
                    // 两者互斥——回执还挂着三档按钮，用户就会在同一条回执上写第二个事件。
                    // 互换的动效走 `AppTransition.FooterSwapEnter/Exit`：只淡换、不给位移（位移会被读成「换了一页」）。
                    // 槽体抽成 [WordCardFooterSlot]：`AnimatedVisibility` 有一枚 `ColumnScope` 扩展重载，
                    // 直接写在 `Column` 的内容 lambda 里，Kotlin 会在这个位置解析到它并报「外层隐式接收者
                    // 不允许隐式调用」——编译当场失败（这是抽出去的**唯一**硬理由，不是排版偏好）。
                    WordCardFooterSlot(
                        state = state,
                        pinRevealAction = pinRevealAction,
                        revealed = revealed,
                        onReveal = guardedReveal,
                        onSubmit = guardedSubmit,
                        onNext = onNext,
                    )
                }
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
 *
 * m03373（用户走查）起摆法是**一种**：三枚整宽上下堆叠、每枚一行（上「不认识」/ 中「模糊」/ 下「认识」），
 * 不再是「1.0 档三等宽横排、顶档才堆叠」的双摆法——那条分档判据（旧 `wordCardStacksFeedbackTiers`）随之退役。
 * 三枚横排在 1.0 档把评分条压在屏幕最底（用户原话「太下了」），堆叠后整组自然上移，且任意字号档都不会横向挤字。
 * 1.0 档实测每枚列宽 ≈ 105dp、扣掉按钮 8dp 内边距只剩 89dp，而这一档 `AppType.Label` 是 12sp×3=36sp，
 * CJK 一字一宽 = 36dp，「不认识」三字 108dp 已经站不住，「想不起来」四字 144dp 更站不住：
 * 左格排成「不认/识/想不/起来」四行，整条吸底区吃掉 226dp（设备实测 `Rect(53,1082)-(361,1675)`），
 * 可滚视口被压到 220dp——词卡那一屏剩下的就只有评分，卡面连词头都放不下（A2.3 的可见部分是同一件事的另一半）。
 *
 * 三档的内容（档位、两行文案、tag）自本批改由 [FeedbackTiers] 单点给出，两个分支各自只换容器：
 * 把三枚按钮在两个分支里各写一遍，等于给「同一档在两处措辞不同」开门。
 */
@Composable
private fun WordCardFeedbackBar(
    state: WordCardUiState.Ready,
    onSubmit: (CardFeedback) -> Unit,
) {
    val colors = appColors()
    Column(
        // m03560（用户走查「放在那个单词下面、屏幕居中」）：评分区不再是吸底工具栏，而是卡面正下方的
        // 文档流块。三件事随之撤掉——不再自铺 `surface`（没有内容从它下面穿过，也就没有「被截断」的缝，
        // 那层底色只剩「一条压屏底的横带」的读感）；不再补槽顶发丝线；不再吃 `navigationBarsPadding`
        // （那会把它从居中位置往下推一截系统条的高度）。留白交给外面的内容列统一给（20dp 左右 + 行距）。
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("word_card_feedback_bar"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // m03373：三档恒竖排（上 不认识 / 中 模糊 / 下 认识）、每枚整宽、行内「档位名 + 副注」居中。
        // 单条清单直接铺在条里——不再有第二种摆法，也就不需要第二个容器。
        FeedbackTiers.forEach { tier ->
            FeedbackButton(state, tier.feedback, tier.label, tier.subCaption, tier.tag, onSubmit, Modifier.fillMaxWidth(), inlineCaption = true)
        }
        // 原型揭晓态脚下那句（`sSession` 的 revealed 分支）。这是唯一一句在**两套流程**里都成立的
        // 评分提示：原型另一句「「认识」直接下一个；模糊/忘记展开释义」描述的是它的揭晓后重评流程，
        // 本应用的评分一律推进，照抄就是假话，故不落。
        if (state.meaningRevealed) {
            Text(
                text = "看过释义也可以照实选，这一档决定它下次什么时候再出现。",
                style = AppType.Caption,
                color = colors.text3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("word_card_feedback_caption"),
            )
        }
    }
}

/**
 * 学习流吸底槽：一枚 `Box` 把两支内容钉在同一格上，回执态与反馈态按 `receipt` 互斥淡换。
 *
 * 为什么单独抽出来：`AnimatedVisibility` 除了顶层版本，还有一枚 `ColumnScope` 扩展重载。
 * 写在 `Column` 的内容 lambda 里时，Kotlin 在「调用不带接收者的顶层版本」与「用外层 `ColumnScope`
 * 当隐式接收者」之间报歧义，实际落成后者并判「外层隐式接收者不允许隐式调用」——编译直接失败。
 * 本函数不是 `Column` 的内容，落到的就是顶层 `AnimatedVisibility`；`Box` 负责让两支共用一个槽位，
 * 淡换期间不是上下两条。
 */
@Composable
private fun WordCardFooterSlot(
    state: WordCardUiState.Ready,
    pinRevealAction: Boolean,
    revealed: Boolean,
    onReveal: () -> Unit,
    onSubmit: (CardFeedback) -> Unit,
    onNext: () -> Unit,
) {
    val receipt = state.receipt
    Box(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = receipt != null,
            enter = AppTransition.FooterSwapEnter,
            exit = AppTransition.FooterSwapExit,
        ) {
            receipt?.let { WordCardReceiptBar(receipt = it, onNext = onNext) }
        }
        AnimatedVisibility(
            visible = receipt == null,
            enter = AppTransition.FooterSwapEnter,
            exit = AppTransition.FooterSwapExit,
        ) {
            if (receipt == null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // QA3 A2.5：顶档（> 2.0）把唯一出口「显示释义」常驻在评分条之上——
                    // 词头在顶档单独就吃掉整个可滚视口，出口留在卡内等于藏在折叠线之下（判据与阈值
                    // 见文件末尾 `wordCardPinsRevealAction`；1.0 / 2.0 档卡面形状一字未动）。
                    // 回执态不进这一支：那时揭晓早已完成，出口是「下一个词」。
                    if (pinRevealAction && !revealed) {
                        // m03560：揭晓出口与评分条同住在内容列里，横向留白由外层给，这里不再自己补 20dp。
                        RevealMeaningButton(
                            onReveal = onReveal,
                            enabled = !state.submitting,
                        )
                    }
                    WordCardFeedbackBar(state = state, onSubmit = onSubmit)
                }
            }
        }
    }
}

@Composable
private fun FeedbackButton(
    state: WordCardUiState.Ready,
    feedback: CardFeedback,
    label: String,
    subCaption: String,
    tag: String,
    onSubmit: (CardFeedback) -> Unit,
    modifier: Modifier = Modifier,
    inlineCaption: Boolean = false,
) {
    val colors = appColors()
    // 原型 `.feedback .btn`（app.css:848-852）：负向两档是白面 + 40% 描边 + 档色文字，
    // 「认识」是实心档色 + 反白。三档不再借用域色与薄荷底——域色把「记不记得」和「学什么」混成一件事。
    val container =
        when (feedback) {
            CardFeedback.Known -> colors.feedbackKnown
            CardFeedback.Fuzzy, CardFeedback.Unknown -> colors.surface
        }
    val content =
        when (feedback) {
            // 实心档色上的墨：浅套反白、深套近黑（原型 `html[data-theme='dark'] .fb-known { color:#08130f }`）。
            // 走 `background` 这一对中性 token 而不是新增专用 token：两套取值恰好就是原型要的那两个极端。
            CardFeedback.Known -> if (LocalIsDark.current) colors.background else Color.White
            CardFeedback.Fuzzy -> colors.feedbackFuzzy
            CardFeedback.Unknown -> colors.feedbackAgain
        }
    val border =
        when (feedback) {
            CardFeedback.Known -> null
            CardFeedback.Fuzzy -> BorderStroke(1.dp, colors.feedbackFuzzy.copy(alpha = 0.4f))
            CardFeedback.Unknown -> BorderStroke(1.dp, colors.feedbackAgain.copy(alpha = 0.4f))
        }
    Button(
        onClick = { onSubmit(feedback) },
        enabled = !state.submitting,
        modifier = modifier
            .heightIn(min = 54.dp)
            .testTag(tag)
            .semantics { contentDescription = label },
        shape = AppShape.Button,
        border = border,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = colors.tint2,
            disabledContentColor = colors.text3,
        ),
    ) {
        // 副标（原型 `.fb-cap` 10px/500/80%）：10sp 在动态字体下过小且不在 AppType 档位表里，
        // 这里取 Caption 12/400，登记为批 1 偏离项。
        FeedbackTierContent(label = label, subCaption = subCaption, ink = content, inline = inlineCaption)
    }
}

/**
 * 一枚评分按钮里的两行文案（QA3 A2.4）：顶档整宽横排成一枚行，钉档居中竖排成两行。
 *
 * 两枚 `Text(` 只写一遍、由 `inline` 选容器：把同一份文案在两个分支各抄一次，
 * 就是「同一档在两处措辞不同」的入口，也是 `eachFeedbackTierShowsItsPrototypeSubCaption`
 * 那条 connected 判据最容易被绕开的写法。
 */
@Composable
private fun FeedbackTierContent(label: String, subCaption: String, ink: Color, inline: Boolean) {
    val tiers: @Composable () -> Unit = {
        Text(label, style = AppType.Label, color = ink, fontWeight = FontWeight.Bold)
        Text(subCaption, style = AppType.Caption, color = ink.copy(alpha = 0.8f))
    }
    if (inline) {
        // 整宽堆叠档：一行里「档位名 + 副注」成对居中对齐（用户 m03373「你居中嘛」的落点）。
        // m03560 走查补的一处：两者之间留 8dp 气口——贴着写会读成一串「不认识想不起来」，
        // 副注那 80% 的墨也分不出层次。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) { tiers() }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) { tiers() }
    }
}

/**
 * 评分三档的内容清单（QA3 A2.4 起唯一落点）：档位、两行文案、connected 定位用的 tag。
 *
 * 顺序即视觉顺序（不认识 → 模糊 → 认识），与 `theThreeTiersReportTheirFixedFeedback`
 * 那条按点击次序断言的用例同口径；枚数由 `WordCardLayoutContractTest` 点名。
 */
private class FeedbackTierSpec(
    val feedback: CardFeedback,
    val label: String,
    val subCaption: String,
    val tag: String,
)

private val FeedbackTiers = listOf(
    FeedbackTierSpec(CardFeedback.Unknown, "不认识", "想不起来", "word_card_feedback_unknown"),
    FeedbackTierSpec(CardFeedback.Fuzzy, "模糊", "差点没想起", "word_card_feedback_fuzzy"),
    FeedbackTierSpec(CardFeedback.Known, "认识", "脱口而出", "word_card_feedback_known"),
)

/**
 * 评分回执（原型 `recap()`）：一枚档色圆点 + 「记为「档名」 + 下次时间」+ 一句解释。
 *
 * 文案里没有任何数字是写死的：时间来自 [FeedbackReceipt.whenLabel]（由调度器真正写入的
 * `nextReviewAt` 反推），解释句只描述 V1 的间隔方向（不会出现原型的「本轮末尾再出现一次」
 * 或「六天后答对再翻倍」——本应用都不做）。
 */
@Composable
private fun WordCardReceipt(
    receipt: FeedbackReceipt,
    focusRequester: FocusRequester,
) {
    val colors = appColors()
    val announcement =
        "记为「${feedbackTierLabel(receipt.feedback)}」，${receipt.whenLabel}。" +
            feedbackTierTip(receipt.feedback)
    val dot =
        when (receipt.feedback) {
            CardFeedback.Unknown -> colors.feedbackAgain
            CardFeedback.Fuzzy -> colors.feedbackFuzzy
            CardFeedback.Known -> colors.feedbackKnown
        }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .testTag("word_card_recap")
            // 整块作为一个可朗读节点：焦点迁到回执时读一遍「记为哪档 + 下次何时 + 为什么」，
            // 而不是让读屏在三段之间逐节点跳。
            .focusRequester(focusRequester)
            .focusable()
            .semantics(mergeDescendants = true) { contentDescription = announcement },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outline),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(6.dp)
                    .clip(AppShape.Pill)
                    .background(dot),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "记为「${feedbackTierLabel(receipt.feedback)}」",
                        style = AppType.Meta,
                        fontWeight = FontWeight.Bold,
                        color = colors.text1,
                    )
                    Text(
                        text = receipt.whenLabel,
                        style = AppType.Meta,
                        fontWeight = FontWeight.Medium,
                        color = colors.text3,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    text = feedbackTierTip(receipt.feedback),
                    style = AppType.Caption,
                    color = colors.text2,
                )
            }
        }
    }
}

/** 回执与学习记录用同一组档名（应用术语「不认识」，不是原型的「忘记」，见批 1 报告）。 */
private fun feedbackTierLabel(feedback: CardFeedback): String =
    when (feedback) {
        CardFeedback.Unknown -> "不认识"
        CardFeedback.Fuzzy -> "模糊"
        CardFeedback.Known -> "认识"
    }

/** 回执的解释句只描述 V1 真做的事：Again 10 分钟 / Hard 1 天 / Good 3 天，且不在本轮重排。 */
private fun feedbackTierTip(feedback: CardFeedback): String =
    when (feedback) {
        CardFeedback.Unknown -> "这条记忆几乎没接通。看完例句，下次从头再回想一次。"
        CardFeedback.Fuzzy -> "想起来了，但花了时间。间隔先收短，下次会更早见到它。"
        CardFeedback.Known -> "脱口而出。间隔拉长了，下次会更晚再见到它。"
    }

/** 回执态的主推进条（原型 `.foot` 的 btn-primary btn-block）。 */
@Composable
private fun WordCardReceiptBar(
    receipt: FeedbackReceipt,
    onNext: () -> Unit,
) {
    Box(
        // m03560：与评分条同一处文档流块规格——两枚 foot 互换时槽形与位置都不变，只有槽里的按钮换了，
        // 用户读到的是「同一个位置换了操作」。不自铺底色、不补发丝线、不吃导航条内边距（理由同上）。
        // 本条的锚点 `word_card_receipt_bar` 仍是吸底槽台账（StickyFooterHairlineGuardTest）的认领符号。
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("word_card_receipt_bar"),
    ) {
        PillButton(
            text = if (receipt.hasNext) "下一个词" else "完成本轮",
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            domain = AppDomain.Learn,
            testTag = "word_card_next",
        )
    }
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
 * 词头下面那两行注脚：IPA 与词性（QA3 A2.8 把它们从屏体里抽出来，为了**只有一处渲染点**）。
 *
 * 旧形是内联在 Ready 分支的一枚 `Column`，A2.8 之后顶档要换容器（药片另起一行），
 * 如果两处分支各写一遍这两枚 `Text`，就长出「同一件控件两个措辞/两个 tag」那扇门——
 * `WordCardLayoutContractTest` 里 `word_card_ipa` / `word_card_part_of_speech` 各恰 1 处的判据当场红。
 *
 * 两行仍合成一列让它们与词面左沿对齐；[modifier] 由调用方给整幅或 `weight(1f)`，
 * 本组件不自己决定占多宽（宽度归那一行的容器，不归注脚）。
 */
@Composable
private fun WordCardPronunciationFootnotes(
    ipa: String,
    partOfSpeech: String,
    partOfSpeechInk: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = ipa,
            style = AppType.Footnote,
            modifier = Modifier
                .testTag("word_card_ipa")
                .semantics { contentDescription = "音标 $ipa" },
        )
        Text(
            text = partOfSpeech,
            style = AppType.Label,
            color = partOfSpeechInk,
            modifier = Modifier
                .testTag("word_card_part_of_speech")
                .semantics { contentDescription = "词性 $partOfSpeech" },
        )
    }
}

/**
 * 顶档降级判据（QA3 A2.5）：字号档超过验收钉住的 2.0 时，回忆态的唯一出口「显示释义」
 * 从卡内搬进吸底区常驻。
 *
 * 为什么必须是「常驻」而不是「滚得到」：本轮在 emulator-5554 走真机路径复现量到，3.0 档一枚词头
 * `progressive` 就占 755px ≈ 288dp，而吸底评分条从 `y=1145` 起画，可滚视口只剩 855px——
 * 出口整颗在折叠线之下，首屏画面是「一个词 + 一整排评分按钮」，看不出还能揭晓。
 * 滑动本身是好的（两下 swipe 后按钮升到 `[100,742][980,920]`、可点高 68dp、点得动），
 * 走查册那句「滑动不产生位移」已在报告里按节点树证伪；本条判据钉的是**首帧零滚动**。
 *
 * 阈值取严格大于 2f：2.0 是 UI 重设计 spec 钉住的组件大字号验收档（AC-R-07），那一档的卡面形状
 * 由 `WordCardScreenTest.lemmaAndRevealedMeaningStayFullyVisibleAtMaximumLargeFontScale` 守着，
 * 不许被这次降级改动。判定收在自有纯函数、屏侧只读 `LocalDensity.current.fontScale`，
 * 与批 B `AppWindowLayoutPolicy.downgradesHeroHeader` 同族（可单测、可复算，不散成一串 `if`）。
 */
internal fun wordCardPinsRevealAction(fontScale: Float): Boolean = fontScale > 2f

/**
 * 切卡后反馈 / 揭晓按钮的「保险」窗口（毫秒），用在 WordCardScreen 的 Ready 分支（见那里的 cardSwitchArmed）。
 *
 * 300ms 的来历：问题 H 的真机复现里两击间隔 ~120ms，连点必然落在窗口内；而「看一眼再评分」最短也要
 * 数百毫秒，正常操作不会被吞。窗口只认「换卡」这一次跃迁，首帧与回执态都不设窗。
 */
internal const val WORD_CARD_ARM_AFTER_SWITCH_MS = 300L

/**
 * 顶档降级判据（QA3 A2.3）：字号档超过验收钉住的 2.0 时，词头从 `AppType.Display`（46sp）
 * 换到 `AppType.StrongTitle`（27sp）。
 *
 * 设备实测（emulator-5554，`WordCardScreenTest.lemmaKeepsEveryLetterLaidOutAndVisibleAtTopFontScale`
 * 的 3.0 档红态）：`confidence` 这一枚词头排版是**完整**的（`GetTextLayoutResult` 末行行尾 = 10 =
 * 词头长度），真实占位高 449dp；可吸底区之上只剩 220dp 视口，第三行整个掉在视口外，
 * 而卡片与视口在同一行上被切断——人眼看到的是「confidenc」，既没有省略号也没有可滚的提示。
 * 走查册那句「词头尾字母被裁、无省略号」量的就是这一条。
 *
 * 为什么换字号而不是加省略号：省略号会把「这个词到底是什么」变成用户要去猜的东西，
 * 而词头正是这一屏唯一的学习对象；`StrongTitle` 仍是同一族衬线显示档，27sp 在 3.0 档排两行 192dp，
 * 整词落得进首帧视口（10 字母一行的临界值约 24sp，两行是这一档既不截字又不吃满屏的落点）。
 *
 * 阈值与 `wordCardPinsRevealAction` / `wordCardStacksPronunciationRow` 同一条口径：严格大于 2f。
 */
internal fun wordCardLemmaUsesCompactTier(fontScale: Float): Boolean = fontScale > 2f

/**
 * 顶档降级判据（QA3 A2.8，收口复测实拍发现、用户 2026-11-08 拍板「立缺陷修，同批口径」）：
 * 字号档超过验收钉住的 2.0 时，喇叭药片从「与音标/词性同一行」改成**另起一行**。
 *
 * 设备实测（emulator-5554，`WordCardScreenTest.pronunciationKeepsTheIpaOnOneLineAtTopFontScale`
 * 的红态 `expected:<1> but was:<3>`）：药片是 `Row { Column(weight(1f)){ 音标 ; 词性 } ; SpeakerButton }`
 * 的行尾一枚，而它的**可见文字**与朗读文案同源（`SpeakerButton.kt` 的「一处定义」口径，
 * 屏侧不许再补定语，`the_screen_uses_the_shared_pill_and_restates_no_readout` 钉着）——
 * 于是这一档那四个字连 18dp 字形一起长到 472px = 180dp，`weight(1f)` 那一侧只剩 235px = 89.6dp，
 * `/prəˈɡresɪv/` 被切成 `/prə` `ˈgres` `ɪv/` 三截的音节，词性 `adj.` 顺带掉出首帧。
 *
 * 换摆法而不是缩文字：药片上的字就是朗读文案，缩掉或截断等于把「点下去会发生什么」这句真值改没；
 * 也不给音标加 `maxLines`——音标是「怎么念」的权威写法，截断它比断行更糟。
 * 药片另起一行后注脚拿到整幅 ≈335dp，一行排完；词性掉出首帧那一半**不钉**成
 * 「首帧必须可见」：内容段带 `verticalScroll`，可滚，与 A2.6③ 同档（口径见决策文档 §不做什么）。
 *
 * 阈值同上三条：严格大于 2f。
 */
internal fun wordCardStacksPronunciationRow(fontScale: Float): Boolean = fontScale > 2f

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

/**
 * 回忆态那枚「显示释义」（F1-03：释义必须由一次明确动作揭开）。
 *
 * 浅底表单式按钮：揭晓不是这一屏的主操作（主操作是底部三档评分），用实心域色会把拇指从评分条吸走。
 *
 * 但**容器不能继续用 `colors.tint`**：这张卡本身就是 `SoftCard`，而 `SoftCard` 的底色正是 `colors.tint`
 * （明档 `0xFFE8F2EA`、暗档 `0xFF1C2A24`）——同色填充等于把按钮画隐形，2026-11-06 截图实测只剩一行
 * 浮着的「显示释义」，回忆→揭晓这一步的关键动作看着不像可点（F1-03 的流程从这里断掉）。
 * 改 `surface` 底 + `outlineSoft` 描边：描边是给暗档准备的，那里 `surface`(`0xFF161F1A`) 比 `tint` 更暗，
 * 只靠明度差 separation 不够。
 *
 * 为什么是一枚独立的顶层组件而不是 `WordCardScreen` 里的一段：容器墨配对判据
 * （`ContainerInkPairingContractTest`）按**顶层函数段**取样——「这一段画了随主题换套的中性容器，
 * 段内所有 `AppType` 前景就必须各自有墨」。按钮塞在大屏函数里时，那一整屏十几处与本次无关的排印点
 * 会一起被判红；判据的粒度本来就是「一个组件自己画容器 + 画前景的范围」，把它抽成组件才是配对形状。
 */
@Composable
private fun RevealMeaningButton(
    onReveal: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Button(
        onClick = onReveal,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("word_card_reveal")
            .semantics { contentDescription = "显示释义" },
        shape = AppShape.Button,
        border = BorderStroke(1.dp, colors.outlineSoft),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surface,
            contentColor = colors.onMint,
            disabledContainerColor = colors.tint2,
            disabledContentColor = colors.text3,
        ),
    ) {
        // 墨必须显式取容器给的 `LocalContentColor`：`AppType` 各档自带一份墨（Label 档 = `text2`），
        // 只写 `style = AppType.Label` 时那枚 `contentColor` 根本到不了字面。取 current 而不是写死
        // `onMint`，是为了让禁用态（`disabledContentColor = text3`）仍由 Button 逐档给，
        // 不在这里复制第二份状态判断。
        Text(
            "显示释义",
            style = AppType.Label,
            color = LocalContentColor.current,
            fontWeight = FontWeight.Bold,
        )
    }
}
