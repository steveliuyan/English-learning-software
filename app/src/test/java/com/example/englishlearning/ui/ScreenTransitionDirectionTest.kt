package com.example.englishlearning.ui

import com.example.englishlearning.learning.domain.WordCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 D-1 方向判据（决策 §3.2；2026-11-09 修正 1 改列）。
 * 链声明序只是**优先级**（z 序 + 返回键先后）；方向比对 route 表自带的 `transitionDepth` 列，
 * 默认等于序号、只在点名边上覆写——覆写清单 == 本文件命名边用例清单，由 `ScreenTransitionContractTest` 钉住。
 */
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

    /**
     * 终评补口 第 1 条（Critical）的四枚命名边：挂载判据带 fall-through 守卫的臂里，
     * 更深的一屏声明在更后（`AppOverlayRouteResolver.kt:25-26`、`:47-50`），只比序号会把前进播成返回。
     * 方向改比 `AppOverlayRoute` 的 `transitionDepth` 列后，这四条边必须判 Forward；
     * 用例注释即发起点行号证据（照决策册「2026-11-09 修正」证据 :1865 的做法逐对找齐）。
     * 本组用例的 to 支集合与覆写清单相等由 `ScreenTransitionContractTest` 钉死。
     * 方向判据只读层深列、不读 payload，故 ArticleReading 取 `readingTarget = null` 的单参实例即可。
     */
    @Test
    fun namedEdgeReadingHistoryToArticleReadingIsForward() = assertEquals(
        ScreenTransitionDirection.Forward,
        ScreenTransitionDirectionPolicy.overlay(
            from = AppOverlayRoute.ReadingHistory,
            to = AppOverlayRoute.ArticleReading(readingTarget = null),
        ),
    ) // 发起点 AppScreen.kt:1872 `onOpenArticle = { historyArticle = it }`（showReadingHistory 仍真，29→32）

    @Test
    fun namedEdgeArticleReadingToReadingHistoryIsBackward() = assertEquals(
        ScreenTransitionDirection.Backward,
        ScreenTransitionDirectionPolicy.overlay(
            from = AppOverlayRoute.ArticleReading(readingTarget = null),
            to = AppOverlayRoute.ReadingHistory,
        ),
    ) // 反向可走：AppScreen.kt:1921 `fromHistory` 腿清 historyArticle，route 32→29 落回历史臂

    @Test
    fun namedEdgeReadingFeedToArticleReadingIsForward() = assertEquals(
        ScreenTransitionDirection.Forward,
        ScreenTransitionDirectionPolicy.overlay(
            from = AppOverlayRoute.ReadingFeed,
            to = AppOverlayRoute.ArticleReading(readingTarget = null),
        ),
    ) // 发起点 AppScreen.kt:1903 `onFetchFeedItem` → ReadingAccessViewModel.kt:243 置 readingTarget（feed 臂守卫 `readingTarget == null` 让位，30→32）

    @Test
    fun namedEdgeArticleReadingToReadingFeedIsBackward() = assertEquals(
        ScreenTransitionDirection.Backward,
        ScreenTransitionDirectionPolicy.overlay(
            from = AppOverlayRoute.ArticleReading(readingTarget = null),
            to = AppOverlayRoute.ReadingFeed,
        ),
    ) // 反向可走：ReadingAccessViewModel.kt:253 `closeArticle()` 清 readingTarget，route 32→30 落回 feed 臂

    @Test
    fun namedEdgeArticleImportToArticleReadingIsForward() = assertEquals(
        ScreenTransitionDirection.Forward,
        ScreenTransitionDirectionPolicy.overlay(
            from = AppOverlayRoute.ArticleImport,
            to = AppOverlayRoute.ArticleReading(readingTarget = null),
        ),
    ) // 发起点 ReadingAccessViewModel.kt:201 `submitImport` 成功置 readingTarget + AppScreen.kt:529 让位清 showArticleImport，route 31→32

    @Test
    fun namedEdgeLearningSetupToLearningFlowIsForward() = assertEquals(
        ScreenTransitionDirection.Forward,
        ScreenTransitionDirectionPolicy.overlay(
            from = AppOverlayRoute.LearningSetup,
            to = AppOverlayRoute.LearningFlow,
        ),
    ) // 设置臂守卫 `setupRequired || showSetup`（resolver :25）压在学习臂（:26）之前；AppScreen.kt:1082-1085 onSaved 落位后 7→8

    @Test
    fun namedEdgeLearningFlowToLearningSetupIsBackward() = assertEquals(
        ScreenTransitionDirection.Backward,
        ScreenTransitionDirectionPolicy.overlay(
            from = AppOverlayRoute.LearningFlow,
            to = AppOverlayRoute.LearningSetup,
        ),
    ) // 裁定 (a) 的反向：计划翻回 MissingSetup（AppScreen.kt:398）时设置层再压上来，8→7 按新列判 Backward

    /**
     * 裁定 (b)：覆写值不许把 tab 宿主顶到比自己浅的位置——两支覆写腿进出宿主仍
     * 前进/返回各一（宿主哨兵 = TAB_HOST_INDEX 35 必须比所有 transitionDepth 都浅）。
     */
    @Test
    fun overriddenDepthsStillLeaveTabHostShallowest() {
        val article = AppOverlayRoute.ArticleReading(readingTarget = null)
        assertEquals(ScreenTransitionDirection.Forward, ScreenTransitionDirectionPolicy.overlay(from = null, to = article))
        assertEquals(ScreenTransitionDirection.Backward, ScreenTransitionDirectionPolicy.overlay(from = article, to = null))
        assertEquals(ScreenTransitionDirection.Forward, ScreenTransitionDirectionPolicy.overlay(from = null, to = AppOverlayRoute.LearningFlow))
        assertEquals(ScreenTransitionDirection.Backward, ScreenTransitionDirectionPolicy.overlay(from = AppOverlayRoute.LearningFlow, to = null))
    }

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
