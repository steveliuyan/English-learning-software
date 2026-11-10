package com.example.englishlearning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.spelling.SpellingPrompt
import com.example.englishlearning.learning.spelling.SpellingQuiz
import com.example.englishlearning.learning.spelling.SpellingResult
import com.example.englishlearning.learning.spelling.SpellingVerdict
import com.example.englishlearning.ui.components.AppCard
import com.example.englishlearning.ui.components.AppField
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.StrongCard
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import kotlin.math.roundToInt

/**
 * 拼写测验（2026-11-09 新建屏）。
 *
 * 这一屏补的是「复习队列」里一直空着的那一格：原型 `sReview`（`assets/app.js:491-494`）在
 * 「复习方式」下画过**第二档**模式行「听发音，选释义 / 练听力时用」，2026-10-29 按「无能力就隐藏」
 * 整行摘掉（见 [ReviewQueueScreen] 类 KDoc 第 3 条）。本轮长出的是**另一种真实能力**——
 * 看中文释义、自己拼出英文单词，判分与容错在 [com.example.englishlearning.learning.spelling.SpellingJudge]
 * 里（大小写、空格、连字符、撇号不扣分；长度 ≤5 容 1 个编辑距离、>5 容 2 个，超出的按错处理）。
 * 于是那一格由**已实现**的拼写模式填回，而不是画一个没有实现的开关。
 *
 * ## 版面
 *
 *  - 页头 [AppScreenHeader]「拼写测验」+ 返回，返回落回复习队列（本屏是队列里的一个内部状态，
 *    不占宿主 `AppScreen` 的overlay 位；[BackHandler] 声明在本屏内，晚于宿主的那些处理器挂载，
 *    所以系统返回先收这一层，与仓内 BackHandler 的链序口径一致）。
 *  - 进度强卡 [StrongCard]：eyebrow 档「第 N / M 题」+ [AppType.StrongTitle] 的已拼对数，
 *    与复习队列那枚强卡同构（卡内两行的墨都走 `LocalContentColor.current`，理由同 [StrongCard] KDoc）。
 *  - 题面 [AppCard]：中文释义 + 词性 + 音标。**例句只在作答之后出现**——例句里往往就含答案本身，
 *    先摆出来等于把题给了。
 *  - 作答 [AppField]：单行输入，作答后置灰不可改（`enabled = !answered`），改字会让「判过的那一次」
 *    与屏幕上的字对不上。
 *  - 判分回执 [InlineNotice]：对 / 差一点 / 错三档各一句话，都把正确拼写念出来（这一屏的用处就是纠错）。
 *  - 结束后：结算强卡 + 一张说明卡（准确率 + 一句真话）+「再来一遍 / 返回复习队列」。
 *
 * ## 判分写进复习排期（2026-11-09 接上）
 *
 * 这一屏最初那版在结算卡上写「这一轮只在本屏结算，不改动复习排期」——那是当时的真话，因为词卡
 * 仓库没有这条写通路。现在接上了：[SpellingQuizViewModel] 把每一题交给
 * `learning/spelling/RecordSpellingFeedbackUseCase`，走的是**已有的** `SubmitCardFeedbackUseCase`
 * （幂等键、算法版本、写事件与卡状态全是同一条路），三档判分按 `SpellingVerdict.toCardFeedback()`
 * 折成 认识 / 模糊 / 不认识。planId 用 `spelling:<profileId>` 这个合成前缀（与「重学」那条先例同口径），
 * 于是词卡排期照常前移，但**不冒充**今日计划里的一次任务完成。
 *
 * 因此有两句必须按事实改写：
 *
 *  - 每题下方的回执只写调度器**真正写入**的时间（`receiptWhenLabel` 从 `nextReviewAt` 反推），
 *    不写死「3 天后」——生产用的是 FSRS 档（Again 10 分钟 / Hard 12 小时 / Good 1 天），
 *    与 V1 档的数字不同，写死就是编。
 *  - 结算卡说明改成「每题已按三档记入排期，下次什么时候问由各自间隔算出」，并点名
 *    「本地保存失败的那题时间不变」这一种真实例外。
 *
 * 接线是**可选**的（[viewModel] 与 [profileId] 都为 null 时本屏仍是纯练习器）：
 * 预览、单独 UI 测试和「只想过一遍词」的场景都不需要写库。
 */
