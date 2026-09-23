package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppleMintEnd
import com.example.englishlearning.ui.theme.AppleMintMiddle
import com.example.englishlearning.ui.theme.AppleMintStart
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint

private val AiFeatureGradient = Brush.linearGradient(listOf(AppleMintStart, AppleMintMiddle, AppleMintEnd))

/**
 * 单个 AI 功能的页面骨架。
 *
 * 这一页刻意不做「空白占位」：它把**当前进度**、**还差什么**、**会用哪些数据**都摆出来，
 * 并给出一个此刻真的能用的动作。用户此前反复遇到「点开是坏的」，所以这里的规则是：
 * 宁可把「没做完」写清楚，也不假装能用。
 */
@Composable
fun AiFeatureScreen(
    feature: AiFeature,
    todayWordCount: Int?,
    dueWordCount: Int?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLearning: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("ai_feature_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(
            onClick = onBack,
            colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark),
            modifier = Modifier
                .testTag("ai_feature_back")
                .semantics { contentDescription = "返回 AI 学" },
        ) { Text("← 返回 AI 学", fontWeight = FontWeight.Bold) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(AiFeatureGradient, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(feature.glyph, fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MintPrimaryDark,
                    modifier = Modifier.testTag("ai_feature_title"),
                )
                Text(feature.summary, style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
            }
        }

        SectionCard(tag = "ai_feature_status_card") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当前进度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark, modifier = Modifier.weight(1f))
                StatusPill(implemented = feature.implemented)
            }
            Spacer(Modifier.height(8.dp))
            Text(feature.status, style = MaterialTheme.typography.bodyMedium, color = MintTextMuted, modifier = Modifier.testTag("ai_feature_status"))
        }

        SectionCard(tag = "ai_feature_dependencies_card") {
            Text("还差什么", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            Spacer(Modifier.height(8.dp))
            feature.dependencies.forEachIndexed { index, dependency ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("ai_feature_dependency_$index"),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("·", color = MintPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                    Text(dependency, style = MaterialTheme.typography.bodyMedium, color = MintTextMuted)
                }
            }
        }

        SectionCard(tag = "ai_feature_data_card") {
            Text("会用到的数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    todayWordCount == null || dueWordCount == null -> "今日计划还没读出来，稍后重试。"
                    else -> "今日新词 $todayWordCount 个 · 待复习 $dueWordCount 个（来自本机，不会离开设备除非发起 AI 请求）"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MintTextMuted,
                modifier = Modifier.testTag("ai_feature_data"),
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MintTint),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("现在可以做的", fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                Text(
                    text = "这个功能还没开发完，但下面两件事现在就能用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MintTextMuted,
                )
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ai_feature_open_settings")
                        .semantics { contentDescription = "去配置 AI 服务" },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
                ) { Text("去配置 AI 服务", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onOpenLearning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, MintOutline, RoundedCornerShape(14.dp))
                        .testTag("ai_feature_open_learning")
                        .semantics { contentDescription = "回到今日学习" },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
                ) { Text("回到今日学习", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SectionCard(tag: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MintOutline, RoundedCornerShape(22.dp)).testTag(tag),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) { content() }
    }
}

@Composable
private fun StatusPill(implemented: Boolean) {
    val label = if (implemented) "可用" else "未实现"
    Box(
        modifier = Modifier
            .background(if (implemented) MintPrimary else MintTint, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("ai_feature_status_pill"),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (implemented) Color.White else MintPrimaryDark,
        )
    }
}
