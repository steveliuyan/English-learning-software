package com.example.englishlearning.core.storage

import com.example.englishlearning.core.error.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.UUID

class AssetRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun get(id: UUID): Result<AssetRecord?> =
        withContext(ioDispatcher) {
            try {
                database.internalAssetDao().findById(id.toString())?.toRecord()?.let(Result.Companion::success)
                    ?: Result.success(null)
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

    private fun com.example.englishlearning.core.storage.entity.AssetRecordEntity.toRecord(): AssetRecord =
        AssetRecord(
            id = id,
            sha256 = sha256,
        )
}
