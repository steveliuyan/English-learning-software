package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.englishlearning.learning.domain.DailyLearningStats
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint
import java.time.DayOfWeek
import java.time.YearMonth

@Composable
fun CheckInScreen(
    state: CheckInUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MintBackground).verticalScroll(rememberScrollState()).padding(20.dp).testTag("check_in_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("学习打卡", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark, modifier = Modifier.weight(1f))
            Text("返回", color = MintPrimaryDark, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("check_in_back").semantics { contentDescription = "返回" }.clickable(onClick = onBack).padding(10.dp))
        }
        when (state) {
            CheckInUiState.Loading -> Text("正在读取打卡记录…", color = MintTextMuted)
            CheckInUiState.Unavailable -> {
                Text("打卡记录暂时无法读取，请稍后重试", color = MintTextMuted, modifier = Modifier.testTag("check_in_unavailable"))
                Button(onClick = onRetry, modifier = Modifier.semantics { contentDescription = "重试" }, colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White)) { Text("重试") }
            }
            is CheckInUiState.Ready -> ReadyContent(state)
        }
    }
}

@Composable
private fun ReadyContent(state: CheckInUiState.Ready) {
    val today = state.today
    Card(colors = CardDefaults.cardColors(containerColor = MintSurface), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().border(1.dp, MintOutline, RoundedCornerShape(22.dp)).testTag("check_in_today")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("今日", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            Text("完成任务 ${today.completedTaskCount}/${today.targetTaskCount}", color = MintPrimaryDark)
            Text("复习 ${today.reviewedWordCount} 词 · 阅读 ${today.completedReadingCount} 篇", color = MintTextMuted)
            Text(if (state.completed) "今日已完成" else "继续保持学习", color = MintPrimary, fontWeight = FontWeight.Bold)
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MintSurface), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("check_in_week_chart")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("本周学习", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                state.week.take(7).forEach { day ->
                    val ratio = if (day.targetTaskCount > 0) (day.completedTaskCount.toFloat() / day.targetTaskCount).coerceIn(0f, 1f) else 0f
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth().height((72 * ratio).coerceAtLeast(4f).dp).clip(RoundedCornerShape(6.dp)).background(if (ratio > 0f) MintPrimary else MintTint).semantics { contentDescription = "${day.localDate} 完成度 ${(ratio * 100).toInt()}%" })
                        Text(day.localDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.CHINA), color = MintTextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    val ratio = today.completionRatio
    Card(colors = CardDefaults.cardColors(containerColor = MintSurface), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("check_in_completion_ratio")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("今日完成度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MintPrimary)
            Text("目标 ${today.targetTaskCount} 项，已完成 ${today.completedTaskCount} 项", color = MintTextMuted)
        }
    }
    MonthCalendar(state.month)
}

@Composable
private fun MonthCalendar(days: List<DailyLearningStats>) {
    val month = days.firstOrNull()?.localDate?.let { YearMonth.from(it) } ?: return
    val byDate = days.associateBy { it.localDate }
    Card(colors = CardDefaults.cardColors(containerColor = MintSurface), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("check_in_calendar")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, modifier = Modifier.weight(1f), color = MintTextMuted, style = MaterialTheme.typography.labelSmall) } }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val cells = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) { week.forEach { date ->
                    Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val done = (byDate[date]?.completedTaskCount ?: 0) > 0
                            Text(date.dayOfMonth.toString(), color = if (done) MintPrimaryDark else MintTextMuted, fontWeight = if (done) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.semantics { contentDescription = if (done) "$date 已完成" else date.toString() })
                        }
                    }
                }; repeat(7 - week.size) { Box(Modifier.weight(1f).height(34.dp)) } }
            }
        }
    }
}
