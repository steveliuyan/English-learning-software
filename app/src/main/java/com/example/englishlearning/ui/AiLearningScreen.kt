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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppGlyph
import com.example.englishlearning.ui.components.AppGlyphLayer
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.EntryRow
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.mascot.AiMascot
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.AppleMintEnd
import com.example.englishlearning.ui.theme.AppleMintMiddle
import com.example.englishlearning.ui.theme.AppleMintStart
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint
import com.example.englishlearning.ui.theme.appColors

private val AiHeaderGradient = Brush.linearGradient(listOf(AppleMintStart, AppleMintMiddle, AppleMintEnd))

/**
 * 头卡表情边长。
 *
 * 140dp 是取舍后的结果，不是随手取的数：手机竖屏内容宽约 320dp（360dp 屏宽减去左右 20dp 内边距），
 * 140dp 约占其四成半，在整页最上方形成明确的视觉入口，同时把标题/副标题留在同一屏内。
 * 再大（≥160dp）会把副标题和状态徽章挤出首屏，用户看不到「AI 尚未接通」这句实话；
 * 再小（≤110dp）则退化成装饰小图标，与「要大」的诉求不符。逐项理由见设计文档 §4.5。
 */
private val AI_MASCOT_SIZE = 140.dp

/**
 * 「AI 学」一级页。
 *
 * 页面上的每一句状态描述都来自 [AiFeature]，不做本地改写：尚未实现的功能，
 * 全页只提供「入口 + 如实说明」，不制造点开即坏的假功能。
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
    // 裁决④（AC-R-23）：默认展开的是**现在能用**的能力，折叠键只管「未上线」那批。
    // 旧形状把 3 枚可用与 4 枚未上线混成一句「AI 功能（7 个）展开 ˅」，
    // 用户要么整页收起看不到能用的东西，要么展开后被四枚坏入口刷屏。
    var unreleasedOpen by rememberSaveable { mutableStateOf(false) }
    val implementedFeatures = AiFeature.entries.filter { it.implemented }
    val unreleasedFeatures = AiFeature.entries.filter { !it.implemented }
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
        Text(
            text = "现在能用（${implementedFeatures.size} 个）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MintPrimaryDark,
            modifier = Modifier.testTag("ai_learning_available_section"),
        )
        implementedFeatures.forEach { feature ->
            AiFeatureRow(feature = feature, onOpen = { onOpenFeature(feature) })
        }
        // 未上线那批压成一条折叠行：这一行自己就标着「未上线」，不必点开也知道下面压的是什么。
        // 折叠入口走共享 EntryRow（48dp 以上可点面 + 展开态语义），不再手搓第二份折叠件。
        EntryRow(
            icon = AppIcon.Spark,
            domain = AppDomain.AiSpeech,
            title = "未上线功能（${unreleasedFeatures.size} 个）",
            subtitle = "点开逐条看进度与还差什么",
            trailing = featureStatusBadge(false),
            expanded = unreleasedOpen,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_learning_unreleased_toggle"),
        ) { unreleasedOpen = !unreleasedOpen }
        if (unreleasedOpen) {
            Column(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                unreleasedFeatures.forEach { feature ->
                    AiFeatureRow(feature = feature, onOpen = { onOpenFeature(feature) })
                }
            }
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
    // 修复轮 R-23.14 回退（批 3 R-23.6 的第三站单迁）：状态胶囊容器是 `Color.White.copy(alpha = 0.85f)`，
    // 主题不变面（且属 RawColorLiteralRatchet 地盘，本批无权动容器）——前景改走主题访问器等于把
    // 深套亮墨钉死在白底上（深档 text2 压白 ≈2.21:1、深套域 deep 压白 ≈1.69:1，即评审席 B1）。
    // 前景因此回到批前两枚登记静态墨；浅档 AiSpeech 的 deep 墨压白 4.02:1 是批前既有债
    // （与 GlassButton.kt:70 同口径，登记在验证文档 Task 23 节），容器随主题改造归 Task 26/27，
    // 届时容器与前景同批重算，不再做第二次「前景单迁」。形状由 AiBadgeContrastFixTest 逐行钉死
    // （本注释刻意不写「包名.成员」点号形——本棘轮按裸文本计数，注释也进账）。
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().testTag("ai_learning_header"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AiHeaderGradient)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 表情居中独占一行：手机屏宽约 360dp，与文字并排会把它压到 110dp 左右，
            // 既与「要大」冲突，也会把副标题挤成四五行的窄条。取舍理由见设计文档 §4.5。
            AiMascot(aiConfigured = aiConfigured, modifier = Modifier.size(AI_MASCOT_SIZE))
            // 批 3 收口（Task 26）：品牌渐变上的文案墨从 `Color.White` 改读 `appColors().onBrand`。
            // 白字压三段渐变最亮端 #A8F3C8 只有 1.29:1（现算），是批 2 I-3 登记的结转债；
            // on-brand 压三段 12.81 / 9.95 / 6.87:1 全过正文 4.5（2026-10-18 裁决 #3「渐变上一律 on-brand」，
            // 与 AiFeatureScreen 徽标同规则）。判据见 `AiBadgeContrastFixTest`。
            Text(
                "AI 学",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = appColors().onBrand,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "用你学过的词，生成专属的阅读与练习",
                style = MaterialTheme.typography.bodyMedium,
                color = appColors().onBrand,
                textAlign = TextAlign.Center,
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
                    // R-23.14：白胶囊容器 `Color.White.copy(alpha = 0.85f)` 不随主题（品牌渐变头卡之上恒为亮面），
                    // 前景必须同为主题不变的两枚静态墨；改成 `appDomain(...)` / `colors.text2` 在深套就是
                    // 亮墨压白（1.69:1 / 2.21:1，B1 第三站形状）。理由与复算见 `AiBadgeContrastFixTest`。
                    color = if (aiConfigured) DomainColors.AiSpeech.deep else AppPalette.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun AiFeatureRow(feature: AiFeature, onOpen: () -> Unit) {
    val status = featureStatusBadge(feature.implemented)
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(20.dp))
            .testTag("ai_feature_${feature.key}")
            .clickable(onClick = onOpen)
            // TalkBack 读一行要同时读到「哪个能力」与「现在能不能用」，只念能力名等于把
            // 裁决④ 的判断又推回给用户自己猜。「状态：」这枚中文引导词是
            // `AccessibilityLabelContractTest` 禁令 1a 要求的（字面量里必须留中文，插值不算）。
            .semantics { contentDescription = "${feature.title}，状态：$status" },
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
                // 裁决④ / spec §4.2：单字中文（文/填/听/讲/析/拍/画）不再当图标，
                // 换 AI 域锚点字形 AppIcon.Spark；层取默认 Duotone（取证与理由见报告 §4）。
                AppGlyph(icon = AppIcon.Spark, tint = MintPrimaryDark, size = 20.dp)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(feature.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                Spacer(Modifier.height(3.dp))
                Text(feature.summary, style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
            }
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MintPrimaryDark,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .testTag("ai_feature_status_badge_${feature.key}"),
            )
            // 「›」这个字符也不再当图标：与 EntryRow / NotConfiguredCard 的尾部箭头同一形状
            // （16dp + 显式 StrokeOnly，ChevronRight 本就在两层豁免表里，没有填充层可画）。
            AppGlyph(
                icon = AppIcon.ChevronRight,
                tint = MintTextMuted,
                size = 16.dp,
                modifier = Modifier.padding(start = 6.dp),
                layer = AppGlyphLayer.StrokeOnly,
            )
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
                        PillButton(
                            text = "查看详情",
                            onClick = onOpenWordList,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            testTag = "ai_learning_nav_detail",
                            contentDescription = "查看详情",
                        )
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
                    ExplainerLine("现在的状态", "AI 能力分批接通中：长难句分析与生图正在开发，其余功能点开只能看到进度与依赖。")
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
