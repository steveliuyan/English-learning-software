package com.example.englishlearning.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.ai.AiPreferenceRepository
import com.example.englishlearning.ai.DefaultImageProfileResult
import com.example.englishlearning.ai.DefaultImageProfileResolver
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.ai.net.AudioHttpRequest
import com.example.englishlearning.ai.net.AudioHttpTransport
import com.example.englishlearning.imagegen.DrawingPromptUseCase
import com.example.englishlearning.imagegen.GeneratedImageStore
import com.example.englishlearning.imagegen.ImageGenerationUseCase
import com.example.englishlearning.learning.FavoriteWord
import com.example.englishlearning.learning.FavoriteWordRepository
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.LearningHistoryGroup
import com.example.englishlearning.learning.LearningRecord
import com.example.englishlearning.learning.LearningRecordRepository
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.learning.PlaceholderWordCardSource
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.reading.ArticleIdFactory
import com.example.englishlearning.reading.ArticleRepository
import com.example.englishlearning.reading.FetchArticleUseCase
import com.example.englishlearning.reading.GenerateArticleUseCase
import com.example.englishlearning.reading.ImportArticleUseCase
import com.example.englishlearning.reading.ReadingCompletionRepository
import com.example.englishlearning.reading.ReadingPreferenceRepository
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
import com.example.englishlearning.ai.AiProfileIdFactory
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiPreference
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.language.SpeechPreferenceRepository
import com.example.englishlearning.language.domain.PronunciationCapability
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.language.domain.PronunciationProvider
import com.example.englishlearning.language.domain.PronunciationResult
import com.example.englishlearning.language.domain.SpeechPreference
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.LearningProfile
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.SubmitCardFeedbackUseCase
import com.example.englishlearning.learning.VocabularyEntry
import com.example.englishlearning.learning.VocabularyRepository
import com.example.englishlearning.learning.WrongWord
import com.example.englishlearning.learning.WrongWordsRepository
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.LearningSettings
import com.example.englishlearning.learning.LearningSettingsRepository
import com.example.englishlearning.learning.LearningSettingsRepositoryResult
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.InMemoryLocalProfileRepository
import com.example.englishlearning.wordqa.WordAiNote
import com.example.englishlearning.wordqa.WordAiNoteRepository
import com.example.englishlearning.wordqa.WordQaUseCase
import com.example.englishlearning.sentence.SentenceAnalysisUseCase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun profileCreationControlsHaveSemanticLabelsAndShowReady() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        composeRule.setContent { AppScreen(viewModel = vm, learningSetupViewModel = setupViewModel(), todayPlanViewModel = todayPlanViewModel(), wordCardViewModel = wordCardFixtureViewModel()) }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选好词书，开始今天的积累").assertExists()
        composeRule.onNodeWithContentDescription("保存学习设置").assertExists()
    }

    @Test
    fun errorScreenRendersSafeStableTextOnly() {
        composeRule.setContent { ErrorScreen(onRetry = {}) }
        composeRule.onNodeWithText("无法创建资料，请检查姓名").assertExists()
        composeRule.onNodeWithText("Exception").assertDoesNotExist()
        composeRule.onNodeWithText("/secret").assertDoesNotExist()
    }

    @Test
    fun errorScreenOffersReturnActionSoUserIsNotStuck() {
        var returned = false
        composeRule.setContent { ErrorScreen(onRetry = { returned = true }) }
        composeRule.onNodeWithContentDescription("返回重新填写").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(true, returned)
    }

    @Test
    fun blankNameDisablesCreateAndDoesNotReloadTodayPlan() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        var invocations = 0
        val todayPlan = TodayPlanViewModel(
            { invocations++; TodayPlanResult.MissingLearningSetup },
            FakeLearningProfileRepository(),
        )
        composeRule.setContent { AppScreen(viewModel = vm, learningSetupViewModel = setupViewModel(), todayPlanViewModel = todayPlan, wordCardViewModel = wordCardFixtureViewModel()) }
        composeRule.onNodeWithContentDescription("创建资料").assertExists().assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("请输入名字提示").assertExists()
        assertEquals(0, invocations)
    }

    @Test
    fun savingSetupReloadsTodayPlan() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        var invocations = 0
        val todayPlan = TodayPlanViewModel(
            { invocations++; TodayPlanResult.MissingLearningSetup },
            FakeLearningProfileRepository(),
        )
        composeRule.setContent { AppScreen(viewModel = vm, learningSetupViewModel = setupViewModel(), todayPlanViewModel = todayPlan, wordCardViewModel = wordCardFixtureViewModel()) }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("测试词书").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("保存学习设置").assertExists().performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { invocations == 2 }
        assertEquals(2, invocations)
    }

    /**
     * 用户要求「在应用底下加个 AI 学」。这里断言四栏都真的出现在应用外壳里，而不只是
     * 组件级测试通过——底导没被接进 AppScreen 是很容易发生的回归。
     */
    @Test
    fun readyPlanShowsTheFourSlotBottomNavigation() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        AppTab.entries.forEach { tab ->
            composeRule.onNodeWithTag("app_tab_${tab.name.lowercase()}").assertExists()
        }
        composeRule.onNodeWithContentDescription("AI 学").assertExists()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
    }

    @Test
    fun wordBookReturnsToTheLetterAndScrollPositionUsedToOpenWordDetail() {
        val wordsViewModel = wordBookWordsViewModelWithAlphabetThroughW()
        composeRule.setContent {
            readyAppScreen(
                todayPlanViewModel = TodayPlanViewModel({ readyPlanResult() }, FakeLearningProfileRepository()),
                wordBookWordsViewModel = wordsViewModel,
                learningRecordsViewModel = learningRecordsViewModelWithAbilityRecord(),
            )
        }
        createProfile()
        composeRule.onNodeWithTag("today_plan_open_word_bank").performScrollTo().performClick()
        composeRule.waitForIdle()
        // 批 1 Task 5 起词书单词列表是词库页的第一段（默认落点），词库壳先在场。
        composeRule.onNodeWithTag("word_bank_screen").assertExists()
        composeRule.onNodeWithTag("wordbook_words_screen").assertExists()
        composeRule.onNodeWithTag("wordbook_words_row_aword").assertExists()
        scrollToRailLetter("W")
        composeRule.onNodeWithTag("wordbook_words_rail_W").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_rail_active_W").assertExists()
        composeRule.onNodeWithTag("wordbook_words_row_wonder").performClick()
        composeRule.onNodeWithTag("card_detail_screen").assertExists()
        composeRule.onNodeWithText("wonder").assertExists()

        // Task 16：详情私有页头已归一到共享 AppScreenHeader——`card_detail_back` 这枚屏侧 tag
        // 随私有件一起删除，返回位只由组件的「返回」朗读定语承载（全页唯一节点）。
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("wordbook_words_screen").assertExists()
        composeRule.onNodeWithTag("wordbook_words_rail_active_W").assertExists()
        composeRule.onNodeWithTag("wordbook_words_row_wonder").assertExists()
        composeRule.onNodeWithTag("wordbook_words_row_aword").assertDoesNotExist()
    }

    @Test
    fun wordBookNewBrowsingSessionStartsAtAAfterLeavingW() {
        val wordsViewModel = wordBookWordsViewModelWithAlphabetThroughW()
        composeRule.setContent {
            readyAppScreen(
                todayPlanViewModel = TodayPlanViewModel({ readyPlanResult() }, FakeLearningProfileRepository()),
                wordBookWordsViewModel = wordsViewModel,
            )
        }
        createProfile()
        composeRule.onNodeWithTag("today_plan_open_word_bank").performScrollTo().performClick()
        scrollToRailLetter("W")
        composeRule.onNodeWithTag("wordbook_words_rail_W").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_rail_active_W").assertExists()
        // Task 5 后词书段没有自己的返回位：退页只由词库页页头那一枚承载。
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithTag("today_plan_open_word_bank").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_rail_active_A").assertExists()
        composeRule.onNodeWithTag("wordbook_words_row_aword").assertExists()
    }

    @Test
    fun wordBookSystemBackRetainsTheNonzeroScrollOffsetWithinW() {
        val wordsViewModel = wordBookWordsViewModelWithAlphabetThroughW(wRows = 12)
        composeRule.setContent {
            readyAppScreen(
                todayPlanViewModel = TodayPlanViewModel({ readyPlanResult() }, FakeLearningProfileRepository()),
                wordBookWordsViewModel = wordsViewModel,
            )
        }
        createProfile()
        composeRule.onNodeWithTag("today_plan_open_word_bank").performScrollTo().performClick()
        scrollToRailLetter("W")
        composeRule.onNodeWithTag("wordbook_words_rail_W").performClick()
        composeRule.waitForIdle()
        val row = composeRule.onNodeWithTag("wordbook_words_row_wonder")
        val topAtHeader = row.fetchSemanticsNode().boundsInRoot.top
        composeRule.onNode(hasScrollToIndexAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, 47f)
        }
        composeRule.waitForIdle()
        val topBeforeDetail = row.fetchSemanticsNode().boundsInRoot.top
        assertEquals("夹具必须实际产生非零滚动偏移", 47f, topAtHeader - topBeforeDetail, 1f)
        row.performClick()
        composeRule.onNodeWithTag("card_detail_screen").assertExists()
        pressSystemBack()
        composeRule.onNodeWithTag("wordbook_words_screen").assertExists()
        assertEquals(
            "系统返回必须保留 W 组内原有像素偏移",
            topBeforeDetail,
            row.fetchSemanticsNode().boundsInRoot.top,
            1f,
        )
        composeRule.onNodeWithTag("wordbook_words_rail_active_W").assertExists()
    }

    @Test
    fun wordBankEntryOpensTheFourSegmentPageAndSystemBackReturnsToTheTodayPlan() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("today_plan_open_word_bank").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_bank_screen").assertExists()
        WordBankSection.entries.forEach { cell ->
            composeRule.onNodeWithTag(cell.tag).assertExists()
        }
        pressSystemBack()
        composeRule.onNodeWithTag("word_bank_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
    }

    /**
     * I-3（修复轮 1，评审选项 ①）：钉「我的词」的三条件前置 BackHandler——三段共用宿主详情
     * （生词本 → `selectedSearchCard`），详情开着时返回键必须**先关详情、再退合并页**，
     * 一按就把整页推掉是这条 finding 点名的未证行为。VM 全 null 时三段停在 Loading，
     * 所以这一枚必须喂一枚真有词条的 `VocabularyViewModel`（宿主在 LEARNING 页会自动 load）。
     */
    @Test
    fun wordBankSystemBackClosesAnOpenWordDetailBeforeLeavingTheBank() {
        composeRule.setContent {
            readyAppScreen(vocabularyViewModel = vocabularyViewModelWithOneEntry())
        }
        createProfile()
        composeRule.onNodeWithTag("today_plan_open_word_bank").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_bank_screen").assertExists()
        // 合并页的三段现在各是一格：生词本段要从第二格切过去。
        composeRule.onNodeWithTag("word_bank_segment_new").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("my_words_screen").assertExists()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("my_words_wordbook_row_0").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("my_words_wordbook_row_0").performScrollTo().performClick()
        composeRule.onNodeWithTag("card_detail_screen").assertExists()

        pressSystemBack()
        composeRule.onNodeWithTag("card_detail_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("my_words_screen").assertExists()

        pressSystemBack()
        composeRule.onNodeWithTag("my_words_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("word_bank_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
    }

    /**
     * Task 22-A §二.4 / plan R2-1 的**第六枚判据**（端到端，经宿主）：`AppScreen` 必须真的把
     * `vocabularyMasteryState` 与 `clearMasteryMessage` 接进「我的词」合并页。
     *
     * 为什么必须新立一枚：既有五枚判据钉的是**屏侧**行为与**静态挂点**——`MyWordsScreenTest` 自己传
     * 复位 lambda、不经宿主，而静态锚 `MyWordsScreenContractTest.the_merged_page_consumes_and_resets_the_mastery_state`
     * 只读源码形状。老路由（`showVocabulary` / `VocabularyScreen`）摘掉后，合并页是这条反馈面在全站的
     * **唯一出口**：宿主少接一环时静默失败的代价不再是「少一个框」，而是**用户以为已保存**。
     *
     * 形状（一个测试方法只一次 `setContent`）：走宿主打开「我的词」→ 生词本段逐行「标记为已掌握」→
     * 共享二次确认框确认 → 断 `Success` 结果对话框可见 → 点「知道了」后断它真的消失且不再残留。
     * 夹具用 `vocabularyViewModelWithOneEntry()`（`NoopLearningEventRepository` 使 `markMastered`
     * 落到 `SubmitFeedbackResult.Recorded` ⇒ `VocabularyMasteryState.Success`），所以这枚判据穿过的是
     * 真 ViewModel、真状态流，不是屏侧注入的假回调。
     *
     * 反向红凭据（本席留档 `t22A-RED-06-connected-AppScreenTest-hostmastery-wiring.log`）：把宿主
     * `MyWordsScreen(...)` 的 `masteryState = vocabularyMasteryState` 换成 `VocabularyMasteryState.Idle`
     * （不动本用例任何被断言字面量的间接路径），本用例即在「结果对话框可见」那一步当场红。
     */
    @Test
    fun mergedPageMasteryResultDialogIsDrivenByTheHostAndClosesOnAck() {
        composeRule.setContent {
            readyAppScreen(vocabularyViewModel = vocabularyViewModelWithOneEntry())
        }
        createProfile()
        composeRule.onNodeWithTag("today_plan_open_word_bank").performScrollTo().performClick()
        composeRule.waitForIdle()
        // 词库页默认落词书段；生词本段（原来「我的词」直达的那一段）在第二格。
        composeRule.onNodeWithTag("word_bank_segment_new").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("my_words_screen").assertExists()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("my_words_wordbook_row_0").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("my_words_wordbook_mastered_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_destructive_confirm").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("my_words_mastery_result_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("my_words_mastery_result_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("已标记为掌握").assertIsDisplayed()

        composeRule.onNodeWithTag("my_words_mastery_result_dismiss").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("my_words_mastery_result_dialog").assertDoesNotExist()
        composeRule.onNodeWithText("已标记为掌握").assertDoesNotExist()
        composeRule.onNodeWithTag("my_words_screen").assertExists()
    }

    /**
     * Task 22-B §四.1 RED-3「能力不截断」的**宿主端到端**判据：摘打卡页两条链、摘
     * `showFavorites` / `showWrongWords` 两条老路由、把收藏页与错词页下线之前，必须先证明合并页
     * `MyWordsScreen` 的三段真能经宿主走到旧两屏独有的三枚能力——
     *  ① 收藏详情：点收藏段词行 → `onOpenFavoriteWord` → `favoritesViewModel.open()` 回源 →
     *     宿主 `selectedFavoriteCard` → `card_detail_screen`；
     *  ② 取消收藏：点收藏段行内 PillButton → `onUnfavorite` → `favoritesViewModel.toggle()` 落仓储 →
     *     列表回刷后该行真的消失（并落回收藏段空态）；
     *  ③ 错词重学入口：点错词段词行 → `onOpenWrongWord` → 宿主 `selectedLearningRecordCard` →
     *     `card_detail_screen` 的「重新学习」（旧错词页点词进的就是这枚只读详情）。
     *
     * 为什么必须新立一枚（§一「先证能力承接，再动任何一行」的铁律）：
     *  - `MyWordsScreenTest.rows_and_unfavorite_invoke_the_host_callbacks` 与
     *    `wordbook_offers_relearn_and_mastered_behind_confirmation` 证的是**屏侧**——它们自己传回调，
     *    宿主那一环（`favoritesViewModel?.open(it)` 等直达 lambda）不在观察面里；
     *  - 既有宿主用例只覆盖生词本段与掌握结果框，收藏段与错词段的宿主通路一直**未证**。
     * 老路由摘掉后合并页是这三族词表能力的唯一出口，少接一环时用户看到的是「点了没反应」，
     * 所以这里喂**真** ViewModel（`favoritesViewModelWithOneWord()` / `wrongWordsViewModelWithOneWord()`），
     * 不走屏侧注入的假回调。
     *
     * 形状约束：一个测试方法只一次 `setContent`（skill §1.2）；详情层与合并页在同一条 `else if` 链上互斥，
     * 所以每次退详情后都要重新 `performScrollTo()`。
     */
    @Test
    fun mergedPageSectionsReachFavoriteDetailUnfavoriteAndWrongWordRelearnThroughTheHost() {
        composeRule.setContent {
            readyAppScreen(
                favoritesViewModel = favoritesViewModelWithOneWord(),
                wrongWordsViewModel = wrongWordsViewModelWithOneWord(),
            )
        }
        createProfile()
        composeRule.onNodeWithTag("today_plan_open_word_bank").performScrollTo().performClick()
        composeRule.waitForIdle()
        // 批 1 Task 5 起收藏 / 错词各是一格：三段不再同屏，验证顺序改为「切到哪段看哪段」。
        composeRule.onNodeWithTag("word_bank_segment_favorite").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("my_words_screen").assertExists()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("my_words_favorite_row_0").fetchSemanticsNodes().isNotEmpty()
        }

        // ① 收藏详情经宿主：详情层的星标 PillButton 描述由 `favoriteIds`（宿主从同一份收藏状态派生）决定，
        // 断它读到「取消收藏 ability」同时钉住了「回源词卡对了」与「宿主收藏集合接上了」。
        composeRule.onNodeWithTag("my_words_favorite_row_0").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("取消收藏 ability").assertIsDisplayed()
        pressSystemBack()
        composeRule.onNodeWithTag("card_detail_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("my_words_screen").assertExists()

        // ② 取消收藏经宿主：真仓储落库 + 真 reload，所以断的是「那一行消失、段退回空态」而不是「回调被调过一次」。
        composeRule.onNodeWithTag("my_words_favorite_unfavorite_0").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("my_words_favorites_empty").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("my_words_favorite_row_0").assertDoesNotExist()

        // ③ 错词重学入口经宿主：切到错词段后，错词行的 onClick 走 `selectedLearningRecordCard` 那条只读详情，
        // 「重新学习」PillButton 由 `onRelearn` 是否为 null 决定，正是旧错词页独有的那枚出口。
        composeRule.onNodeWithTag("word_bank_segment_wrong").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("my_words_wrong_row_0").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("my_words_wrong_row_0").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("重新学习 achieve").assertIsDisplayed()
        pressSystemBack()
        composeRule.onNodeWithTag("card_detail_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("my_words_screen").assertExists()
    }

    @Test
    fun checkInEntryOpensOverlayAndBackReturnsToTodayPlan() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("today_plan_open_check_in")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("check_in_screen").assertExists()
        // Task 22-A §二.1：打卡页页头改用共享 AppScreenHeader 后，返回位没有 testTag（组件返回位只带
        // contentDescription "返回"），故这里由 tag 选取换成描述选取——语义仍是「点返回位、回调把叠层关掉」，未放宽。
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
        composeRule.onNodeWithTag("check_in_screen").assertDoesNotExist()
        AppTab.entries.forEach { tab ->
            composeRule.onNodeWithTag("app_tab_${tab.name.lowercase()}").assertExists()
        }
    }

    @Test
    fun aiTabOpensTheAiLearningScreen() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("app_tab_ai").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_learning_screen").assertExists()
        composeRule.onNodeWithTag("ai_learning_header").assertExists()
        // 裁决④（Task 23）之后：已上线的三枚默认可见，折叠键只管「未上线」那批，
        // 所以这里点开折叠行才凑得齐全部七枚入口（旧标签 ai_learning_features_toggle 已随形状改名）。
        composeRule.onNodeWithTag("ai_learning_unreleased_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        AiFeature.entries.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}").assertExists()
        }
        // 换 tab 之后学习页应当已经让位，不能被压在下面继续占位。
        composeRule.onNodeWithTag("today_plan_summary").assertDoesNotExist()
    }

    @Test
    fun settingsTabOpensTheProfilePageAndKeepsTheWorksheetEntry() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.waitForIdle()
        // 批 1 Task 6：「我的」这个 tab 现在的根是 `ProfileScreen`（档案头 + 三格统计 + 学习记录），
        // 原设置四组九行原样接在它尾部（`SettingsGroupsContent`）。所以这里同时断两件事——
        // 新根在、旧根不在——否则「ProfileScreen 套在 SettingsScreen 里」这种双滚动壳也能过。
        composeRule.onNodeWithTag("profile_screen").assertExists()
        composeRule.onNodeWithTag("settings_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_open_worksheet").performScrollTo().assertExists().assertHasClickAction()
    }

    @Test
    fun speechStatusUsesLiveSpeechKeyStateAndShowsCurrentProvider() {
        val fixture = speechFixture()
        composeRule.setContent { readyAppScreen(fixture.speech, fixture.ai) }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo()
        composeRule.onNodeWithText("当前供应商：小米 MiMo").assertExists()
        // 新层级：一级设置页不再出现引擎状态行与「待接入」厂商占位行。
        composeRule.onNodeWithTag("settings_speech_openai").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_speech_mimo").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_speech_zipvoice").assertDoesNotExist()
        composeRule.onNodeWithText("当前供应商 · 已配置").assertDoesNotExist()
        composeRule.onNodeWithText("待接入").assertDoesNotExist()
    }

    /**
     * M6（Task 9 必须项）：语音设置页的语速行**不是静默失效**。
     *
     * 为什么断在 AppScreen 这一层而不是只断 `SpeechSettingsScreen`：
     * `SpeechSettingsScreen` 与 `AppScreen` 两侧的 `onCycleSpeechRate` 都带默认空 lambda
     * （`AppScreen.kt` 里那一行 `onCycleSpeechRate = { speechSettingsViewModel?.cycleSpeechRate() }`
     * 被整条删掉，屏级用例照旧全绿，用户点语速却永远不变）。本用例走真实宿主，
     * 断言因此落在这条接线上：删掉它本用例必须红（Task 9 报告的删行探针即以此取证）。
     *
     * 「恰好一次点按前进一档」钉的是宿主接线：`AppScreen` 把 `onCycleSpeechRate` 接到
     * `viewModel::cycleSpeechRate`，且每次一点只前进一步（持久化本身由
     * `SpeechSettingsViewModelTest` 覆盖——本 fixture 的偏好仓储 `save` 是空实现，不能在这里
     * 声称「值来自持久化偏好」）。若宿主或屏幕重复派发，会直接跳到 1.5x。
     * 四档循环口径 {0.75x, 1.0x, 1.25x, 1.5x} 见决策 2026-10-15 §2。
     */
    @Test
    fun speechRateRowOnTheHostedSpeechPageCyclesOneStepPerPress() {
        val fixture = speechFixture()
        composeRule.setContent { readyAppScreen(fixture.speech, fixture.ai) }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_settings_screen").assertExists()

        // 未合并树：当前倍速文本在可点击 Row 内会被语义合并（与 speech_auto_speak_state 同一口径）。
        // 滚动只作用在可点击 Row 上（未合并子节点不带滚动语义）。
        composeRule.onNodeWithTag("speech_rate_toggle").performScrollTo()
        composeRule.onNodeWithTag("speech_rate_value", useUnmergedTree = true).assertTextEquals("1x")
        composeRule.onNodeWithTag("speech_rate_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("speech_rate_value", useUnmergedTree = true).assertTextEquals("1.25x")
    }

    @Test
    fun aiProfilesOpenedFromSpeechReturnToSpeechWithRefreshedCandidates() {
        val fixture = speechFixture()
        fixture.keys.clear()
        composeRule.setContent { readyAppScreen(fixture.speech, fixture.ai) }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_settings_screen").assertExists()
        // 候选配置现在是折叠二级菜单：先展开再操作。
        // v32 语速行让本页变高，折叠按钮会落在视口外：performClick 前必须 performScrollTo，
        // 否则点击命中别处、展开态不变（与本页其它 scroll+click 用例同一写法）。
        composeRule.onNodeWithTag("speech_toggle_candidates").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_open_ai_profiles").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai_profiles_screen").assertExists()
        fixture.keys += "mimo"
        composeRule.onNodeWithContentDescription("返回上一层").performClick()
        composeRule.onNodeWithTag("speech_settings_screen").assertExists()
        composeRule.onNodeWithTag("speech_toggle_candidates").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_profile_mimo").performScrollTo()
        composeRule.onNodeWithText("可用").assertExists()
        composeRule.onNodeWithTag("speech_bound_profile_mimo", useUnmergedTree = true).assertExists()
        pressSystemBack()
        composeRule.onNodeWithTag("profile_screen").assertExists()
    }

    @Test
    fun returningFromAiProfilesOpenedDirectlyFromSettingsRefreshesSpeechSummary() {
        val fixture = speechFixture()
        composeRule.setContent { readyAppScreen(fixture.speech, fixture.ai) }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo()
        composeRule.onNodeWithText("当前供应商：小米 MiMo").assertExists()
        composeRule.onNodeWithTag("settings_open_ai_profiles").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai_profiles_screen").assertExists()
        fixture.keys.clear()
        pressSystemBack()
        composeRule.onNodeWithTag("profile_screen").assertExists()
        // 一级页不再有引擎状态行；刷新语义下沉到语音二级页——直接进入 AI Profile 改完 Key
        // 返回后再进语音设置，状态必须是新鲜的。
        composeRule.onNodeWithTag("settings_speech_openai").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_settings_screen").assertExists()
        // 引擎行详情是「配置名 · 密钥状态」格式；Key 被删后必须立刻反映。
        composeRule.onNodeWithTag("speech_engine_status_mimo", useUnmergedTree = true).assertTextEquals("测试语音 · 缺少密钥")
    }

    @Test
    fun returningFromAiProfilesRefreshesRemovedKeyWithoutDroppingSpeechLayer() {
        val fixture = speechFixture()
        composeRule.setContent { readyAppScreen(fixture.speech, fixture.ai) }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_toggle_candidates").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_profile_mimo").performScrollTo()
        composeRule.onNodeWithText("可用").assertExists()
        composeRule.onNodeWithTag("speech_open_ai_profiles").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai_profiles_screen").assertExists()
        fixture.keys.clear()
        pressSystemBack()
        composeRule.onNodeWithTag("speech_settings_screen").assertExists()
        composeRule.onNodeWithTag("speech_toggle_candidates").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_profile_mimo").performScrollTo()
        composeRule.onNodeWithTag("speech_engine_status_mimo", useUnmergedTree = true).assertTextEquals("测试语音 · 缺少密钥")
        composeRule.onNodeWithTag("speech_bound_profile_mimo", useUnmergedTree = true).assertExists()
        pressSystemBack()
        composeRule.onNodeWithTag("profile_screen").assertExists()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo()
        composeRule.onNodeWithText("当前供应商：小米 MiMo").assertExists()
    }

    @Test
    fun readingTabOpensTheReadingScreenWithoutItsOwnBackButton() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reading_access_screen").assertExists()
        // 一级 tab 没有「上一层」可回，页面上不该出现任何返回入口。
        composeRule.onNodeWithTag("reading_access_back").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("返回上一层").assertDoesNotExist()
    }

    @Test
    fun openingAnAiFeatureHidesTheBottomBarAndBackReturnsToTheAiTab() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("app_tab_ai").performClick()
        composeRule.waitForIdle()
        // 裁决④ 后已上线三行默认可见、折叠键只管「未上线」那批；cloze 属未上线批，
        // 这一枚点开折叠行是**必要动作**，不是旧「整页默认收起」形状的残留。
        composeRule.onNodeWithTag("ai_learning_unreleased_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_feature_cloze").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_feature_screen").assertExists()
        composeRule.onNodeWithTag("ai_feature_title").assertExists()
        composeRule.onNodeWithTag("app_tab_ai").assertDoesNotExist()

        pressSystemBack()
        composeRule.onNodeWithTag("ai_learning_screen").assertExists()
        composeRule.onNodeWithTag("app_tab_ai").assertExists()
    }

    @Test
    fun systemBackFromANonLearningTabReturnsToTheLearningTab() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_screen").assertExists()

        pressSystemBack()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
    }

    @Test
    fun startLearningStillHidesTheBottomBarSoTheCardGetsTheWholeScreen() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithContentDescription("开始学习").assertExists().performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_card_screen").assertExists()
        composeRule.onNodeWithTag("app_tab_learning").assertDoesNotExist()
    }

    @Test
    fun recordingProviderKeepsLegacyWordAndSentenceVerbsDistinct() = kotlinx.coroutines.runBlocking {
        val provider = RecordingPronunciationProvider(PronunciationResult.Played)

        provider.speak("legacy")
        provider.speakWord("ability", com.example.englishlearning.language.domain.PronunciationAccent.American)
        provider.speakSentence("Birds fly.", com.example.englishlearning.language.domain.PronunciationAccent.British)

        assertEquals(listOf("legacy"), provider.legacyCalls)
        assertEquals(listOf(Triple("ability", com.example.englishlearning.language.domain.PronunciationAccent.American, false)), provider.wordCalls)
        assertEquals(listOf(Triple("Birds fly.", com.example.englishlearning.language.domain.PronunciationAccent.British, false)), provider.sentenceCalls)
    }

    /** D1 新契约：词卡就绪自动朗读 1 次，用户点击再 1 次，两者共存互不取消。 */
    @Test
    fun learningCardPronunciationRoutesTheVisibleLemmaToTheProvider() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Played)
        composeRule.setContent { readyAppScreen(pronunciationProvider = provider) }
        createProfile()

        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.isNotEmpty() }
        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.size == 2 }

        assertEquals(listOf("ability", "ability"), provider.spokenTexts)
        composeRule.onNodeWithTag("word_card_pronunciation_message").assertExists()
        composeRule.onNodeWithText("发音已播放。").assertExists()
    }

    /** D1：自动发音同样走安全文案——失败在词卡就绪时就显示，不泄露 endpoint/密钥。 */
    @Test
    fun learningCardPronunciationFailureShowsSafeMessageAndKeepsFeedbackActions() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Failed(IllegalStateException("secret endpoint")))
        composeRule.setContent { readyAppScreen(pronunciationProvider = provider) }
        createProfile()

        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("发音播放失败，请重试。").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.size == 2 }

        composeRule.onNodeWithText("发音播放失败，请重试。").assertExists()
        composeRule.onNodeWithText("secret endpoint").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("不认识").assertHasClickAction()
        composeRule.onNodeWithContentDescription("模糊").assertHasClickAction()
        composeRule.onNodeWithContentDescription("认识").assertHasClickAction()
    }

    /** D1：词卡就绪自动 1 次 + 详情美式/英式各点击 1 次，全部是同一个 lemma；详情页自身不自动发音。 */
    @Test
    fun submittedCardDetailPronunciationRoutesTheHeldLemmaToTheProvider() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Played)
        composeRule.setContent { readyAppScreen(pronunciationProvider = provider) }
        createProfile()

        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.isNotEmpty() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_feedback_fuzzy").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_feedback_fuzzy").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertDetailLemma("ability")
        detailSpeakSlot("card_detail_speak", "美").performClick()
        detailSpeakSlot("card_detail_speak_british", "英").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.size == 3 }

        assertEquals(listOf("ability", "ability", "ability"), provider.spokenTexts)
        assertEquals(
            listOf(
                com.example.englishlearning.language.domain.PronunciationAccent.American,
                com.example.englishlearning.language.domain.PronunciationAccent.American,
                com.example.englishlearning.language.domain.PronunciationAccent.British,
            ),
            provider.wordCalls.map { it.second },
        )
        composeRule.onNodeWithText("发音已播放。").assertExists()
    }

    /** D1：开关关闭后词卡就绪不自动发音，点击仍恰好触发一次。 */
    @Test
    fun learningAutoSpeakHonoursTheOffToggleAndClickStillSpeaksOnce() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Played)
        val speech = speechFixture(autoSpeakOnCardReady = false)
        composeRule.setContent {
            readyAppScreen(pronunciationProvider = provider, speechSettingsViewModel = speech.speech)
        }
        createProfile()

        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_speak").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        assertEquals(emptyList<String>(), provider.spokenTexts)

        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.size == 1 }
        assertEquals(listOf("ability"), provider.spokenTexts)
        composeRule.onNodeWithText("发音已播放。").assertExists()
    }

    @Test
    fun learningRecordDetailPronunciationRoutesTheHeldLemmaToTheProvider() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Played)
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                learningRecordsViewModel = learningRecordsViewModelWithAbilityRecord(),
            )
        }
        createProfile()

        composeRule.onNodeWithContentDescription("学习记录").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("learning_record_row_ability").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("learning_record_row_ability").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertDetailLemma("ability")
        detailSpeakSlot("card_detail_speak", "美").performClick()
        detailSpeakSlot("card_detail_speak_british", "英").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.size == 2 }

        assertEquals(listOf("ability", "ability"), provider.spokenTexts)
        assertEquals(
            listOf(
                com.example.englishlearning.language.domain.PronunciationAccent.American,
                com.example.englishlearning.language.domain.PronunciationAccent.British,
            ),
            provider.wordCalls.map { it.second },
        )
        composeRule.onNodeWithText("发音已播放。").assertExists()
    }

    @Test
    fun readingHighlightDetailPronunciationRoutesSelectedLemmaToProvider() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Failed(IllegalStateException("private endpoint")))
        val reading = readingFixture()
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
            )
        }
        createProfile()
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_open_today").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reading_open_today").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("article_english").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("coverage_popup_close").performClick()
        composeRule.onNodeWithTag("article_english").performClick()
        composeRule.onNodeWithTag("card_detail_screen").assertExists()
        assertDetailLemma("apple")
        detailSpeakSlot("card_detail_speak", "美").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("发音播放失败，请重试。").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf("apple"), provider.spokenTexts)
        composeRule.onNodeWithText("private endpoint", substring = true).assertDoesNotExist()
    }

    // ------------------------------------------------------ Task 9 修复轮 1：发音清场与失败保持（I-1/I-2/I-3/I-5/I-6）

    /**
     * I-1：退出学习流（exitLearning）必须清零词卡发音状态。
     * 复现路径：点喇叭播一个挂起音 → 返回今日计划 → 再点「开始学习」。
     * 旧形状里 exitLearning 只清 message 不清 status（仍 Playing），新卡会立刻长出「暂停/停止」行、
     * 但文案节点不出现（message 已 null）——读屏用户听到的是上一张卡的状态语义。
     * 备注：Ready 首卡没有「返回今日计划」按钮（WordCardScreen 只在 NoCards/ReviewCompleted/AllDone
     * 给该按钮），退出走系统返回 → BackHandler(showLearning) → 同一个 exitLearning，路径等价。
     */
    @Test
    fun leavingAndReenteringLearningClearsTheWordCardPronunciationState() {
        val provider = HangingPronunciationProvider()
        val speech = speechFixture(autoSpeakOnCardReady = false)
        composeRule.setContent {
            readyAppScreen(pronunciationProvider = provider, speechSettingsViewModel = speech.speech)
        }
        createProfile()
        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_speak").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_pronunciation_stop").fetchSemanticsNodes().isNotEmpty()
        }

        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("today_plan_summary").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_speak").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_pronunciation_stop").assertDoesNotExist()
        composeRule.onNodeWithText("正在播放发音…").assertDoesNotExist()
        provider.release()
    }

    /**
     * Task 15 Step 1（F5-03 移交账「退出学习不停播」在此关闭）：朗读仍在进行时退出学习流，
     * 停播必须打到 provider 上**恰好一次**，且退出后屏上不残留任何语播控件。
     *
     * 与 I-1（`leavingAndReenteringLearningClearsTheWordCardPronunciationState`）的分工：I-1 只清**呈现状态**
     * （status/message 归 Idle/null），旧形状下退出后界面是干净的、声音却还在放——那正是移交账点名的缺陷。
     * 本用例断播放器层真收到停播：`HangingPronunciationProvider.stopCalls` 是计数假播放器，
     * 「停播逻辑存在但永不触发」那一族变异（写进永假分支、挪去别处、只清状态不碰播放器）都会在这里红；
     * 屏侧的形状与每轮组合次数由 `WordCardScreenTest.leavingTheScreenForwardsTheStopActionExactlyOnce` 负责。
     *
     * 全程走真实导航与真实宿主：自动发音关掉 → 点药片 → provider 挂起在发声里（呈现态钉在 Playing）→
     * 系统返回（`BackHandler(enabled = showLearning)` → `exitLearning` → `showLearning = false` →
     * 词卡屏离开组合 → 退出停播守卫转调宿主的 onStopPronunciation → 播放器收到停播）。
     */
    @Test
    fun leavingLearningStopsTheStillPlayingAudioAtTheProvider() {
        val provider = HangingPronunciationProvider()
        val speech = speechFixture(autoSpeakOnCardReady = false)
        composeRule.setContent { readyAppScreen(pronunciationProvider = provider, speechSettingsViewModel = speech.speech) }
        createProfile()
        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_speak").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_pronunciation_stop").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("退出之前不该有任何停播，否则本用例断的是别处的停播", 0, provider.stopCalls)

        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("today_plan_summary").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals("退出学习流必须让仍在播的音停下来，且恰好一次", 1, provider.stopCalls)
        // F5-03「退出后无语播焦点」：三枚语播控件与药片朗读文字都不许残留。
        composeRule.onNodeWithTag("word_card_speak").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_pronunciation_stop").assertDoesNotExist()
        composeRule.onNodeWithText("播放发音").assertDoesNotExist()
        provider.release()
    }

    /**
     * I-2：阅读页三元组（message/status/request）在**任何**退出路径都必须清场。
     * 三条路径逐一验证：①屏内「返回」按钮（onBack）；②系统返回退今日文章
     * （BackHandler readingTarget → closeArticle）；③系统返回退历史旧版（BackHandler historyArticle）。
     * 旧形状里三条全不清：重进文章（或另一篇）立刻长出上一次的播控行与文案，且代次不自增还挡不住
     * 迟到的 Played。每轮清场后重进都断三个 tag 不存在。
     */
    @Test
    fun leavingTheArticleThroughEveryExitPathClearsItsPronunciationPresentation() {
        val provider = HangingPronunciationProvider()
        val reading = readingFixture()
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
            )
        }
        createProfile()
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_open_today").fetchSemanticsNodes().isNotEmpty()
        }

        // 轮 1：屏内「返回」按钮退出。
        openTodayArticle()
        speakArticleAndAwaitControls()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_access_screen").fetchSemanticsNodes().isNotEmpty()
        }
        openTodayArticle()
        assertNoArticlePronunciationNodes()

        // 轮 2：系统返回键退今日文章（BackHandler :493 closeArticle 分支）。
        speakArticleAndAwaitControls()
        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_access_screen").fetchSemanticsNodes().isNotEmpty()
        }
        openTodayArticle()
        assertNoArticlePronunciationNodes()

        // 轮 3：先退出仍开着的文章，再走历史旧版（historyArticle）用系统返回退出
        // （BackHandler :489 分支）。
        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_access_screen").fetchSemanticsNodes().isNotEmpty()
        }
        // 批 1 Task 7：Ready 态的历史入口搬进「更多」组（`reading_more_history`）；
        // 锁定态那枚仍是 `reading_history_entry`（锁定屏只有一条说明 + 历史入口，不需要分组）。
        composeRule.onNodeWithTag("reading_more_history").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_history_item_a1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reading_history_item_a1").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("article_english").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("coverage_popup_close").performClick()
        speakArticleAndAwaitControls()
        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reading_history_item_a1").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("article_english").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("coverage_popup_close").performClick()
        assertNoArticlePronunciationNodes()
        provider.release()
    }

    /** 今日文章入口：开文章并关掉进文覆盖词弹窗（弹窗每次进组合都会重弹，见 rememberSaveable 口径）。 */
    private fun openTodayArticle() {
        composeRule.onNodeWithTag("reading_open_today").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("article_english").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("coverage_popup_close").performClick()
    }

    /** 点「发音」朗读整篇（挂起 provider 钉住 Playing），直到瞬时消息节点出现。 */
    private fun speakArticleAndAwaitControls() {
        composeRule.onNodeWithTag("article_pronunciation").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("article_pronunciation_message").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoArticlePronunciationNodes() {
        composeRule.onNodeWithTag("article_pronunciation_message").assertDoesNotExist()
        composeRule.onNodeWithTag("article_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("article_pronunciation_stop").assertDoesNotExist()
    }

    /**
     * 详情页发音槽的取样面（修复轮 1 / C 组，Task 16 `0414334` 的波及收口）。
     *
     * 旧形状靠 `onNodeWithContentDescription("播放 ability 发音")` / `("播放 ability 英式发音")` 找节点：
     * 那两句是把 lemma 与口音拼进朗读定语的**屏侧私有文案**。Task 16 把详情发音件归一到共享药片后，
     * 屏侧不再写任何朗读定语（`git grep "播放 \$" HEAD -- app/src/main` 为空），取样面随之消失——
     * 本 helper 把取样面搬到**同一枚节点的实参与可见文字**上，三条语义逐条对等、不放宽：
     *  - 「哪一槽可点」：`card_detail_speak` = 美式默认槽、`card_detail_speak_british` = 英式槽，
     *    且必须 `assertExists()` + `assertHasClickAction()`（旧形状里 performClick 隐含「可点」，这里显式钉）；
     *  - 「口音归属」：旧 CD 的「英式」二字现在是药片尾缀槽的可见文字「美」/「英」
     *    （clickable 的 MergeDescendants 把它并进同一枚语义节点），逐槽点名；
     *  - 「状态文案」：待播档的可见文字「播放发音」必须在槽上（三态唯一真值口径，见 SpeakerButton KDoc）。
     *
     * 「朗读归属（哪个词）」这一维旧形状靠 CD 里的 lemma 承载，现由 [assertDetailLemma] 逐字钉回详情词头。
     * 先例：`CardDetailScreenTest.detailOffersAmericanByDefaultAndSeparateBritishAction`（Task 16 同款换面）、
     * `CardDetailPronunciationControlsTest`（私有 CD → 组件可见文字）。
     */
    private fun detailSpeakSlot(tag: String, accentSuffix: String) =
        composeRule.onNodeWithTag(tag)
            .assertExists()
            .assertHasClickAction()
            .assert(hasText("播放发音", substring = true))
            .assert(hasText(accentSuffix, substring = true))

    /** 旧 CD 里那句「播放 <lemma> 发音」的 lemma 维：换成药片所在详情的词头可见文字逐字钉。 */
    private fun assertDetailLemma(lemma: String) {
        composeRule.onNodeWithTag("card_detail_lemma").assertTextEquals(lemma)
    }

    /**
     * I-3：系统返回键关闭阅读生词详情时必须清**共享详情状态**（detailPronunciation*）。
     * 屏内 onBack/onNext 与记录宿主都清了，唯独 BackHandler(selectedArticleCard) 漏了。
     * 「另一处详情」选学习流宿主：记录/词书宿主打开详情时会顺手复位（掩盖缺陷），
     * 学习流详情则是纯消费者——旧形状下它会把上一词残留的 Playing 原样端出来。
     */
    @Test
    fun systemBackClosingAReadingWordDetailClearsTheSharedDetailPronunciationState() {
        val provider = HangingPronunciationProvider()
        val reading = readingFixture()
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
            )
        }
        openReadingWordDetail()
        assertDetailLemma("apple")
        detailSpeakSlot("card_detail_speak", "美").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_pronunciation_message").fetchSemanticsNodes().isNotEmpty()
        }

        // 系统返回键关详情（BackHandler :494——本轮修复的收口点）。
        pressSystemBack()
        composeRule.onNodeWithTag("card_detail_screen").assertDoesNotExist()

        // 退文章 → 今日计划 → 进学习流，提交一张卡打开「下一处详情」（学习流宿主）。
        pressSystemBack()
        composeRule.onNodeWithTag("app_tab_learning").performClick()
        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_feedback_fuzzy").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_feedback_fuzzy").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_pronunciation_stop").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_pronunciation_message").assertDoesNotExist()
        provider.release()
    }

    /**
     * I-5（决策 §2「播控失败的状态机口径」）：暂停未被引擎接受（pausePlayback() 返回 false，
     * 即系统 TTS 无公开 pause()/resume() 的平台事实）时状态必须**保持 Playing**——
     * 落 Idle 会让整组控件（含唯一可用的「停止」）在朗读仍在继续时消失，与「停止始终可达」相悖。
     * 走真实宿主（AppScreen 词卡 onPauseResume）：旧形状 `else -> Idle` 下本用例必红。
     * 说明：brief 曾允许「组件级 harness 复刻 when」，但 harness 复刻对 AppScreen 真实代码恒绿、
     * 不具证伪力，按「必须 RED」的硬口径上收为宿主级用例。
     */
    @Test
    fun aPauseRejectedByTheEngineKeepsPlayingSoStopStaysReachable() {
        val provider = HangingPronunciationProvider()
        val speech = speechFixture(autoSpeakOnCardReady = false)
        composeRule.setContent {
            readyAppScreen(pronunciationProvider = provider, speechSettingsViewModel = speech.speech)
        }
        createProfile()
        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_speak").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_pronunciation_toggle").fetchSemanticsNodes().isNotEmpty()
        }

        // 点暂停：provider.pausePlayback() 返回 false（未被引擎接受）→ 状态保持 Playing。
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_card_pronunciation_stop").assertIsDisplayed()
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").assertIsDisplayed()
        composeRule.onNodeWithText("正在播放发音…").assertIsDisplayed()
        provider.release()
    }

    /**
     * N-1（T9 复评 Minor）：I-5 的「暂停被拒 → 保持原状态」在决策 §2 下有**五处**宿主
     * （记录详情 / 词卡 / 收藏详情 / 阅读整篇 / 阅读点词详情，均写作 `else -> 原状态`），
     * 修复轮只钉了词卡宿主一处。本例把**阅读整篇宿主**与下一例的**阅读点词详情宿主**补上，
     * 形状与 I-5 完全一致：恒返回 false 的挂起 provider + 真实宿主导航 + 点暂停后断
     * 「停止/暂停仍 `assertIsDisplayed()`」且状态文案仍是「正在播放发音…」
     * （落 Paused 会变「已暂停播放…」，落 Idle 则整组与文案一起消失）。
     * 宿主行号不写死（派发实测锚点已随注释漂移，一律以 grep `else -> ` 为准）。
     * 证伪口径（伪修反证，报告 §N-1）：把整篇宿主的 `else -> articlePronunciationStatus` 改成
     * `else -> PronunciationStatus.Idle` 后本例必红——失败信息即「整组消失」。
     */
    @Test
    fun anArticlePauseRejectedByTheEngineKeepsPlayingSoStopStaysReachable() {
        val provider = HangingPronunciationProvider()
        val reading = readingFixture()
        val speech = speechFixture(autoSpeakOnCardReady = false)
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                speechSettingsViewModel = speech.speech,
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
            )
        }
        createProfile()
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_open_today").fetchSemanticsNodes().isNotEmpty()
        }
        openTodayArticle()
        speakArticleAndAwaitControls()

        composeRule.onNodeWithTag("article_pronunciation_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("article_pronunciation_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("article_pronunciation_stop").assertIsDisplayed()
        composeRule.onNodeWithText("正在播放发音…").assertIsDisplayed()
        provider.release()
    }

    /**
     * N-1 第二处：阅读页点词详情宿主（`AppScreen.kt:1472` `else -> held`）。
     * 走真实导航（今日文章 → 关覆盖窗 → 点正文词 → 详情喇叭），点暂停后播控组与 Playing 文案都必须在；
     * 该宿主与整篇宿主**不共用状态变量**（详情用 `detailPronunciationMessage`），故必须单独钉。
     */
    @Test
    fun aReadingWordDetailPauseRejectedByTheEngineKeepsPlayingSoStopStaysReachable() {
        val provider = HangingPronunciationProvider()
        val reading = readingFixture()
        val speech = speechFixture(autoSpeakOnCardReady = false)
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                speechSettingsViewModel = speech.speech,
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
            )
        }
        openReadingWordDetail()
        assertDetailLemma("apple")
        detailSpeakSlot("card_detail_speak", "美").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_pronunciation_toggle").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("card_detail_pronunciation_stop").assertIsDisplayed()
        composeRule.onNodeWithText("正在播放发音…").assertIsDisplayed()
        provider.release()
    }

    /**
     * I-6 宿主级接线钉：详情三宿主的 `pronunciationControlsEnabled = true` 之一（记录详情）+
     * 停止收口。Playing 下播控组存在（MiMo 引擎 → 暂停可用），点停止后整组与文案离开组合、
     * provider.stopPlayback 恰调用一次。
     */
    @Test
    fun hostedRecordDetailShowsControlsWhilePlayingAndStopClosesTheGroup() {
        val provider = HangingPronunciationProvider()
        val speech = speechFixture()
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                speechSettingsViewModel = speech.speech,
                learningRecordsViewModel = learningRecordsViewModelWithAbilityRecord(),
            )
        }
        createProfile()
        composeRule.onNodeWithContentDescription("学习记录").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("learning_record_row_ability").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("learning_record_row_ability").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertDetailLemma("ability")
        detailSpeakSlot("card_detail_speak", "美").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_pronunciation_stop").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").assertIsDisplayed()
        composeRule.onNodeWithText("正在播放发音…").assertIsDisplayed()

        composeRule.onNodeWithTag("card_detail_pronunciation_stop").performClick()
        composeRule.waitForIdle()
        assertEquals(1, provider.stopCalls)
        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_pronunciation_stop").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_pronunciation_message").assertDoesNotExist()
        provider.release()
    }

    @Test
    fun wordQaOverlayOpensFromReadingCardDetailAndAsksThroughViewModel() {
        val reading = readingFixture()
        composeRule.setContent {
            readyAppScreen(
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
                wordQaViewModel = wordQaViewModelWith(DefaultTextProfileResult.NoSelection),
            )
        }
        openReadingWordDetail()

        composeRule.onNodeWithContentDescription("问 AI（apple）").performClick()
        composeRule.onNodeWithTag("word_qa_screen").assertExists()
        composeRule.onNodeWithTag("word_qa_chip_Sentence").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_qa_not_configured").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_qa_back").performClick()
        composeRule.onNodeWithTag("card_detail_screen").assertExists()
        assert(composeRule.onAllNodesWithTag("word_qa_screen").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun wordQaOutboundConfirmationWiredThroughOverlayBeforeAnyNetwork() {
        val reading = readingFixture()
        composeRule.setContent {
            readyAppScreen(
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
                wordQaViewModel = wordQaViewModelWith(DefaultTextProfileResult.Selected(wordQaTextProfile())),
            )
        }
        openReadingWordDetail()

        composeRule.onNodeWithContentDescription("问 AI（apple）").performClick()
        composeRule.onNodeWithTag("word_qa_chip_Mnemonic").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_qa_outbound_confirmation_text").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("api.example.com", substring = true).assertExists()
        composeRule.onNodeWithTag("word_qa_decline_outbound").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_qa_chip_Mnemonic").assertIsEnabled()
        assert(composeRule.onAllNodesWithTag("word_qa_outbound_confirmation_text").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun lockedReadingTabStillListsTheLocalHistory() {
        // 真机实证的缺陷：今日计划未完成时 readingState=Locked，而 AppScreen 只用
        // `(readingState as? Ready)?.history` 取历史 → 点「查看本地阅读历史」恒显示
        // 「暂无本地文章」，可同一个库里确有文章。AGENTS.md：历史已解锁文章始终可重读，
        // 未解锁只限制「当天新文章生成入口」。
        val reading = readingFixture(unlocked = false)
        composeRule.setContent {
            readyAppScreen(
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
            )
        }
        createProfile()
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_access_locked_reason").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("reading_history_entry").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("reading_history_screen").assertExists()
        composeRule.onNodeWithTag("reading_history_item_a1").assertExists().assertHasClickAction()
        composeRule.onNodeWithText("Apple Day", substring = true).assertExists()
        composeRule.onNodeWithText("暂无本地文章", substring = true).assertDoesNotExist()
    }

    /** 阅读流导航到点词详情：profile → 阅读 tab → 今日文章 → 关覆盖窗 → 点正文词。 */
    private fun openReadingWordDetail() {
        createProfile()
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_open_today").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reading_open_today").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("article_english").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("coverage_popup_close").performClick()
        composeRule.onNodeWithTag("article_english").performClick()
        composeRule.onNodeWithTag("card_detail_screen").assertExists()
    }

    /** 词问答 ViewModel 夹具：transport 一旦被调用就失败——出站确认前零字节的证明。 */
    private fun wordQaViewModelWith(selection: DefaultTextProfileResult): WordAiQaViewModel {
        val transport = object : AiHttpTransport {
            override suspend fun send(request: AiHttpRequest) =
                error("word QA wiring test must not perform network requests")
        }
        val secrets = AiProfileSecretUseCase(object : SecretStore {
            override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
            override fun read(reference: SecretReference) = Result.success(charArrayOf('k'))
            override fun delete(reference: SecretReference) = Result.success(Unit)
            override fun has(reference: SecretReference) = Result.success(true)
        })
        val notes = object : WordAiNoteRepository {
            override suspend fun save(note: WordAiNote) = Result.success(Unit)
            override suspend fun list(profileId: String, lemma: String, wordBookId: String, cardId: String) = Result.success(emptyList<WordAiNote>())
            override suspend fun delete(profileId: String, noteId: String) = Result.success(Unit)
        }
        return WordAiQaViewModel(
            WordQaUseCase(DefaultTextProfileResolver { selection }, secrets, transport),
            notes,
            WordNoteIdFactory { "note-1" },
            FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC),
        )
    }

    private fun wordQaTextProfile() = AiProfile(
        profileId = "qa",
        displayName = "问答配置",
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "gpt-test",
        capabilities = setOf(AiCapability.Text),
        secretReference = AiProfileSecretUseCase.referenceFor("qa"),
    )

    @Test
    fun sentenceAnalysisFeatureRoutesToItsRealScreenAndAsksThroughViewModel() {
        composeRule.setContent {
            readyAppScreen(
                sentenceAnalysisViewModel = sentenceAnalysisViewModelWith(DefaultTextProfileResult.NoSelection),
            )
        }
        createProfile()
        composeRule.onNodeWithTag("app_tab_ai").performClick()
        composeRule.waitForIdle()
        // 长难句分析是已上线行（裁决④ 后默认可见），折叠键只管未上线批，这里不需要点它。
        composeRule.onNodeWithTag("ai_feature_sentence-analysis").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sentence_analysis_screen").assertExists()
        composeRule.onNodeWithTag("app_tab_ai").assertDoesNotExist()

        composeRule.onNodeWithTag("sentence_analysis_input").performTextInput("Birds fly.")
        composeRule.onNodeWithTag("sentence_analysis_analyze").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("sentence_analysis_not_configured").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sentence_analysis_back").performClick()
        composeRule.onNodeWithTag("ai_learning_screen").assertExists()
        composeRule.onAllNodesWithTag("sentence_analysis_screen").fetchSemanticsNodes().isEmpty()
    }

    /** 长难句分析 ViewModel 夹具：transport 一旦被调用就失败——出站确认前零字节的证明。 */
    private fun sentenceAnalysisViewModelWith(selection: DefaultTextProfileResult): SentenceAnalysisViewModel {
        val transport = object : AiHttpTransport {
            override suspend fun send(request: AiHttpRequest) =
                error("sentence analysis wiring test must not perform network requests")
        }
        val secrets = AiProfileSecretUseCase(object : SecretStore {
            override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
            override fun read(reference: SecretReference) = Result.success(charArrayOf('k'))
            override fun delete(reference: SecretReference) = Result.success(Unit)
            override fun has(reference: SecretReference) = Result.success(true)
        })
        return SentenceAnalysisViewModel(
            SentenceAnalysisUseCase(DefaultTextProfileResolver { selection }, secrets, transport),
        )
    }

    @Test
    fun imageStudioFeatureRoutesToItsRealScreenAndAsksThroughViewModel() {
        composeRule.setContent {
            readyAppScreen(
                imageStudioViewModel = imageStudioViewModelWith(DefaultTextProfileResult.NoSelection),
            )
        }
        createProfile()
        composeRule.onNodeWithTag("app_tab_ai").performClick()
        composeRule.waitForIdle()
        // AI 生图同样是已上线行，默认可见；点开未上线折叠行是白增耦合，删之。
        composeRule.onNodeWithTag("ai_feature_image-studio").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("image_studio_screen").assertExists()
        composeRule.onNodeWithTag("app_tab_ai").assertDoesNotExist()

        composeRule.onNodeWithTag("image_studio_subject_input").performTextInput("苹果")
        composeRule.onNodeWithTag("image_studio_generate_prompt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("image_studio_not_configured").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("image_studio_back").performClick()
        composeRule.onNodeWithTag("ai_learning_screen").assertExists()
        composeRule.onAllNodesWithTag("image_studio_screen").fetchSemanticsNodes().isEmpty()
    }

    /** 生图 ViewModel 夹具：两个出站通道一旦被调用就失败——确认前零字节的证明。 */
    private fun imageStudioViewModelWith(selection: DefaultTextProfileResult): ImageStudioViewModel {
        val transport = object : AiHttpTransport {
            override suspend fun send(request: AiHttpRequest) =
                error("image studio wiring test must not perform network requests")
        }
        val audioTransport = object : AudioHttpTransport {
            override suspend fun send(request: AudioHttpRequest) =
                error("image studio wiring test must not download images")
        }
        val secrets = AiProfileSecretUseCase(object : SecretStore {
            override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
            override fun read(reference: SecretReference) = Result.success(charArrayOf('k'))
            override fun delete(reference: SecretReference) = Result.success(Unit)
            override fun has(reference: SecretReference) = Result.success(true)
        })
        val directory = java.io.File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "image-studio-wiring",
        )
        return ImageStudioViewModel(
            drawPrompt = DrawingPromptUseCase(DefaultTextProfileResolver { selection }, secrets, transport),
            generateImage = ImageGenerationUseCase(
                DefaultImageProfileResolver { DefaultImageProfileResult.NoSelection },
                secrets,
                transport,
            ),
            store = GeneratedImageStore(audioTransport, directory),
        )
    }

    private data class ReadingFixture(        val access: ReadingAccessViewModel,
        val article: ArticleReadingViewModel,
        val todayPlan: TodayPlanViewModel,
    )

    private fun readingFixture(unlocked: Boolean = true): ReadingFixture {
        // 固定时钟锚定「今日」：Asia/Shanghai 下 Instant.EPOCH 的本地日就是 1970-01-01，
        // 计划 localDate 与文章 localDate 必须等于它。仓储 fake 按真实检索键校验，
        // 键不匹配一律返回空——无条件返回文章会把生产代码的日期/身份错配掩盖成绿灯。
        val plan = TodayPlan(
            planId = "plan-1",
            profileId = "default",
            localDate = java.time.LocalDate.of(1970, 1, 1),
            zoneId = "Asia/Shanghai",
            activeWordBookId = "cet4",
            newTarget = 1,
            dueTarget = 0,
            newCardIds = listOf("apple-id"),
            dueCardIds = emptyList(),
            ruleVersion = "v1",
            generatedAt = java.time.Instant.EPOCH,
        )
        val article = Article(
            articleId = "a1", profileId = "default", localDate = plan.localDate.toString(),
            activeWordBookId = plan.activeWordBookId, articleType = ArticleType.STORY,
            lengthTier = ArticleLengthTier.STANDARD, version = 1, title = "Apple Day",
            englishText = "apple", chineseText = "苹果", generatedAtEpochMillis = 1,
            coveredLemmas = listOf("apple"), source = ArticleSource.UserImported,
        )
        // unlocked=false 时今日计划一个词都没完成（newTarget=1）→ UnlockPolicy 判锁定。
        // 锁定与否只改完成集合，不改历史来源：两种情况都得能读到那篇文章。
        val completed = if (unlocked) listOf("apple-id") else emptyList()
        val events = object : LearningEventRepository by NoopLearningEventRepository() {
            override suspend fun completedCardIds(planId: String) =
                RepositoryResult.Success(if (planId == plan.planId) completed else emptyList())
        }
        val plans = object : TodayPlanRepository {
            override suspend fun find(profileId: String, localDate: java.time.LocalDate) =
                if (profileId == plan.profileId && localDate == plan.localDate) TodayPlanResult.Ready(plan)
                else TodayPlanResult.NotFound
            override suspend fun findLatest(profileId: String) =
                if (profileId == plan.profileId) TodayPlanResult.Ready(plan) else TodayPlanResult.NotFound
            override suspend fun saveIfAbsent(plan: TodayPlan) = TodayPlanResult.Ready(plan)
        }
        val articles = object : ArticleRepository {
            override suspend fun saveNewVersion(article: Article) = Result.success(article)
            override suspend fun findLatest(
                profileId: String, localDate: String, activeWordBookId: String,
                articleType: ArticleType, lengthTier: ArticleLengthTier,
            ) = if (profileId == plan.profileId && localDate == plan.localDate.toString() &&
                activeWordBookId == plan.activeWordBookId && articleType == ArticleType.STORY &&
                lengthTier == ArticleLengthTier.STANDARD
            ) {
                Result.success(article)
            } else {
                Result.success(null)
            }
            override suspend fun findHistory(profileId: String) = Result.success(listOf(article))
            override suspend fun findBySourceUrl(url: String) = Result.success<Article?>(null)
        }
        val preferences = object : ReadingPreferenceRepository {
            override suspend fun getPreference(profileId: String) =
                if (profileId == plan.profileId) Result.success(ReadingPreference(profileId))
                else Result.failure(IllegalStateException("unexpected reading profileId"))
            override suspend fun savePreference(preference: ReadingPreference) = Result.success(Unit)
        }
        val cards = object : com.example.englishlearning.learning.WordCardSource {
            override suspend fun cardIds(wordBookId: String) =
                if (wordBookId == plan.activeWordBookId) listOf("apple-id") else emptyList()
            override suspend fun cards(cardIds: List<String>) = cardIds.mapNotNull { id ->
                if (id == "apple-id") com.example.englishlearning.learning.domain.WordCard(id, "cet4", "apple", "", "", "苹果") else null
            }
        }
        val clock = FixedClockProvider(plan.generatedAt, java.time.ZoneId.of(plan.zoneId))
        val ids = ArticleIdFactory { "new-article" }
        val offline = object : AiHttpTransport {
            override suspend fun send(request: AiHttpRequest): com.example.englishlearning.ai.net.AiHttpResult =
                error("Reading pronunciation test must not perform network requests")
        }
        val secrets = AiProfileSecretUseCase(object : SecretStore {
            override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
            override fun read(reference: SecretReference) = Result.success(charArrayOf())
            override fun delete(reference: SecretReference) = Result.success(Unit)
            override fun has(reference: SecretReference) = Result.success(false)
        })
        val generated = GenerateArticleUseCase(
            DefaultTextProfileResolver { DefaultTextProfileResult.NoSelection }, secrets, offline, articles, ids, { clock.instant() },
        )
        return ReadingFixture(
            ReadingAccessViewModel(
                articles, preferences, plans, events, cards, generated,
                FetchArticleUseCase(offline, articles, ids, { clock.instant() }),
                ImportArticleUseCase(articles, plans, events, cards, ids, { clock.instant() }), clock,
            ),
            ArticleReadingViewModel(preferences, object : ReadingCompletionRepository {
                override suspend fun record(article: Article) = Result.success(true)
                override suspend fun completionsToday(profileId: String) = Result.success(0)
            }),
            TodayPlanViewModel({ TodayPlanResult.Ready(plan) }, FakeLearningProfileRepository(), events),
        )
    }

    private class RecordingPronunciationProvider(
        private val result: PronunciationResult,
    ) : PronunciationProvider {
        val spokenTexts = mutableListOf<String>()
        val wordCalls = mutableListOf<Triple<String, com.example.englishlearning.language.domain.PronunciationAccent, Boolean>>()
        val sentenceCalls = mutableListOf<Triple<String, com.example.englishlearning.language.domain.PronunciationAccent, Boolean>>()
        val legacyCalls = mutableListOf<String>()

        override fun capabilities() = setOf(PronunciationCapability.RemoteAudio)

        override suspend fun speak(text: String): PronunciationResult {
            legacyCalls += text
            return result
        }

        override suspend fun speakWord(
            text: String,
            accent: com.example.englishlearning.language.domain.PronunciationAccent,
            forceRegenerate: Boolean,
            rate: Float,
        ): PronunciationResult {
            spokenTexts += text
            wordCalls += Triple(text, accent, forceRegenerate)
            return result
        }

        override suspend fun speakSentence(
            text: String,
            accent: com.example.englishlearning.language.domain.PronunciationAccent,
            forceRegenerate: Boolean,
        ): PronunciationResult {
            sentenceCalls += Triple(text, accent, forceRegenerate)
            return result
        }
    }

    /**
     * 可控挂起发音 provider：`speak*` 一路挂到 `release()` 为止——设备用例用它把呈现状态钉在
     * Playing（speak 前置），从而能在「仍在播」窗口里断清场与失败保持；迟到结果即使 release
     * 也被各宿主的代次/引用守卫挡住，不会污染断言。
     * `pausePlayback()/resumePlayback()` 恒返回 false——正是决策 §2 记录的平台事实
     * （`android.speech.tts.TextToSpeech` 无公开 pause()/resume()，引擎未接受）；
     * `stopPlayback()` 计数，供宿主级停止收口用例断恰好一次。
     */
    private class HangingPronunciationProvider : PronunciationProvider {
        private val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var stopCalls = 0
            private set

        override fun capabilities() = setOf(PronunciationCapability.RemoteAudio)

        override suspend fun speak(text: String): PronunciationResult {
            gate.await()
            return PronunciationResult.Played
        }

        override fun pausePlayback(): Boolean = false

        override fun resumePlayback(): Boolean = false

        override fun stopPlayback() {
            stopCalls++
        }

        fun release() {
            gate.complete(Unit)
        }
    }

    private data class SpeechFixture(
        val speech: SpeechSettingsViewModel,
        val ai: AiProfileSettingsViewModel,
        val keys: MutableSet<String>,
    )

    private fun speechFixture(autoSpeakOnCardReady: Boolean = true): SpeechFixture {
        val keys = mutableSetOf<String>()
        // OpenAI 引擎选项已移除：fixture 改用 MiMo 引擎 + XIAOMI_MIMO 协议 Profile，
        // 与真实存储路径（语音页只能选中系统 TTS / MiMo）保持一致。
        val profile = AiProfile(
            profileId = "mimo",
            displayName = "测试语音",
            websiteUrl = "https://example.com",
            endpoint = "https://api.example.com/v1",
            model = "tts-1",
            capabilities = setOf(AiCapability.Speech),
            secretReference = AiProfileSecretUseCase.referenceFor("mimo"),
            providerKind = AiProviderKind.XIAOMI_MIMO,
        )
        val profiles = object : AiProfileRepository {
            override suspend fun list() = Result.success(listOf(profile))
            override suspend fun find(profileId: String) = Result.success(profile.takeIf { it.profileId == profileId })
            override suspend fun save(profile: AiProfile) = Result.success(Unit)
            override suspend fun delete(profileId: String) = Result.success(Unit)
        }
        val secrets = AiProfileSecretUseCase(object : SecretStore {
            override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
            override fun read(reference: SecretReference) = Result.success(charArrayOf())
            override fun delete(reference: SecretReference) = Result.success(Unit)
            override fun has(reference: SecretReference) = Result.success(reference.alias.removePrefix("ai-profile-") in keys)
        })
        val preferences = object : SpeechPreferenceRepository {
            override suspend fun get() = Result.success(
                SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "mimo", autoSpeakOnCardReady = autoSpeakOnCardReady),
            )
            override suspend fun save(preference: SpeechPreference) = Result.success(Unit)
        }
        return SpeechFixture(
            SpeechSettingsViewModel(
                profiles,
                secrets,
                preferences,
                AiProfileIdFactory { "new-id" },
                object : PronunciationProvider {
                    override fun capabilities() = setOf(PronunciationCapability.RemoteAudio)
                    override suspend fun speak(text: String) = PronunciationResult.Played
                },
            ),
            AiProfileSettingsViewModel(profiles, secrets, AiProfileIdFactory { "new-id" }, object : AiPreferenceRepository {
                override suspend fun get() = Result.success(AiPreference())
                override suspend fun save(preference: AiPreference) = Result.success(Unit)
            }),
            keys,
        ).also { it.keys += "mimo" }
    }

    /**
     * Task 21：字母索引从右侧竖排压缩槽搬到**正文上方的横向可滚 48dp 芯片条**，
     * 靠后的字母（这里用到的是 W）落在视口外，`performClick` 的坐标打不到芯片。
     * 与 `WordBookWordsScreenTest.scrollToRailLetter` 同法走 `performScrollTo()`：
     * 设备实测手推 `SemanticsActions.ScrollBy (dx, 0f)` 在本 Compose 版本上对
     * `Modifier.horizontalScroll` 容器不产生位移（只有竖向 LazyColumn 吃这个动作），
     * 而 `performScrollTo()` 会沿父链找真正能滚的祖先，可靠。
     */
    private fun scrollToRailLetter(letter: String) {
        composeRule.onNodeWithTag("wordbook_words_rail_$letter").performScrollTo()
        composeRule.waitForIdle()
    }

    private fun wordBookWordsViewModelWithAlphabetThroughW(wRows: Int = 1): WordBookWordsViewModel {
        val lemmas = ('A'..'V').map { "${it.lowercaseChar()}word" } +
            listOf("wonder") + (2..wRows).map { "wword$it" }
        val words = lemmas.map { lemma ->
            val cardId = "test-book:$lemma"
            WordBookRecord(
                card = com.example.englishlearning.learning.domain.WordCard(cardId, "test-book", lemma, "", "", "测试释义"),
                cardId = cardId,
                displayLemma = lemma,
                status = com.example.englishlearning.learning.WordBookRecordStatus.Unlearned,
            )
        }
        val repository = object : LearningRecordRepository {
            override suspend fun today(profileId: String, planId: String) = RepositoryResult.Success(emptyList<LearningRecord>())
            override suspend fun history(profileId: String) = RepositoryResult.Success(emptyList<LearningHistoryGroup>())
            override suspend fun allWords(wordBookId: String, now: java.time.Instant) =
                if (wordBookId == "test-book") RepositoryResult.Success(words)
                else RepositoryResult.Success(emptyList<WordBookRecord>())
        }
        return WordBookWordsViewModel(repository, FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC))
    }

    private fun learningRecordsViewModelWithAbilityRecord(): LearningRecordsViewModel {
        val cardId = PlaceholderWordCardSource.cardId("cet4", "ability")
        val card = kotlinx.coroutines.runBlocking { PlaceholderWordCardSource().cards(listOf(cardId)) }.first()
        val record = LearningRecord(
            cardId = card.cardId,
            displayLemma = card.lemma,
            wordCard = card,
            feedback = CardFeedback.Fuzzy,
            occurredAt = java.time.Instant.EPOCH,
            localDate = java.time.LocalDate.of(1970, 1, 1),
            planId = "plan-1",
        )
        val repository = object : LearningRecordRepository {
            override suspend fun today(profileId: String, planId: String) =
                if (planId == "plan-1") RepositoryResult.Success(listOf(record))
                else RepositoryResult.Success(emptyList())
            override suspend fun history(profileId: String) =
                RepositoryResult.Success(emptyList<LearningHistoryGroup>())
            override suspend fun allWords(wordBookId: String, now: java.time.Instant) =
                RepositoryResult.Success(emptyList<WordBookRecord>())
        }
        val stats = object : com.example.englishlearning.learning.LearningStatsRepository {
            override suspend fun today(profileId: String, localDate: java.time.LocalDate) =
                Result.failure<com.example.englishlearning.learning.domain.DailyLearningStats>(UnsupportedOperationException())
            override suspend fun range(profileId: String, from: java.time.LocalDate, to: java.time.LocalDate) =
                Result.failure<List<com.example.englishlearning.learning.domain.DailyLearningStats>>(UnsupportedOperationException())
            override suspend fun historyRange(profileId: String, from: java.time.LocalDate, to: java.time.LocalDate) =
                Result.success(emptyList<com.example.englishlearning.learning.domain.DailyLearningHistory>())
            override suspend fun totals(profileId: String) =
                Result.failure<com.example.englishlearning.learning.domain.LearningTotals>(UnsupportedOperationException())
            override suspend fun streak(profileId: String, today: java.time.LocalDate) =
                Result.failure<Int>(UnsupportedOperationException())
        }
        return LearningRecordsViewModel(repository, stats, FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC))
    }

    /**
     * 生词本单条夹具（I-3 修复轮 1）：`list()` 返回一条命中占位内容库的词条，
     * 卡片由 `PlaceholderWordCardSource` 真解出——宿主在 LEARNING 页会自动 `load`，
     * 于是「我的词」生词本段真的有一行可点进详情（上一枚用例钉的就是这行的返回次序）。
     */
    private fun vocabularyViewModelWithOneEntry(): VocabularyViewModel {
        val cardId = PlaceholderWordCardSource.cardId("cet4", "ability")
        val card = kotlinx.coroutines.runBlocking { PlaceholderWordCardSource().cards(listOf(cardId)) }.first()
        val entry = VocabularyEntry(
            profileId = "profile-1",
            wordBookId = card.wordBookId,
            cardId = cardId,
            addedAt = java.time.Instant.EPOCH,
            lastFeedback = "Manual",
        )
        val events = NoopLearningEventRepository()
        return VocabularyViewModel(
            repository = object : VocabularyRepository {
                override suspend fun add(profileId: String, wordBookId: String, cardId: String, feedback: String, addedAt: java.time.Instant) = Unit
                override suspend fun remove(profileId: String, wordBookId: String, cardId: String) = Unit
                override suspend fun contains(profileId: String, wordBookId: String, cardId: String) = true
                override suspend fun list(profileId: String, wordBookId: String?) = RepositoryResult.Success(listOf(entry))
            },
            cards = PlaceholderWordCardSource(),
            submitFeedback = SubmitCardFeedbackUseCase(events, FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)),
            eventIds = EventIdFactory { "vocabulary-event-1" },
        )
    }

    /**
     * 收藏段单条夹具（Task 22-B RED-3）：走**真** `FavoritesViewModel`，仓储是一枚内存假件——
     * `toggle` 会真的把那行删掉，于是「取消收藏后该行消失」这条能力能被端到端观察到（屏侧用例只观察到回调）。
     * 词卡由 `PlaceholderWordCardSource` 真回源，`open()` 的一次性事件才会带着词卡回到宿主。
     */
    private fun favoritesViewModelWithOneWord(): FavoritesViewModel {
        val cardId = PlaceholderWordCardSource.cardId("cet4", "ability")
        return FavoritesViewModel(
            repository = InMemoryFavoriteWordRepository(
                mutableListOf(
                    FavoriteWord(
                        profileId = "profile-1",
                        cardId = cardId,
                        wordBookId = "cet4",
                        lemma = "ability",
                        addedAt = java.time.Instant.EPOCH,
                    ),
                ),
            ),
            cards = PlaceholderWordCardSource(),
            io = kotlinx.coroutines.Dispatchers.IO,
        )
    }

    /** 错词段单条夹具（Task 22-B RED-3）：一条「最近反馈 Again」的派生条目，回源到占位词卡 achieve。 */
    private fun wrongWordsViewModelWithOneWord(): WrongWordsViewModel {
        val cardId = PlaceholderWordCardSource.cardId("cet4", "achieve")
        return WrongWordsViewModel(
            repository = object : WrongWordsRepository {
                override suspend fun wrongWords(profileId: String) = Result.success(
                    listOf(
                        WrongWord(
                            cardId = cardId,
                            wordBookId = "cet4",
                            lastFeedbackAtEpochMillis = 0L,
                            nextReviewAtEpochMillis = null,
                        ),
                    ),
                )
            },
            cards = PlaceholderWordCardSource(),
            io = kotlinx.coroutines.Dispatchers.IO,
        )
    }

    /** 可变的内存收藏仓储：只服务上面的夹具，顺序按 `listFavorites` 声明的「收藏时间降序」。 */
    private class InMemoryFavoriteWordRepository(
        private val words: MutableList<FavoriteWord>,
    ) : FavoriteWordRepository {
        override suspend fun toggle(profileId: String, cardId: String, wordBookId: String, lemma: String): Result<Unit> {
            if (!words.removeAll { it.cardId == cardId }) {
                words += FavoriteWord(profileId, cardId, wordBookId, lemma, java.time.Instant.EPOCH)
            }
            return Result.success(Unit)
        }

        override suspend fun isFavorite(profileId: String, cardId: String): Result<Boolean> =
            Result.success(words.any { it.cardId == cardId })

        override suspend fun listFavorites(profileId: String): Result<List<FavoriteWord>> =
            Result.success(words.sortedByDescending { it.addedAt }.toList())
    }

    @Composable
    private fun readyAppScreen(
        speechSettingsViewModel: SpeechSettingsViewModel? = null,
        aiProfileViewModel: AiProfileSettingsViewModel? = null,
        pronunciationProvider: PronunciationProvider? = null,
        todayPlanViewModel: TodayPlanViewModel? = null,
        readingAccessViewModel: ReadingAccessViewModel? = null,
        articleReadingViewModel: ArticleReadingViewModel? = null,
        wordQaViewModel: WordAiQaViewModel? = null,
        sentenceAnalysisViewModel: SentenceAnalysisViewModel? = null,
        imageStudioViewModel: ImageStudioViewModel? = null,
        learningRecordsViewModel: LearningRecordsViewModel? = null,
        wordBookWordsViewModel: WordBookWordsViewModel? = null,
        vocabularyViewModel: VocabularyViewModel? = null,
        // Task 22-B（RED-3 能力承接）：合并页的收藏段与错词段各需一枚真 ViewModel，
        // 否则这两段永远停在 Loading，端到端只能证到屏侧注入的假回调。
        favoritesViewModel: FavoritesViewModel? = null,
        wrongWordsViewModel: WrongWordsViewModel? = null,
    ) {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        AppScreen(
            viewModel = vm,
            learningSetupViewModel = setupViewModel(),
            todayPlanViewModel = todayPlanViewModel ?: TodayPlanViewModel({ placeholderCardPlan() }, FakeLearningProfileRepository()),
            wordCardViewModel = wordCardFixtureViewModel(),
            readingAccessViewModel = readingAccessViewModel,
            articleReadingViewModel = articleReadingViewModel,
            speechSettingsViewModel = speechSettingsViewModel,
            aiProfileViewModel = aiProfileViewModel,
            pronunciationProvider = pronunciationProvider,
            wordQaViewModel = wordQaViewModel,
            sentenceAnalysisViewModel = sentenceAnalysisViewModel,
            imageStudioViewModel = imageStudioViewModel,
            learningRecordsViewModel = learningRecordsViewModel,
            wordBookWordsViewModel = wordBookWordsViewModel,
            vocabularyViewModel = vocabularyViewModel,
            favoritesViewModel = favoritesViewModel,
            wrongWordsViewModel = wrongWordsViewModel,
        )
    }

    private fun createProfile() {
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
    }

    private fun todayPlanViewModel(): TodayPlanViewModel = TodayPlanViewModel({ TodayPlanResult.MissingLearningSetup }, FakeLearningProfileRepository())

    @Test
    fun readyPlanEntryReopensSetupAndSavingReturnsToTodayPlan() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        var invocations = 0
        val todayPlan = TodayPlanViewModel({ invocations++; readyPlanResult() }, FakeLearningProfileRepository())
        composeRule.setContent { AppScreen(viewModel = vm, learningSetupViewModel = setupViewModel(), todayPlanViewModel = todayPlan, wordCardViewModel = wordCardFixtureViewModel()) }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()

        composeRule.onNodeWithContentDescription("调整词书与目标").assertExists().performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选好词书，开始今天的积累").assertExists()

        composeRule.onNodeWithContentDescription("保存学习设置").assertExists().performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("today_plan_summary").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
        assertEquals(2, invocations)
    }

    @Test
    fun optionalSetupCanBeCancelledBackToTodayPlanWithoutReloading() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        var invocations = 0
        val todayPlan = TodayPlanViewModel({ invocations++; readyPlanResult() }, FakeLearningProfileRepository())
        composeRule.setContent { AppScreen(viewModel = vm, learningSetupViewModel = setupViewModel(), todayPlanViewModel = todayPlan, wordCardViewModel = wordCardFixtureViewModel()) }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("调整词书与目标").assertExists().performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选好词书，开始今天的积累").assertExists()

        composeRule.onNodeWithContentDescription("返回上一层").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
        assertEquals(1, invocations)
    }

    @Test
    fun mandatorySetupOffersNoCancelEntry() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        composeRule.setContent { AppScreen(viewModel = vm, learningSetupViewModel = setupViewModel(), todayPlanViewModel = todayPlanViewModel(), wordCardViewModel = wordCardFixtureViewModel()) }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选好词书，开始今天的积累").assertExists()
        composeRule.onNodeWithContentDescription("返回上一层").assertDoesNotExist()
    }

    @Test
    fun startLearningOpensTheWordCardAndBackReturnsToTheTodayPlan() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        val todayPlan = TodayPlanViewModel({ placeholderCardPlan() }, FakeLearningProfileRepository())
        composeRule.setContent {
            AppScreen(
                viewModel = vm,
                learningSetupViewModel = setupViewModel(),
                todayPlanViewModel = todayPlan,
                wordCardViewModel = wordCardFixtureViewModel(),
            )
        }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("开始学习").assertExists().performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_card_screen").assertExists()
        composeRule.onNodeWithText("ability").assertExists()
        composeRule.onNodeWithContentDescription("不认识").assertExists()

        // The card is on its first (Ready) card, which offers no "back to plan" button; leaving
        // the flow is a system-back action handled by AppScreen's BackHandler.
        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("today_plan_summary").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
    }

    @Test
    fun returningFromLearningReloadsTodayPlan() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        var invocations = 0
        val todayPlan = TodayPlanViewModel({ invocations++; placeholderCardPlan() }, FakeLearningProfileRepository())
        composeRule.setContent {
            AppScreen(
                viewModel = vm,
                learningSetupViewModel = setupViewModel(),
                todayPlanViewModel = todayPlan,
                wordCardViewModel = wordCardFixtureViewModel(),
            )
        }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        // Initial load once the profile becomes Ready.
        assertEquals(1, invocations)

        composeRule.onNodeWithContentDescription("开始学习").assertExists().performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_card_screen").assertExists()

        // Leaving via system back must recompute today's progress (F1-04: re-entering the today
        // page recomputes state), otherwise the plan stays stale after reviewing cards.
        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("today_plan_summary").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
        assertEquals(2, invocations)
    }

    /**
     * Drives a real system back on the host Activity, exercising the same
     * OnBackPressedDispatcher path that AppScreen's `BackHandler` listens on. `Espresso.pressBack()`
     * is not usable here (espresso-core is only on the androidTest runtime classpath, not the
     * compile classpath), so the key event is injected directly through the instrumentation.
     */
    private fun pressSystemBack() {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }

    private fun readyPlanResult(): TodayPlanResult.Ready = readyPlanResult("test-book")

    private fun readyPlanResult(wordBookId: String): TodayPlanResult.Ready = TodayPlanResult.Ready(
        TodayPlan(
            planId = "plan-1",
            profileId = "profile-1",
            localDate = java.time.LocalDate.of(2026, 9, 19),
            zoneId = "Asia/Shanghai",
            activeWordBookId = wordBookId,
            newTarget = 10,
            dueTarget = 0,
            newCardIds = emptyList(),
            dueCardIds = emptyList(),
            ruleVersion = "v1",
            generatedAt = java.time.Instant.EPOCH,
        ),
    )

    private fun setupViewModel(): LearningSetupViewModel {
        val repository = FakeLearningProfileRepository()
        val seedWordBooks = SeedWordBooksUseCase(
            { "[{\"id\":\"test-book\",\"displayName\":\"测试词书\",\"level\":\"Test\",\"totalWords\":1,\"dataVersion\":\"v1\",\"sourceId\":\"ngsl-nawl-1.2\",\"sourcePolicy\":\"应用内学习分组，不是官方考试大纲词表。词条尚未随本任务打包。\"}]" },
            repository,
        )
        val settingsRepo = FakeLearningSettingsRepository()
        return LearningSetupViewModel(
            repository,
            seedWordBooks,
            SelectWordBookAndSetDailyTargetUseCase(repository),
            GetLearningSettingsUseCase(settingsRepo),
            SaveLearningSettingsUseCase(settingsRepo),
            // `test-book` 是种子里的内置册，不声明就会被可见性策略过滤掉。
            bundledIds = com.example.englishlearning.learning.BundledWordBookIdSource { setOf("test-book") },
        )
    }

    private class FakeLearningProfileRepository : LearningProfileRepository {
        private var profile: LearningProfile? = null
        private val wordBooks = mutableListOf<WordBook>()

        override suspend fun current(profileId: String) = RepositoryResult.Success(profile)

        override suspend fun save(profile: LearningProfile): RepositoryResult<Unit> {
            this.profile = profile
            return RepositoryResult.Success(Unit)
        }

        override suspend fun listWordBooks() = RepositoryResult.Success(wordBooks)

        override suspend fun findWordBook(id: String) = RepositoryResult.Success(wordBooks.find { it.id == id })

        override suspend fun upsertWordBook(wordBook: WordBook): RepositoryResult<Unit> {
            wordBooks.removeAll { it.id == wordBook.id }
            wordBooks += wordBook
            return RepositoryResult.Success(Unit)
        }
    }

    private class FakeLearningSettingsRepository : LearningSettingsRepository {
        private val stored = mutableMapOf<String, LearningSettings>()

        override suspend fun find(profileId: String): LearningSettingsRepositoryResult<LearningSettings?> =
            LearningSettingsRepositoryResult.Success(stored[profileId])

        override suspend fun save(settings: LearningSettings): LearningSettingsRepositoryResult<Unit> {
            stored[settings.profileId] = settings
            return LearningSettingsRepositoryResult.Success(Unit)
        }
    }
}
