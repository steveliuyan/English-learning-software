package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint

@Composable
fun TodayPlanScreen(
    state: TodayPlanUiState,
    onRetry: () -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onStartLearning: () -> Unit = {},
    onOpenReading: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MintBackground).padding(horizontal = 20.dp, vertical = 24.dp).testTag("today_plan_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("今日学习计划", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark, modifier = Modifier.semantics { contentDescription = "今日学习计划" })
        when (state) {
            TodayPlanUiState.Loading -> {
                CircularProgressIndicator(color = MintPrimary, modifier = Modifier.size(48.dp).testTag("today_plan_loading"))
                Text("正在准备今日计划…", color = MintTextMuted)
            }
            TodayPlanUiState.MissingSetup -> {
                Text("先选择词书并设置每日目标", color = MintTextMuted, modifier = Modifier.testTag("today_plan_missing_setup"))
                Button(
                    onClick = onOpenSetup,
                    modifier = Modifier.testTag("today_plan_open_setup").semantics { contentDescription = "去设置词书" },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
                ) { Text("去设置词书") }
            }
            TodayPlanUiState.Unavailable -> {
                Text("今日计划暂时无法读取，请稍后重试", color = MintTextMuted, modifier = Modifier.testTag("today_plan_unavailable"))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.testTag("today_plan_retry").semantics { contentDescription = "重试" },
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
                ) { Text("重试") }
            }
            is TodayPlanUiState.Ready -> {
                Card(colors = CardDefaults.cardColors(containerColor = MintSurface), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().border(1.dp, MintOutline, RoundedCornerShape(24.dp)).testTag("today_plan_summary")) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.wordBookName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                        Text("应用内学习分组，不是官方考试大纲", style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
                        Text("计划日期：${state.localDateLabel}", style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
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
                    CountCard("今日新增 ${state.newTarget} 词", "今日新增 ${state.newTarget} 词", "today_plan_new_count")
                    CountCard("今日复习 ${state.dueTarget} 词", "今日复习 ${state.dueTarget} 词", "today_plan_due_count")
                }
                if (state.totalTasks == 0) Text("今天暂无学习任务", color = MintTextMuted, modifier = Modifier.testTag("today_plan_empty"))
                else Text("今日计划共 ${state.totalTasks} 项", color = MintPrimaryDark, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("today_plan_task_total"))
                Text("新增 ${state.newDone}/${state.newTarget} · 复习 ${state.dueDone}/${state.dueTarget}", color = MintPrimaryDark, modifier = Modifier.testTag("today_plan_progress"))
                val totalDone = state.newDone + state.dueDone
                val totalTarget = state.newTarget + state.dueTarget
                Text("总进度 $totalDone/$totalTarget", color = MintPrimaryDark, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("today_plan_total_progress"))
                val unlockText = if (state.isUnlocked) "已解锁：文章已解锁" else "未解锁：${state.unlockReason}"
                Text(unlockText, color = if (state.isUnlocked) MintPrimary else MintTextMuted, modifier = Modifier.testTag("today_plan_unlock_status"))
                Button(
                    onClick = onOpenReading,
                    enabled = state.isUnlocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("today_plan_open_reading")
                        .semantics { contentDescription = "阅读文章" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MintPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = MintTint,
                        disabledContentColor = MintPrimaryDark,
                    ),
                ) { Text("阅读文章", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onStartLearning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("today_plan_start_learning")
                        .semantics { contentDescription = "开始学习" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MintPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = MintTint,
                        disabledContentColor = MintPrimaryDark,
                    ),
                ) { Text("开始学习", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun RowScope.CountCard(text: String, description: String, tag: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MintTint), shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f).testTag(tag).semantics { contentDescription = description }) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MintPrimaryDark, modifier = Modifier.padding(18.dp))
    }
}
