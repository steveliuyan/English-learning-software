package com.example.englishlearning.phototranslation

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.FileOps
import com.example.englishlearning.core.storage.PrivateMediaStore
import com.example.englishlearning.core.storage.dao.InternalAssetDao
import com.example.englishlearning.core.storage.dao.InternalPhotoTranslationDao
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.PhotoTranslationRecordEntity
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.language.domain.OcrEngineKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Room 历史仓库边界：引擎以固定枚举名入库（None 表示手动文本）；删除按 profile 收窄并
 * 级联清理资产行与文件；原图写入走 PrivateMediaStore 的校验发布，登记 relativePath 约定。
 */
class RoomPhotoTranslationHistoryRepositoryTest {
    private val clock = FixedClockProvider(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)

    private val database = mockk<AppDatabase>()
    private val recordDao = mockk<InternalPhotoTranslationDao>(relaxed = true)
    private val assetDao = mockk<InternalAssetDao>(relaxed = true)
    private val media = mockk<PrivateMediaStore>()
    private val files = mockk<FileOps>(relaxed = true)

    @Test
    fun `save encodes engine as enum name and None for missing engine`() =
        runTest {
            val executor = Executors.newSingleThreadExecutor()
            try {
                coEvery { database.internalPhotoTranslationDao() } returns recordDao
                val inserted = mutableListOf<PhotoTranslationRecordEntity>()
                coEvery { recordDao.insert(any()) } answers {
                    inserted += firstArg<PhotoTranslationRecordEntity>()
                    Unit
                }

                val repository = repository(executor)
                repository.save(sample(engine = OcrEngineKind.Tesseract)).getOrThrow()
                repository.save(sample(recordId = "r2", engine = null)).getOrThrow()

                assertEquals("Tesseract", inserted[0].engine)
                assertEquals("r1", inserted[0].recordId)
                assertEquals("asset-9", inserted[0].imageAssetId)
                assertEquals("None", inserted[1].engine, "缺失引擎必须编码为固定字面量 None，而不是任意字符串")
                assertNull(inserted[1].imageAssetId)
            } finally {
                executor.shutdownNow()
            }
        }

    @Test
    fun `listRecent decodes known engine names and treats anything else as no engine`() =
        runTest {
            val executor = Executors.newSingleThreadExecutor()
            try {
                coEvery { database.internalPhotoTranslationDao() } returns recordDao
                coEvery { recordDao.listRecent("p1", any()) } returns
                    listOf(
                        entity("r1", "MlKit"),
                        entity("r2", "None"),
                        entity("r3", "TamperDog"),
                    )

                val records = repository(executor).listRecent("p1").getOrThrow()

                assertEquals(OcrEngineKind.MlKit, records[0].engine)
                assertNull(records[1].engine)
                assertNull(records[2].engine, "非白名单字面量不得被当作引擎值")
                assertEquals("r1", records[0].recordId)
            } finally {
                executor.shutdownNow()
            }
        }

    @Test
    fun `delete cascades the image asset row and file`() =
        runTest {
            val executor = Executors.newSingleThreadExecutor()
            try {
                coEvery { database.internalPhotoTranslationDao() } returns recordDao
                coEvery { database.internalAssetDao() } returns assetDao
                coEvery { recordDao.imageAssetIdFor("p1", "r1") } returns "asset-9"
                coEvery { recordDao.deleteOne("p1", "r1") } returns Unit
                coEvery { assetDao.deleteById("asset-9") } returns Unit

                repository(executor).delete("p1", "r1").getOrThrow()

                coVerify(exactly = 1) { recordDao.deleteOne("p1", "r1") }
                coVerify(exactly = 1) { assetDao.deleteById("asset-9") }
                coVerify(exactly = 1) { files.delete("asset-9") }
            } finally {
                executor.shutdownNow()
            }
        }

