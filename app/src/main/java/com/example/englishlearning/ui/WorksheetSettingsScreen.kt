package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.worksheet.WorksheetDirection
import com.example.englishlearning.learning.worksheet.WorksheetRange
import com.example.englishlearning.learning.worksheet.WorksheetSettings
import com.example.englishlearning.learning.worksheet.WorksheetTemplate
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.LocalIsDark
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * 默写纸设置（spec §5 逐屏表「默写纸设置」行）。
 *
 * 结构与顺序：共享页头 → 一句说明 → 本次词条摘要 → **默写方向**（仅拼写测试模板）→ **词条范围**
 * （单选）→ 词表模板（单选）→ 纸张样式（两个开关，对每种模板都成立）→ 提示行 → 预览主操作。
 *
 * 三处版式修正：
 * ① 方向控件上移到范围之前——方向决定「这张纸怎么默」，范围决定「拿哪些词」；
 * ② 范围从复选框改单选（一次只能导一种范围，复选框的形状在说谎）；
 * ③ 纸张样式标题不再藏在方向分支里（旧版非拼写测试模板下标题消失、下面两个开关却还在）。
 *
 * 圆角/底色/排印全部走 `AppShape` / `appColors()` / `AppType`；本屏是设置域的中性页，
 * 唯一实底留给底部「预览默写纸」。
 */
@Composable
fun WorksheetSettingsScreen(
    settings: WorksheetSettings,
    selectedCount: Int,
    onToggleDirection: (WorksheetDirection) -> Unit,
    onSelectRange: (WorksheetRange) -> Unit = {},
    onSelectTemplate: (WorksheetTemplate) -> Unit = {},
    onToggleGrid: (Boolean) -> Unit,
    onToggleAnswers: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = appColors()
    val canPreview = settings.isReadyToPreview() && selectedCount > 0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("worksheet_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppScreenHeader(title = "生成默写纸", onBack = onBack, modifier = Modifier.padding(bottom = 2.dp))
        Text(
            text = "先选择默写方向，再预览纸张内容。词条只来自当前学习计划。",
            style = AppType.Footnote,
            color = colors.text2,
        )
        CountCard(selectedCount)
        // 方向在范围之前：先定「怎么默」，再定「拿哪些词」（spec §5）。
        if (settings.requiresDirection()) {
            DirectionSection(settings = settings, onToggleDirection = onToggleDirection)
        }
        RangeSection(settings = settings, onSelectRange = onSelectRange)
        TemplateSection(settings = settings, onSelectTemplate = onSelectTemplate)
        PaperStyleSection(settings = settings, onToggleGrid = onToggleGrid, onToggleAnswers = onToggleAnswers)
        if (settings.requiresDirection() && settings.directions.isEmpty()) {
            InlineNotice("至少选择一种默写方向后才能预览。", InlineNoticeKind.Error)
        } else if (selectedCount == 0) {
            Text(
                text = "当前没有可导出的已完成词条。",
                style = AppType.Footnote,
                color = colors.text2,
            )
        }
        Button(
            onClick = onPreview,
            enabled = canPreview,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .testTag("worksheet_preview_action"),
            shape = AppShape.Button,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.strongSurface,
                contentColor = colors.onStrong,
                disabledContainerColor = colors.tint,
                disabledContentColor = colors.text3,
            ),
        ) {
            // 前景色跟着按钮下发的 ink 走（`contentColor` / `disabledContentColor`）：`AppType.Label`
            // 自带 `text2`，自钉 `onStrong` 又会让禁用态变成近白字压浅底——两态都得由按钮说。
            Text("预览默写纸", style = AppType.Label, color = LocalContentColor.current)
        }
    }
}

/** 段落标题：`AppType.Title` + 读屏 heading，比正文字重高一级但不抢页头。 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = AppType.Title,
        color = appColors().text1,
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics { heading() },
    )
}

/** 本次词条摘要：一眼知道「这张纸能导出多少词」，数字用主词档承接。 */
@Composable
private fun CountCard(selectedCount: Int) {
    val colors = appColors()
    SoftCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("本次词条", style = AppType.Label, color = colors.text1)
                Text(
                    text = "当前计划中可导出的词条",
                    style = AppType.Footnote,
                    color = colors.text2,
                )
            }
            Text(
                text = "$selectedCount 词",
                style = AppType.Headline,
                color = colors.text1,
            )
        }
    }
}

