package com.example.englishlearning.ui

import com.example.englishlearning.learning.domain.WordCard
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun tabHostIsTheShallowestLayer() = assertEquals(
        AppOverlayRouteResolver.TAB_HOST_INDEX,
        ScreenTransitionDirectionPolicy.mountIndex(null),
    )
}
