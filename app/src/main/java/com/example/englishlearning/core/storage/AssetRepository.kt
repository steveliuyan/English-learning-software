package com.example.englishlearning.core.storage

import com.example.englishlearning.core.error.AppError
import kotlinx.coroutines.CoroutineDispatcher
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
            } catch (_: Exception) {
                Result.failure(AppErrorException(AppError.DatabaseMigrationFailed))
            }
        }

    private fun com.example.englishlearning.core.storage.entity.AssetRecordEntity.toRecord(): AssetRecord =
        AssetRecord(
            id = id,
            sha256 = sha256,
        )
}
