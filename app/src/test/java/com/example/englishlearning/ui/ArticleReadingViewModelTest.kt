package com.example.englishlearning.ui

import com.example.englishlearning.reading.ReadingPreferenceRepository
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleDisplayMode
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
import com.example.englishlearning.learning.domain.WordCard
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleReadingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun article(
        coveredLemmas: List<String>,
        englishText: String = "The apple grows in the garden",
    ) = Article(
        articleId = "a1",
        profileId = "p1",
        localDate = "2026-09-24",
        activeWordBookId = "cet4",
        articleType = ArticleType.STORY,
        lengthTier = ArticleLengthTier.STANDARD,
        version = 1,
        title = "Apple Day",
        englishText = englishText,
        chineseText = "苹果长在园子里。",
        generatedAtEpochMillis = 1_000L,
        coveredLemmas = coveredLemmas,
        source = ArticleSource.AiGenerated(modelName = "gpt-x", parameterSummary = "summary"),
    )

    private val todayCards = listOf(
        WordCard("c1", "cet4", "apple", "", "", ""),
        WordCard("c2", "cet4", "banana", "", "", ""),
    )

    private fun viewModel(
        preference: ReadingPreference = ReadingPreference("p1"),
        preferences: FakePreferenceRepository = FakePreferenceRepository(preference),
    ): Pair<ArticleReadingViewModel, FakePreferenceRepository> {
        Dispatchers.setMain(dispatcher)
        val viewModel = ArticleReadingViewModel(preferences)
        return viewModel to preferences
    }

    @Test
    fun defaultsToTheStoredDisplayMode() = runTest(dispatcher) {
        val (viewModel, _) = viewModel(ReadingPreference("p1", displayMode = ArticleDisplayMode.BILINGUAL))

        viewModel.load(article(listOf("apple")), todayCards)
        advanceUntilIdle()

        assertEquals(ArticleDisplayMode.BILINGUAL, viewModel.uiState.value?.mode)
    }

    @Test
    fun englishFirstHidesTheTranslationUntilToggled() = runTest(dispatcher) {
        val (viewModel, _) = viewModel(ReadingPreference("p1", displayMode = ArticleDisplayMode.ENGLISH_FIRST))

        viewModel.load(article(listOf("apple")), todayCards)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value?.translationExpanded)
        viewModel.toggleTranslation()
        assertEquals(true, viewModel.uiState.value?.translationExpanded)
    }

    @Test
    fun fullTranslationStartsExpanded() = runTest(dispatcher) {
        val (viewModel, _) = viewModel(ReadingPreference("p1", displayMode = ArticleDisplayMode.FULL_TRANSLATION))

        viewModel.load(article(listOf("apple")), todayCards)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value?.translationExpanded)
    }

    @Test
    fun changingTheModePersistsThePreference() = runTest(dispatcher) {
        val (viewModel, preferences) = viewModel(
            ReadingPreference("p1", defaultArticleType = ArticleType.SCIENCE, displayMode = ArticleDisplayMode.BILINGUAL),
        )
        viewModel.load(article(listOf("apple")), todayCards)
        advanceUntilIdle()

        viewModel.setMode(ArticleDisplayMode.FULL_TRANSLATION)
        advanceUntilIdle()

        val saved = preferences.saved
        assertEquals(ArticleDisplayMode.FULL_TRANSLATION, saved?.displayMode)
        // 偏好的其他字段必须原样保留，不能被阅读页重置。
        assertEquals(ArticleType.SCIENCE, saved?.defaultArticleType)
    }

    @Test
    fun changingTheModeDoesNotRewriteTheArticleText() = runTest(dispatcher) {
        val (viewModel, _) = viewModel()
        viewModel.load(article(listOf("apple")), todayCards)
        advanceUntilIdle()
        val before = viewModel.uiState.value?.article

        viewModel.setMode(ArticleDisplayMode.ENGLISH_FIRST)

        val after = viewModel.uiState.value?.article
        assertEquals(before?.englishText, after?.englishText)
        assertEquals(before?.chineseText, after?.chineseText)
        assertEquals(before?.title, after?.title)
    }

    @Test
    fun derivesHighlightsFromTheStoredLemmasNotFromTodaysPlan() = runTest(dispatcher) {
        val (viewModel, _) = viewModel()
        // 今天的计划已经换了词（banana），但文章当初的 coveredLemmas 是 apple——
        // 高亮必须跟原文走，而不是跟今天的计划走。
        val swappedPlanCards = listOf(WordCard("c9", "cet4", "banana", "", "", ""))

        viewModel.load(article(listOf("apple")), swappedPlanCards)
        advanceUntilIdle()

        val highlights = viewModel.uiState.value?.highlights.orEmpty()
        assertTrue(highlights.any { it.lemma == "apple" }, "highlight must come from the stored lemmas")
    }

    @Test
    fun exposesTheUncoveredLemmas() = runTest(dispatcher) {
        val (viewModel, _) = viewModel()
        // 正文只出现了 apple；banana 在请求词表里但没出现在正文中。
        val text = "The apple grows quietly"

        viewModel.load(article(listOf("apple", "banana"), englishText = text), todayCards)
        advanceUntilIdle()

        assertEquals(listOf("banana"), viewModel.uiState.value?.uncoveredLemmas)
    }

    @Test
    fun preferenceReadFailureFallsBackToTheDefaultMode() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val preferences = FakePreferenceRepository(failRead = true)
        val viewModel = ArticleReadingViewModel(preferences)

        viewModel.load(article(listOf("apple")), todayCards)
        advanceUntilIdle()

        // 偏好只是呈现设置，读不到就退回默认呈现，不能拦住整篇阅读。
        assertEquals(ArticleDisplayMode.ENGLISH_FIRST, viewModel.uiState.value?.mode)
    }

    private class FakePreferenceRepository(
        private val preference: ReadingPreference = ReadingPreference("p1"),
        private val failRead: Boolean = false,
    ) : ReadingPreferenceRepository {
        var saved: ReadingPreference? = null
        override suspend fun getPreference(profileId: String): Result<ReadingPreference> =
            if (failRead) Result.failure(IllegalStateException("storage closed")) else Result.success(preference)

        override suspend fun savePreference(preference: ReadingPreference): Result<Unit> {
            saved = preference
            return Result.success(Unit)
        }
    }
}
