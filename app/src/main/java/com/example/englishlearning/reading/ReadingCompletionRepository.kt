package com.example.englishlearning.reading

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.ReadingCompletionEntity
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.core.time.ClockProvider
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** F3-02：阅读完成记录端口。幂等语义由实现保证（articleId 主键）。 */
interface ReadingCompletionRepository {
    /** 首记返回 true；该文章此前已完成返回 false；存储失败返回 failure。 */
    suspend fun record(article: Article): Result<Boolean>

    suspend fun completionsToday(profileId: String): Result<Int>
}

class RoomReadingCompletionRepository @Inject constructor(
    private val database: AppDatabase,
    private val clock: ClockProvider,
    private val ioDispatcher: CoroutineDispatcher,
) : ReadingCompletionRepository {
    override suspend fun record(article: Article): Result<Boolean> = guarded {
        val today = clock.instant().atZone(clock.zoneId()).toLocalDate().toString()
        val inserted = database.internalReadingCompletionDao().insert(
            ReadingCompletionEntity(
                articleId = article.articleId,
                profileId = article.profileId,
                localDate = today,
                completedAtEpochMillis = clock.instant().toEpochMilli(),
            ),
        )
        inserted != -1L
    }

    override suspend fun completionsToday(profileId: String): Result<Int> = guarded {
        val today = clock.instant().atZone(clock.zoneId()).toLocalDate().toString()
        database.internalReadingCompletionDao().countForDate(profileId, today)
    }

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(ioDispatcher) { block() })
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(AppErrorException(AppError.StorageUnavailable))
    }
}
