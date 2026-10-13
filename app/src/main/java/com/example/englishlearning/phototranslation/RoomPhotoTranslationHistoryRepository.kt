package com.example.englishlearning.phototranslation

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.FileOps
import com.example.englishlearning.core.storage.PrivateMediaStore
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.PhotoTranslationRecordEntity
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.language.domain.OcrEngineKind
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Room + 私有资产区的拍照翻译历史实现。
 *
 * 文本记录进 `photo_translation_records`；原图字节走 [PrivateMediaStore.writeVerified]
 * （tmp 写入 → SHA-256 校验 → 原子发布），登记进 `asset_records`，因此 F4-03 备份的
 * 媒体导出（`rows.assetRecords` → `resources/<id>`）在「包含媒体」开关打开时自动带走，
 * 无需单独的媒体白名单。删除记录时级联删除资产行与文件。
 */
class RoomPhotoTranslationHistoryRepository(
    private val database: AppDatabase,
    private val media: PrivateMediaStore,
    private val files: FileOps,
    private val clock: ClockProvider,
    private val ioDispatcher: CoroutineDispatcher,
) : PhotoTranslationHistoryRepository {
    override suspend fun save(record: SavedPhotoTranslation): Result<Unit> = runStorage {
        database.internalPhotoTranslationDao().insert(record.toEntity())
    }

    override suspend fun listRecent(profileId: String): Result<List<SavedPhotoTranslation>> = runStorage {
        database.internalPhotoTranslationDao().listRecent(profileId, LIST_LIMIT).map { it.toDomain() }
    }

    override suspend fun delete(
        profileId: String,
        recordId: String,
    ): Result<Unit> = runStorage {
        val dao = database.internalPhotoTranslationDao()
        val assetId = dao.imageAssetIdFor(profileId, recordId)
        dao.deleteOne(profileId, recordId)
        if (assetId != null) deleteAsset(assetId)
    }

    override suspend fun storeImage(bytes: ByteArray): Result<String> =
        try {
            withContext(ioDispatcher) {
                val assetId = UUID.randomUUID()
                val sha256 = sha256Of(bytes)
                media
                    .writeVerified(assetId, bytes.inputStream(), sha256)
                    .mapCatching {
                        database.internalAssetDao()
                            .upsert(
                                AssetRecordEntity(
                                    id = assetId.toString(),
                                    sha256 = sha256,
                                    relativePath = "assets/$assetId",
                                    byteSize = bytes.size.toLong(),
                                    createdAt = clock.instant().toEpochMilli(),
                                ),
                            )
                        assetId.toString()
                    }
            }
        } catch (cancellation: CancellationException) {
            if (currentCoroutineContext().isActive) {
                Result.failure(AppErrorException(AppError.StorageUnavailable))
            } else {
                throw cancellation
            }
        } catch (_: Exception) {
            Result.failure(AppErrorException(AppError.StorageUnavailable))
        }

    override suspend fun removeImage(assetId: String): Result<Unit> = runStorage { deleteAsset(assetId) }

    private suspend fun deleteAsset(assetId: String) {
        database.internalAssetDao().deleteById(assetId)
        files.delete(assetId)
    }

    private fun sha256Of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte -> "%02x".format(byte) }

    private suspend fun <T> runStorage(block: suspend () -> T): Result<T> =
        try {
            Result.success(withContext(ioDispatcher) { block() })
        } catch (cancellation: CancellationException) {
            if (currentCoroutineContext().isActive) {
                Result.failure(AppErrorException(AppError.StorageUnavailable))
            } else {
                throw cancellation
            }
        } catch (error: AppErrorException) {
            Result.failure(error)
        } catch (_: Exception) {
            Result.failure(AppErrorException(AppError.StorageUnavailable))
        }

    private fun SavedPhotoTranslation.toEntity() = PhotoTranslationRecordEntity(
        recordId = recordId,
        profileId = profileId,
        sourceText = sourceText,
        translatedText = translatedText,
        engine = engine?.name ?: ENGINE_NONE,
        imageAssetId = imageAssetId,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    private fun PhotoTranslationRecordEntity.toDomain() = SavedPhotoTranslation(
        recordId = recordId,
        profileId = profileId,
        sourceText = sourceText,
        translatedText = translatedText,
        engine = engineOf(engine),
        imageAssetId = imageAssetId,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    private fun engineOf(
        value: String,
    ): OcrEngineKind? = OcrEngineKind.entries.firstOrNull { it.name == value }

    companion object {
        const val LIST_LIMIT: Int = 100
        private const val ENGINE_NONE = "None"
    }
}
