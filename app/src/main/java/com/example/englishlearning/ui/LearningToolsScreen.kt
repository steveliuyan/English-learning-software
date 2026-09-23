package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted

@Composable
fun LearningToolsScreen(
    onBack: () -> Unit,
    onOpenWorksheet: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(MintBackground).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark)) {
            Text("← 返回今日计划", fontWeight = FontWeight.Bold)
        }
        Text("学习工具", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text("把今天学过的词带到纸上，复习会更牢。", color = MintTextMuted)
        Card(colors = CardDefaults.cardColors(containerColor = MintSurface), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("导出单词表", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                Text("选择中译英或英译中，预览后生成可打印 PDF。", color = MintTextMuted)
                Button(onClick = onOpenWorksheet, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White)) {
                    Text("生成默写纸", fontWeight = FontWeight.Bold)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MintSurface), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("更多学习工具", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                Text("错题本、打印计划和复习统计将在后续版本开放。", color = MintTextMuted)
            }
        }
    }
}