    @Test
    fun `delete of a record without an image leaves the asset layer untouched`() =
        runTest {
            val executor = Executors.newSingleThreadExecutor()
            try {
                coEvery { database.internalPhotoTranslationDao() } returns recordDao
                coEvery { recordDao.imageAssetIdFor("p1", "r1") } returns null
                coEvery { recordDao.deleteOne("p1", "r1") } returns Unit

                repository(executor).delete("p1", "r1").getOrThrow()

                coVerify(exactly = 0) { database.internalAssetDao() }
                coVerify(exactly = 0) { files.delete(any()) }
            } finally {
                executor.shutdownNow()
            }
        }

    @Test
    fun `storeImage publishes through the verified media store and registers the asset row`() =
        runTest {
            val executor = Executors.newSingleThreadExecutor()
            try {
                coEvery { database.internalAssetDao() } returns assetDao
                val published = slot<AssetRecordEntity>()
                coEvery { assetDao.upsert(capture(published)) } returns Unit
                val repository = repository(executor)
                val bytes = byteArrayOf(1, 2, 3)
                val sha = sha256Hex(bytes)
                coEvery { media.writeVerified(any(), any(), sha) } returns Result.success(Unit)

                val assetId = repository.storeImage(bytes).getOrThrow()

                coVerify(exactly = 1) { media.writeVerified(java.util.UUID.fromString(assetId), any(), sha) }
                assertEquals(assetId, published.captured.id)
                assertEquals("assets/$assetId", published.captured.relativePath)
                assertEquals(sha, published.captured.sha256)
                assertEquals(3L, published.captured.byteSize)
                assertEquals(1_700_000_000_000L, published.captured.createdAt)
            } finally {
                executor.shutdownNow()
            }
        }

    @Test
    fun `storeImage propagates the media verification failure`() =
        runTest {
            val executor = Executors.newSingleThreadExecutor()
            try {
                coEvery { media.writeVerified(any(), any(), any()) } returns
                    Result.failure(AppErrorException(AppError.IntegrityMismatch))

                val result = repository(executor).storeImage(byteArrayOf(1))

                assertEquals(AppError.IntegrityMismatch, assertIs<AppErrorException>(result.exceptionOrNull()).appError)
                coVerify(exactly = 0) { database.internalAssetDao() }
            } finally {
                executor.shutdownNow()
            }
        }

    @Test
    fun `removeImage drops the row and the file`() =
        runTest {
            val executor = Executors.newSingleThreadExecutor()
            try {
                coEvery { database.internalAssetDao() } returns assetDao
                coEvery { assetDao.deleteById("asset-9") } returns Unit

                repository(executor).removeImage("asset-9").getOrThrow()

                coVerify(exactly = 1) { assetDao.deleteById("asset-9") }
                coVerify(exactly = 1) { files.delete("asset-9") }
            } finally {
                executor.shutdownNow()
            }
        }

    @Test
    fun `dao failures map to a storage error without leaking details`() =
        runTest {
            val executor = Executors.newSingleThreadExecutor()
            try {
                coEvery { database.internalPhotoTranslationDao() } throws IllegalStateException("SELECT secret/path")

                val result = repository(executor).listRecent("p1")

                assertEquals(AppError.StorageUnavailable, assertIs<AppErrorException>(result.exceptionOrNull()).appError)
            } finally {
                executor.shutdownNow()
            }
        }

    private fun repository(executor: java.util.concurrent.ExecutorService) =
        RoomPhotoTranslationHistoryRepository(
            database,
            media,
            files,
            clock,
            executor.asCoroutineDispatcher(),
        )

    private fun sample(
        recordId: String = "r1",
        engine: OcrEngineKind? = OcrEngineKind.MlKit,
    ) = SavedPhotoTranslation(
        recordId = recordId,
        profileId = "p1",
        sourceText = "Birds fly.",
        translatedText = "鸟儿会飞。",
        engine = engine,
        imageAssetId = if (engine == null) null else "asset-9",
        createdAtEpochMillis = 1_700_000_000_000L,
    )

    private fun entity(
        recordId: String,
        engine: String,
    ) = PhotoTranslationRecordEntity(
        recordId = recordId,
        profileId = "p1",
        sourceText = "Birds fly.",
        translatedText = "鸟儿会飞。",
        engine = engine,
        imageAssetId = null,
        createdAtEpochMillis = 1_700_000_000_000L,
    )

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
