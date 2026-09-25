package com.example.englishlearning.learning

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.domain.DailyLearningStats
import java.time.LocalDate

class RoomLearningStatsRepository(
    private val database: AppDatabase,
    private val clock: ClockProvider,
) : LearningStatsRepository {
    override fun today(profileId: String, localDate: LocalDate): Result<DailyLearningStats> =
        runCatching { load(profileId, localDate, localDate).single() }
            .recoverCatching { throw AppErrorException(AppError.StorageUnavailable) }

    override fun range(profileId: String, from: LocalDate, to: LocalDate): Result<List<DailyLearningStats>> =
        if (from.isAfter(to)) {
            Result.failure(IllegalArgumentException("from must not be after to"))
        } else {
            runCatching { load(profileId, from, to) }
                .recoverCatching { throw AppErrorException(AppError.StorageUnavailable) }
        }

    private fun load(profileId: String, from: LocalDate, to: LocalDate): List<DailyLearningStats> {
        val statsDao = database.internalLearningStatsDao()
        val plansDao = database.internalTodayPlanDao()
        val zone = clock.zoneId()
        val readings = statsDao.countReadings(profileId, from.toString(), to.toString()).associate { LocalDate.parse(it.localDate) to it.count }
        return generateSequence(from) { date -> date.plusDays(1).takeUnless { it.isAfter(to) } }
            .map { date ->
                val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val reviewed = statsDao.countReviewedCards(profileId, start, end)
                val plan = plansDao.findPlan(profileId, date.toString())
                val target = plan?.let { it.newTarget + it.dueTarget } ?: 0
                val readingCount = readings[date] ?: 0
                DailyLearningStats(date, reviewed, readingCount, reviewed + readingCount, target)
            }.toList()
    }
}
