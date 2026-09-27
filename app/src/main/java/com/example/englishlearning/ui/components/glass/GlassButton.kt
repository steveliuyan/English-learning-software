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
    // Primary 用垂直渐变（base→deep）增加品牌质感；Secondary/Text 保持纯色。
    val contentColor = when (style) {
        PillStyle.Primary -> Color.White
        PillStyle.Secondary -> accent.deep
        PillStyle.Text -> accent.deep
    }
    val border = if (style == PillStyle.Secondary) BorderStroke(1.dp, AppPalette.Separator) else null
    val containerColor = when (style) {
        // Surface 无 brush 重载的等价写法：透明容器 + Modifier.background(brush, shape)
        PillStyle.Primary -> Color.Transparent
        PillStyle.Secondary -> AppPalette.Surface
        PillStyle.Text -> Color.Transparent
    }
    if (style == PillStyle.Primary) {
        m = m.background(
            Brush.verticalGradient(colors = listOf(accent.base, accent.deep)),
            shape = AppShape.Pill,
        )
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
