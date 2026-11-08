package com.example.englishlearning.ui

/** 转场方向（决策 §3.2；2026-11-09 修正 1）。三形就够：更深 = Forward，更浅 = Backward，同层 = Crossfade。 */
enum class ScreenTransitionDirection { Forward, Backward, Crossfade }

/**
 * 方向判据：比 route 表自带的 `transitionDepth` 列（默认 = 声明序号，覆写只出现在点名边上，
 * 清单由 `ScreenTransitionContractTest` 钉住），tab 宿主最浅（[AppOverlayRouteResolver.TAB_HOST_INDEX]）。
 * 序号（[mountIndex]）只承担挂载/返回键优先级，不再当层深用——挂载判据带 fall-through 守卫，
 * 更深的一屏常声明在更后（历史→文章、设置→学习流），只比序号会把前进播成返回（决策册「2026-11-09 修正」修正 1）。
 * 「基座 → 子页」「子页 → 更深子页」「子页 → 返回」三种腿仍共用同一条比较。
 * tab 之间按 `AppTab.ordinal`（LEARNING 0 / READING 1 / AI 2 / PROFILE 3）：序号增大 = Forward。
 * 首次组合不播由 `AnimatedContent` 自己的语义保证（只有状态变化后才启动 transition），这里不写 `initial()`。
 */
object ScreenTransitionDirectionPolicy {
    /** 挂载/返回键语义用的优先级序号（tab 宿主 = [AppOverlayRouteResolver.TAB_HOST_INDEX]）；方向判定不读它。 */
    fun mountIndex(route: AppOverlayRoute?): Int = route?.mountIndex ?: AppOverlayRouteResolver.TAB_HOST_INDEX

    /** 方向用的层深（越小越深）；tab 宿主仍是枚最大哨兵，覆写值不得顶过它（方向用例 overriddenDepthsStillLeaveTabHostShallowest 钉住）。 */
    fun transitionDepth(route: AppOverlayRoute?): Int = route?.transitionDepth ?: AppOverlayRouteResolver.TAB_HOST_INDEX

    fun overlay(from: AppOverlayRoute?, to: AppOverlayRoute?): ScreenTransitionDirection = when {
        transitionDepth(to) < transitionDepth(from) -> ScreenTransitionDirection.Forward
        transitionDepth(to) > transitionDepth(from) -> ScreenTransitionDirection.Backward
        else -> ScreenTransitionDirection.Crossfade
    }

    fun tab(from: AppTab, to: AppTab): ScreenTransitionDirection = when {
        to.ordinal > from.ordinal -> ScreenTransitionDirection.Forward
        to.ordinal < from.ordinal -> ScreenTransitionDirection.Backward
        else -> ScreenTransitionDirection.Crossfade
    }
}
