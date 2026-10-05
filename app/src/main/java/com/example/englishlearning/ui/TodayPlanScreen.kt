package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.WordBookProgress
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted

@Composable
fun TodayPlanScreen(
    state: TodayPlanUiState,
    onRetry: () -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onStartLearning: () -> Unit = {},
    onOpenReading: () -> Unit = {},
    onOpenLearningTools: () -> Unit = {},
    onOpenCheckIn: () -> Unit = {},
    onOpenLearningRecords: () -> Unit = {},
    onOpenVocabulary: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    vocabularyCount: Int? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            // 底部导航会吃掉 64dp，这里必须可滚动，否则小屏上最后一排按钮会被裁掉。
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("today_plan_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("今日学习计划", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark, modifier = Modifier.semantics { contentDescription = "今日学习计划" })
        SearchEntry(onClick = onOpenSearch)
        when (state) {
            TodayPlanUiState.Loading -> {
                CircularProgressIndicator(color = DomainColors.AiSpeech.deep, modifier = Modifier.size(48.dp).testTag("today_plan_loading"))
                Text("正在准备今日计划…", color = MintTextMuted)
            }
            TodayPlanUiState.MissingSetup -> {
                Text("先选择词书并设置每日目标", color = MintTextMuted, modifier = Modifier.testTag("today_plan_missing_setup"))
                Button(
                    onClick = onOpenSetup,
                    modifier = Modifier.testTag("today_plan_open_setup").semantics { contentDescription = "去设置词书" },
                    colors = ButtonDefaults.buttonColors(containerColor = DomainColors.AiSpeech.deep, contentColor = Color.White),
                ) { Text("去设置词书") }
            }
            TodayPlanUiState.Unavailable -> {
                Text("今日计划暂时无法读取，请稍后重试", color = MintTextMuted, modifier = Modifier.testTag("today_plan_unavailable"))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.testTag("today_plan_retry").semantics { contentDescription = "重试" },
                    colors = ButtonDefaults.buttonColors(containerColor = DomainColors.AiSpeech.deep, contentColor = Color.White),
                ) { Text("重试") }
            }
            is TodayPlanUiState.Ready -> {
                Card(colors = CardDefaults.cardColors(containerColor = MintSurface), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().border(1.dp, MintOutline, RoundedCornerShape(24.dp)).testTag("today_plan_summary")) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.wordBookName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                        Text("应用内学习分组，不是官方考试大纲", style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
                        Text("计划日期：${state.localDateLabel}", style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
                        // 参考竞品词书卡：左边给百分比，右边给「已学/总数 词」，条子在下，
                        // 再补一行「未学」——用户要的是一眼看出这本还剩多少没碰。
                        state.bookProgress?.let { progress ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(
                                    text = learnedPercentLabel(progress),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MintPrimaryDark,
                                    modifier = Modifier.testTag("today_plan_learned_percent"),
                                )
                                Text(
                                    text = "${progress.learned}/${progress.total} 词",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MintTextMuted,
                                    modifier = Modifier.testTag("today_plan_book_words"),
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress.fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                                    .testTag("today_plan_book_progress"),
                                color = MintPrimary,
                                trackColor = MintOutline.copy(alpha = 0.35f),
                            )
                            Text(
                                text = "未学 ${progress.unlearned} 词",
                                style = MaterialTheme.typography.bodySmall,
                                color = MintTextMuted,
                                modifier = Modifier.testTag("today_plan_book_unlearned"),
                            )
                        }
                    }
                }
                Button(
                    onClick = onOpenSetup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
                        .testTag("today_plan_open_setup_entry")
                        .semantics { contentDescription = "调整词书与目标" },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
                ) { Text("调整词书与目标", fontWeight = FontWeight.Bold) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CountCard("今日新增 ${state.newTarget} 词", "今日新增 ${state.newTarget} 词", "today_plan_new_count", Color(0xFF68BDA6))
                    CountCard("今日复习 ${state.dueTarget} 词", "今日复习 ${state.dueTarget} 词", "today_plan_due_count", Color(0xFFC7A47D))
                }
                val totalDone = state.newDone + state.dueDone
                val totalTarget = state.newTarget + state.dueTarget
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppPalette.Surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppPalette.Separator, RoundedCornerShape(18.dp))
                        .testTag("today_plan_progress_summary"),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.totalTasks == 0) {
                            Text("今天暂无学习任务", color = MintTextMuted, modifier = Modifier.testTag("today_plan_empty"))
                        } else {
                            Text("今日学习进度", color = MintPrimaryDark, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("today_plan_task_total"))
                            Text("$totalDone/$totalTarget 项已完成", color = MintPrimaryDark, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("today_plan_total_progress"))
                            Text("新增 ${state.newDone}/${state.newTarget} · 复习 ${state.dueDone}/${state.dueTarget}", color = MintTextMuted, modifier = Modifier.testTag("today_plan_progress"))
                        }
                    }
                }
                val unlockText = if (state.isUnlocked) "已解锁：文章已解锁" else "未解锁"
                Text(unlockText, color = if (state.isUnlocked) DomainColors.AiSpeech.deep else MintTextMuted, modifier = Modifier.testTag("today_plan_unlock_status"))
                // 这里原本是「优先复习 N 个到期词，再学习新增词」的文字提示。
                // 按用户要求改成图解：一条分段条把「已学 / 今日复习 / 今日新学」各画成一节，
                // 精确数字下沉到条下的图例里。所以这一段不再保留对应文案——同一件事
                // 既画条又写句子只会让主操作上方变得啰嗦。
                val workload = TodayWorkload.of(
                    bookProgress = state.bookProgress,
                    newTarget = state.newTarget,
                    newDone = state.newDone,
                    dueTarget = state.dueTarget,
                    dueDone = state.dueDone,
                )
                // 主按钮的措辞与条子同源：条子说今天还有复习量，按钮就说「开始复习」。
                val learningAction = if (workload.reviewPending > 0) "开始复习" else "开始学习"
                if (!workload.isEmpty) TodayWorkloadBar(workload)
                PillButton(
                    text = learningAction,
                    onClick = onStartLearning,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    testTag = "today_plan_start_learning",
                    contentDescription = learningAction,
                )
                Button(
                    onClick = onOpenReading,
                    enabled = state.isUnlocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("today_plan_open_reading")
                        .semantics { contentDescription = "阅读文章" },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AppPalette.Separator),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppPalette.Surface,
                        contentColor = DomainColors.Reading.deep,
                        disabledContainerColor = AppPalette.Surface,
                        disabledContentColor = AppPalette.TextSecondary,
                    ),
                ) { Text("阅读文章", fontWeight = FontWeight.Medium) }
                if (!state.isUnlocked) {
                    Text(
                        "完成新词与复习后解锁文章",
                        color = MintTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("today_plan_reading_status"),
                    )
                }
                Button(
                    onClick = onOpenLearningTools,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("today_plan_learning_tools")
                        .semantics { contentDescription = "学习工具与设置" },
                    colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
                ) { Text("学习工具与设置", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onOpenCheckIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("today_plan_open_check_in")
                        .semantics { contentDescription = "查看打卡与成就" },
                    colors = ButtonDefaults.buttonColors(containerColor = AppPalette.Surface, contentColor = Color(0xFF795B36)),
                ) { Text("查看打卡与成就", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onOpenLearningRecords,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("today_plan_open_learning_records")
                        .semantics { contentDescription = "学习记录" },
                    colors = ButtonDefaults.buttonColors(containerColor = AppPalette.Surface, contentColor = MintPrimaryDark),
                ) { Text("学习记录", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onOpenVocabulary,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("today_plan_open_vocabulary")
                        .semantics {
                            contentDescription = if (vocabularyCount == null) {
                                "生词本"
                            } else {
                                "生词本，共 ${vocabularyCount} 个词"
                            }
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = AppPalette.Surface, contentColor = MintPrimaryDark),
                ) {
                    Text(
                        if (vocabularyCount == null) "生词本" else "生词本（${vocabularyCount}个）",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MintSurface, RoundedCornerShape(16.dp))
            .border(1.dp, MintOutline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("today_plan_search_entry")
            .semantics { contentDescription = "搜索单词或短语" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕", style = MaterialTheme.typography.headlineSmall, color = MintPrimary)
        Spacer(Modifier.width(10.dp))
        Text("搜索单词或短语", color = MintTextMuted)
    }
}

@Composable
private fun RowScope.CountCard(text: String, description: String, tag: String, marker: Color) {
    val shape = RoundedCornerShape(18.dp)
    Card(
        colors = CardDefaults.cardColors(containerColor = AppPalette.Surface),
        shape = shape,
        modifier = Modifier.weight(1f).border(1.dp, AppPalette.Separator, shape).testTag(tag)
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(7.dp).background(marker, CircleShape))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = AppPalette.TextPrimary)
        }
    }
}

/**
 * 「已学 88.1%」——一位小数，**截断**而非四舍五入。
 *
 * 截断是刻意的：2680/3039 四舍五入会得到 88.2%，看起来像「多学了一个千分点」；
 * 进度条这种只增不减的指标按「已达成的下界」表述更不容易引起争议。
 *
 * 不用 `String.format` 也是刻意的：它按默认 Locale 输出，在逗号小数点的地区会给出
 * 「88,1%」。这里用整数运算凑出小数位，结果与地区无关。
 */
private fun learnedPercentLabel(progress: WordBookProgress): String {
    val tenths = (progress.fraction * 1000f).toInt()
    return "已学 ${tenths / 10}.${tenths % 10}%"
}

/** 与两张计数卡上的圆点同色，避免「今日复习」在同一个屏幕里出现两套颜色。 */
private val WorkloadReviewColor = Color(0xFFC7A47D)
private val WorkloadNewColor = Color(0xFF68BDA6)

private fun WorkloadKind.color(): Color = when (this) {
    WorkloadKind.LEARNED -> MintPrimary
    WorkloadKind.REVIEW -> WorkloadReviewColor
    WorkloadKind.NEW -> WorkloadNewColor
}

private fun WorkloadKind.label(): String = when (this) {
    WorkloadKind.LEARNED -> "已学"
    WorkloadKind.REVIEW -> "今日复习"
    WorkloadKind.NEW -> "今日新学"
}

private fun WorkloadKind.tag(): String = "today_plan_workload_${name.lowercase()}"

/**
 * 今日工作量分段条：`[已学][今日复习][今日新学]`，剩下的部分留成轨道色。
 *
 * 宽度全在 [TodayWorkload] 里算好了——那里是纯 Kotlin，语义由 JVM 单元测试钉住；
 * 这里只负责把 [WorkloadSegment] 画出来，以及把三行图例写全（含计数为 0 的那些）。
 */
@Composable
private fun TodayWorkloadBar(workload: TodayWorkload) {
    val shape = RoundedCornerShape(5.dp)
    Column(modifier = Modifier.fillMaxWidth().testTag("today_plan_workload")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(shape)
                .background(MintOutline.copy(alpha = 0.35f)),
        ) {
            workload.segments.forEach { segment ->
                Box(
                    Modifier
                        .weight(segment.share)
                        .fillMaxHeight()
                        .background(segment.kind.color())
                        .testTag(segment.kind.tag() + "_bar")
                        .semantics {
                            contentDescription = "${segment.kind.label()} ${segment.count} 词"
                        },
                )
            }
            if (workload.remainder > 0f) {
                Box(Modifier.weight(workload.remainder).fillMaxHeight())
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkloadKind.entries.forEach { kind ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(kind.color(), CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "${kind.label()} ${workload.countOf(kind)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintTextMuted,
                        modifier = Modifier.testTag(kind.tag() + "_label"),
                    )
                }
            }
        }
    }
}
