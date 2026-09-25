package com.example.englishlearning.learning

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.domain.DailyLearningStats
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class RoomLearningStatsRepository(
    private val database: AppDatabase,
    private val clock: ClockProvider,
    private val ioDispatcher: CoroutineDispatcher,
) : LearningStatsRepository {
    override suspend fun today(profileId: String, localDate: LocalDate): Result<DailyLearningStats> = withContext(ioDispatcher) {
        try {
            Result.success(load(profileId, localDate, localDate).single())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.failure(AppErrorException(AppError.StorageUnavailable))
        }
    }

    override suspend fun range(profileId: String, from: LocalDate, to: LocalDate): Result<List<DailyLearningStats>> = withContext(ioDispatcher) {
        if (from.isAfter(to)) {
            Result.failure(IllegalArgumentException("from must not be after to"))
        } else {
            try {
                Result.success(load(profileId, from, to))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                Result.failure(AppErrorException(AppError.StorageUnavailable))
            }
        }
    }

    private suspend fun load(profileId: String, from: LocalDate, to: LocalDate): List<DailyLearningStats> {
        val statsDao = database.internalLearningStatsDao()
        val plansDao = database.internalTodayPlanDao()
        val zone = clock.zoneId()
        val readings = statsDao.countReadings(profileId, from.toString(), to.toString()).associate { LocalDate.parse(it.localDate) to it.count }
        val result = mutableListOf<DailyLearningStats>()
        var date = from
        while (!date.isAfter(to)) {
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val reviewed = statsDao.countReviewedCards(profileId, start, end)
            val plan = plansDao.findPlan(profileId, date.toString())
            val target = plan?.let { it.newTarget + it.dueTarget } ?: 0
            val readingCount = readings[date] ?: 0
            result += DailyLearningStats(date, reviewed, readingCount, reviewed + readingCount, target)
            date = date.plusDays(1)
        }
        return result
    }
}
