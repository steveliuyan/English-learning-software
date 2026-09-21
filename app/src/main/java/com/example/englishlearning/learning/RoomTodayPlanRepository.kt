package com.example.englishlearning.learning

import android.database.sqlite.SQLiteConstraintException
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

class RoomTodayPlanRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : TodayPlanRepository {
    override suspend fun find(profileId: String, localDate: LocalDate): TodayPlanResult {
        return try {
            withContext(ioDispatcher) {
                database.internalTodayPlanDao().findPlan(profileId, localDate.toString())?.let { entity ->
                    readResult(entity)
                } ?: TodayPlanResult.NotFound
            }
        } catch (cancellation: CancellationException) {
            if (currentCoroutineContext().isActive) TodayPlanResult.StorageUnavailable else throw cancellation
        } catch (_: Exception) {
            TodayPlanResult.StorageUnavailable
        }
    }

    override suspend fun saveIfAbsent(plan: TodayPlan): TodayPlanResult {
        return try {
            withContext(ioDispatcher) {
                val dao = database.internalTodayPlanDao()
                try {
                    dao.insertIfAbsent(plan.toEntity(), plan.toTaskEntities())
                    TodayPlanResult.Ready(plan)
                } catch (cancellation: CancellationException) {
                    if (currentCoroutineContext().isActive) TodayPlanResult.StorageUnavailable else throw cancellation
                } catch (failure: SQLiteConstraintException) {
                    if (failure.isTodayPlanUniqueConflict()) {
                        dao.findPlan(plan.profileId, plan.localDate.toString())?.let { entity ->
                            readResult(entity)
                        } ?: TodayPlanResult.StorageUnavailable
                    } else {
                        TodayPlanResult.StorageUnavailable
                    }
                } catch (_: Exception) {
                    TodayPlanResult.StorageUnavailable
                }
            }
        } catch (cancellation: CancellationException) {
            if (currentCoroutineContext().isActive) TodayPlanResult.StorageUnavailable else throw cancellation
        } catch (_: Exception) {
            TodayPlanResult.StorageUnavailable
        }
    }

    private suspend fun readResult(entity: TodayPlanEntity): TodayPlanResult.Ready {
        val tasks = database.internalTodayPlanDao().findTasks(entity.planId)
        return TodayPlanResult.Ready(
            TodayPlan(
                planId = entity.planId,
                profileId = entity.profileId,
                localDate = LocalDate.parse(entity.localDate),
                zoneId = entity.zoneId,
                activeWordBookId = entity.activeWordBookId,
                newTarget = entity.newTarget,
                dueTarget = entity.dueTarget,
                newCardIds = tasks.filter { it.taskKind == NEW }.map { it.cardId },
                dueCardIds = tasks.filter { it.taskKind == DUE }.map { it.cardId },
                ruleVersion = entity.ruleVersion,
                generatedAt = Instant.ofEpochMilli(entity.generatedAtEpochMillis),
            ),
        )
    }

    private fun SQLiteConstraintException.isTodayPlanUniqueConflict(): Boolean {
        val uniquePrefix = "UNIQUE constraint failed: "
        val normalizedMessage = message?.trim() ?: return false
        if (!normalizedMessage.startsWith(uniquePrefix)) return false
        val constraintDescription = normalizedMessage.removePrefix(uniquePrefix)
        return constraintDescription.substringBefore(" (code ") ==
            "today_plans.profileId, today_plans.localDate"
    }

    private fun TodayPlan.toEntity() =
        TodayPlanEntity(
            planId = planId,
            profileId = profileId,
            localDate = localDate.toString(),
            zoneId = zoneId,
            activeWordBookId = activeWordBookId,
            newTarget = newTarget,
            dueTarget = dueTarget,
            ruleVersion = ruleVersion,
            generatedAtEpochMillis = generatedAt.toEpochMilli(),
        )

    private fun TodayPlan.toTaskEntities(): List<TodayPlanTaskEntity> =
        newCardIds.mapIndexed { ordinal, cardId -> TodayPlanTaskEntity(planId, cardId, NEW, ordinal) } +
            dueCardIds.mapIndexed { ordinal, cardId -> TodayPlanTaskEntity(planId, cardId, DUE, ordinal) }

    private companion object {
        const val NEW = "NEW"
        const val DUE = "DUE"
    }
}
