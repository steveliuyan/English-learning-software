package com.example.englishlearning.core.storage

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.dao.InternalAssetDao
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

                assertEquals(AppError.StorageUnavailable, assertIs<AppErrorException>(result.exceptionOrNull()).appError)
            } finally {
                executor.shutdownNow()
            }
        }

    @Test
    fun `get rethrows cancellation when the caller coroutine is already cancelled`() =
        runTest {
            val id = UUID.fromString("00000000-0000-0000-0000-000000000003")
            val database = mockk<AppDatabase>()
            val dao = mockk<InternalAssetDao>()
            val cancellation = CancellationException("caller cancelled")
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            coEvery { database.internalAssetDao() } returns dao
            coEvery { dao.findById(id.toString()) } answers {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                throw cancellation
            }
            val executor = Executors.newSingleThreadExecutor()
            try {
                val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    assertFailsWith<CancellationException> {
                        AssetRepository(database, executor.asCoroutineDispatcher()).get(id)
                    }
                }
                check(entered.await(5, TimeUnit.SECONDS))
                job.cancel(cancellation)
                release.countDown()
                job.join()
            } finally {
                executor.shutdownNow()
            }
        }

    @Test
    fun `get maps internal-scope cancellation to a storage failure while the caller stays active`() =
        runTest {
            val id = UUID.fromString("00000000-0000-0000-0000-000000000002")
            val database = mockk<AppDatabase>()
            val dao = mockk<InternalAssetDao>()
            // Closed-database reads surface as a CancellationException raised on Room's own
            // scope while the caller's coroutine is still active; that must be mapped, not
            // swallowed as a plain storage failure nor leaked as a caller cancellation.
            coEvery { database.internalAssetDao() } returns dao
            coEvery { dao.findById(id.toString()) } throws CancellationException("closed database access")
            val executor = Executors.newSingleThreadExecutor()
            try {
                val result = AssetRepository(database, executor.asCoroutineDispatcher()).get(id)

                assertEquals(
                    AppError.StorageUnavailable,
                    assertIs<AppErrorException>(result.exceptionOrNull()).appError,
                )
            } finally {
                executor.shutdownNow()
            }
        }
}
