package com.example.englishlearning.notification

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.entity.ReminderPreferenceEntity
import com.example.englishlearning.notification.domain.ReminderPreference
import com.example.englishlearning.notification.domain.ReminderPreferenceRepository
import com.example.englishlearning.notification.domain.reminderPreferenceFrom
import com.example.englishlearning.notification.domain.toMinutesOfDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class RoomReminderPreferenceRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : ReminderPreferenceRepository {

    override suspend fun get(): Result<ReminderPreference> = runStorage {
        (database.internalReminderPreferenceDao().find() ?: ReminderPreferenceEntity()).toDomain()
    }

    override suspend fun save(preference: ReminderPreference): Result<Unit> = runStorage {
        database.internalReminderPreferenceDao().upsert(
            ReminderPreferenceEntity(
                reminderId = "device",
                enabled = preference.enabled,
                minutesOfDay = preference.toMinutesOfDay(),
            ),
        )
    }

    private suspend fun <T> runStorage(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(ioDispatcher) { block() })
    } catch (cancellation: CancellationException) {
        if (currentCoroutineContext().isActive) {
            Result.failure(AppErrorException(AppError.StorageUnavailable))
        } else {
            throw cancellation
        }
    } catch (_: Exception) {
        Result.failure(AppErrorException(AppError.StorageUnavailable))
    }

    private fun ReminderPreferenceEntity.toDomain() =
        reminderPreferenceFrom(minutesOfDay, enabled)
}
