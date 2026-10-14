package com.example.englishlearning.learning

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.dao.PlanRow
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.domain.DailyLearningHistory
import com.example.englishlearning.learning.domain.DailyLearningStats
import com.example.englishlearning.learning.domain.LearningTotals
import com.example.englishlearning.learning.domain.computeStreak
import java.time.Instant
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

    override suspend fun historyRange(profileId: String, from: LocalDate, to: LocalDate): Result<List<DailyLearningHistory>> =
        withContext(ioDispatcher) {
            if (from.isAfter(to)) {
                Result.failure(IllegalArgumentException("from must not be after to"))
            } else {
                guarded { loadHistory(profileId, from, to) }
            }
        }

    override suspend fun totals(profileId: String): Result<LearningTotals> = withContext(ioDispatcher) {
        guarded {
            val statsDao = database.internalLearningStatsDao()
            LearningTotals(
                learnedWordCount = statsDao.totalLearnedCards(profileId),
                reviewEventCount = statsDao.totalReviewEvents(profileId),
            )
        }
    }

    override suspend fun streak(profileId: String, today: LocalDate): Result<Int> = withContext(ioDispatcher) {
        guarded {
            // 连续天数至多覆盖设备生命周期内的日历日；两年回溯窗口在 Spec 场景内是上限保护而非截断。
            val history = loadHistory(profileId, today.minusDays(STREAK_LOOKBACK_DAYS), today)
            computeStreak(history.associate { it.localDate to it.unlocked }, today)
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.failure(AppErrorException(AppError.StorageUnavailable))
        }

    /**
     * Assemble per-day history. Display done counts come from the SQL fast path
     * ([com.example.englishlearning.core.storage.dao.InternalLearningStatsDao.dailyDoneByTaskKind],
     * clamped to targets), while the unlock decision MUST go through
     * [UnlockPolicy.isUnlocked] on the reconstructed snapshot — the two calibers are pinned
     * equivalent by the connected test `DailyHistoryAcrossDaysTest`.
     */
    private suspend fun loadHistory(profileId: String, from: LocalDate, to: LocalDate): List<DailyLearningHistory> {
        val statsDao = database.internalLearningStatsDao()
        val fromString = from.toString()
        val toString = to.toString()
        val planRows = statsDao.planRows(profileId, fromString, toString)
        val tasksByPlan = statsDao.planTaskRowsForRange(profileId, fromString, toString).groupBy { it.planId }
        val completedByPlan = statsDao.completedCardsForRange(profileId, fromString, toString)
            .groupBy({ it.planId }, { it.cardId })
            .mapValues { (_, cardIds) -> cardIds.toSet() }
        val doneRows = statsDao.dailyDoneByTaskKind(profileId, fromString, toString)
        val readings = statsDao.countReadings(profileId, fromString, toString)
            .associate { LocalDate.parse(it.localDate) to it.count }

        return planRows.map { row ->
            val date = LocalDate.parse(row.localDate)
            val tasks = tasksByPlan[row.planId].orEmpty()
            val plan = TodayPlan(
                planId = row.planId,
                profileId = row.profileId,
                localDate = date,
                zoneId = row.zoneId,
                activeWordBookId = row.activeWordBookId,
                newTarget = row.newTarget,
                dueTarget = row.dueTarget,
                newCardIds = tasks.filter { it.taskKind == NEW_TASK_KIND }.map { it.cardId },
                dueCardIds = tasks.filter { it.taskKind == DUE_TASK_KIND }.map { it.cardId },
                ruleVersion = row.ruleVersion,
                generatedAt = Instant.ofEpochMilli(row.generatedAtEpochMillis),
            )
            val completed = completedByPlan[row.planId].orEmpty()
            val mode = UnlockPolicy.modeForRuleVersion(row.ruleVersion)
            DailyLearningHistory(
                localDate = date,
                activeWordBookId = row.activeWordBookId,
                activeWordBookName = row.displayName ?: UNKNOWN_WORD_BOOK_NAME,
                newDone = doneRows.filter { it.localDate == row.localDate && it.taskKind == NEW_TASK_KIND }.sumOf { it.done }.coerceAtMost(row.newTarget),
                newTarget = row.newTarget,
                dueDone = doneRows.filter { it.localDate == row.localDate && it.taskKind == DUE_TASK_KIND }.sumOf { it.done }.coerceAtMost(row.dueTarget),
                dueTarget = row.dueTarget,
                unlockMode = mode,
                unlocked = UnlockPolicy.isUnlocked(plan, completed, mode),
                articleGenerated = row.articleGenerated,
                articleRead = (readings[date] ?: 0) > 0,
            )
        }
    }

    private companion object {
        const val STREAK_LOOKBACK_DAYS = 730L
        const val UNKNOWN_WORD_BOOK_NAME = "未知词书"
        const val NEW_TASK_KIND = "NEW"
        const val DUE_TASK_KIND = "DUE"
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
