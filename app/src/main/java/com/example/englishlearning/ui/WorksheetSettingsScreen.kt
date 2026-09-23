package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.worksheet.WorksheetDirection
import com.example.englishlearning.learning.worksheet.WorksheetSettings
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint

@Composable
fun WorksheetSettingsScreen(
    settings: WorksheetSettings,
    selectedCount: Int,
    onToggleDirection: (WorksheetDirection) -> Unit,
    onSelectRange: (com.example.englishlearning.learning.worksheet.WorksheetRange) -> Unit = {},
    onSelectTemplate: (com.example.englishlearning.learning.worksheet.WorksheetTemplate) -> Unit = {},
    onToggleGrid: (Boolean) -> Unit,
    onToggleAnswers: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onBack: () -> Unit,
) {
    val canPreview = settings.isReadyToPreview() && selectedCount > 0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("worksheet_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.semantics { contentDescription = "返回学习工具" },
            colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark),
        ) { Text("← 返回学习工具", fontWeight = FontWeight.Bold) }
        Text(
            text = "生成默写纸",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MintPrimaryDark,
        )
        Text(
            text = "先选择默写方向，再预览纸张内容。词条只来自当前学习计划。",
            style = MaterialTheme.typography.bodyMedium,
            color = MintTextMuted,
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MintTint),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("本次词条", color = MintPrimary, fontWeight = FontWeight.Bold)
                    Text("当前计划中可导出的词条", color = MintTextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Text("$selectedCount 词", color = MintPrimaryDark, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        Text("词表模板", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        com.example.englishlearning.learning.worksheet.WorksheetTemplate.values().forEach { template ->
            val selected = template == settings.template
            Card(
                colors = CardDefaults.cardColors(containerColor = if (selected) MintTint else MintSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (selected) MintPrimary else MintOutline, RoundedCornerShape(18.dp))
                    .clickable { onSelectTemplate(template) }
                    .testTag(templateTag(template)),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selected,
                        onClick = { onSelectTemplate(template) },
                        colors = RadioButtonDefaults.colors(selectedColor = MintPrimary),
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(templateLabel(template), color = MintPrimaryDark, fontWeight = FontWeight.Bold)
                        Text(templateDescription(template), color = MintTextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text("词条范围", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        com.example.englishlearning.learning.worksheet.WorksheetRange.values().forEach { range ->
            val selected = range == settings.range
            Card(
                colors = CardDefaults.cardColors(containerColor = if (selected) MintTint else MintSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, if (selected) MintPrimary else MintOutline, RoundedCornerShape(16.dp)).clickable { onSelectRange(range) },
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = selected, onCheckedChange = { onSelectRange(range) }, colors = CheckboxDefaults.colors(checkedColor = MintPrimary))
                    Text(rangeLabel(range), color = MintPrimaryDark, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        if (settings.requiresDirection()) {
            Text("默写方向", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
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
            Text("纸张样式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        }
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
        if (settings.requiresDirection() && settings.directions.isEmpty()) {
            Text("至少选择一种默写方向后才能预览。", color = MaterialTheme.colorScheme.error)
        } else if (selectedCount == 0) {
            Text("当前没有可导出的已完成词条。", color = MintTextMuted)
        }
        Spacer(Modifier.height(2.dp))
        Button(
            onClick = onPreview,
            enabled = canPreview,
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("worksheet_preview_action"),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MintPrimary,
                contentColor = Color.White,
                disabledContainerColor = MintTint,
                disabledContentColor = MintTextMuted,
            ),
        ) { Text("预览默写纸", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun DirectionRow(
    tag: String,
    title: String,
    description: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (checked) MintTint else MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (checked) MintPrimary else MintOutline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = title },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(checkedColor = MintPrimary, uncheckedColor = MintOutline),
            )
            Column(Modifier.padding(start = 4.dp)) {
                Text(title, color = MintPrimaryDark, fontWeight = FontWeight.Bold)
                Text(description, color = MintTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun rangeLabel(range: com.example.englishlearning.learning.worksheet.WorksheetRange): String = when (range) {
    com.example.englishlearning.learning.worksheet.WorksheetRange.COMPLETED_TODAY -> "今天已完成的词"
    com.example.englishlearning.learning.worksheet.WorksheetRange.ALL_PLAN_ITEMS -> "当前计划全部词"
    com.example.englishlearning.learning.worksheet.WorksheetRange.COMPLETED_NEW -> "今天已完成的新词"
    com.example.englishlearning.learning.worksheet.WorksheetRange.COMPLETED_REVIEW -> "今天已完成的复习词"
    com.example.englishlearning.learning.worksheet.WorksheetRange.DIFFICULT_TODAY -> "今天的易错词"
}

@Composable
private fun templateLabel(template: com.example.englishlearning.learning.worksheet.WorksheetTemplate): String = when (template) {
    com.example.englishlearning.learning.worksheet.WorksheetTemplate.FULL_LIST -> "我的词表"
    com.example.englishlearning.learning.worksheet.WorksheetTemplate.SPELLING_TEST -> "我的词表 · 拼写测试"
    com.example.englishlearning.learning.worksheet.WorksheetTemplate.EBBINGHAUS_REVIEW -> "我的词表 · 艾宾浩斯抗遗忘"
}

@Composable
private fun templateDescription(template: com.example.englishlearning.learning.worksheet.WorksheetTemplate): String = when (template) {
    com.example.englishlearning.learning.worksheet.WorksheetTemplate.FULL_LIST -> "双向并排完整词表，每页 40 词，可直接背诵"
    com.example.englishlearning.learning.worksheet.WorksheetTemplate.SPELLING_TEST -> "左右镜像默写，每页 20 词，一边给提示一边留空格"
    com.example.englishlearning.learning.worksheet.WorksheetTemplate.EBBINGHAUS_REVIEW -> "每页 20 词，右侧带 D1…D90 复习打卡格"
}

@Composable
private fun templateTag(template: com.example.englishlearning.learning.worksheet.WorksheetTemplate): String = when (template) {
    com.example.englishlearning.learning.worksheet.WorksheetTemplate.FULL_LIST -> "worksheet_template_full_list"
    com.example.englishlearning.learning.worksheet.WorksheetTemplate.SPELLING_TEST -> "worksheet_template_spelling_test"
    com.example.englishlearning.learning.worksheet.WorksheetTemplate.EBBINGHAUS_REVIEW -> "worksheet_template_ebbinghaus"
}

@Composable
private fun OptionSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MintOutline, RoundedCornerShape(18.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MintPrimaryDark, fontWeight = FontWeight.Bold)
                Text(description, color = MintTextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = MintPrimary),
            )
        }
    }
}