/** 默写方向（多选）：中译英 / 英译中可同时要，所以是复选框而非单选。 */
@Composable
private fun DirectionSection(
    settings: WorksheetSettings,
    onToggleDirection: (WorksheetDirection) -> Unit,
) {
    SectionTitle("默写方向")
    DirectionRow(
        tag = "worksheet_direction_zh_to_en",
        title = "中译英",
        description = "看到中文释义，默写英文单词",
        checked = WorksheetDirection.ZH_TO_EN in settings.directions,
        onClick = { onToggleDirection(WorksheetDirection.ZH_TO_EN) },
    )
    DirectionRow(
        tag = "worksheet_direction_en_to_zh",
        title = "英译中",
        description = "看到英文单词，默写中文释义",
        checked = WorksheetDirection.EN_TO_ZH in settings.directions,
        onClick = { onToggleDirection(WorksheetDirection.EN_TO_ZH) },
    )
}

@Composable
private fun DirectionRow(
    tag: String,
    title: String,
    description: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val colors = appColors()
    // 复选选中色与开关轨道同一条口径（批 5 遗留 1 收口）：深套 `strongSurface`(#22332C) 压在卡片
    // `surface`(#17201C) 上几乎同色，勾没勾上看不出来；深套改读 Settings 域色，浅套取值不动。
    val settingsInk = appDomain(AppDomain.Settings)
    SelectableRow(
        tag = tag,
        selected = checked,
        onSelect = onClick,
        description = title,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onClick() },
            colors = CheckboxDefaults.colors(
                checkedColor = if (LocalIsDark.current) settingsInk.base else colors.strongSurface,
                uncheckedColor = colors.outline,
            ),
        )
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = AppType.Label, color = colors.text1)
            Text(description, style = AppType.Footnote, color = colors.text2)
        }
    }
}

