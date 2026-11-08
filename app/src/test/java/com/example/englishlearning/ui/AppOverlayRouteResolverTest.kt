package com.example.englishlearning.ui

import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import kotlin.test.Test
import kotlin.test.assertEquals
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
            AppMountFlags(selectedSearchCard = card, showSettings = true, showAbout = true),
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
            AppOverlayRoute.ArticleReading(null, cardArticle()),
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

    @Test
    fun everyRouteVariantCarriesItsTableIndex() {
        val indices = listOf(
            AppMountFlags(selectedSearchCard = card) to 0, AppMountFlags(showGlobalVocabularySearch = true) to 1,
            AppMountFlags(selectedRelearnCard = card) to 2, AppMountFlags(selectedLearningRecordCard = card) to 3,
            AppMountFlags(selectedFavoriteCard = card) to 4, AppMountFlags(showVocabularyRelearn = true) to 5,
            AppMountFlags(showLearningRecords = true) to 6, AppMountFlags(showSetup = true) to 7,
            AppMountFlags(showLearning = true) to 8, AppMountFlags(showWorksheetPreview = true) to 9,
            AppMountFlags(showWorksheetSettings = true) to 10,
            AppMountFlags(showReviewQueue = true, todayReady = plan) to 11,
            AppMountFlags(showTodayWordList = true, todayReady = plan) to 12,
            AppMountFlags(showCheckIn = true) to 13, AppMountFlags(showWordBank = true) to 14,
            AppMountFlags(showAiProfiles = true) to 15, AppMountFlags(showSpeechSettings = true) to 16,
            AppMountFlags(showReminderSettings = true) to 17, AppMountFlags(showBackupExport = true) to 18,
            AppMountFlags(showBackupRestore = true) to 19, AppMountFlags(showBackupPairingExport = true) to 20,
            AppMountFlags(showBackupHub = true) to 21, AppMountFlags(showLicenses = true) to 22,
            AppMountFlags(showAbout = true) to 23, AppMountFlags(showArticleImport = true) to 31,
            AppMountFlags(showWordBookTransfer = true) to 33, AppMountFlags(showSettings = true) to 34,
        )
        indices.forEach { (flags, expected) ->
            assertEquals(expected, AppOverlayRouteResolver.resolve(flags)?.mountIndex, "flags → $flags")
        }
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
