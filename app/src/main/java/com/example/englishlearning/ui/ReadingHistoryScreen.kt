package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted

@Composable
fun ReadingHistoryScreen(history: List<Article>, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MintBackground).padding(horizontal = 20.dp, vertical = 24.dp).testTag("reading_history_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Button(
            onClick = onBack,
            modifier = Modifier.semantics { contentDescription = "返回每日阅读" },
            colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
        ) { Text("返回") }
        Text("本地阅读历史", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        if (history.isEmpty()) {
            Text("暂无本地文章。生成后的文章会保存在这台设备上，可离线重读。", color = MintTextMuted, modifier = Modifier.testTag("reading_history_empty"))
        } else {
            history.forEach { article ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MintSurface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().testTag("reading_history_item_${article.articleId}"),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(article.title, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                        Text("${article.localDate} · 第 ${article.version} 版", color = MintTextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
