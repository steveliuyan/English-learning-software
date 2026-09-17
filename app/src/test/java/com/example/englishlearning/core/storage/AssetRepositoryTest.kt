package com.example.englishlearning.core.storage

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.dao.InternalAssetDao
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AssetRepositoryTest {
    @Test
    fun `get returns only asset metadata from database dispatcher`() =
        runTest {
            val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val database = mockk<AppDatabase>()
            val dao = mockk<InternalAssetDao>()
            val executor = Executors.newSingleThreadExecutor()
            try {
                coEvery { database.internalAssetDao() } returns dao
                coEvery { dao.findById(id.toString()) } returns
                    AssetRecordEntity(
                        id = id.toString(),
                        sha256 = "abc",
                        relativePath = id.toString(),
                        byteSize = 3,
                        createdAt = 1,
                    )

                val result = AssetRepository(database, executor.asCoroutineDispatcher()).get(id)

                assertEquals(AssetRecord(id.toString(), "abc"), result.getOrThrow())
            } finally {
                executor.shutdownNow()
            }
        }

    @Test
    fun `get maps database failures without leaking storage details`() =
        runTest {
            val database = mockk<AppDatabase>()
            coEvery { database.internalAssetDao() } throws IllegalStateException("SELECT private/path")
            val executor = Executors.newSingleThreadExecutor()
            try {
                val result = AssetRepository(database, executor.asCoroutineDispatcher()).get(UUID.randomUUID())

                assertEquals(AppError.DatabaseMigrationFailed, assertIs<AppErrorException>(result.exceptionOrNull()).appError)
            } finally {
                executor.shutdownNow()
            }
        }
}
