package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeAction
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain
import java.time.LocalTime

/**
 * F4-02 提醒设置页：开关 + 时间选择。默认关闭；开启时由宿主请求
 * POST_NOTIFICATIONS（拒绝后不重复弹窗，这里提供系统设置入口说明）。
 * 非精确调度说明固定展示（Spec 要求）。
 *
 * 批 5 Task 2 按 spec §5「学习提醒」行重排，四处改动：
 *  1. 页头换共享 `AppScreenHeader`（两分支都换），两处自写 `TextButton("返回")` 与
 *     `Text("学习提醒", fontSize = 22.sp)` 一并下线——排印归六档 `AppType`，色归 `appColors()`；
 *  2. 时间选择从自写 `TimeGrid`（八枚整点 `TextButton`）换成 Material `TimePicker`：整点网格号称
 *     「15 分钟步进」却只能选整点，且滑杆/对话框才是用户对「时间」的心智模型；确认键 `reminder_time_confirm`
 *     才回写 `viewModel.setTime`，取消不落盘；
 *  3. 读设置失败的错误行补「重试」（`reminder_retry` → `viewModel.retry()`）。**不能**直接再调 `load()`：
 *     那条路径的落位守卫是 `loading == true`，读失败后它已是 `false`，直接调就是一枚假按钮；
 *  4. 两个分支的根列都补 `verticalScroll`：大字号（验收口径 fontScale 2.0）与小屏下
 *     「提醒时间 + 隐私说明」原本直接掉出屏幕。
 *
 * 可空 ViewModel 是生产组装点防回归约定（见 AppScreenProductionWiringTest）；
 * null 时页面退化为只读提示，不崩溃。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    onEnableRequested: () -> Unit = {},
    viewModel: ReminderSettingsViewModel? = null,
) {
    if (viewModel == null) {
        val colors = appColors()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("reminder_settings_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppScreenHeader(
                title = "学习提醒",
                subtitle = "每天最多一次，系统可能稍有延迟",
                onBack = onBack,
            )
            SoftCard {
                Text(text = "提醒功能暂不可用", style = AppType.Body, color = colors.text1)
                Text(
                    text = "设置服务还没准备好。返回上一页再进来，通常就恢复了。",
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        return
    }

    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    val colors = appColors()
    val settingsAccent = appDomain(AppDomain.Settings)
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("reminder_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppScreenHeader(
            title = "学习提醒",
            subtitle = "每天最多一次，系统可能稍有延迟",
            onBack = onBack,
        )

        if (state.loading) {
            SoftCard {
                Text(
                    text = "正在读取提醒设置…",
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.testTag("reminder_settings_loading"),
                )
            }
            return@Column
        }

        // 错误行收编共享 `InlineNotice`（批 1 Task 26 Step 3 起的唯一真值）：
        // 批 5 Task 2 在此补上「重试」动作槽，重试走 `viewModel.retry()`（见 VM 注释里的守卫说明）。
        state.error?.let { message ->
            InlineNotice(
                text = message,
                kind = InlineNoticeKind.Error,
                action = {
                    InlineNoticeAction(
                        kind = InlineNoticeKind.Error,
                        label = "重试",
                        onClick = { viewModel.retry() },
                        modifier = Modifier.testTag("reminder_retry"),
                    )
                },
            )
        }

        SoftCard {
            Text(text = "每日提醒", style = AppType.Body, color = colors.text1)
            Text(
                text = "每天最多一次，系统可能稍有延迟（非精确提醒）",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.padding(top = 4.dp).testTag("reminder_inexact_notice"),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 原型 `app.js:1332` 的 row：标题 + 副案「只在当天没完成计划时提醒」（本仓
                // `ReminderSchedulingPolicy` 当日计划完成即取消，同一行为，不是空话）。
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = "开启提醒", style = AppType.Body, color = colors.text1)
                    Text(text = "只在当天没完成计划时提醒", style = AppType.Caption, color = colors.text3)
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { checked ->
                        if (checked) onEnableRequested() else viewModel.setEnabled(false)
                    },
                    modifier = Modifier
                        .testTag("reminder_enabled_switch")
                        .semantics { contentDescription = "开启学习提醒" },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.surface,
                        checkedTrackColor = settingsAccent.base,
                    ),
                )
            }
            // 原型 `app.js:1334` 的「重复 / 周一到周日 / 每天」：本仓没有按星期配置提醒的能力
            // （`ReminderSchedulingPolicy` 固定每天最多一次），所以这一行是**只读说明**，不给 `onClick`。
            BareRow(
                title = "重复",
                subtitle = "周一到周日",
                trailing = "每天",
                showChevron = false,
                topDivider = true,
                modifier = Modifier.testTag("reminder_repeat_row"),
            )
        }

        if (!state.enabled) {
            Text(
                text = "开启后每天在这个时间提醒一次；当天任务已完成则不发。",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (state.enabled) {
            SoftCard {
                Text(text = "提醒时间", style = AppType.Label, color = colors.text2)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "%02d:%02d".format(state.time.hour, state.time.minute),
                        style = AppType.Title,
                        color = colors.text1,
                        modifier = Modifier.weight(1f),
                    )
                    PillButton(
                        text = "修改",
                        onClick = { showTimePicker = true },
                        style = PillStyle.Secondary,
                        domain = AppDomain.Settings,
                        testTag = "reminder_time_button",
                        contentDescription = "修改提醒时间",
                    )
                }
            }
            Text(
                text = "提醒仅在当日任务未完成时发送，内容不含学习数据。",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("reminder_privacy_notice").padding(horizontal = 4.dp),
            )
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = state.time.hour,
            initialMinute = state.time.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(text = "提醒时间", style = AppType.Body, color = colors.text1) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                PillButton(
                    text = "确定",
                    onClick = {
                        viewModel.setTime(LocalTime.of(pickerState.hour, pickerState.minute))
                        showTimePicker = false
                    },
                    domain = AppDomain.Settings,
                    testTag = "reminder_time_confirm",
                )
            },
            dismissButton = {
                PillButton(
                    text = "取消",
                    onClick = { showTimePicker = false },
                    style = PillStyle.Text,
                    domain = AppDomain.Settings,
                )
            },
        )
    }
}
