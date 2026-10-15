package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted

/**
 * F4-01 决策 §8「看得见」：历史按「同日+同册+同类型+同长度档」分组——
 * 单版本组保持点卡直开（F3-01A 行为不变）；多版本组点开展开版本列表，
 * 每版给「重读」与「删除此版」。唯一版本连删除入口都不给，「删不空」在
 * 入口侧就先立住一道；仓储事务里的判定是最后一道。
 */
@Composable
fun ReadingHistoryScreen(
    history: List<Article>,
    onBack: () -> Unit,
    onOpenArticle: (Article) -> Unit = {},
    onRequestDeleteVersion: (Article) -> Unit = {},
    notice: String? = null,
) {
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
        if (notice != null) {
            Text(notice, color = MintPrimaryDark, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("reading_version_notice"))
        }
        if (history.isEmpty()) {
            Text("暂无本地文章。生成后的文章会保存在这台设备上，可离线重读。", color = MintTextMuted, modifier = Modifier.testTag("reading_history_empty"))
        } else {
            // findHistory 已按 localDate DESC, generatedAt DESC, version DESC 排全部版本行；
            // groupBy 保持遭遇顺序，组内顺序即版本从新到旧。
            val groups = history.groupBy { it.versionGroupKey }
            groups.forEach { (key, versions) ->
                val newest = versions.first()
                if (versions.size == 1) {
                    val article = versions.single()
                    Card(
                        onClick = { onOpenArticle(article) },
                        colors = CardDefaults.cardColors(containerColor = MintSurface),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().testTag("reading_history_item_${article.articleId}"),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(article.title, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                            Text("${article.localDate} · 第 ${article.version} 版", color = MintTextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    var expanded by remember(key) { mutableStateOf(false) }
                    Card(
                        onClick = { expanded = !expanded },
                        colors = CardDefaults.cardColors(containerColor = MintSurface),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reading_history_group_$key")
                            .semantics { stateDescription = if (expanded) "已展开" else "已收起" },
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(newest.title, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                            Text("共 ${versions.size} 个版本 · 点按${if (expanded) "收起" else "展开"}", color = MintTextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (expanded) {
                        versions.forEach { article ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MintSurface),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp).testTag("reading_version_row_${article.articleId}"),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    Text("第 ${article.version} 版 · ${article.localDate}", color = MintPrimaryDark, style = MaterialTheme.typography.bodyMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { onOpenArticle(article) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = AppPalette.OnMintPrimary),
                                            modifier = Modifier.testTag("reading_version_reread_${article.articleId}"),
                                        ) { Text("重读") }
                                        TextButton(
                                            onClick = { onRequestDeleteVersion(article) },
                                            modifier = Modifier.testTag("reading_version_delete_${article.articleId}"),
                                        ) { Text("删除此版", color = MintPrimaryDark) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val Article.versionGroupKey: String
    get() = "${localDate}_${activeWordBookId}_${articleType.name}_${lengthTier.name}"
