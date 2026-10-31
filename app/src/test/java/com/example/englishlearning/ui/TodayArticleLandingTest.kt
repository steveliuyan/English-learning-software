package com.example.englishlearning.ui

import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 学习页「今日文章」的落点判据
 * （决策 `docs/decisions/2026-11-03-today-article-direct-open-and-checkin-name.md` §1–§2，
 * 收盘点表 `docs/design/2026-10-31-information-architecture-audit.md` 的 D-5）。
 *
 * 旧形状是「点行 = 切阅读 tab」，用户还得在阅读首页再点一次今日文章卡才读到文章；
 * 标签承诺的是「文章」，落点给的是「阅读首页」。本批改成**按真值判落点**：
 *
 *  - `Ready` 且带着今日文章 ⇒ 直接打开文章（覆盖层挂在当前 tab 之上，**不切 tab**，退出回学习页）；
 *  - 其余一切（`Locked` / `Loading` / `Unavailable` / `Ready` 但今天还没生成）⇒ 切阅读 tab，
 *    由阅读首页交代原因（锁定原因、生成入口）。
 *
 * 判据分两层：
 *  1. **逐态**——判定本体是 [ReadingAccessUiState] 上的纯扩展属性，五种状态各钉一枚，
 *     这样「加一个新状态时默认不许直达」由编译器逼表态（与 `historyOrEmpty` 同一口径）；
 *  2. **接线形状**——宿主只能有**一处**判定（第二份 `as? Ready` 就是第二份权威）、
 *     学习页两枚 `onOpenReading`（未学完那档 + 全完成收尾屏）都必须走这条判定，且阅读取数条件必须含学习 tab
 *     （否则停留学习 tab 时 `readingState` 恒为 `Loading`，直达永远是假话）。
 */
class TodayArticleLandingTest {

    private val host: String by lazy { stripComments(uiScreenSource("AppScreen.kt")) }

    private val preference = ReadingPreference("p1", ArticleType.STORY, ArticleLengthTier.STANDARD)

    private fun article(id: String) = Article(
        articleId = id,
        profileId = "p1",
        localDate = "2026-09-25",
        activeWordBookId = "cet4",
        articleType = ArticleType.STORY,
        lengthTier = ArticleLengthTier.STANDARD,
        version = 1,
        title = "Today",
        englishText = "apple",
        chineseText = "苹果",
        generatedAtEpochMillis = 1,
        coveredLemmas = listOf("apple"),
        source = ArticleSource.AiGenerated(modelName = "", parameterSummary = ""),
    )

    // ---------- 1. 逐态 ----------

    @Test
    fun readyStateCarryingTodaysArticleOpensTheArticleItself() {
        val state = ReadingAccessUiState.Ready(preference, history = listOf(article("a1")), todayArticle = article("today-1"))
        assertTrue(
            state.canOpenTodayArticleDirectly,
            "已解锁且今天有文章：学习页那一行点下去就该读到它，不该再多绕一次 tab",
        )
    }

    @Test
    fun readyStateWithoutTodaysArticleFallsToTheReadingHome() {
        val state = ReadingAccessUiState.Ready(preference, history = listOf(article("a1")), todayArticle = null)
        assertFalse(
            state.canOpenTodayArticleDirectly,
            "解锁了但今天还没生成文章：直达无从谈起，落阅读首页才有「生成今日文章」的出口",
        )
    }

    @Test
    fun lockedStateFallsToTheReadingHomeEvenWhenHistoryExists() {
        val state = ReadingAccessUiState.Locked("还差 1 个新词", history = listOf(article("a1")))
        assertFalse(
            state.canOpenTodayArticleDirectly,
            "锁定态带历史也不等于今日文章可读：直达只认 Ready + todayArticle，原因交给阅读首页说明",
        )
    }

    @Test
    fun loadingAndUnavailableNeverClaimADirectOpen() {
        assertFalse(ReadingAccessUiState.Loading.canOpenTodayArticleDirectly, "取数未回来就直达 = 假按钮")
        assertFalse(ReadingAccessUiState.Unavailable.canOpenTodayArticleDirectly, "读不到库时直达只会开出一篇空文章")
    }

