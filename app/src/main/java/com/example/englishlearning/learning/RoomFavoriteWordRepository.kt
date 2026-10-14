package com.example.englishlearning.learning

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.entity.FavoriteWordEntity
import com.example.englishlearning.core.time.ClockProvider
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * F4-01 决策⑦收藏仓储。写序与错误映射沿用 [RoomLearningStatsRepository] 的口径：
 * 单条 SQL 即一个原子操作，异常收敛为 `AppErrorException(StorageUnavailable)`，
 * 协程取消照常上抛。`addedAt` 取注入时钟而非系统时间，保证测试可控。
 */
class RoomFavoriteWordRepository(
    private val database: AppDatabase,
    private val clock: ClockProvider,
    private val ioDispatcher: CoroutineDispatcher,
) : FavoriteWordRepository {
    override suspend fun toggle(profileId: String, cardId: String, wordBookId: String, lemma: String): Result<Unit> =
        withContext(ioDispatcher) {
            guarded {
                val dao = database.internalFavoriteWordDao()
                if (dao.isFavorite(profileId, cardId)) {
                    dao.delete(profileId, cardId)
                } else {
                    dao.insertIgnore(
                        FavoriteWordEntity(
                            profileId = profileId,
                            cardId = cardId,
                            wordBookId = wordBookId,
                            lemma = lemma,
                            addedAtEpochMillis = clock.instant().toEpochMilli(),
                        ),
                    )
                }
                Unit
            }
        }

    override suspend fun isFavorite(profileId: String, cardId: String): Result<Boolean> =
        withContext(ioDispatcher) {
            guarded { database.internalFavoriteWordDao().isFavorite(profileId, cardId) }
        }

    override suspend fun listFavorites(profileId: String): Result<List<FavoriteWord>> =
        withContext(ioDispatcher) {
            guarded {
                // 顺序由 DAO 查询（addedAt 降序 + cardId 平局裁决）给出，仓储不重排。
                database.internalFavoriteWordDao().listForProfile(profileId).map { entity ->
                    FavoriteWord(
                        profileId = entity.profileId,
                        cardId = entity.cardId,
                        wordBookId = entity.wordBookId,
                        lemma = entity.lemma,
                        addedAt = Instant.ofEpochMilli(entity.addedAtEpochMillis),
                    )
                }
            }
        }

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.failure(AppErrorException(AppError.StorageUnavailable))
        }
}
