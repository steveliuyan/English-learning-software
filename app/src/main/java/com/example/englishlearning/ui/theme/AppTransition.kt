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
 *
 * **依赖方向与退路记录（批 D 冲突扫描 #11 的裁定，2026-11-09 终评补口第 7 条第 4 枚落成注释）**：
 * 文件头那枚 `import com.example.englishlearning.ui.ScreenTransitionDirection`（`:14`）是 `ui.theme → ui` 的**反向 import**，
 * 本批裁定维持不动——规格表留在 `ui/theme`（与 `AppMotion` token 同处，批 A 的规格唯一落点承诺），
 * 方向枚举留在 `ui`（它是 route 表的读侧，跟着 `AppOverlayRoute`/`ScreenTransitionDirectionPolicy` 走）。
 * 这一行 import 是该裁定的**唯一代价**，不是漏网的分层违规。
 * 若将来把 `AppTransition` 挪出 `ui/theme`（或把枚举挪进 theme），必须同步改两处，否则契约闸会静默读空：
 *  `ScreenTransitionContractTest.APP_TRANSITION_SOURCE_PATH`（`:1210`，源码闸读文件的相对路径常量）与判据
 *  `transitionSpecSurfaceLivesOnlyInAppTransition`（`:445`，「转场规格面只住这一份文件」的宿主白名单）。
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

    /**
     * 同层互换只做淡入淡出，不假装判得出方向。
     * 2026-11-09 终评补口第 2 条后的口径：这一组规格**只服务「身份不同」的同支互换**——
     * 站 1 的 `AnimatedContent` 按 `AppOverlayRoute.navigationIdentity` 取键，所以「同支换 payload」里
     * **身份相同**的那一类（同一张卡提交三档反馈、同一份计划刷新进度）**根本不起转场**，就地更新、不进本组规格；
     * 起本组淡入淡出的是身份真的换了，如同支换到另一张卡（词卡详情点「下一个」，`cardId` 变 ⇒ 身份变）。
     */
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
