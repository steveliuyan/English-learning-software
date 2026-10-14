package com.example.englishlearning.ui

import com.example.englishlearning.reading.ArticleDeletionImpact
import com.example.englishlearning.reading.ArticleVersionRepository
import com.example.englishlearning.reading.VersionDeleteOutcome
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * F4-01 决策 §8 文章版本管理 ViewModel 状态机：确认框未点「确认删除」前仓储
 * deleteVersion 必须零调用（JVM 假仓储记数）；删除成功才递增 versionDeletions
 * 让宿主重放历史加载；拒删唯一版本与失败都只落文案、不发刷新事件。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArticleVersionViewModelTest {
    @Test
    fun `requestDelete fills pending with the cascade preview and deletes nothing yet`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeVersionRepository(impact = ArticleDeletionImpact(1, 2, 0))
        val viewModel = ArticleVersionViewModel(repository, Dispatchers.Unconfined)

        viewModel.requestDelete(article(version = 2))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.pending?.article?.version)
        assertEquals(ArticleDeletionImpact(1, 2, 0), state.pending?.impact)
        assertEquals(null, state.notice)
        assertEquals(0, repository.deleteCalls, "确认框还没点「确认删除」就必须零删除")
        Dispatchers.resetMain()
    }

    @Test
    fun `requestDelete failure leaves an honest notice and no dialog`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = ArticleVersionViewModel(FakeVersionRepository(impactFailure = true), Dispatchers.Unconfined)

        viewModel.requestDelete(article())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.pending)
        assertEquals(true, state.notice?.contains("稍后重试"), "预览失败要给出可重试文案：${state.notice}")
        Dispatchers.resetMain()
    }

    @Test
    fun `confirmDelete removes the version announces refresh and reports the impact`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeVersionRepository(outcome = VersionDeleteOutcome.Deleted(ArticleDeletionImpact(1, 2, 0)))
        val viewModel = ArticleVersionViewModel(repository, Dispatchers.Unconfined)

        viewModel.requestDelete(article(version = 2))
        advanceUntilIdle()
        viewModel.confirmDelete()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.pending)
        assertEquals(false, state.busy)
        assertEquals(true, state.notice?.contains("已删除"), "成功要回执：${state.notice}")
        assertEquals(true, state.notice?.contains("1 条阅读完成记录"), "回执要说清级联条数：${state.notice}")
        assertEquals(1, viewModel.versionDeletions.value, "删除成功要发刷新事件")
        Dispatchers.resetMain()
    }

    @Test
    fun `confirmDelete refuses the only version without a refresh event`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeVersionRepository(outcome = VersionDeleteOutcome.RefusedLastVersion)
        val viewModel = ArticleVersionViewModel(repository, Dispatchers.Unconfined)

        viewModel.requestDelete(article())
        advanceUntilIdle()
        viewModel.confirmDelete()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.pending)
        assertEquals(true, state.notice?.contains("唯一版本"), "拒绝文案要说为什么不许删：${state.notice}")
        assertEquals(0, viewModel.versionDeletions.value)
        Dispatchers.resetMain()
    }

    @Test
    fun `confirmDelete storage failure keeps deletions unsignalled`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = ArticleVersionViewModel(FakeVersionRepository(deleteFailure = true), Dispatchers.Unconfined)

        viewModel.requestDelete(article())
        advanceUntilIdle()
        viewModel.confirmDelete()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.pending)
        assertEquals(false, state.busy)
        assertEquals(true, state.notice?.contains("存储不可用"), "失败口径与全应用一致：${state.notice}")
        assertEquals(0, viewModel.versionDeletions.value)
        Dispatchers.resetMain()
    }

    @Test
    fun `confirmDelete runs the repository once while a delete is in flight`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeVersionRepository()
        val viewModel = ArticleVersionViewModel(repository, Dispatchers.Unconfined)

        viewModel.requestDelete(article())
        advanceUntilIdle()
        viewModel.confirmDelete()
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals(1, repository.deleteCalls, "busy 闩锁必须挡住连点导致的重复删除")
        Dispatchers.resetMain()
    }

    @Test
    fun `cancelDelete closes the dialog without touching the repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeVersionRepository()
        val viewModel = ArticleVersionViewModel(repository, Dispatchers.Unconfined)

        viewModel.requestDelete(article())
        advanceUntilIdle()
        viewModel.cancelDelete()

        assertEquals(null, viewModel.uiState.value.pending)
        assertEquals(0, repository.deleteCalls)
        Dispatchers.resetMain()
    }

    private fun article(version: Int = 2) = Article(
        articleId = "a$version", profileId = "p1", localDate = "2026-09-22", activeWordBookId = "cet4",
        articleType = ArticleType.STORY, lengthTier = ArticleLengthTier.STANDARD, version = version,
        title = "A local story", englishText = "text", chineseText = "译文",
        generatedAtEpochMillis = 1L, coveredLemmas = emptyList(),
        source = ArticleSource.AiGenerated(modelName = "", parameterSummary = ""),
    )

    private class FakeVersionRepository(
        private val impact: ArticleDeletionImpact = ArticleDeletionImpact(1, 2, 0),
        private val impactFailure: Boolean = false,
        private val outcome: VersionDeleteOutcome = VersionDeleteOutcome.Deleted(ArticleDeletionImpact(1, 2, 0)),
        private val deleteFailure: Boolean = false,
    ) : ArticleVersionRepository {
        var deleteCalls = 0

        override suspend fun deletionImpact(article: Article): Result<ArticleDeletionImpact> =
            if (impactFailure) Result.failure(IllegalStateException("storage")) else Result.success(impact)

        override suspend fun deleteVersion(article: Article): Result<VersionDeleteOutcome> {
            deleteCalls++
            return if (deleteFailure) Result.failure(IllegalStateException("storage")) else Result.success(outcome)
        }
    }
}
