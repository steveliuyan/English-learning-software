package com.example.englishlearning.ui

import com.example.englishlearning.learning.LearningDataDeletionImpact
import com.example.englishlearning.learning.LearningDataDeletionRepository
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
 * F4-01 决策 §9「删除全部学习数据」ViewModel 状态机：入口只做影响预览，
 * 确认框未点「确认删除」前仓储 deleteAllLearningData 必须零调用（JVM 假仓储记数）；
 * 只有真删成功才递增 dataDeletions 让宿主重放今日/统计/错词加载。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LearningDataDeletionViewModelTest {
    @Test
    fun `requestDelete fills pending with the cascade preview and deletes nothing yet`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeDeletionRepository(impact = impact())
        val viewModel = LearningDataDeletionViewModel(repository, Dispatchers.Unconfined)

        viewModel.requestDelete("p1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(impact(), state.pending)
        assertEquals(null, state.notice)
        assertEquals(0, repository.deleteCalls, "确认框还没点「确认删除」就必须零删除")
        Dispatchers.resetMain()
    }

    @Test
    fun `requestDelete failure leaves an honest notice and no dialog`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = LearningDataDeletionViewModel(FakeDeletionRepository(impactFailure = true), Dispatchers.Unconfined)

        viewModel.requestDelete("p1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.pending)
        assertEquals(true, state.notice?.contains("稍后重试"), "预览失败要给出可重试文案：${state.notice}")
        Dispatchers.resetMain()
    }

    @Test
    fun `cancelDelete closes the dialog without touching the repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeDeletionRepository()
        val viewModel = LearningDataDeletionViewModel(repository, Dispatchers.Unconfined)

        viewModel.requestDelete("p1")
        advanceUntilIdle()
        viewModel.cancelDelete()

        assertEquals(null, viewModel.uiState.value.pending)
        assertEquals(0, repository.deleteCalls)
        Dispatchers.resetMain()
    }

    @Test
    fun `confirmDelete wipes announces retention and signals refresh`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeDeletionRepository(outcome = impact())
        val viewModel = LearningDataDeletionViewModel(repository, Dispatchers.Unconfined)

        viewModel.requestDelete("p1")
        advanceUntilIdle()
        viewModel.confirmDelete("p1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.pending)
        assertEquals(false, state.busy)
        assertEquals(true, state.notice?.contains("已删除"), "成功要回执：${state.notice}")
        assertEquals(true, state.notice?.contains("学习记录 7 条"), "回执要说清删除条数：${state.notice}")
        assertEquals(true, state.notice?.contains("文章 3 篇与收藏 2 条保留"), "回执要说清保留面：${state.notice}")
        assertEquals(1, viewModel.dataDeletions.value, "删除成功要发刷新事件")
        Dispatchers.resetMain()
    }

    @Test
    fun `confirmDelete storage failure keeps deletions unsignalled`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = LearningDataDeletionViewModel(FakeDeletionRepository(deleteFailure = true), Dispatchers.Unconfined)

        viewModel.requestDelete("p1")
        advanceUntilIdle()
        viewModel.confirmDelete("p1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.pending)
        assertEquals(false, state.busy)
        assertEquals(true, state.notice?.contains("存储不可用"), "失败口径与全应用一致：${state.notice}")
        assertEquals(0, viewModel.dataDeletions.value)
        Dispatchers.resetMain()
    }

    @Test
    fun `confirmDelete runs the repository once while a delete is in flight`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeDeletionRepository()
        val viewModel = LearningDataDeletionViewModel(repository, Dispatchers.Unconfined)

        viewModel.requestDelete("p1")
        advanceUntilIdle()
        viewModel.confirmDelete("p1")
        viewModel.confirmDelete("p1")
        advanceUntilIdle()

        assertEquals(1, repository.deleteCalls, "busy 闩锁必须挡住连点导致的重复删除")
        Dispatchers.resetMain()
    }

    private fun impact() = LearningDataDeletionImpact(
        eventCount = 7, reviewStateCount = 5, planCount = 4, completionCount = 6, migrationAuditCount = 1,
        keptArticleCount = 3, keptFavoriteCount = 2,
    )

    private class FakeDeletionRepository(
        private val impact: LearningDataDeletionImpact = LearningDataDeletionImpact(0, 0, 0, 0, 0, 0, 0),
        private val impactFailure: Boolean = false,
        private val outcome: LearningDataDeletionImpact = LearningDataDeletionImpact(0, 0, 0, 0, 0, 0, 0),
        private val deleteFailure: Boolean = false,
    ) : LearningDataDeletionRepository {
        var deleteCalls = 0

        override suspend fun deletionImpact(profileId: String): Result<LearningDataDeletionImpact> =
            if (impactFailure) Result.failure(IllegalStateException("storage")) else Result.success(impact)

        override suspend fun deleteAllLearningData(profileId: String): Result<LearningDataDeletionImpact> {
            deleteCalls++
            return if (deleteFailure) Result.failure(IllegalStateException("storage")) else Result.success(outcome)
        }
    }
}
