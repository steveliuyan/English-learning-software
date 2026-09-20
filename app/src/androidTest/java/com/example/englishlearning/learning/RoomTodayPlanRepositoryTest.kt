package com.example.englishlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RoomTodayPlanRepositoryTest {
    @Test
    fun missingPlanReturnsNotFound() = runBlocking {
        withRepository { repository ->
            assertEquals(TodayPlanResult.NotFound, repository.find("profile-1", LocalDate.parse("2026-09-19")))
        }
    }

    @Test
    fun savedPlanRoundTripsExactFieldsAndTaskOrdering() = runBlocking {
        withRepository { repository ->
            val plan = plan(newCards = listOf("new-2", "new-1"), dueCards = listOf("due-2", "due-1"))

            assertEquals(TodayPlanResult.Ready(plan), repository.saveIfAbsent(plan))
            assertEquals(TodayPlanResult.Ready(plan), repository.find(plan.profileId, plan.localDate))
        }
    }

    @Test
    fun uniqueConflictReturnsPersistedSnapshotWithoutReplacingTasks() = runBlocking {
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
    fun nonUniqueSqliteFailureWithExistingSnapshotReturnsStorageUnavailable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "today-plan-storage-failure-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        try {
            val persisted = plan(newCards = listOf("new-1"), dueCards = listOf("due-1"))
            val rejected = persisted.copy(planId = "plan-rejected")
            database.internalTodayPlanDao().insertIfAbsent(
                todayPlanEntity(persisted),
                todayPlanTasks(persisted),
            )
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_today_plan_insert BEFORE INSERT ON today_plans " +
                    "WHEN NEW.planId = 'plan-rejected' BEGIN " +
                    "SELECT RAISE(ABORT, 'today_plans.profileId, today_plans.localDate'); END",
            )

            assertEquals(
                TodayPlanResult.StorageUnavailable,
                RoomTodayPlanRepository(database, Dispatchers.Unconfined).saveIfAbsent(rejected),
            )
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun closedDatabaseReturnsStableStorageUnavailableWithoutSqlLeakage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "closed-today-plan-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        val repository = RoomTodayPlanRepository(database, Dispatchers.Unconfined)
        database.close()

        assertEquals(TodayPlanResult.StorageUnavailable, repository.find("profile-1", LocalDate.parse("2026-09-19")))
        context.deleteDatabase(name)
        Unit
    }

    @Test
    fun closedDatabaseReturnsStableStorageUnavailableForSaveIfAbsent(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "closed-today-plan-save-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        val repository = RoomTodayPlanRepository(database, Dispatchers.Unconfined)
        database.close()

        assertEquals(
            TodayPlanResult.StorageUnavailable,
            repository.saveIfAbsent(plan(newCards = listOf("new-1"), dueCards = listOf("due-1"))),
        )
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
