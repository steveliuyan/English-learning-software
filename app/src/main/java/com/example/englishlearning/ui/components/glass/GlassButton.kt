package com.example.englishlearning.ui.components.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.*

/** 统一按压反馈：缩放 0.97。与 clickable(interactionSource = …) 配合使用。 */
@Composable
fun Modifier.pressableScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(AppMotion.Fast, easing = AppMotion.Easing),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

enum class PillStyle { Primary, Secondary, Text }

/**
 * 一枚药片在当前「档位 × 域 × 可用态」下的全部墨色：容器、前景、描边，以及只有品牌档才携带的渐变三端。
 *
 * `gradient != null` 时真实底是渐变（容器写 `Color.Transparent`，由 `Modifier.background(brush, shape)` 承载，
 * 因为 `Surface` 无 brush 重载）；容器为 `Color.Transparent` 且无渐变时真实底是页面。
 * 对比度判据（前景压容器／渐变每一端／页面两底）由 `PillButtonInkTest` 当场按 WCAG 相对亮度复算。
 */
data class PillInk(
    val container: Color,
    val foreground: Color,
    val border: Color?,
    val gradient: List<Color>?,
)

/**
 * 品牌渐变三端（R-14.1 ① 判定现状已合规，本席一行不改）：浅深两套同三段（spec §2.1「品牌渐变保持现状不动」），
 * 所以这一族容器-墨对是**主题无关**的，前景也不许改成 `set.onMint`（深套 `onMint` 是亮墨，压薄荷渐变会塌）。
 */
private val BrandMintGradientStops: List<Color> = listOf(
    AppleMintStart,
    AppleMintMiddle,
    Color(0xFF65D3B9),
)

/**
 * Secondary / Text 档的域色墨（R-14.1 ② + R-14.4 第二条）。
 *
 * 深套 `base == deep`，所以「浅 `deep` / 深 `base`」的分叉塌成一条统一取 `palette.deep`；唯一例外是
 * **浅套 AiSpeech**（它的 `deep` 压白只有 4.016，不达标）取 `set.onMint`。这条例外不是纸面边界：
 * 默认形参即 AiSpeech，消费面有 3 处默认 + 5 处显式真的走这两档（取证见 Task 14A-i 报告）。
 */
private fun domainInkOnLightSurface(
    set: AppColorSet,
    palette: DomainPalette,
    domain: AppDomain,
    isDark: Boolean,
): Color = if (!isDark && domain == AppDomain.AiSpeech) set.onMint else palette.deep

/**
 * 药片墨色的**纯表**（Task 14A / R-14.1 + R-14.4 的落点）：不经 Compose 运行时，
 * 由 JVM 契约 `PillButtonInkTest` 直接喂两套 [AppColorSet] + [DomainPalette] + `isDark` 复算配对合法性。
 *
 * 决策表（阈值口径同上，逐档理由写在 R-14.1／R-14.4 与用例 KDoc）：
 *  - 禁用（三档共用）：前景 `text2`；容器 Primary `tint2` / Secondary `surface` / Text 透明；描边只给 Secondary。
 *  - Primary + AiSpeech：品牌渐变三端 + 主题无关深墨 `AppPalette.OnMintPrimary`（本文件唯一保留的静态读点，
 *    归属债结转批 3 Task 26 Step 3）。
 *  - Primary 其余六域：**纯色** `palette.deep` 容器（禁止任何含 `base` 端的渐变），前景按套分叉
 *    浅套 `Color.White` / 深套 `set.surface`。
 *  - Secondary：容器 `surface`、描边 `outlineSoft`（浅套取值与旧 `AppPalette.Separator` 逐字相同，观感不变）、
 *    前景 [domainInkOnLightSurface]。
 *  - Text：容器透明、无描边、前景 [domainInkOnLightSurface]（真实底是页面）。
 */
