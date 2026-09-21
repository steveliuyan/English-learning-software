package com.example.englishlearning.core.storage

import com.example.englishlearning.core.error.AppError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class PrivateMediaStore(
    private val files: FileOps,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun writeVerified(
        assetId: UUID,
        source: InputStream,
        expectedSha256: String,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            val finalName = assetId.toString()
            val temporaryName = "$finalName.tmp"
            var published = false
            try {
                files.delete(temporaryName)
                files.write(temporaryName, source)
                if (!files.sha256(temporaryName).equals(expectedSha256, ignoreCase = true)) {
                    return@withContext Result.failure(AppErrorException(AppError.IntegrityMismatch))
                }
                files.atomicMove(temporaryName, finalName)
                published = true
                Result.success(Unit)
            } catch (error: java.io.IOException) {
                Result.failure(error.toAppErrorException())
            } catch (error: SecurityException) {
                Result.failure(error.toAppErrorException())
            } catch (error: UnsupportedOperationException) {
                Result.failure(error.toAppErrorException())
            } finally {
                files.delete(temporaryName)
                if (!published) files.delete(finalName)
            }
        }

    fun availability(asset: AssetRecord): MediaAvailability =
        try {
            if (files.exists(asset.id) && files.sha256(asset.id).equals(asset.sha256, ignoreCase = true)) {
                MediaAvailability.Available
            } else {
                MediaAvailability.UnavailableRebuildable
            }
        } catch (_: IOException) {
            MediaAvailability.UnavailableRebuildable
        } catch (_: SecurityException) {
            MediaAvailability.UnavailableRebuildable
        }
}

class AppErrorException(val appError: AppError) : RuntimeException(null, null, false, false)

private fun Exception.toAppErrorException(): AppErrorException =
    when (this) {
        is AppErrorException -> this
        is IOException -> AppErrorException(AppError.StorageUnavailable)
        else -> AppErrorException(AppError.StorageUnavailable)
    }
