package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalTime
import androidx.compose.runtime.LaunchedEffect

/**
 * F4-02 提醒设置页：开关 + 时间选择。默认关闭；开启时由宿主请求
 * POST_NOTIFICATIONS（拒绝后不重复弹窗，这里提供系统设置入口说明）。
 * 非精确调度说明固定展示（Spec 要求）。
 */
@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    onEnableRequested: () -> Unit = {},
    viewModel: ReminderSettingsViewModel? = null,
) {
    // 可空 ViewModel 是生产组装点防回归约定（见 AppScreenProductionWiringTest）；
    // null 时页面退化为只读提示，不崩溃。
    if (viewModel == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp).testTag("reminder_settings_screen")) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("reminder_settings_back")) { Text("返回") }
            Text("学习提醒", fontSize = 22.sp)
            Text("提醒功能暂不可用")
        }
        return
    }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("reminder_settings_screen"),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("reminder_settings_back")) { Text("返回") }
        Text("学习提醒", fontSize = 22.sp)
        if (state.loading) {
            Text("加载中…", modifier = Modifier.testTag("reminder_settings_loading"))
            return@Column
        }
        state.error?.let { Text(it, color = androidx.compose.ui.graphics.Color.Red, modifier = Modifier.testTag("reminder_settings_error")) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("每日提醒")
                Text(
                    "每天最多一次，系统可能稍有延迟（非精确提醒）",
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("reminder_inexact_notice"),
                )
            }
            Switch(
                checked = state.enabled,
                onCheckedChange = { checked ->
                    if (checked) onEnableRequested() else viewModel.setEnabled(false)
                },
                modifier = Modifier.testTag("reminder_enabled_switch"),
            )
        }

        if (state.enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("提醒时间", modifier = Modifier.weight(1f))
                TimePickerButton(time = state.time, onTimeChanged = viewModel::setTime)
            }
            Text(
                "提醒仅在当日任务未完成时发送，内容不含学习数据",
                fontSize = 12.sp,
                modifier = Modifier.testTag("reminder_privacy_notice"),
            )
        }
    }
}

/** 简单的整点/半点选择器（V1：15 分钟步进的时间选择，避免引入 Material time-picker 依赖差异）。 */
@Composable
private fun TimePickerButton(time: LocalTime, onTimeChanged: (LocalTime) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("reminder_time_button")) {
            Text("%02d:%02d".format(time.hour, time.minute))
        }
        if (expanded) {
            TimeGrid(time, onTimeChanged) { expanded = false }
        }
    }
}

@Composable
private fun TimeGrid(time: LocalTime, onTimeChanged: (LocalTime) -> Unit, onSelected: () -> Unit) {
    val slots = listOf(7, 8, 12, 18, 19, 20, 21, 22)
    Column {
        slots.chunked(4).forEach { rowSlots ->
            Row {
                rowSlots.forEach { hour ->
                    val candidate = LocalTime.of(hour, 0)
                    TextButton(onClick = {
                        onTimeChanged(candidate)
                        onSelected()
                    }, modifier = Modifier.testTag("reminder_time_$hour")) {
                        Text("%02d:00".format(hour), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
