package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppCard
import com.example.englishlearning.ui.components.AppGlyph
import com.example.englishlearning.ui.components.AppGlyphLayer
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.InlineIconBadge
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 「AI 助学」一级页（tab 根页），2026-11-02 按参考原型 `sAiHome` 重做。
 *
 * 上一版是「品牌渐变头卡 + 白胶囊状态 + 现在能用裸行组 + 未上线折叠行 + 查看词表 + 使用说明」。
 * 用户逐条否掉了它：「把那个 AI 助学这个那个颜色给删掉，跟我之前给你的参考里面就没有这种东西」。
 * 参考页只有四件东西，本文件就只留这四件（决策
 * `docs/decisions/2026-11-02-profile-hub-and-four-tabs.md` §5）：
 *  1. 普通页头：眉标「AI 助学」、标题「把词用起来」、一句说明、行尾一枚配置按钮——不再是 hero；
 *  2. 双列功能卡：已接通的功能在前，卡面 = 功能字形 + 标题 + 一句说明；
 *  3. 「还在打磨」分组：未接通的四项同样双列，但不可点、带状态徽章；
 *  4. 页末一条隐私提示。
 *
 * 「查看词表」与「AI 助学如何起作用」两段整块离场：前者在参考里不存在（词库存取另有学习页与
 * 「我的 → 词书」两条路），后者是上一版为解释渐变头卡而写的补充说明，内容已由设置页与隐私提示覆盖。
 *
 * @param onOpenFeature 点开某个已接通的功能页。
 * @param onOpenAiProfiles 页头配置按钮，进入 AI 服务配置页。
 */
@Composable
fun AiLearningScreen(
    onOpenFeature: (AiFeature) -> Unit,
    onOpenAiProfiles: () -> Unit,
) {
    val implementedFeatures = AiFeature.entries.filter { it.implemented }
    val unreleasedFeatures = AiFeature.entries.filter { !it.implemented }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors().background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("ai_learning_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            eyebrow = "AI 助学",
            title = "把词用起来",
            subtitle = "每个功能都先做本机能做的部分，再把必要的文字交给已配置的服务。",
            action = { AiProfilesAction(onClick = onOpenAiProfiles) },
        )
        AiFeatureGrid(features = implementedFeatures, onOpenFeature = onOpenFeature)
        SectionHeading(
            text = "还在打磨",
            trailing = "${unreleasedFeatures.size} 个",
            modifier = Modifier.testTag("ai_learning_unreleased_section"),
        )
        // 未接通那一组传 null 回调：卡面照画、状态照标，但不挂 clickable——点不开的东西不给点。
        AiFeatureGrid(features = unreleasedFeatures, onOpenFeature = null)
        // 原型 `sAiHome` 页末 `notice(…, 'info', 'shield')`：把「什么不出本机」集中说一次。
        // 两件事都是真实形状——拍照翻译的图只在本机 OCR（AC3-05），AI 生图先把中文转英文提示词
        // （两段式，省一次按张计费的调用），不是宣传句。
        InlineNotice(
            text = "拍照翻译的图片只在本机识别，不上传；AI 生图会先把中文描述转成英文提示词。",
            kind = InlineNoticeKind.Info,
            modifier = Modifier.testTag("ai_learning_privacy_notice"),
        )
    }
}

/**
 * 功能卡网格（参考 `grid-template-columns:1fr 1fr`）。
 *
 * 行高按 `IntrinsicSize.Min` 拉齐：参考里同一行的两张卡等高，单靠 `weight` 只能等宽，
 * 说明长短不一时右卡会比左卡矮一截。末行不满列时逐格补等宽 `Spacer`，否则最后一张会撑满整行。
 *
 * 列数不是常量（QA3 A2.6②）：走查册实测顶档 3.0 下每张卡在半宽列里排到约 714dp 高、状态徽标被推出可视区，
 * 「双列」这一形状只在钉档以内成立，超过就退回单列让每张卡拿到整幅宽度。判档收在 [aiFeatureGridColumns]。
 */
@Composable
private fun AiFeatureGrid(
    features: List<AiFeature>,
    onOpenFeature: ((AiFeature) -> Unit)?,
) {
    val columns = aiFeatureGridColumns(LocalDensity.current.fontScale)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        features.chunked(columns).forEach { rowFeatures ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowFeatures.forEach { feature ->
                    AiFeatureCard(
                        feature = feature,
                        onOpen = onOpenFeature?.let { callback -> { callback(feature) } },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                if (rowFeatures.size < columns) {
                    repeat(columns - rowFeatures.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 顶档字号降级：功能卡列数 = 字号档的函数。
 *
 * 阈值取严格大于 `2f`，不是「≥2.5 才降」——`2.0` 是 AC-R-07 钉住的验收档，那一档的双列形状是设计稿口径，
 * 已经在 [AiLearningScreenTest] 里逐列点名；把它一起改掉就等于用顶档整改偷走了钉档的形状。
 * 口径与批 B 的 `AppWindowLayoutPolicy.downgradesHeroHeader` 同源：**超过**钉档才降级。
 *
 * 抽成 `internal` 纯函数是为了让这四档算术能在 JVM 侧点名（connected 只能证「顶档确实上下堆叠」，证不出 2.0 不许动）。
 */
internal fun aiFeatureGridColumns(fontScale: Float): Int = if (fontScale > 2f) 1 else 2

/**
 * 单张功能卡：字形 + 标题 + 一句说明（可点档再加状态徽章）。
 *
 * 状态词来自共享 [featureStatusBadge]，屏侧不另写一套「可用 / 未上线」措辞；徽章只长在未上线的卡上——
 * 已接通的卡整张可点，「现在能用」这件事由能不能点直接说明，不必每张都盖一枚章。
 *
 * TalkBack 读一张卡要同时读到「哪个能力」与「现在能不能用」：`contentDescription` 由屏侧补，
 * 「，状态：」这枚中文引导词是 `AccessibilityLabelContractTest` 禁令 1a 要求的。
 */
@Composable
private fun AiFeatureCard(
    feature: AiFeature,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val status = featureStatusBadge(feature.implemented)
    val clickModifier = if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier
    AppCard(
        modifier = modifier
            .testTag("ai_feature_${feature.key}")
            .semantics { contentDescription = "${feature.title}，状态：$status" }
            .then(clickModifier),
    ) {
        InlineIconBadge(icon = feature.icon, domain = AppDomain.AiSpeech)
        Text(
            text = feature.title,
            style = AppType.Body,
            color = colors.text1,
        )
        Text(
            text = feature.summary,
            style = AppType.Footnote,
            color = colors.text2,
        )
        if (!feature.implemented) {
            Text(
                text = status,
                style = AppType.Label,
                color = colors.text2,
                modifier = Modifier.testTag("ai_feature_status_badge_${feature.key}"),
            )
        }
    }
}

/**
 * 页头行尾的配置按钮（参考 `aria-label="AI 服务配置"` 的落点）。
 *
 * 几何同学习页齿轮：外层 48dp 可点区、内层 40dp 视觉容器，字形 20dp 只描边。
 */
@Composable
private fun AiProfilesAction(onClick: () -> Unit) {
    val colors = appColors()
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(AppShape.Pill)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "AI 服务配置" }
            .testTag("ai_learning_open_profiles"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(AppShape.Pill),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyph(icon = AppIcon.Gear, tint = colors.text1, size = 20.dp, layer = AppGlyphLayer.StrokeOnly)
        }
    }
}
