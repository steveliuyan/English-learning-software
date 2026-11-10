package com.example.englishlearning.ui

import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 批 D-1 挂载权威（判据：`docs/decisions/2026-11-08-page-transition-routing-and-motion.md` §3.1）。
 * 三条纪律：① 判据逐字搬自链，多真取**先声明**那支；② payload 冻进 route，退场腿不回读实时状态；
 * ③ 顺序承诺是语义不是风格——QA3 A1「许可页必须排在关于页之前」在这里钉一次。
 */
class AppOverlayRouteResolverTest {
    private val card = WordCard("c1", "cet4", "apple", "", "", "")
    private val plan = TodayPlanUiState.Ready(
        wordBookName = "四级核心词", localDateLabel = "11 月 8 日",
        newTarget = 10, dueTarget = 5, totalTasks = 15,
    )

    @Test
    fun noFlagsMeansTabHost() = assertNull(AppOverlayRouteResolver.resolve(AppMountFlags()))

    @Test
    fun firstDeclaredWinsWhenSeveralAreTrue() {
        val route = AppOverlayRouteResolver.resolve(
            AppMountFlags(selectedSearchCard = card, showWordBookTransfer = true, showAbout = true),
        )
        assertEquals(AppOverlayRoute.VocabularyDetail(card), route)
    }

    @Test
    fun licensesOutranksAboutExactlyAsTheChainDoes() {
        val route = AppOverlayRouteResolver.resolve(AppMountFlags(showLicenses = true, showAbout = true))
        assertTrue(route is AppOverlayRoute.Licenses, "A1 顺序承诺被搬反：$route")
        assertEquals(22, route.mountIndex)
        assertEquals(23, AppOverlayRouteResolver.resolve(AppMountFlags(showAbout = true))!!.mountIndex)
    }

    @Test
    fun guardedBranchesFallToTheShallowerHostWhenTheGuardFails() {
        // 链原文 `showReviewQueue && todayReady != null`：ready 缺席时这一支根本不挂，
        // 必须落回浅层（null = tab 宿主），而不是挂一支没有 plan 的 route。
        val route = AppOverlayRouteResolver.resolve(AppMountFlags(showReviewQueue = true, todayReady = null))
        assertNull(route)
        val withPlan = AppOverlayRouteResolver.resolve(AppMountFlags(showReviewQueue = true, todayReady = plan))
        assertEquals(AppOverlayRoute.ReviewQueue(plan), withPlan)
    }

    @Test
    fun articleLayerOutranksHistoryAndFeedBoth() {
        // 链原文：`showReadingHistory && historyArticle == null` / `showReadingFeed && readingTarget == null
        // && historyArticle == null` / `readingTarget != null || historyArticle != null`
        assertEquals(
            AppOverlayRoute.ReadingHistory,
            AppOverlayRouteResolver.resolve(AppMountFlags(showReadingHistory = true, historyArticle = null)),
        )
        assertEquals(
            // 历史腿的 payload 只有 `readingTarget` 一枚（终评补口 第 3 条摘掉 `historyArticle`）：
            // 这一支照样要压过 ReadingHistory，挂载判据读的是 flags，不是 payload。
            AppOverlayRoute.ArticleReading(readingTarget = null),
            AppOverlayRouteResolver.resolve(AppMountFlags(showReadingHistory = true, historyArticle = cardArticle())),
        )
        assertEquals(
            AppOverlayRoute.ReadingFeed,
            AppOverlayRouteResolver.resolve(
                AppMountFlags(showReadingFeed = true, readingTarget = null, historyArticle = null),
            ),
        )
    }

    @Test
    fun payloadIsFrozenIntoTheRouteNotReadBackFromLiveState() {
        val route = AppOverlayRouteResolver.resolve(AppMountFlags(selectedRelearnCard = card))
        assertEquals(AppOverlayRoute.RelearnCard(card), route)
        assertEquals(card, (route as AppOverlayRoute.RelearnCard).card)
    }

