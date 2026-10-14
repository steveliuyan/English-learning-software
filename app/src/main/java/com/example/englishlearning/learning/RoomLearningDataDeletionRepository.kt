package com.example.englishlearning.learning

import androidx.room.withTransaction
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * F4-01 决策 §9「删除全部学习数据」的 Room 实现。SQL 口径由 androidTest
 * `RoomLearningDataDeletionTest` 真库兜底：删五类（事件/调度态/计划+任务/阅读完成/迁移审计），
 * 文章、收藏、搜索历史与别档案行零触碰。
 *
 * **写序**：预览只读；删除在单一事务里**先数后删**——返回值即实删条数，
 * 回执与确认框因此出自同一批真数。任一步异常整体回滚，不存在「删了一半」的状态。
 */
class RoomLearningDataDeletionRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : LearningDataDeletionRepository {
    override suspend fun deletionImpact(profileId: String): Result<LearningDataDeletionImpact> = runStorage {
        withContext(ioDispatcher) { readImpact(database, profileId) }
    }

    override suspend fun deleteAllLearningData(profileId: String): Result<LearningDataDeletionImpact> = runStorage {
        withContext(ioDispatcher) {
            database.withTransaction {
                val impact = readImpact(database, profileId)
                val events = database.internalLearningEventDao()
                events.deleteEventsForProfile(profileId)
                events.deleteAllReviewStates()
                val plans = database.internalTodayPlanDao()
                plans.deleteTasksForProfile(profileId)
                plans.deleteForProfile(profileId)
                database.internalReadingCompletionDao().deleteForProfile(profileId)
                database.internalWordBookProgressMigrationAuditDao().deleteForProfile(profileId)
                impact
            }
        }
    }

    private suspend fun readImpact(database: AppDatabase, profileId: String): LearningDataDeletionImpact {
        val events = database.internalLearningEventDao()
        val plans = database.internalTodayPlanDao()
        return LearningDataDeletionImpact(
            eventCount = events.countEventsForProfile(profileId),
            reviewStateCount = events.countAllReviewStates(),
            planCount = plans.countForProfile(profileId),
            completionCount = database.internalReadingCompletionDao().countForProfile(profileId),
            migrationAuditCount = database.internalWordBookProgressMigrationAuditDao().countForProfile(profileId),
            keptArticleCount = database.internalArticleDao().countForProfile(profileId),
            keptFavoriteCount = database.internalFavoriteWordDao().countForProfile(profileId),
        )
    }

    private suspend fun <T> runStorage(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        if (currentCoroutineContext().isActive) {
            Result.failure(AppErrorException(AppError.StorageUnavailable))
        } else {
            throw cancellation
        }
    } catch (_: Exception) {
        Result.failure(AppErrorException(AppError.StorageUnavailable))
    }
}
