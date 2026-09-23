package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.worksheet.WorksheetDirection
import com.example.englishlearning.learning.worksheet.WorksheetPage
import com.example.englishlearning.learning.worksheet.WorksheetQuestionRow
import com.example.englishlearning.learning.worksheet.WorksheetTemplate
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted

/**
 * 导出前的强制预览：逐页展示手机端排好的内容，确认后才允许生成 PDF。
 *
 * 预览只呈现分页结果与文字内容，纸张边框、配色等版式由 PDF 渲染器负责。
 */
@Composable
fun WorksheetPreviewScreen(
    pages: List<WorksheetPage>,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MintBackground)
            .padding(20.dp)
            .testTag("worksheet_preview_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark)) {
            Text("← 返回修改", fontWeight = FontWeight.Bold)
        }
        Text("预览默写纸", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text("共 ${pages.size} 页 · 确认无误后再导出 PDF", color = MintTextMuted)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(pages) { index, page ->
                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(18.dp)) {
                    Text(pageTitle(index, page), color = MintPrimaryDark, fontWeight = FontWeight.Bold)
                    Text(
                        "共 ${page.questionRows.size + page.answers.size} 条",
                        color = MintTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (page.answers.isNotEmpty()) {
                        page.answers.forEach { answer ->
                            Text(
                                "${answer.number}. ${answer.lemma} ${answer.ipa} · ${answer.partOfSpeech} ${answer.meaningZh}",
                                color = BodyText,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    } else {
                        page.questionRows.forEach { row ->
                            Text(
                                "• ${row.number}. ${questionLine(row, page.template)}",
                                color = BodyText,
                                fontWeight = if (row.number == 1) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
            ) { Text("返回修改") }
            Button(
                onClick = onExport,
                modifier = Modifier.weight(1f).height(52.dp).testTag("worksheet_export_action"),
                colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
            ) { Text("导出 PDF", fontWeight = FontWeight.Bold) }
        }
    }
}

private fun pageTitle(index: Int, page: WorksheetPage): String {
    val label = if (page.answers.isNotEmpty()) {
        "答案页"
    } else {
        when (page.template) {
            WorksheetTemplate.FULL_LIST -> "我的词表"
            WorksheetTemplate.SPELLING_TEST -> when (page.direction) {
                WorksheetDirection.EN_TO_ZH -> "拼写测试 · 英译中"
                else -> "拼写测试 · 中译英"
            }
            WorksheetTemplate.EBBINGHAUS_REVIEW -> "艾宾浩斯抗遗忘"
        }
    }
    return "第 ${index + 1} 页 · $label"
}

/**
 * 拼写测试的题面：中译英给出释义、留空写单词；英译中给出单词、留空写释义。
 */
private fun questionLine(row: WorksheetQuestionRow, template: WorksheetTemplate): String = when (template) {
    WorksheetTemplate.SPELLING_TEST -> "${row.partOfSpeech} ${row.meaningZh}"
    else -> "${row.lemma} ${row.ipa}  ${row.partOfSpeech} ${row.meaningZh}"
}

private val BodyText = Color(0xFF33433D)