@Composable
fun SpellingQuizScreen(
    prompts: List<SpellingPrompt>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpellingQuizViewModel? = null,
    profileId: String? = null,
) {
    val colors = appColors()
    BackHandler { onBack() }

    // 会话对象可变（[SpellingQuiz] 内部持 mutable 列表），这里不去包装它的字段，
    // 改用 `version` 记「刚推进过」，让下面那枚快照重算——一处递增、一处读取，界面不会读到半个状态。
    var round by remember(prompts) { mutableStateOf(0) }
    var version by remember(prompts) { mutableStateOf(0) }
    var input by remember(prompts) { mutableStateOf("") }
    var lastResult by remember(prompts) { mutableStateOf<SpellingResult?>(null) }
    val quiz = remember(prompts, round) { SpellingQuiz.of(prompts) }
    val snapshot = remember(quiz, version) {
        QuizSnapshot(
            index = quiz.currentIndex,
            total = quiz.total,
            finished = quiz.isFinished,
            answered = quiz.isCurrentAnswered,
            correct = quiz.correctCount,
        )
    }
    // 排期回执（见类 KDoc「判分写进复习排期」）。注入缺一不可：没有 VM 或没有 profileId 时
    // 本屏退化成纯练习器，一个字段都不写。
    val schedule = viewModel?.schedule?.collectAsState()?.value ?: SpellingScheduleState.Idle
    var recordFailed by remember(prompts, round) { mutableStateOf(false) }
    LaunchedEffect(schedule) {
        if (schedule is SpellingScheduleState.Failed) recordFailed = true
    }
    val recorder: (SpellingPrompt, SpellingVerdict) -> Unit = remember(viewModel, profileId) {
        { prompt, verdict ->
            if (viewModel != null && profileId != null) {
                viewModel.record(profileId = profileId, prompt = prompt, verdict = verdict)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.background).testTag("spelling_quiz_screen")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppScreenHeader(
                title = "拼写测验",
                onBack = onBack,
            )
            if (snapshot.total == 0) {
                // 队列空着的兜底：入口本身只在有到期词时出现，这一支是防「0 题也要拼一场」。
                InlineNotice(
                    text = "队列里还没有可以拼写的词，先回队列挑几个。",
                    kind = InlineNoticeKind.Info,
                )
            } else if (snapshot.finished) {
                StrongCard(modifier = Modifier.testTag("spelling_quiz_summary")) {
                    Text(
                        text = "本轮拼写结束",
                        style = AppType.Eyebrow,
                        color = LocalContentColor.current.copy(alpha = 0.74f),
                    )
                    Text(
                        text = "${snapshot.correct} / ${snapshot.total} 个拼对",
                        style = AppType.StrongTitle,
                        color = LocalContentColor.current,
                    )
                }
                AppCard(modifier = Modifier.testTag("spelling_quiz_summary_detail")) {
                    Text(
                        text = "准确率 ${(snapshot.correct * 100f / snapshot.total).roundToInt()}%",
                        style = AppType.Body,
                        color = colors.text1,
                    )
                    Text(
                        text = "每题结果已按三档记入复习排期：拼对当「认识」、差一点当「模糊」、" +
                            "拼错当「不认识」，下次什么时候再问由各自的间隔算出。",
                        style = AppType.Caption,
                        color = colors.text3,
                    )
                    if (recordFailed) {
                        // 真例外只说一次：写库失败的那几题排期没有动。
                        Text(
                            text = "其中有的题本地没记上，那几题的复习时间没变。",
                            style = AppType.Caption,
                            color = colors.text3,
                        )
                    }
                }
                PillButton(
                    text = "再来一遍",
                    onClick = {
                        round += 1
                        version = 0
                        input = ""
                        lastResult = null
                        viewModel?.clear()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    domain = AppDomain.Review,
                    testTag = "spelling_quiz_restart",
                    contentDescription = "再来一遍同样的词",
                )
                PillButton(
                    text = "返回复习队列",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    style = PillStyle.Secondary,
                    domain = AppDomain.Review,
                    testTag = "spelling_quiz_back",
                    contentDescription = "返回复习队列",
                )
            } else {
                StrongCard(modifier = Modifier.testTag("spelling_quiz_progress")) {
                    Text(
                        text = "第 ${snapshot.index + 1} / ${snapshot.total} 题",
                        style = AppType.Eyebrow,
                        color = LocalContentColor.current.copy(alpha = 0.74f),
                    )
                    Text(
                        text = "已拼对 ${snapshot.correct} 个",
                        style = AppType.StrongTitle,
                        color = LocalContentColor.current,
                    )
                }
                val prompt = quiz.current
                if (prompt != null) {
                    AppCard(modifier = Modifier.testTag("spelling_quiz_prompt")) {
                        Text(
                            text = prompt.meaningZh,
                            style = AppType.BodyLg,
                            color = colors.text1,
                        )
                        val gloss = listOf(prompt.partOfSpeech, prompt.ipa)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .joinToString(" · ")
                        if (gloss.isNotEmpty()) {
                            Text(
                                text = gloss,
                                style = AppType.Caption,
                                color = colors.text3,
                            )
                        }
                        val example = prompt.example
                        if (snapshot.answered && !example.isNullOrBlank()) {
                            Text(
                                text = example,
                                style = AppType.Footnote,
                                color = colors.text3,
                            )
                        }
                    }
                    AppField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth().testTag("spelling_quiz_answer"),
                        label = "拼出这个词",
                        placeholder = "看释义，写出英文",
                        hint = "大小写、空格、连字符与撇号都不扣分",
                        enabled = !snapshot.answered,
                    )
                    val result = lastResult
                    if (result != null) {
                        when (result.verdict) {
                            is SpellingVerdict.Correct -> InlineNotice(
                                text = "拼对了：${prompt.answer}",
                                kind = InlineNoticeKind.Success,
                            )
                            is SpellingVerdict.CloseMiss -> InlineNotice(
                                text = "差一点：正确拼写是 ${prompt.answer}",
                                kind = InlineNoticeKind.Info,
                            )
                            is SpellingVerdict.Wrong -> InlineNotice(
                                text = "正确拼写是 ${prompt.answer}",
                                kind = InlineNoticeKind.Error,
                            )
                        }
                    }
                    when (schedule) {
                        is SpellingScheduleState.Recorded -> Box(
                            modifier = Modifier.testTag("spelling_quiz_schedule"),
                        ) {
                            InlineNotice(
                                text = "已记入复习排期：这个词${schedule.whenLabel}。",
                                kind = InlineNoticeKind.Success,
                            )
                        }

                        SpellingScheduleState.Failed -> Box(
                            modifier = Modifier.testTag("spelling_quiz_schedule_failed"),
                        ) {
                            InlineNotice(
                                text = "这题没记上：本地保存失败，这个词的复习时间没变。",
                                kind = InlineNoticeKind.Error,
                            )
                        }

                        SpellingScheduleState.Idle, SpellingScheduleState.Saving -> Unit
                    }
                    if (!snapshot.answered) {
                        PillButton(
                            text = "检查拼写",
                            onClick = {
                                val submitted = quiz.submit(input)
                                lastResult = submitted
                                version += 1
                                if (submitted != null) {
                                    recorder(submitted.prompt, submitted.verdict)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            domain = AppDomain.Review,
                            enabled = input.isNotBlank(),
                            testTag = "spelling_quiz_submit",
                            contentDescription = "检查拼写",
                        )
                    } else {
                        PillButton(
                            text = if (snapshot.index + 1 >= snapshot.total) "看本轮结果" else "下一题",
                            onClick = {
                                quiz.advance()
                                input = ""
                                lastResult = null
                                version += 1
                                // 回执是「这一题」的话，换题时撤掉；否则下一题看起来像已经记过了。
                                viewModel?.clear()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            domain = AppDomain.Review,
                            testTag = "spelling_quiz_next",
                            contentDescription = "下一题",
                        )
                    }
                }
            }
        }
    }
}

/**
 * 一次作答后要画的五个读数（索引 / 总数 / 是否已结束 / 本题是否已判 / 已拼对数）。
 *
 * 存在的唯一理由：会话对象是可变类，直接读它的 getter 不会触发重组；把这一帧要用的读数
 * 一次取齐放进 state，重组边界就落在这一处（[SpellingQuizScreen] 里 `version` 是它的键）。
 */
private data class QuizSnapshot(
    val index: Int,
    val total: Int,
    val finished: Boolean,
    val answered: Boolean,
    val correct: Int,
)
