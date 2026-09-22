package com.example.englishlearning.ui

import com.example.englishlearning.reading.ArticleRepository
import com.example.englishlearning.reading.ReadingPreferenceRepository
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
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

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingAccessViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun lockedProfileShowsReasonWithoutExposingArticleChoices() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val viewModel = ReadingAccessViewModel(FakeArticleRepository(), FakePreferenceRepository())

        viewModel.load("p1", isUnlocked = false, unlockReason = "还差 2 个新词")
        advanceUntilIdle()

        assertEquals(ReadingAccessUiState.Locked("还差 2 个新词"), viewModel.uiState.value)
    }

    @Test
    fun selectingArticleTypePersistsUpdatedPreference() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val preferences = FakePreferenceRepository()
        val viewModel = ReadingAccessViewModel(FakeArticleRepository(), preferences)
        viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()

        viewModel.selectType(ArticleType.WORKPLACE)
        advanceUntilIdle()

        assertEquals(ArticleType.WORKPLACE, preferences.saved?.defaultArticleType)
    }

    @Test
    fun unlockedProfileLoadsPreferenceAndOfflineHistoryCount() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val viewModel = ReadingAccessViewModel(
            FakeArticleRepository(history = listOf(article("a1"), article("a2"))),
            FakePreferenceRepository(ReadingPreference("p1", ArticleType.SCIENCE, ArticleLengthTier.LONG)),
        )

        viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()

        assertEquals(
            ReadingAccessUiState.Ready(ReadingPreference("p1", ArticleType.SCIENCE, ArticleLengthTier.LONG), 2),
            viewModel.uiState.value,
        )
    }

    private fun article(id: String) = Article(
        articleId = id, profileId = "p1", localDate = "2026-09-22", activeWordBookId = "cet4",
        articleType = ArticleType.STORY, lengthTier = ArticleLengthTier.STANDARD, version = 1,
        title = "title", englishText = "text", chineseText = "译文", generatedAtEpochMillis = 1, modelName = null,
    )

    private class FakeArticleRepository(private val history: List<Article> = emptyList()) : ArticleRepository {
        override suspend fun saveNewVersion(article: Article) = Result.success(article)
        override suspend fun findLatest(profileId: String, localDate: String, activeWordBookId: String, articleType: ArticleType, lengthTier: ArticleLengthTier) = Result.success<Article?>(null)
        override suspend fun findHistory(profileId: String) = Result.success(history)
    }

    private class FakePreferenceRepository(private val preference: ReadingPreference = ReadingPreference("p1")) : ReadingPreferenceRepository {
        var saved: ReadingPreference? = null
        override suspend fun getPreference(profileId: String) = Result.success(preference)
        override suspend fun savePreference(preference: ReadingPreference): Result<Unit> {
            saved = preference
            return Result.success(Unit)
        }
    }
}
