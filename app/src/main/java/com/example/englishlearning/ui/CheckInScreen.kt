package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.domain.DailyLearningStats
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain
import java.time.DayOfWeek
import java.time.YearMonth

@Composable
fun CheckInScreen(
    state: CheckInUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    // Task 22-B（R-22.1）：原 `onOpenFavorites` / `onOpenWrongWords` 两枚形参已随头部裸文字链一并摘除；
    // 收藏与错词能力由合并页 MyWordsScreen 承载，打卡页不再持有这两条入口。
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MintBackground).verticalScroll(rememberScrollState()).padding(20.dp).testTag("check_in_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Task 22-A §二.1（R-22.3）：页头交给共享 AppScreenHeader，屏侧不留私有页头 Row。
        // 这里原本是一枚 `Row(fillMaxWidth().horizontalScroll(…))`：那条 `horizontalScroll` 只钉得住
        // **修饰符顺序**——反写成 `horizontalScroll().fillMaxWidth()` 会让标题的 `weight(1f)` 在无界约束下
        // 塌成 0 宽（emulator-5574 实测 `DpRect(left = right = 20.190475)`），但 F5-03 / Task 11 已取证：
        // 它买不到「可滚可达」，真实的大字号症状是标题被 `weight(1f)` 压到 78.48dp 后换行，
        // `horizontalScroll` 修不了它，当时登记为 L-T11-1「归 UI 重设计轮」。
        // 这个反例形态的教训现在迁进组件自己：`AppScreenHeader` 的 `Row` 显式 `fillMaxWidth()`，
        // KDoc 点名「无界宽宿主下标题列会塌宽（CheckInScreenTest 钉过这个反例形态）」。
        // L-T11-1 的账由「换用统一页头组件」这一动作结，屏侧不留「本轮未收口」的陈旧自述；
        // 护栏改述见 `CheckInScreenTest.headerTitleStaysReadableAtMaximumLargeFontScale`。
        // Task 22-B（R-22.1）：F5-03 / Task 10 遗留在 action 槽里的两枚裸文字链（我的收藏 / 错词本）已摘除，
        // 收藏与错词能力由合并页 MyWordsScreen 承载；组件的 action 无默认值（默认 null），故不再传入。
        AppScreenHeader(
            title = "学习打卡",
            onBack = onBack,
        )
        when (state) {
            CheckInUiState.Loading -> Text("正在读取打卡记录…", color = MintTextMuted)
            CheckInUiState.Unavailable -> {
                Text("打卡记录暂时无法读取，请稍后重试", color = MintTextMuted, modifier = Modifier.testTag("check_in_unavailable"))
                Button(onClick = onRetry, modifier = Modifier.semantics { contentDescription = "重试" }, colors = ButtonDefaults.buttonColors(containerColor = appDomain(AppDomain.Review).deep, contentColor = Color.White)) { Text("重试") }
            }
            is CheckInUiState.Ready -> ReadyContent(state)
        }
    }
}

@Composable
private fun ReadyContent(state: CheckInUiState.Ready) {
    val today = state.today
    val colors = appColors()
    // 复习域的深墨贯穿本屏的强调位（周柱、今日完成状态、月历已打卡日期）。
    val reviewInk = appDomain(AppDomain.Review).deep
    SoftCard(modifier = Modifier.testTag("check_in_today")) {
        Text("今日", style = AppType.Title, color = colors.text1)
        Text(
            "完成任务 ${today.completedTaskCount}/${today.targetTaskCount}",
            style = AppType.Body,
            color = colors.text1,
        )
        Text(
            "复习 ${today.reviewedWordCount} 词 · 阅读 ${today.completedReadingCount} 篇",
            style = AppType.Footnote,
            color = colors.text2,
        )
        Text(
            if (state.completed) "今日已完成" else "继续保持学习",
            style = AppType.Footnote,
            color = if (state.completed) reviewInk else colors.text2,
        )
    }
    // 批 1 Task 8（§5）：连续天数是本屏**唯一**的大数字（Display），累计统计降一档（Title）。
    // 旧版把完成度另画成一枚 132dp 的 Canvas 环，与「完成任务 x/y」重复表达同一件事——
    // 环整块删除，今日卡是这条信息的唯一出处（`CheckInDisplayAuthorityTest` 钉住）。
    SoftCard(modifier = Modifier.testTag("check_in_totals")) {
        Text("连续学习", style = AppType.Label, color = colors.text2)
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                state.streakDays.toString(),
                style = AppType.Display,
                color = reviewInk,
                modifier = Modifier.testTag("check_in_streak_days"),
            )
            Text(
                "天",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.padding(bottom = 8.dp).testTag("check_in_streak_text"),
            )
        }
        Text(
            state.totalsText,
            style = AppType.Title,
            color = colors.text1,
            modifier = Modifier.testTag("check_in_totals_text"),
        )
    }
    SoftCard(modifier = Modifier.testTag("check_in_week_chart")) {
        Text("本周学习", style = AppType.Title, color = colors.text1)
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            state.week.take(7).forEachIndexed { index, day ->
                val ratio = if (day.targetTaskCount > 0) (day.completedTaskCount.toFloat() / day.targetTaskCount).coerceIn(0f, 1f) else 0f
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth().height((72 * ratio).coerceAtLeast(4f).dp).clip(RoundedCornerShape(6.dp)).background(if (ratio > 0f) reviewInk else colors.outlineSoft).testTag("check_in_week_bar_$index").semantics { contentDescription = "${day.localDate} 完成度 ${(ratio * 100).toInt()}%" })
                    Text(
                        day.localDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.CHINA),
                        color = colors.text2,
                        style = AppType.Label,
                    )
                }
            }
        }
    }
    MonthCalendar(state.month)
}

@Composable
private fun MonthCalendar(days: List<DailyLearningStats>) {
    val month = days.firstOrNull()?.localDate?.let { YearMonth.from(it) } ?: return
    val byDate = days.associateBy { it.localDate }
    val colors = appColors()
    val reviewInk = appDomain(AppDomain.Review).deep
    SoftCard(modifier = Modifier.testTag("check_in_calendar")) {
        Text("${month.year}年${month.monthValue}月", style = AppType.Title, color = colors.text1)
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, modifier = Modifier.weight(1f), color = colors.text2, style = AppType.Label)
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val cells = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) { week.forEach { date ->
                Box(Modifier.weight(1f).heightIn(min = 34.dp), contentAlignment = Alignment.Center) {
                    if (date != null) {
                        val done = (byDate[date]?.completedTaskCount ?: 0) > 0
                        // 已打卡日期只靠墨色区分（不再叠字重）：颜色不是唯一线索——语义里也写了「已完成」。
                        Text(
                            date.dayOfMonth.toString(),
                            color = if (done) reviewInk else colors.text2,
                            style = AppType.Footnote,
                            modifier = Modifier.semantics { contentDescription = if (done) "$date 已完成" else date.toString() },
                        )
                    }
                }
            }; repeat(7 - week.size) { Box(Modifier.weight(1f).heightIn(min = 34.dp)) } }
        }
    }
}
