package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppGlyph
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.AppleMintEnd
import com.example.englishlearning.ui.theme.AppleMintMiddle
import com.example.englishlearning.ui.theme.AppleMintStart
import com.example.englishlearning.ui.theme.LocalIsDark
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

private val AiFeatureGradient = Brush.linearGradient(listOf(AppleMintStart, AppleMintMiddle, AppleMintEnd))

/**
 * 单个 AI 功能的页面骨架。
 *
 * 这一页刻意不做「空白占位」：它把**当前进度**摆出来，并给出此刻真的能用的动作。
 * 用户此前反复遇到「点开是坏的」，所以这里的规则是：宁可把「没做完」写清楚，也不假装能用。
 *
 * 2026-10-25 简化批删掉了「还差什么（依赖）」与「会用到的数据」两张卡：前者是给开发者看的
 * 阻塞项清单，后者把同一批本机数据连同来源说明再说一遍，两张卡都排在唯一可执行动作之前，
 * 让没上线的功能页长了三屏解释。裁决见 `docs/decisions/2026-10-25-ui-simplification-deletions.md`。
 *
 * 批 4 Task 2（spec §5「骨架页」行：「两按钮上移，下半屏补一句轻量说明」）：
 *  - 页头归共享 `AppScreenHeader`（标题 / 副标题 / 返回位），44dp 渐变徽标走它的 `badge` 槽；
 *  - 骨架从「进度卡 → 说明卡 → 两枚堆在卡里的按钮」改成「页头 → 进度卡 → 主操作 → 次操作 → 页末一句」：
 *    改前用户在 411dp 屏上要滑过大半屏才看到唯一的主操作；
 *  - 分组靠共享 `SoftCard`（面 + 20 圆角 + 无描边），本屏没有强卡，品牌渐变只出现在头部徽标；
 *  - 排印与墨色全部走组合期令牌（六档 `AppType` + `appColors()` / `appDomain(...)`），深色档不再停在浅值。
 */
@Composable
fun AiFeatureScreen(
    feature: AiFeature,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLearning: () -> Unit,
) {
    // 落点墨色一律组合期取：改前本屏 36 处静态薄荷桥 + 12 处 `MaterialTheme.typography`，深色档整页停在浅值。
    // 顶层 `AiFeatureGradient` 保持静态（`MintTokenIsComposableContractTest` 按符号名钉着它读的是
    // `AppleMint*` 而不是本屏的静态读点）。
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("ai_feature_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = feature.title,
            subtitle = feature.summary,
            onBack = onBack,
            badge = { AiFeatureBadge(feature) },
        )

        SoftCard(modifier = Modifier.testTag("ai_feature_status_card")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "当前进度",
                    style = AppType.Title,
                    color = colors.text1,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(implemented = feature.implemented)
            }
            Text(
                text = feature.status,
                style = AppType.Body,
                color = colors.text2,
                modifier = Modifier.testTag("ai_feature_status"),
            )
        }

        // 主操作：全屏唯一一枚实底按钮，容器取域色（与徽章「现在能用」档同族）。
        // 容器 `appDomain(AppDomain.AiSpeech).base` 两套主题皆亮（深套 DomainDark base/deep 合一），
        // 前景因此按 R-23.13 分档：浅套 onMint、深套 onBrand（裸 onMint 在深套是亮墨压亮底 ≈1.49:1）。
        Button(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("ai_feature_open_settings")
                .semantics { contentDescription = "去配置 AI 服务" },
            shape = AppShape.Button,
            colors = ButtonDefaults.buttonColors(
                containerColor = appDomain(AppDomain.AiSpeech).base,
                contentColor = if (!LocalIsDark.current) colors.onMint else colors.onBrand,
            ),
        ) {
            // 前景显式补色（`ContainerInkPairingContractTest` 在「同段已迁中性容器」的段里要求每处
            // `AppType` 排印点写明墨）——与 `contentColor` 同值，不引入第二个判据源。
            Text(
                text = "去配置 AI 服务",
                style = AppType.Label,
                color = if (!LocalIsDark.current) colors.onMint else colors.onBrand,
            )
        }

        // 次操作：面 + 1dp 描边（中性 `surface` + `outlineSoft`），不与主操作争实底域色。
        Button(
            onClick = onOpenLearning,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .border(1.dp, colors.outlineSoft, AppShape.Button)
                .testTag("ai_feature_open_learning")
                .semantics { contentDescription = "回到今日学习" },
            shape = AppShape.Button,
            colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.text1),
        ) {
            Text(
                text = "回到今日学习",
                style = AppType.Label,
                color = colors.text1,
            )
        }

        // 页末一句轻量说明（spec §5「下半屏补一句轻量说明」）：先说现在真能用的两件事，
        // 再说其余能力的现状——不含任何「即将上线 / 马上支持」的承诺。
        Text(
            text = "这个功能上线前，上面两件事随时可用；其余能力点开只会看到进度。",
            style = AppType.Footnote,
            color = colors.text2,
            modifier = Modifier.testTag("ai_feature_light_note"),
        )
    }
}

