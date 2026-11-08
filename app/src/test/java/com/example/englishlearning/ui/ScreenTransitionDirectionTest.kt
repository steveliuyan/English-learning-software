package com.example.englishlearning.ui

import com.example.englishlearning.learning.domain.WordCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 批 D-1 方向判据（决策 §3.2）：链声明序就是层深，不建第二张深度表。 */
class ScreenTransitionDirectionTest {
    private val settings = AppOverlayRoute.Settings
    private val about = AppOverlayRoute.About
    private val detail = AppOverlayRoute.VocabularyDetail(WordCard("c1", "cet4", "apple", "", "", ""))

    @Test
    fun baseToSubPageIsForward() = assertEquals(
        ScreenTransitionDirection.Forward,
        ScreenTransitionDirectionPolicy.overlay(from = null, to = settings),
    )

    @Test
    fun subPageBackToBaseIsBackward() = assertEquals(
        ScreenTransitionDirection.Backward,
        ScreenTransitionDirectionPolicy.overlay(from = settings, to = null),
    )

    @Test
    fun shallowerToDeeperIsForward() = assertEquals(
        ScreenTransitionDirection.Forward,
        ScreenTransitionDirectionPolicy.overlay(from = settings, to = about),
    )

    @Test
    fun deeperToShallowerIsBackward() = assertEquals(
        ScreenTransitionDirection.Backward,
        ScreenTransitionDirectionPolicy.overlay(from = about, to = settings),
    )

    @Test
    fun sameLayerSwapOnlyCrossfades() = assertEquals(
        ScreenTransitionDirection.Crossfade,
        ScreenTransitionDirectionPolicy.overlay(
            from = AppOverlayRoute.VocabularyDetail(WordCard("c1", "cet4", "apple", "", "", "")),
            to = AppOverlayRoute.VocabularyDetail(WordCard("c2", "cet4", "banana", "", "", "")),
        ),
    )

    @Test
    fun tabOrderDecidesLateralDirection() {
        assertEquals(ScreenTransitionDirection.Forward, ScreenTransitionDirectionPolicy.tab(AppTab.LEARNING, AppTab.PROFILE))
        assertEquals(ScreenTransitionDirection.Backward, ScreenTransitionDirectionPolicy.tab(AppTab.PROFILE, AppTab.LEARNING))
        assertEquals(ScreenTransitionDirection.Crossfade, ScreenTransitionDirectionPolicy.tab(AppTab.READING, AppTab.READING))
    }

    /**
     * tab 宿主哨兵（决策 §3.2）。旧写法是 `mountIndex(null) == TAB_HOST_INDEX`，而
     * `ScreenTransitionDirection.mountIndex` 的定义就是 `route?.mountIndex ?: TAB_HOST_INDEX`——
     * 常量与自身比较，`TAB_HOST_INDEX` 改成 0 或 -1 都照过，等于没牙。这里改成三条：
     * 字面量钉死序号、policy 的 null 腿走同一枚字面量、宿主必须比数值最大的那枚 route
     * （`Settings` = 34）更浅，否则进出方向判据整个反过来。
     */
    @Test
    fun tabHostIsTheShallowestLayer() {
        assertEquals(35, AppOverlayRouteResolver.TAB_HOST_INDEX)
        assertEquals(35, ScreenTransitionDirectionPolicy.mountIndex(null))
        assertTrue(
            ScreenTransitionDirectionPolicy.mountIndex(null) >
                ScreenTransitionDirectionPolicy.mountIndex(AppOverlayRoute.Settings),
            "tab 宿主必须比 34 支 route 都浅，否则进出方向判据整个反过来",
        )
    }
}