/** 词条范围（单选）：一次只能导一种范围。 */
@Composable
private fun RangeSection(
    settings: WorksheetSettings,
    onSelectRange: (WorksheetRange) -> Unit,
) {
    SectionTitle("词条范围")
    // 单选选中色同前：深套下 `strongSurface` 与卡片同色，选没选中看不出（见 `OptionSwitchRow` 注释）。
    val settingsInk = appDomain(AppDomain.Settings)
    WorksheetRange.values().forEach { range ->
        val selected = range == settings.range
        SelectableRow(
            tag = rangeTag(range),
            selected = selected,
            onSelect = { onSelectRange(range) },
        ) {
            RadioButton(
                selected = selected,
                onClick = { onSelectRange(range) },
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (LocalIsDark.current) settingsInk.base else appColors().strongSurface,
                    unselectedColor = appColors().outline,
                ),
            )
            Text(
                text = rangeLabel(range),
                style = AppType.Body,
                color = appColors().text1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** 词表模板（单选）：三种纸张形态里选一种。 */
@Composable
private fun TemplateSection(
    settings: WorksheetSettings,
    onSelectTemplate: (WorksheetTemplate) -> Unit,
) {
    SectionTitle("词表模板")
    // 同上（词条范围那节的说明），两枚单选用同一个口径。
    val settingsInk = appDomain(AppDomain.Settings)
    WorksheetTemplate.values().forEach { template ->
        val selected = template == settings.template
        SelectableRow(
            tag = templateTag(template),
            selected = selected,
            onSelect = { onSelectTemplate(template) },
        ) {
            RadioButton(
                selected = selected,
                onClick = { onSelectTemplate(template) },
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (LocalIsDark.current) settingsInk.base else appColors().strongSurface,
                    unselectedColor = appColors().outline,
                ),
            )
            Column(Modifier.padding(start = 8.dp)) {
                Text(templateLabel(template), style = AppType.Label, color = appColors().text1)
                Text(
                    text = templateDescription(template),
                    style = AppType.Footnote,
                    color = appColors().text2,
                )
            }
        }
    }
}

/** 纸张样式：对**每一种**模板都成立，所以这一段的标题与两个开关一起放在方向分支之外。 */
@Composable
private fun PaperStyleSection(
    settings: WorksheetSettings,
    onToggleGrid: (Boolean) -> Unit,
    onToggleAnswers: (Boolean) -> Unit,
) {
    SectionTitle("纸张样式")
    OptionSwitchRow(
        title = "使用四线三格",
        description = "更适合练习英文书写",
        checked = settings.useFourLineGrid,
        onCheckedChange = onToggleGrid,
    )
    OptionSwitchRow(
        title = "附带答案页",
        description = "在默写纸末尾附上核对答案",
        checked = settings.includeAnswerPage,
        onCheckedChange = onToggleAnswers,
    )
}

/**
 * 可选行的统一几何（弱卡档：白面 / 选中浅底 / 无描边 / 圆角 20）。
 *
 * 模板、范围、方向三种行只差一枚控件和几行文字，几何与点击行为由这里一处交付；
 * 行内控件自己也要响应点击（读屏可聚焦到控件），所以 `onSelect` 同时给行和控件用。
 */
@Composable
private fun SelectableRow(
    tag: String,
    selected: Boolean,
    onSelect: () -> Unit,
    description: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape.Card)
            .background(if (selected) colors.tint else colors.surface)
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(tag)
            .semantics {
                if (description != null) {
                    contentDescription = description
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun rangeLabel(range: WorksheetRange): String = when (range) {
    WorksheetRange.COMPLETED_TODAY -> "今天已完成的词"
    WorksheetRange.ALL_PLAN_ITEMS -> "当前计划全部词"
    WorksheetRange.COMPLETED_NEW -> "今天已完成的新词"
    WorksheetRange.COMPLETED_REVIEW -> "今天已完成的复习词"
    WorksheetRange.DIFFICULT_TODAY -> "今天的易错词"
}

@Composable
private fun rangeTag(range: WorksheetRange): String = when (range) {
    WorksheetRange.COMPLETED_TODAY -> "worksheet_range_completed_today"
    WorksheetRange.ALL_PLAN_ITEMS -> "worksheet_range_all_plan_items"
    WorksheetRange.COMPLETED_NEW -> "worksheet_range_completed_new"
    WorksheetRange.COMPLETED_REVIEW -> "worksheet_range_completed_review"
    WorksheetRange.DIFFICULT_TODAY -> "worksheet_range_difficult_today"
}

@Composable
private fun templateLabel(template: WorksheetTemplate): String = when (template) {
    WorksheetTemplate.FULL_LIST -> "我的词表"
    WorksheetTemplate.SPELLING_TEST -> "我的词表 · 拼写测试"
    WorksheetTemplate.EBBINGHAUS_REVIEW -> "我的词表 · 艾宾浩斯抗遗忘"
}

@Composable
private fun templateDescription(template: WorksheetTemplate): String = when (template) {
    WorksheetTemplate.FULL_LIST -> "双向并排完整词表，每页 40 词，可直接背诵"
    WorksheetTemplate.SPELLING_TEST -> "左右镜像默写，每页 20 词，一边给提示一边留空格"
    WorksheetTemplate.EBBINGHAUS_REVIEW -> "每页 20 词，右侧带 D1…D90 复习打卡格"
}

@Composable
private fun templateTag(template: WorksheetTemplate): String = when (template) {
    WorksheetTemplate.FULL_LIST -> "worksheet_template_full_list"
    WorksheetTemplate.SPELLING_TEST -> "worksheet_template_spelling_test"
    WorksheetTemplate.EBBINGHAUS_REVIEW -> "worksheet_template_ebbinghaus"
}

/** 开关行：行标题就是开关的朗读标签（裸 `Switch` 必须带 `contentDescription = title`）。 */
@Composable
private fun OptionSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = appColors()
    // 本屏是设置域的中性页（见文件头），开关轨道深套下改读 Settings 域色。
    val settings = appDomain(AppDomain.Settings)
    SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = AppType.Label, color = colors.text1)
                Text(description, style = AppType.Footnote, color = colors.text2)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = title },
                // 深套 `strongSurface`(#22332C) 与卡片 `surface`(#17201C) 几乎同色，「开」态等于没画；
                // 深套改域色，浅套保持签署过的取值（批 5 遗留 1 收口，口径同 `SpeechSettingsScreen.kt:258`）。
                colors = SwitchDefaults.colors(
                    checkedTrackColor = if (LocalIsDark.current) settings.base else colors.strongSurface,
                ),
            )
        }
    }
}