/**
 * 头部 44dp 渐变徽标（R-23.15 登记过的「携带修复」）：从改前的「标题 Row 左边」挪进页头的 `badge` 槽，
 * 形状与墨色一字不动——单字中文不当图标，换 `feature.icon`（字形唯一出处在 `AiFeature` 目录里），
 * 压品牌渐变的前景按 2026-10-18 裁决 #3 取 onBrand（白压渐变起点只有 1.29:1，`AiBadgeContrastFixTest` 复算）。
 *
 * 2026-11-02 换字形：此前七枚功能点进来都是同一枚 `AppIcon.Spark`（AI 域锚点），用户从「拍照翻译」
 * 那一行点进去，页头亮的却是 Spark，刚在列表里建立的「相机＝拍照翻译」当场被剪断。域锚点已由头部
 * 品牌渐变与 `AppDomain.AiSpeech` 配色承担，字形该说的是「你点的是哪个功能」。
 * 徽标因此要接住 `feature`——这正是 `AiBadgeContrastFixTest` 那条 onBrand 腿换锚成 `feature.icon` 的原因。
 */
@Composable
private fun AiFeatureBadge(feature: AiFeature) {
    val colors = appColors()
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(AppShape.Icon)
            .background(AiFeatureGradient),
        contentAlignment = Alignment.Center,
    ) {
        AppGlyph(icon = feature.icon, tint = colors.onBrand, size = 22.dp)
    }
}

@Composable
private fun StatusPill(implemented: Boolean) {
    val colors = appColors()
    // 徽章面文案只有一个出处（R-23.9）：featureStatusBadge(implemented)。
    // 目录原文（AiFeature.status 的「已可用：…」/「未实现：…」）是另一面，本屏照旧原样渲染。
    val label = featureStatusBadge(implemented)
    Box(
        modifier = Modifier
            .background(if (implemented) appDomain(AppDomain.AiSpeech).base else colors.tint, AppShape.Pill)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("ai_feature_status_pill"),
    ) {
        Text(
            text = label,
            style = AppType.Label,
            // 「现在能用」档底是实底域色——深色档同族仍是**亮底**，所以前景按 R-23.13 再分一层档：
            // 浅档 onMint、深档 onBrand（单迁裸 onMint = 亮墨压亮底 ≈1.49:1，正是 B1 的形状；
            // 写法取 `AiProfileSettingsScreen.kt:524` 的门控链式同形）。
            // 「未上线」档底是中性 `tint` + `text1` 深字；两档底色按裁决保持不变。
            color = if (implemented && !LocalIsDark.current) colors.onMint else if (implemented) colors.onBrand else colors.text1,
        )
    }
}
