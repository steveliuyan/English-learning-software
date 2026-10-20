package com.example.englishlearning.ui.components.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppMotion
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.appColors

/** active 时对下层页面内容做真模糊（API 31+；以下版本 Modifier.blur 自动无效）。 */
fun Modifier.frosted(active: Boolean): Modifier = if (active) this.blur(8.dp) else this

/**
 * 页内玻璃浮层（下拉菜单替代品）：全屏 scrim + 居中玻璃面板，点击 scrim 关闭。
 * `modifier` 应用于全屏 scrim（不用于浮层面板本体）。
 * visible=false 时不组合任何内容。
 *
 * 批 2 全分支终审修复席 B（task-18fix-b，与席 A 的 `GlassDialog` 同一条裁决）：
 *  - **容器底随主题换套**：原来读静态浅套 `AppPalette.GlassFill`（`0xEFFFFFFF`），现读
 *    `appColors().surfaceGlass`——浅套取值与之逐字相同 ⇒ 浅色侧像素零变化，深色侧才拿到那套深玻璃。
 *  - **面板内前景必须同批**：本浮层唯一消费者是 `ui/AiProfileSettingsScreen.kt` 的音色浮层，
 *    其选项行 `VoiceOverlayOption` 的墨随本批一起迁回按套取值（容器—前景同源，判据见
 *    `GlassPanelInkPairingContractTest`；否则深色档就是「深玻璃压浅套深墨」的当场不可读）。
 *  - `AppPalette.Scrim` 与描边 `AppPalette.GlassHighlight` 是**主题无关的 alpha 蒙版**（两套同值，
 *    见 `DarkTokenConsumptionRatchetTest` 的 `ThemeInvariantMaskMembers`），按台账口径保持静态读点不动。
 */
@Composable
fun BoxScope.GlassOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(AppMotion.Normal, easing = AppMotion.Easing)) +
            scaleIn(tween(AppMotion.Normal, easing = AppMotion.Easing), initialScale = 0.92f),
        exit = fadeOut(tween(AppMotion.Normal, easing = AppMotion.Easing)) +
            scaleOut(tween(AppMotion.Normal, easing = AppMotion.Easing), targetScale = 0.92f),
        modifier = Modifier.align(Alignment.Center),
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(AppPalette.Scrim)
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = AppShape.Dialog,
                color = appColors().surfaceGlass,
                border = BorderStroke(1.dp, AppPalette.GlassHighlight),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                    ) { /* 吃掉点击，不传给 scrim */ },
            ) {
                Column(Modifier.padding(vertical = 8.dp), content = content)
            }
        }
    }
}
