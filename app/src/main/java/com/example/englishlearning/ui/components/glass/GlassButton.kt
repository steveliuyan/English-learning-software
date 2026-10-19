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

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Primary,
    accent: DomainAccent = DomainColors.AiSpeech,
    enabled: Boolean = true,
    testTag: String? = null,
    contentDescription: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    var m = modifier
        .pressableScale(interaction)
        .heightIn(min = 48.dp)
    if (testTag != null) m = m.testTag(testTag)
    if (contentDescription != null) m = m.semantics { this.contentDescription = contentDescription }
    // 品牌 CTA 以浅薄荷到柔和青绿渐变搭配深青字；其他域色维持原语义。
    val usesBrandGradient = accent == DomainColors.AiSpeech
    // Task 12.5 修复轮 I-1：药片族整族回退基线，交回批 2 Task 14 的按钮族裁决。
    // 只迁容器（Secondary 填充 / 描边 / 禁用底）而留下 `accent.deep` 与 `AppPalette.TextSecondary` 两枚静态墨，
    // 会在深色档造出 2.6–3.0:1 的前景与消失的描边，而 `PillButton` 的下游是 7+ 个本轮没走查的屏。
    // 形参 `accent: DomainAccent` 是静态色对、不是 `AppDomain`，组件内无法就地改读 `appDomain(...)`，
    // 所以配对必须连着签名一起改（R-14.1），本轮不动。
    val contentColor = when {
        !enabled -> AppPalette.TextSecondary
        style == PillStyle.Primary && usesBrandGradient -> AppPalette.OnMintPrimary
        style == PillStyle.Primary -> Color.White
        else -> accent.deep
    }
    val border = if (style == PillStyle.Secondary) BorderStroke(1.dp, AppPalette.Separator) else null
    val containerColor = when (style) {
        // Surface 无 brush 重载的等价写法：透明容器 + Modifier.background(brush, shape)
        PillStyle.Primary -> Color.Transparent
        PillStyle.Secondary -> AppPalette.Surface
        PillStyle.Text -> Color.Transparent
    }
    if (style == PillStyle.Primary) {
        m = if (enabled) {
            val gradient = if (usesBrandGradient) {
                Brush.verticalGradient(listOf(AppleMintStart, AppleMintMiddle, Color(0xFF65D3B9)))
            } else {
                Brush.verticalGradient(listOf(accent.base, accent.deep))
            }
            m.background(gradient, AppShape.Pill)
        } else {
            m.background(AppPalette.Separator, AppShape.Pill)
        }
    }
    Surface(
        shape = AppShape.Pill,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = m.clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(text, style = AppType.Title, color = contentColor, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}