    // ---------- 2. 接线形状 ----------

    @Test
    fun theLearningPageEntrustsTheLandingToTheSharedDecision() {
        assertEquals(
            0,
            countMatches(host, Regex(Regex.escape("onOpenReading = { selectedTab = AppTab.READING }"))),
            "学习页的阅读入口不得再是无条件切 tab：那正是 D-5 报的形状（标签承诺文章、落点是首页）",
        )
        assertEquals(
            1,
            countMatches(host, Regex(Regex.escape("onOpenReading = openTodayArticleFromLearningPage"))),
            "学习页那枚 `onOpenReading` 必须接到共用的落点 lambda（0 = 直达没接线；2 = 两处各接一份，改一处漏一处）",
        )
        assertEquals(
            1,
            countMatches(host, Regex("""val openTodayArticleFromLearningPage""")),
            "落点 lambda 在宿主恰声明一处",
        )
    }

    @Test
    fun theDecisionIsReadFromTheStateAccessorExactlyOnce() {
        assertEquals(
            1,
            countMatches(host, Regex(Regex.escape("canOpenTodayArticleDirectly"))),
            "宿主只能有一处判定：再写一份 `as? ReadingAccessUiState.Ready` 就是第二份权威，" +
                "将来加状态时谁会漏改看不出来",
        )
    }

    /**
     * 第三枚入口（决策 §1-B，用户「并回来」）。
     *
     * 收尾屏那枚「去读今日文章」原本自己写了一份「退学习流 + 无条件切 tab」。它必须改成
     * 先 `exitLearning()`、再调学习页那枚共用 lambda——**不能**再读一遍判定，
     * 否则 `canOpenTodayArticleDirectly` 在宿主出现第二处（上面那条用例当场变红）。
     *
     * 顺序是挂载链逼的，不是风格：文章覆盖层挂在 `showLearning` 分支**之后**，
     * 而挂载 `else if` 先声明者赢 ⇒ `showLearning` 还成立时直达开出的 `readingTarget` 进不了组合，
     * 用户只会看到收尾屏关掉、文章没出现。
     */
    @Test
    fun theSummaryScreenExitIsMergedIntoTheSameDecision() {
        assertEquals(
            1,
            countMatches(host, Regex(Regex.escape("onOpenReading = openTodayArticleFromSummary"))),
            "收尾屏那枚「去读今日文章」必须接到共用的落点 lambda（0 = 它还自己切 tab，正是 D-5 报的形状；" +
                "2 = 两处各接一份，改一处漏一处）",
        )
        assertEquals(
            0,
            countMatches(host, Regex("""exitLearning\(\)\s*\n\s*selectedTab = AppTab\.READING""")),
            "收尾屏不得再自己写「退学习 + 切 tab」：切 tab 只能是共用判定的那一支",
        )
        val decision = host
            .substringAfter("val openTodayArticleFromSummary")
            .substringBefore("}")
        val exitsAt = decision.indexOf("exitLearning()")
        val landingAt = decision.indexOf("openTodayArticleFromLearningPage()")
        assertTrue(
            exitsAt in 0 until landingAt,
            "顺序必须是「先 exitLearning() 再走共用判定」：先退学习流，文章覆盖层才轮得到挂载。" +
                "lambda 体实际内容：$decision",
        )
    }

    @Test
    fun theReadingStateIsLoadedWhileTheLearningTabIsShowing() {
        val window = host
            .substringAfter("LaunchedEffect(selectedTab, state.profile.id, todayReady?.isUnlocked)")
            .substringBefore("readingAccessViewModel?.load(")
        listOf(AppTab.LEARNING, AppTab.READING, AppTab.PROFILE).forEach { tab ->
            assertEquals(
                1,
                countMatches(window, Regex(Regex.escape("selectedTab == AppTab.${tab.name}"))),
                "阅读取数条件必须含 $tab：学习页的直达判定读的就是这份 `readingState`，" +
                    "停在 Loading 上就等于把直达做成永远不成立（落点永远切 tab）。窗口实际内容：$window",
            )
        }
    }
}