    @Test
    fun unknownFeatureKeyFallsToTheScaffoldAndNotToTheUnavailableScreen() {
        // 链原文两支：`selectedFeature != null`（27）在前、`selectedFeatureKey != null`（28）在后。
        val known = AppMountFlags(
            selectedFeature = AiFeature.SENTENCE_ANALYSIS,
            selectedFeatureKey = AiFeature.SENTENCE_ANALYSIS.key,
        )
        assertEquals(AppOverlayRoute.SentenceAnalysis, AppOverlayRouteResolver.resolve(known))
        assertEquals(
            AppOverlayRoute.UnknownAiFeature("a-key-nobody-registered"),
            AppOverlayRouteResolver.resolve(AppMountFlags(selectedFeatureKey = "a-key-nobody-registered")),
        )
    }

    /**
     * route 表逐行断言（计划批 D 节 34 行表，行序源出 `AppOverlayRouteResolver.kt` 的 `when` 声明序）。
     * （2026-11-10 m03373 选项 1：`Settings` 支退役，表从 35 行缩到 34 行。）
     *
     * 四件事一起钉住，缺一件都不算有牙：
     * ① 行数——必须 0..33 一枚不少（表驱动把「每一支都有测试」从口头承诺变成断言）；
     * ② 行序——期望序号列本身必须逐行等于 `(0..33)`，谁插队或漏行都会让这张列先红；
     * ③ 逐行归属——每一行的 flags 除了点亮本支判据，**还点亮紧随其后的那一支**，
     *    所以「先声明先赢」是逐行验出来的，不是只在一两处验。相邻两支被对调时，
     *    前一行就会拿到后一支的 `mountIndex`，直接红（变异探针 1 就是这条）；
     *    删掉任何一支，它的 flags 会落到更浅的一支上，同样红；
     * ④ 序号——`mountIndex` 与 route 变体一一绑定，两枚 route 撞号（改序号）必有一行对不上
     *    （变异探针 2 就是这条）。
     *
     * 两处按判据本身让步，不是漏覆盖：
     * · 24/25/26 三支读的是同一枚 `selectedFeature`，取值互斥，任何一行的 flags 都无法同时点亮
     *   相邻支——它们的先后只相对兜底支 27 有意义，而这一层由「27 只能用不属于前三支的
     *   `AiFeature.CLOZE`」逐行验到（24/25/26 三支各自成立，26 之后才轮到兜底）；
     * · 29/30 的守卫要求 `historyArticle` / `readingTarget` 为空，所以它们的「下一支」只在
     *   不破坏守卫的范围内点亮（29 点亮 30 的 `showReadingFeed`，30 点亮 31 的 `showArticleImport`）。
     *
     * 32 的 payload 只有 `readingTarget` 一枚可空值（终评补口 第 3 条摘掉 `historyArticle` 后如此），
     * 表里走 `historyArticle` **判据**那一腿，`readingTarget` 腿在表下单独补一条；34 是表末，它的「下一支」是 tab 宿主，由
     * `ScreenTransitionDirectionTest.tabHostIsTheShallowestLayer` 与 `noFlagsMeansTabHost` 合起来钉。
     */
    @Test
    fun routeTableMapsEveryDeclaredBranchToItsOwnIndex() {
        val rows: List<Pair<AppMountFlags, Int>> = listOf(
            AppMountFlags(selectedSearchCard = card, showGlobalVocabularySearch = true) to 0,
            AppMountFlags(showGlobalVocabularySearch = true, selectedRelearnCard = card) to 1,
            AppMountFlags(selectedRelearnCard = card, selectedLearningRecordCard = card) to 2,
            AppMountFlags(selectedLearningRecordCard = card, selectedFavoriteCard = card) to 3,
            AppMountFlags(selectedFavoriteCard = card, showVocabularyRelearn = true) to 4,
            AppMountFlags(showVocabularyRelearn = true, showLearningRecords = true) to 5,
            AppMountFlags(showLearningRecords = true, setupRequired = true, showSetup = true) to 6,
            AppMountFlags(showSetup = true, showLearning = true) to 7,
            AppMountFlags(showLearning = true, showWorksheetPreview = true) to 8,
            AppMountFlags(showWorksheetPreview = true, showWorksheetSettings = true) to 9,
            AppMountFlags(showWorksheetSettings = true, showReviewQueue = true, todayReady = plan) to 10,
            AppMountFlags(showReviewQueue = true, showTodayWordList = true, todayReady = plan) to 11,
            AppMountFlags(showTodayWordList = true, showCheckIn = true, todayReady = plan) to 12,
            AppMountFlags(showCheckIn = true, showWordBank = true) to 13,
            AppMountFlags(showWordBank = true, showAiProfiles = true) to 14,
            AppMountFlags(showAiProfiles = true, showSpeechSettings = true) to 15,
            AppMountFlags(showSpeechSettings = true, showReminderSettings = true) to 16,
            AppMountFlags(showReminderSettings = true, showBackupExport = true) to 17,
            AppMountFlags(showBackupExport = true, showBackupRestore = true) to 18,
            AppMountFlags(showBackupRestore = true, showBackupPairingExport = true) to 19,
            AppMountFlags(showBackupPairingExport = true, showBackupHub = true) to 20,
            AppMountFlags(showBackupHub = true, showLicenses = true) to 21,
            AppMountFlags(showLicenses = true, showAbout = true) to 22,
            AppMountFlags(showAbout = true, selectedFeature = AiFeature.SENTENCE_ANALYSIS) to 23,
            AppMountFlags(selectedFeature = AiFeature.SENTENCE_ANALYSIS) to 24,
            AppMountFlags(selectedFeature = AiFeature.PHOTO_TRANSLATION) to 25,
            AppMountFlags(selectedFeature = AiFeature.IMAGE_STUDIO) to 26,
            AppMountFlags(selectedFeature = AiFeature.CLOZE, selectedFeatureKey = AiFeature.CLOZE.key) to 27,
            AppMountFlags(selectedFeatureKey = "a-key-nobody-registered", showReadingHistory = true) to 28,
            AppMountFlags(showReadingHistory = true, showReadingFeed = true, historyArticle = null) to 29,
            AppMountFlags(showReadingFeed = true, showArticleImport = true, historyArticle = null) to 30,
            AppMountFlags(showArticleImport = true, historyArticle = cardArticle()) to 31,
            AppMountFlags(historyArticle = cardArticle(), showWordBookTransfer = true) to 32,
            AppMountFlags(showWordBookTransfer = true) to 33,
        )

        assertEquals(34, rows.size, "route 表必须 34 行整（0..33），少一行就是漏支")
        assertEquals(
            (0..33).toList(),
            rows.map { (_, expected) -> expected },
            "行的期望序号必须逐行等于 resolver `when` 的声明序",
        )

        rows.forEach { (flags, expected) ->
            val route = AppOverlayRouteResolver.resolve(flags)
            assertNotNull(route, "flags → $flags 没有挂出任何 route（期望 $expected）")
            assertEquals(expected, route.mountIndex, "flags → $flags")
        }

        // 27 是兜底支，payload 必须带的是实际选中的那枚功能，不许被前三枚具名支顶掉。
        assertEquals(
            AppOverlayRoute.AiFeatureScaffold(AiFeature.CLOZE),
            AppOverlayRouteResolver.resolve(AppMountFlags(selectedFeature = AiFeature.CLOZE)),
        )

        // 32 的另一条腿：由 readingTarget 触发（`fromHistory` 判据读的就是这一枚 payload）。
        val target = ReadingTarget(cardArticle(), listOf(card))
        val articleFromTarget = AppOverlayRouteResolver.resolve(AppMountFlags(readingTarget = target))
        assertEquals(32, articleFromTarget?.mountIndex)
        assertEquals(AppOverlayRoute.ArticleReading(target), articleFromTarget)
    }

    @Test
    fun setupRequiredCountsWithoutTheUserOpeningIt() {
        assertEquals(
            AppOverlayRoute.LearningSetup,
            AppOverlayRouteResolver.resolve(AppMountFlags(setupRequired = true, showSetup = false)),
        )
    }

    /** 构造形状逐字取自 `app/src/test/java/com/example/englishlearning/reading/GenerateArticleUseCaseTest.kt:175`。 */
    private fun cardArticle(): Article = Article(
        articleId = "a1",
        profileId = "p1",
        localDate = "2026-11-08",
        activeWordBookId = "cet4",
        articleType = ArticleType.STORY,
        lengthTier = ArticleLengthTier.STANDARD,
        version = 1,
        title = "Old",
        englishText = "Some english text.",
        chineseText = "旧译文。",
        generatedAtEpochMillis = 0L,
        coveredLemmas = listOf("apple"),
        source = ArticleSource.UserImported,
    )
}