fun pillButtonInk(
    style: PillStyle,
    domain: AppDomain,
    enabled: Boolean,
    set: AppColorSet,
    palette: DomainPalette,
    isDark: Boolean,
): PillInk = when {
    !enabled -> when (style) {
        PillStyle.Primary -> PillInk(
            container = set.tint2,
            foreground = set.text2,
            border = null,
            gradient = null,
        )

        PillStyle.Secondary -> PillInk(
            container = set.surface,
            foreground = set.text2,
            border = set.outlineSoft,
            gradient = null,
        )

        PillStyle.Text -> PillInk(
            container = Color.Transparent,
            foreground = set.text2,
            border = null,
            gradient = null,
        )
    }

    style == PillStyle.Primary && domain == AppDomain.AiSpeech -> PillInk(
        container = Color.Transparent,
        foreground = AppPalette.OnMintPrimary,
        border = null,
        gradient = BrandMintGradientStops,
    )

    style == PillStyle.Primary -> PillInk(
        container = palette.deep,
        foreground = if (isDark) set.surface else Color.White,
        border = null,
        gradient = null,
    )

    style == PillStyle.Secondary -> PillInk(
        container = set.surface,
        foreground = domainInkOnLightSurface(set, palette, domain, isDark),
        border = set.outlineSoft,
        gradient = null,
    )

    else -> PillInk(
        container = Color.Transparent,
        foreground = domainInkOnLightSurface(set, palette, domain, isDark),
        border = null,
        gradient = null,
    )
}

/**
 * 组合期薄壳：只取三样主题输入转给纯表 [pillButtonInk]，自身不做任何配色判断。
 *
 * 末位实参必须逐字写 `LocalIsDark.current`：`appDomain(domain)` 在深套返回 `base == deep` 的亮色，
 * 但「浅套白字 / 深套 knockout 墨」这组分叉靠的是 `isDark`，写成 `true`/`false` 常量都会让两套之一
 * 在组合期塌回浅套。判据不靠注释——`PillButtonInkTest.glass_button takes its ink only from the pure table`
 * 在 `GlassButton.kt` 剥注释后的源码上钉死这条转调链（含 `isDark` 不得为布尔常量的反向断言）。
 */
@Composable
@ReadOnlyComposable
fun pillButtonInk(style: PillStyle, domain: AppDomain, enabled: Boolean): PillInk =
    pillButtonInk(
        style,
        domain,
        enabled,
        appColors(),
        appDomain(domain),
        LocalIsDark.current,
    )

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Primary,
    domain: AppDomain = AppDomain.AiSpeech,
    enabled: Boolean = true,
    testTag: String? = null,
    contentDescription: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    // Task 14A（R-14.4）：形参从静态色对 `accent: DomainAccent` 迁成主题无关的 `domain: AppDomain`，
    // 于是本组件可以就地读 `appDomain(domain)` + `appColors()` + `LocalIsDark.current`——
    // 全部墨色只来自这张纯表一次取用，体内不再算第二遍色。
    val ink = pillButtonInk(style, domain, enabled)
    var m = modifier
        .pressableScale(interaction)
        .heightIn(min = 48.dp)
    if (testTag != null) m = m.testTag(testTag)
    if (contentDescription != null) m = m.semantics { this.contentDescription = contentDescription }
    // Surface 无 brush 重载的等价写法：渐变档用透明容器 + Modifier.background(brush, shape)，纯色档交回 Surface.color。
    val gradient = ink.gradient?.let { Brush.verticalGradient(it) }
    if (gradient != null) m = m.background(gradient, AppShape.Pill)
    Surface(
        shape = AppShape.Pill,
        color = ink.container,
        contentColor = ink.foreground,
        border = ink.border?.let { BorderStroke(1.dp, it) },
        // 批 2 终审修复席 A（评审 I-1）：点击必须带 role —— `Modifier.clickable` 的 role 默认 null，
        // 缺它则语义节点只有「可点按」而无「按钮」，无障碍三元组（testTag + Role + 状态）断一环；
        // 房内其余 7 处生产点击都是 clickable(role = Role.Button, …)，本枚对齐同一条口径。
        // 禁用态仍走 enabled 参数（clickable 自行维护语义可用性），不额外塞 stateDescription。
        modifier = m.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        ),
    ) {
        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(text, style = AppType.Title, color = ink.foreground, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}
