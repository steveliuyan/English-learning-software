package com.example.englishlearning.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.example.englishlearning.ui.ScreenTransitionDirection

/**
 * 页面转场规格表：全仓**唯一**的转场时长/缓动/位移落点（批 D-3，决策 §3.3）。
 *
 * 值全部取 `AppMotion` token，页面腿 `Slow` 300 + 入 `EasingOut` / 退 `EasingIn`；
 * 位移取 **¼ 幅不是整幅**：300ms 走完整屏会读成「甩」，¼ 幅保住方向感又不让人等（Material 轴向惯例 ⅛–¼）。
 * 腿本身 `fillMaxSize`，所以 `slideInHorizontally { it / 4 }` 的 `it` 就是全屏宽；
 * 本文件不用 `slideIntoContainer`——它按整容器宽度位移，与 ¼ 幅口径不符。
 *
 * 对话框（`DialogEnter` / `DialogExit`）的可见形态逐字保持 `GlassOverlay` 今天的样子（淡入 + 92% 缩放、无位移）：
 * 浮层不是页面，给它加位移会误导层级。**两站之外不留 per-screen 逃生口**：
 * 不提供「某屏自己传 spec」的参数，逐屏自定义就是长出第二份规格。
 */
object AppTransition {
    private const val PageOffsetDenominator = 4

    val PageForwardEnter = fadeIn(tween(AppMotion.Slow, easing = AppMotion.EasingOut)) +
        slideInHorizontally(tween(AppMotion.Slow, easing = AppMotion.EasingOut)) { it / PageOffsetDenominator }
    val PageForwardExit = fadeOut(tween(AppMotion.Slow, easing = AppMotion.EasingIn)) +
        slideOutHorizontally(tween(AppMotion.Slow, easing = AppMotion.EasingIn)) { -it / PageOffsetDenominator }
    val PageBackwardEnter = fadeIn(tween(AppMotion.Slow, easing = AppMotion.EasingOut)) +
        slideInHorizontally(tween(AppMotion.Slow, easing = AppMotion.EasingOut)) { -it / PageOffsetDenominator }
    val PageBackwardExit = fadeOut(tween(AppMotion.Slow, easing = AppMotion.EasingIn)) +
        slideOutHorizontally(tween(AppMotion.Slow, easing = AppMotion.EasingIn)) { it / PageOffsetDenominator }

    /** 同层互换（同支换 payload，如词卡详情点「下一个」）只做淡入淡出，不假装判得出方向。 */
    val CrossfadeEnter = fadeIn(tween(AppMotion.Slow, easing = AppMotion.EasingOut))
    val CrossfadeExit = fadeOut(tween(AppMotion.Slow, easing = AppMotion.EasingIn))

    val DialogEnter = fadeIn(tween(AppMotion.Normal, easing = AppMotion.EasingOut)) +
        scaleIn(tween(AppMotion.Normal, easing = AppMotion.EasingOut), initialScale = 0.92f)
    val DialogExit = fadeOut(tween(AppMotion.Normal, easing = AppMotion.EasingIn)) +
        scaleOut(tween(AppMotion.Normal, easing = AppMotion.EasingIn), targetScale = 0.92f)

    /**
     * 两站共用同一张表：tab 站的「序号增大 = 新页从右进」与子页站的「更深 = 新页从右进」
     * 是同一条口径（决策 §3.3 里 tab 行与 page 行取值逐字相同），所以不复制第二套规格。
     */
    fun contentTransform(direction: ScreenTransitionDirection): ContentTransform = when (direction) {
        ScreenTransitionDirection.Forward -> PageForwardEnter togetherWith PageForwardExit
        ScreenTransitionDirection.Backward -> PageBackwardEnter togetherWith PageBackwardExit
        ScreenTransitionDirection.Crossfade -> CrossfadeEnter togetherWith CrossfadeExit
    }
}
