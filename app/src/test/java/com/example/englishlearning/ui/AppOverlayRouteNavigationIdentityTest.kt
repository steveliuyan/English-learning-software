package com.example.englishlearning.ui

import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/**
 * 终评补口 第 2 条（Important）：route 的**导航身份列**。
 *
 * 缺陷形状（决策册「2026-11-09 修正」修正 2）：payload 里冻的是实时派生数据，而 route 是 data class，
 * 于是**每提交一次三档反馈**都造出一枚不等的 route ⇒ 站 1 的 `AnimatedContent` 把「同一屏的数据变了」
 * 读成「用户换了一屏」⇒ 起一轮新转场、该腿重新组合、屏内 `remember` 态清零、首帧效果重跑。
 *
 * 本类只验身份列本身的代数（同屏换数据 ⇒ 相等；换屏/换主键 ⇒ 不等），三层判据的另外两层在
 * `ScreenTransitionContractTest`（源码闸）与 `ScreenTransitionInstrumentedTest`（设备腿数与 `remember` 存活）。
 *
 * 身份口径（席 B2 裁定）：每一枚都带**支别前缀**（`mountIndex`），这样「同一枚主键的两支不同屏」
 * 一定不等（`VocabularyDetail(c1)` vs `RelearnCard(c1)` 是真实存在的一条边：`AppScreen.kt:834-836`），
 * 而「同一支的同一枚主键、payload 其余字段全变」一定相等。
 */
class AppOverlayRouteNavigationIdentityTest {
    private val card = WordCard("c1", "cet4", "apple", "ˈæpl", "n.", "苹果")

    private fun plan(
        planId: String? = "plan-2026-11-08",
        newDone: Int = 0,
        dueDone: Int = 0,
        activeWordBookId: String? = "cet4",
        localDateLabel: String = "11 月 8 日",
    ) = TodayPlanUiState.Ready(
        wordBookName = "四级核心词",
        localDateLabel = localDateLabel,
        newTarget = 10,
        dueTarget = 5,
        totalTasks = 15,
        newDone = newDone,
        dueDone = dueDone,
        planId = planId,
        activeWordBookId = activeWordBookId,
    )

    private fun article(articleId: String) = Article(
        articleId = articleId,
        profileId = "p1",
        localDate = "2026-11-08",
        activeWordBookId = "cet4",
        articleType = ArticleType.STORY,
        lengthTier = ArticleLengthTier.STANDARD,
        version = 1,
        title = "A title",
        englishText = "Some english text.",
        chineseText = "译文。",
        generatedAtEpochMillis = 0L,
        coveredLemmas = listOf("apple"),
        source = ArticleSource.UserImported,
    )

    // ---------------------------------------------------------------------------------------------
    // 今日计划两支：裁定点名的 ReviewQueue / TodayWordList
    // ---------------------------------------------------------------------------------------------

    /** 裁定给的那条失败用例的纯函数半边：只改 `newDone` 的一份 Ready 不得换身份。 */
    @Test
    fun reviewQueueIdentitySurvivesProgressRefresh() {
        val before = AppOverlayRoute.ReviewQueue(plan())
        val after = AppOverlayRoute.ReviewQueue(plan(newDone = 3, dueDone = 2))
        assertEquals(
            before.navigationIdentity,
            after.navigationIdentity,
            "只改 newDone/dueDone 的同一份计划被当成换了一屏：这正是「一次数据刷新起一轮转场」的根因。",
        )
    }

    @Test
    fun reviewQueueIdentityDistinguishesDifferentPlans() {
        assertNotEquals(
            AppOverlayRoute.ReviewQueue(plan(planId = "plan-a")).navigationIdentity,
            AppOverlayRoute.ReviewQueue(plan(planId = "plan-b")).navigationIdentity,
            "两份不同计划的复习队列共用一个身份：跨天/跨词书重建计划时那一屏不会重新挂载。",
        )
    }

    @Test
    fun todayWordListIdentitySurvivesProgressRefreshAndDistinguishesPlans() {
        assertEquals(
            AppOverlayRoute.TodayWordList(plan()).navigationIdentity,
            AppOverlayRoute.TodayWordList(plan(newDone = 7, dueDone = 5)).navigationIdentity,
            "「今日词单」同样冻了 Ready，进度刷新不该起转场。",
        )
        assertNotEquals(
            AppOverlayRoute.TodayWordList(plan(planId = "plan-a")).navigationIdentity,
            AppOverlayRoute.TodayWordList(plan(planId = "plan-b")).navigationIdentity,
            "换计划就是换一屏，词单的身份必须跟着变。",
        )
    }

