package com.example.englishlearning.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.domain.DailyLearningStats
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import java.time.DayOfWeek
import java.time.YearMonth

@Composable
fun CheckInScreen(
    state: CheckInUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    // F4-01 决策⑦：打卡页是收藏页的唯一入口；ViewModel 未接线时入口整体不渲染。
    onOpenFavorites: (() -> Unit)? = null,
    // F4-01 决策⑤：错词本入口与收藏并列，同样按接线与否显隐。
    onOpenWrongWords: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MintBackground).verticalScroll(rememberScrollState()).padding(20.dp).testTag("check_in_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // F5-03 / Task 11（§E.2，Task 10 评审 I-4；Task 11 评审 I-1 订正本注释口径）：
        // 本 Row 保留 `fillMaxWidth()` 在前的 `horizontalScroll`，但**取证表明该顺序不产生滚动范围**——
        // 加/不加滚动时头部边界逐位相同（标题 DpRect(20.190475..98.666664)，emulator-5574 fontScale=2.0），
        // 摘掉滚动后三条链仍可达（P4 实测仍绿），即「可滚可达」的效果本轮**没有取得**，不得据此认为溢出已收口。
        // 留着它只为钉住**修饰符顺序**：反写成 horizontalScroll().fillMaxWidth() 会让标题的 weight(1f)
        // 在无界约束下塌成 0 宽（实测 DpRect(left=right=20.190475)，P3 必红），属回归。
        // 真实的大字号症状是标题被 weight(1f) 压到 78.48dp 换行，`horizontalScroll` 修不了它 → 归 UI 重设计轮（L-T11-1）。
        // 护栏见 `CheckInScreenTest` 的 fontScale=2.0 用例（该护栏不钉本修饰符的存在性）。
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            // F5-03 / Task 10：三条文字链原来只有 padding(10.dp)（实测 36.19dp 高，不足 48dp 触控下限），
            // 一律在 clickable 之前补 defaultMinSize(48.dp)；padding 与 testTag/semantics 链保持原样。
            Text("学习打卡", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark, modifier = Modifier.weight(1f))
            onOpenFavorites?.let { open ->
                Text("我的收藏", color = MintPrimaryDark, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("check_in_open_favorites").semantics { contentDescription = "打开我的收藏" }.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clickable(onClick = open).padding(10.dp))
            }
            onOpenWrongWords?.let { open ->
                Text("错词本", color = MintPrimaryDark, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("check_in_open_wrong_words").semantics { contentDescription = "打开错词本" }.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clickable(onClick = open).padding(10.dp))
            }
            Text("返回", color = MintPrimaryDark, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("check_in_back").semantics { contentDescription = "返回" }.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clickable(onClick = onBack).padding(10.dp))
        }
        when (state) {
            CheckInUiState.Loading -> Text("正在读取打卡记录…", color = MintTextMuted)
            CheckInUiState.Unavailable -> {
                Text("打卡记录暂时无法读取，请稍后重试", color = MintTextMuted, modifier = Modifier.testTag("check_in_unavailable"))
                Button(onClick = onRetry, modifier = Modifier.semantics { contentDescription = "重试" }, colors = ButtonDefaults.buttonColors(containerColor = DomainColors.Review.deep, contentColor = Color.White)) { Text("重试") }
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
            Text(if (state.completed) "今日已完成" else "继续保持学习", color = if (state.completed) DomainColors.Review.deep else MintTextMuted, fontWeight = FontWeight.Bold)
        }
    }
    // F4-01 基础统计卡：累计学习/复习、连续天数（按快照解锁口径，不要求阅读）、近 7 日完成率条。
    Card(colors = CardDefaults.cardColors(containerColor = MintSurface), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("check_in_totals")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("累计与连续", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            Text(state.totalsText, color = MintPrimaryDark, modifier = Modifier.testTag("check_in_totals_text"))
            Text(state.streakText, color = MintTextMuted, modifier = Modifier.testTag("check_in_streak_text"))
            if (state.recentDays.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 88.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    state.recentDays.forEachIndexed { index, day ->
                        val ratio = day.completionRatio
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.fillMaxWidth().height((56 * ratio).coerceAtLeast(4f).dp).clip(RoundedCornerShape(6.dp)).background(if (ratio > 0f) DomainColors.Review.deep else AppPalette.Separator).testTag("check_in_recent_bar_$index").semantics { contentDescription = "${day.localDate} 完成率 ${(ratio * 100).toInt()}%" })
                            Text(day.localDate.dayOfMonth.toString(), color = MintTextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MintSurface), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("check_in_week_chart")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("本周学习", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            Row(Modifier.fillMaxWidth().heightIn(min = 120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                state.week.take(7).forEachIndexed { index, day ->
                    val ratio = if (day.targetTaskCount > 0) (day.completedTaskCount.toFloat() / day.targetTaskCount).coerceIn(0f, 1f) else 0f
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth().height((72 * ratio).coerceAtLeast(4f).dp).clip(RoundedCornerShape(6.dp)).background(if (ratio > 0f) DomainColors.Review.deep else AppPalette.Separator).testTag("check_in_week_bar_$index").semantics { contentDescription = "${day.localDate} 完成度 ${(ratio * 100).toInt()}%" })
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
            Box(Modifier.size(132.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().testTag("check_in_completion_arc")) {
                    val strokeWidth = 12.dp.toPx()
                    drawArc(color = Color(0xFFFFF1DB), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(strokeWidth, cap = StrokeCap.Round))
                    if (ratio > 0f) {
                        drawArc(color = DomainColors.Review.deep, startAngle = -90f, sweepAngle = ratio * 360f, useCenter = false, style = Stroke(strokeWidth, cap = StrokeCap.Round))
                    }
                }
                Text("${(ratio * 100).toInt()}%", modifier = Modifier.testTag("check_in_completion_percent"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = DomainColors.Review.deep)
            }
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
                    Box(Modifier.weight(1f).heightIn(min = 34.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val done = (byDate[date]?.completedTaskCount ?: 0) > 0
                            Text(date.dayOfMonth.toString(), color = if (done) DomainColors.Review.deep else MintTextMuted, fontWeight = if (done) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.semantics { contentDescription = if (done) "$date 已完成" else date.toString() })
                        }
                    }
                }; repeat(7 - week.size) { Box(Modifier.weight(1f).heightIn(min = 34.dp)) } }
            }
        }
    }
}
