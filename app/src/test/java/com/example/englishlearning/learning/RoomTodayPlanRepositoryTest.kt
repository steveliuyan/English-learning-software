package com.example.englishlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

class RoomTodayPlanRepositoryTest {
    @Test
    fun `missing plan returns not found`() = runTest {
        withRepository { repository ->
            assertEquals(TodayPlanResult.NotFound, repository.find("profile-1", LocalDate.parse("2026-09-19")))
        }
    }

    @Test
    fun `saved plan round trips exact fields and task ordering`() = runTest {
        withRepository { repository ->
            val plan = plan(newCards = listOf("new-2", "new-1"), dueCards = listOf("due-2", "due-1"))

            assertEquals(TodayPlanResult.Ready(plan), repository.saveIfAbsent(plan))
            assertEquals(TodayPlanResult.Ready(plan), repository.find(plan.profileId, plan.localDate))
        }
    }

    @Test
    fun `unique conflict returns persisted snapshot without replacing tasks`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "today-plan-conflict-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        try {
            val first = plan(newCards = listOf("new-1"), dueCards = listOf("due-1"))
            val replacement = first.copy(
                planId = "plan-replacement",
                newCardIds = listOf("new-replacement"),
                dueCardIds = listOf("due-replacement"),
            )
            database.internalTodayPlanDao().insertIfAbsent(
                todayPlanEntity(first),
                todayPlanTasks(first),
            )

            val repository = RoomTodayPlanRepository(database, Dispatchers.Unconfined)

            assertEquals(TodayPlanResult.Ready(first), repository.saveIfAbsent(replacement))
            assertEquals(TodayPlanResult.Ready(first), repository.find(first.profileId, first.localDate))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `closed database returns stable storage unavailable without SQL leakage`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "closed-today-plan-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        val repository = RoomTodayPlanRepository(database, Dispatchers.Unconfined)
        database.close()

        assertEquals(TodayPlanResult.StorageUnavailable, repository.find("profile-1", LocalDate.parse("2026-09-19")))
        context.deleteDatabase(name)
    }

    private suspend fun withRepository(block: suspend (RoomTodayPlanRepository) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "today-plan-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        try {
            block(RoomTodayPlanRepository(database, Dispatchers.Unconfined))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun openDatabase(context: Context, name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()

    private fun todayPlanEntity(plan: TodayPlan) =
        TodayPlanEntity(
            planId = plan.planId,
            profileId = plan.profileId,
            localDate = plan.localDate.toString(),
            zoneId = plan.zoneId,
            activeWordBookId = plan.activeWordBookId,
            newTarget = plan.newTarget,
            dueTarget = plan.dueTarget,
            ruleVersion = plan.ruleVersion,
            generatedAtEpochMillis = plan.generatedAt.toEpochMilli(),
        )

    private fun todayPlanTasks(plan: TodayPlan): List<TodayPlanTaskEntity> =
        plan.newCardIds.mapIndexed { ordinal, cardId ->
            TodayPlanTaskEntity(plan.planId, cardId, "NEW", ordinal)
        } + plan.dueCardIds.mapIndexed { ordinal, cardId ->
            TodayPlanTaskEntity(plan.planId, cardId, "DUE", ordinal)
        }

    private fun plan(newCards: List<String>, dueCards: List<String>) =
        TodayPlan(
            planId = "plan-1",
            profileId = "profile-1",
            localDate = LocalDate.parse("2026-09-19"),
            zoneId = "Asia/Shanghai",
            activeWordBookId = "word-book-1",
            newTarget = newCards.size,
            dueTarget = dueCards.size,
            newCardIds = newCards,
            dueCardIds = dueCards,
            ruleVersion = "v1",
            generatedAt = Instant.parse("2026-09-18T16:00:00Z"),
        )
}
