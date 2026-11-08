package com.example.englishlearning.ui

/** 转场方向（决策 §3.2）。三形就够：更深 = Forward，更浅 = Backward，同层 = Crossfade。 */
enum class ScreenTransitionDirection { Forward, Backward, Crossfade }

/**
 * 方向判据：链声明序就是层深，tab 宿主最浅（[AppOverlayRouteResolver.TAB_HOST_INDEX]）。
 * 因此「基座 → 子页」「子页 → 更深子页」「子页 → 返回」三种腿共用一条比较。
 * tab 之间按 `AppTab.ordinal`（LEARNING 0 / READING 1 / AI 2 / PROFILE 3）：序号增大 = Forward。
 * 首次组合不播由 `AnimatedContent` 自己的语义保证（只有状态变化后才启动 transition），这里不写 `initial()`。
 */
object ScreenTransitionDirectionPolicy {
    fun mountIndex(route: AppOverlayRoute?): Int = route?.mountIndex ?: AppOverlayRouteResolver.TAB_HOST_INDEX

    fun overlay(from: AppOverlayRoute?, to: AppOverlayRoute?): ScreenTransitionDirection = when {
        mountIndex(to) < mountIndex(from) -> ScreenTransitionDirection.Forward
        mountIndex(to) > mountIndex(from) -> ScreenTransitionDirection.Backward
        else -> ScreenTransitionDirection.Crossfade
    }

    fun tab(from: AppTab, to: AppTab): ScreenTransitionDirection = when {
        to.ordinal > from.ordinal -> ScreenTransitionDirection.Forward
        to.ordinal < from.ordinal -> ScreenTransitionDirection.Backward
        else -> ScreenTransitionDirection.Crossfade
    }
}
