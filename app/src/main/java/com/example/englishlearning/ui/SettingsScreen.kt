package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppThemePreferences
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.ThemeMode
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.rememberAppThemeMode

/** 三态在界面上的用户语言标签（存储键仍走 `ThemeMode.storageKey`，这里只是文案）。 */
private val ThemeMode.userLabel: String
    get() = when (this) {
        ThemeMode.System -> "跟随系统"
        ThemeMode.Light -> "浅色"
        ThemeMode.Dark -> "深色"
    }

/**
 * 「设置」子页（决策 §3，2026-11-02）：**只放全局开关**，内容是「外观」一段 + 一句范围说明。
 *
 * 这一屏的形状换过三次，理由各不相同，记录在此避免第四次：
 *  - 最初它是「我的」页尾部的一组行；
 *  - 2026-10-31 升成独立的第五个一级 tab（`docs/decisions/2026-10-31-settings-as-fifth-tab.md`）；
 *  - 2026-11-02 用户否掉「我的」与「设置」并列（同一件事的两份入口），按参考把设置收回「我的」里，
 *    原来的 4 组 9 行分发到 hub 的 13 行（新台账 `ProfileGroups`，判据 `ProfileHubLedgerTest`）。
 *
 * 因此本页**不再**持有：计划 / 提醒 / 发音 / AI / 备份 / 关于 / 词书包的任何入口与回调，也不再读
 * 引擎状态与词书名——那些行现在各住在 hub，各自的屏仍是唯一落点。页末那句说明按参考逐字写，
 * 且这一次它是真话。
 *
 * 「主题」为三态分段选择：选中即 `AppThemePreferences.write`，回读经 [rememberAppThemeMode]
 * 的监听让整棵树即时换套，无需重启。写偏好与回读的接线留在本页，[ThemeModeRow] 只是纯选择器。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val colors = appColors()
    val themeMode = rememberAppThemeMode()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            // 底部导航会吃掉一截高度，小屏或大字号下整页必须可滚动，否则最后一组被裁掉。
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = "设置",
            subtitle = "只影响这台设备的显示",
            onBack = onBack,
        )
        SectionHeading(text = "外观", modifier = Modifier.testTag("settings_group_appearance"))
        ThemeModeRow(mode = themeMode, onSelect = { AppThemePreferences.write(context, it) })
        InlineNotice(
            text = "这一页只放全局开关。提醒与发音、计划、词书、备份与许可都在「我的」里，不在这里重复出现。",
            kind = InlineNoticeKind.Info,
        )
    }
}

/**
 * 主题三态选择卡（稿 §7④「主题」分段控件）：纯展示组件——[mode] 进、[onSelect] 出，
 * 写偏好与回读由调用方接线（`AppThemePreferences.write` + `rememberAppThemeMode()`）。
 *
 * 三档标签固定为「跟随系统 / 浅色 / 深色」；每档可点面 `heightIn(min = 48.dp)`（F5-03 触控下限）。
 *
 * TalkBack 口径（Task 26 Step 4 / AC-R-12 走查发现）：原实现是三个各自 `.clickable` 的 Box，
 * 读屏只念三段文字、不报「当前选中哪一档」。本件按单选组语义改写：容器 `.selectableGroup()`、
 * 每档 `.selectable(selected, role = Role.RadioButton)`——读屏念 Role 并附加「已选中」，
 * 判据见 `SettingsScreenTest#the_theme_segments_expose_their_selected_state_to_talkback`。
 *
 * 2026-11-02：卡标题与卡片 testTag 原来取自设置台账里「主题」那一行；台账退役后本件自带这两枚字面，
 * 仍然是全站唯一一份（`SettingsPrototypeAlignmentContractTest` 钉「外观段只有一枚 ThemeModeRow 调用点」）。
 */
@Composable
fun ThemeModeRow(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = appColors()
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineSoft, RoundedCornerShape(18.dp))
            .testTag("settings_theme_mode"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "主题",
                style = AppType.Body,
                fontWeight = FontWeight.Bold,
                color = colors.text1,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .background(colors.outlineSoft, AppShape.Button)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ThemeMode.entries.forEach { option ->
                    val selected = option == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) colors.surface else Color.Transparent)
                            .selectable(selected = selected, role = Role.RadioButton) { onSelect(option) }
                            .heightIn(min = 48.dp)
                            .testTag("settings_theme_${option.storageKey}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.userLabel,
                            style = AppType.Footnote,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) colors.text1 else colors.text2,
                        )
                    }
                }
            }
            Text(
                text = "切换后立即生效，仅影响本机显示，不属于学习数据。",
                style = AppType.Footnote,
                color = colors.text2,
            )
        }
    }
}
