package com.example.englishlearning.learning

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * F4-01 决策⑤：错词直接从 `learning_events` 派生，无新表、无新写路径——
 * 「当前还在错」的状态每次现算，挽回为其他反馈后自动消失，列表只反映当下。
 */
class RoomWrongWordsRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : WrongWordsRepository {
    override suspend fun wrongWords(profileId: String): Result<List<WrongWord>> = withContext(ioDispatcher) {
        try {
            Result.success(
                database.internalLearningStatsDao().wrongWordRows(profileId).map { row ->
                    WrongWord(
                        cardId = row.cardId,
                        wordBookId = row.wordBookId,
                        lastFeedbackAtEpochMillis = row.lastFeedbackAtEpochMillis,
                        nextReviewAtEpochMillis = row.nextReviewAtEpochMillis,
                    )
                },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.failure(AppErrorException(AppError.StorageUnavailable))
        }
    }
}