    /**
     * `Ready.planId` 是可空字段（`TodayPlanViewModel.kt:72`）⇒ 身份必须有非空兜底，
     * 否则 `null to null` 会把两份不同词书/不同天的计划并成同一枚身份。
     */
    @Test
    fun planIdentityFallsBackToWordBookAndDateWhenPlanIdIsMissing() {
        assertEquals(
            AppOverlayRoute.ReviewQueue(plan(planId = null)).navigationIdentity,
            AppOverlayRoute.ReviewQueue(plan(planId = null, newDone = 4)).navigationIdentity,
            "planId 缺席时兜底身份必须只看词书 + 日期标签，不看进度。",
        )
        assertNotEquals(
            AppOverlayRoute.ReviewQueue(plan(planId = null, activeWordBookId = "cet4")).navigationIdentity,
            AppOverlayRoute.ReviewQueue(plan(planId = null, activeWordBookId = "kaoyan")).navigationIdentity,
            "兜底把活动词书丢了：换册后的今日屏不会重新挂载。",
        )
        assertNotEquals(
            AppOverlayRoute.ReviewQueue(plan(planId = null, localDateLabel = "11 月 8 日")).navigationIdentity,
            AppOverlayRoute.ReviewQueue(plan(planId = null, localDateLabel = "11 月 9 日")).navigationIdentity,
            "兜底把日期丢了：跨天的今日屏不会重新挂载。",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 四枚词卡支：卡面（lemma/ipa/释义/例句）是易变字段，cardId 才是身份
    // ---------------------------------------------------------------------------------------------

    @Test
    fun cardBranchIdentitiesSurviveCardFaceRefresh() {
        val refreshed = card.copy(lemma = "apple-refreshed", ipa = "", meaningZh = "另一条释义")
        listOf<Pair<AppOverlayRoute, AppOverlayRoute>>(
            AppOverlayRoute.VocabularyDetail(card) to AppOverlayRoute.VocabularyDetail(refreshed),
            AppOverlayRoute.RelearnCard(card) to AppOverlayRoute.RelearnCard(refreshed),
            AppOverlayRoute.LearningRecordDetail(card) to AppOverlayRoute.LearningRecordDetail(refreshed),
            AppOverlayRoute.FavoriteDetail(card) to AppOverlayRoute.FavoriteDetail(refreshed),
        ).forEach { (before, after) ->
            assertEquals(
                before.navigationIdentity,
                after.navigationIdentity,
                "${before::class.simpleName} 的身份跟着卡面变了：同一枚词的释义刷新会重播一次转场。",
            )
        }
    }

    @Test
    fun cardBranchIdentitiesDistinguishCards() {
        val other = WordCard("c2", "cet4", "banana", "", "", "香蕉")
        listOf<AppOverlayRoute>(
            AppOverlayRoute.VocabularyDetail(card),
            AppOverlayRoute.RelearnCard(card),
            AppOverlayRoute.LearningRecordDetail(card),
            AppOverlayRoute.FavoriteDetail(card),
        ).forEach { route ->
            val sibling = when (route) {
                is AppOverlayRoute.VocabularyDetail -> AppOverlayRoute.VocabularyDetail(other)
                is AppOverlayRoute.RelearnCard -> AppOverlayRoute.RelearnCard(other)
                is AppOverlayRoute.LearningRecordDetail -> AppOverlayRoute.LearningRecordDetail(other)
                else -> AppOverlayRoute.FavoriteDetail(other)
            }
            assertNotEquals(
                route.navigationIdentity,
                sibling.navigationIdentity,
                "${route::class.simpleName} 换一张卡却没有换身份：那一屏不会重新挂载。",
            )
        }
    }

    /**
     * 真实存在的一条边：词卡详情里点「重新学习」把同一枚 card 交给 `selectedRelearnCard`（`AppScreen.kt:834-836`），
     * 两支的 cardId 相同、屏却不同 ⇒ 身份必须带支别，否则这一跳不起转场。
     */
    @Test
    fun sameCardAcrossDifferentBranchesIsStillANewScreen() {
        assertNotEquals(
            AppOverlayRoute.VocabularyDetail(card).navigationIdentity,
            AppOverlayRoute.RelearnCard(card).navigationIdentity,
            "身份没有支别：同一张卡从详情跳到重学页会被读成「还是那一屏」。",
        )
        assertNotEquals(
            AppOverlayRoute.LearningRecordDetail(card).navigationIdentity,
            AppOverlayRoute.FavoriteDetail(card).navigationIdentity,
            "同上：四枚词卡支共用 cardId 就必须靠支别区分。",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // AI 两支 + 文章一支
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aiFeatureScaffoldIdentityTracksTheFeatureEnum() {
        assertEquals(
            AppOverlayRoute.AiFeatureScaffold(AiFeature.CLOZE).navigationIdentity,
            AppOverlayRoute.AiFeatureScaffold(AiFeature.CLOZE).navigationIdentity,
            "同一枚功能的兜底脚手架两次解析必须同身份（枚举实例本身就是稳定身份，裁定 §二）。",
        )
        assertNotEquals(
            AppOverlayRoute.AiFeatureScaffold(AiFeature.CLOZE).navigationIdentity,
            AppOverlayRoute.AiFeatureScaffold(AiFeature.WORD_PASSAGE).navigationIdentity,
            "换一枚 AI 功能却没有换身份。",
        )
        assertNotEquals(
            AppOverlayRoute.AiFeatureScaffold(AiFeature.CLOZE).navigationIdentity,
            AppOverlayRoute.SentenceAnalysis.navigationIdentity,
            "脚手架支与三枚具名功能支的身份不能相同：它们各自是一屏。",
        )
    }

    @Test
    fun unknownAiFeatureIdentityIsItsKey() {
        assertEquals(
            AppOverlayRoute.UnknownAiFeature("a-key").navigationIdentity,
            AppOverlayRoute.UnknownAiFeature("a-key").navigationIdentity,
            "同一枚未知 key 的兜底页两次解析必须同身份。",
        )
        assertNotEquals(
            AppOverlayRoute.UnknownAiFeature("a-key").navigationIdentity,
            AppOverlayRoute.UnknownAiFeature("b-key").navigationIdentity,
            "换一个未知 key 却没有换身份。",
        )
    }

    @Test
    fun articleReadingIdentityIsTheArticleId() {
        val target = ReadingTarget(article("a1"), listOf(card))
        assertEquals(
            AppOverlayRoute.ArticleReading(target).navigationIdentity,
            AppOverlayRoute.ArticleReading(target.copy(cards = emptyList())).navigationIdentity,
            "同一篇文章换一份词卡列表不算换屏：cards 是易变字段。",
        )
        assertNotEquals(
            AppOverlayRoute.ArticleReading(target).navigationIdentity,
            AppOverlayRoute.ArticleReading(ReadingTarget(article("a2"), listOf(card))).navigationIdentity,
            "换一篇文章却没有换身份。",
        )
    }

    /** 历史腿（`readingTarget == null`）没有可携带的主键（`historyArticle` 已按第 3 条摘除），落到支别哨兵身份。 */
    @Test
    fun nullArticleReadingLegSharesOneIdentityAndStaysDistinctFromOtherBranches() {
        assertEquals(
            AppOverlayRoute.ArticleReading(null).navigationIdentity,
            AppOverlayRoute.ArticleReading(null).navigationIdentity,
            "历史腿的身份必须自反：它进出一律经过 ReadingHistory 列表（AppScreen.kt:1872 是唯一发起点）。",
        )
        assertNotEquals(
            AppOverlayRoute.ArticleReading(null).navigationIdentity,
            AppOverlayRoute.ArticleReading(ReadingTarget(article("a1"), emptyList())).navigationIdentity,
            "历史腿与选读腿并成同一身份：从历史文章切到选读文章不会起转场。",
        )
        assertNotEquals(
            AppOverlayRoute.ArticleReading(null).navigationIdentity,
            AppOverlayRoute.ReadingHistory.navigationIdentity,
            "文章层与历史列表层的身份不能相同。",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 26 枚 data object：默认访问器 `this` 就是它们的身份（不许覆写，覆写只会长出第二套口径）
    // ---------------------------------------------------------------------------------------------

    @Test
    fun dataObjectBranchesUseTheSingletonAsIdentity() {
        listOf<AppOverlayRoute>(
            AppOverlayRoute.Settings,
            AppOverlayRoute.About,
            AppOverlayRoute.LearningFlow,
            AppOverlayRoute.LearningSetup,
            AppOverlayRoute.ReadingHistory,
            AppOverlayRoute.WordBookTransfer,
        ).forEach { route ->
            assertSame(route, route.navigationIdentity, "${route::class.simpleName} 是单例，身份就该是它自己")
        }
        assertNotEquals(AppOverlayRoute.Settings.navigationIdentity, AppOverlayRoute.About.navigationIdentity)
    }

    /**
     * 缺陷的本体：data class 支的身份**不许**是 route 实例自己（`equals` 意义上的实例）。
     * 只要还等于实例，payload 任一字段一变就是一枚新身份 ⇒ 一次数据刷新起一轮转场。
     */
    @Test
    fun dataClassBranchesDoNotUseTheRouteInstanceAsIdentity() {
        listOf<AppOverlayRoute>(
            AppOverlayRoute.VocabularyDetail(card),
            AppOverlayRoute.RelearnCard(card),
            AppOverlayRoute.LearningRecordDetail(card),
            AppOverlayRoute.FavoriteDetail(card),
            AppOverlayRoute.ReviewQueue(plan()),
            AppOverlayRoute.TodayWordList(plan()),
            AppOverlayRoute.AiFeatureScaffold(AiFeature.CLOZE),
            AppOverlayRoute.UnknownAiFeature("a-key"),
            AppOverlayRoute.ArticleReading(ReadingTarget(article("a1"), listOf(card))),
        ).forEach { route ->
            assertNotEquals(
                route as Any,
                route.navigationIdentity,
                "${route::class.simpleName} 的身份仍是 route 实例自己：payload 每变一次就被 `AnimatedContent` 读成换了一屏。",
            )
        }
    }
}
