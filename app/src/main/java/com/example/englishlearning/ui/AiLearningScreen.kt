package com.example.englishlearning.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

private val AiHeaderGradient = Brush.linearGradient(listOf(AppleMintStart, AppleMintMiddle, AppleMintEnd))

/**
 * 「AI 学」一级页。
 *
 * 页面上的每一句状态描述都来自 [AiFeature]，不做本地改写：四个功能的生成逻辑尚未实现，
 * 所以全页只提供「入口 + 如实说明」，不制造点开即坏的假功能。
 *
 * @param todayWordCount 今日计划的新词数，`null` 表示今日计划还不可读。
 * @param dueWordCount 今日计划的待复习词数，`null` 同上。
 * @param aiConfigured 是否已有可用的 AI 配置。取自本机真实的 AI Profile：至少有一套配了密钥才为真。
 *   AI 调用本身（F2-03 网络客户端）仍未接通，所以这一个徽章只描述「配置」状态，不承诺「能生成」。
 * @param onOpenFeature 点开某个功能页。
 * @param onOpenWordList 点「查看词表」的详情，跳去看得到真实词表的地方。
 */
@Composable
fun AiLearningScreen(
    todayWordCount: Int?,
    dueWordCount: Int?,
    aiConfigured: Boolean,
    onOpenFeature: (AiFeature) -> Unit,
    onOpenWordList: () -> Unit,
) {
    var explainerOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("ai_learning_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AiHeaderCard(aiConfigured = aiConfigured)
        AiFeature.entries.forEach { feature ->
            AiFeatureRow(feature = feature, onOpen = { onOpenFeature(feature) })
        }
        AiNavigationCard(
            todayWordCount = todayWordCount,
            dueWordCount = dueWordCount,
            onOpenWordList = onOpenWordList,
        )
        AiExplainer(open = explainerOpen, onToggle = { explainerOpen = !explainerOpen })
    }
}

@Composable
private fun AiHeaderCard(aiConfigured: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().testTag("ai_learning_header"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AiHeaderGradient)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("AI 学", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                text = "用你学过的词，生成专属的阅读与练习",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("ai_learning_status_badge")
                    .semantics { contentDescription = if (aiConfigured) "AI 已配置" else "AI 尚未接通" },
            ) {
                Text(
                    text = if (aiConfigured) "AI 已配置" else "AI 尚未接通",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MintPrimaryDark,
                )
            }
        }
    }
}

@Composable
private fun AiFeatureRow(feature: AiFeature, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(20.dp))
            .testTag("ai_feature_${feature.key}")
            .clickable(onClick = onOpen)
            .semantics { contentDescription = feature.title },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MintTint, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(feature.glyph, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(feature.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                Spacer(Modifier.height(3.dp))
                Text(feature.summary, style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MintTextMuted)
        }
    }
}

@Composable
private fun AiNavigationCard(todayWordCount: Int?, dueWordCount: Int?, onOpenWordList: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AI 学习导航", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Card(
            colors = CardDefaults.cardColors(containerColor = MintSurface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MintOutline, RoundedCornerShape(24.dp))
                .testTag("ai_learning_nav_card"),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("学习预热加速！", fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                Text(
                    text = "先把今天要学的词过一遍，再让 AI 围绕它们出内容，命中率会更高。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MintTextMuted,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AiHeaderGradient, RoundedCornerShape(20.dp))
                        .padding(2.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MintSurface, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("查看词表", fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                        Text(
                            text = wordListLine(todayWordCount, dueWordCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MintTextMuted,
                            modifier = Modifier.testTag("ai_learning_word_list_count"),
                        )
                        Button(
                            onClick = onOpenWordList,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("ai_learning_nav_detail")
                                .semantics { contentDescription = "查看详情" },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
                        ) { Text("查看详情", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

/**
 * 词表规模文案。今日计划不可读时如实说不可读，不拿 0 冒充。
 */
private fun wordListLine(todayWordCount: Int?, dueWordCount: Int?): String = when {
    todayWordCount == null || dueWordCount == null -> "今日计划还没读出来，稍后重试。"
    else -> "今日新词 $todayWordCount 个 · 待复习 $dueWordCount 个"
}

@Composable
private fun AiExplainer(open: Boolean, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(24.dp))
            .testTag("ai_learning_explainer"),
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            TextButton(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_learning_explainer_toggle")
                    .semantics { contentDescription = "AI 学如何起作用说明" },
                colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark),
            ) {
                Text(
                    text = if (open) "AI 学如何起作用？收起 ˄" else "AI 学如何起作用？展开 ˅",
                    fontWeight = FontWeight.Bold,
                )
            }
            if (open) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExplainerLine("会用到的数据", "今日新词与待复习词、当前词书等级、你的复习反馈记录。")
                    ExplainerLine("数据发往哪里", "只发给你自己配置的第三方 AI 服务；应用不内置密钥，也不上传到我们的服务器。")
                    ExplainerLine("现在的状态", "AI 网关尚未接通，四个功能都还没开发完，点开只能看到进度与依赖。")
                    ExplainerLine("不配置会怎样", "背词、复习、默写纸、阅读记录全部离线可用，不受影响。")
                }
            }
        }
    }
}

@Composable
private fun ExplainerLine(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
    }
}
