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
    fun findLatestWithoutAnyPlanReturnsNotFound() = runBlocking {
        withRepository { repository ->
            assertEquals(TodayPlanResult.NotFound, repository.findLatest("profile-1"))
        }
    }

    @Test
    fun findLatestReturnsTheGreatestLocalDateRegardlessOfInsertionOrder() = runBlocking {
        withRepository { repository ->
            val later = plan(newCards = listOf("new-later"), dueCards = emptyList()).copy(
                planId = "plan-later",
                localDate = LocalDate.parse("2026-09-20"),
            )
            val earlier = plan(newCards = listOf("new-earlier"), dueCards = emptyList()).copy(
                planId = "plan-earlier",
                localDate = LocalDate.parse("2026-09-19"),
            )

            // Insert the greatest date first: the query must order by localDate, not by insert order.
            assertEquals(TodayPlanResult.Ready(later), repository.saveIfAbsent(later))
            assertEquals(TodayPlanResult.Ready(earlier), repository.saveIfAbsent(earlier))

            // Ready equality carries the task snapshot, so this also proves tasks are loaded.
            assertEquals(TodayPlanResult.Ready(later), repository.findLatest(later.profileId))
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
    fun findLatestOnClosedDatabaseReturnsStableStorageUnavailable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "closed-today-plan-latest-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        val repository = RoomTodayPlanRepository(database, Dispatchers.Unconfined)
        database.close()

        assertEquals(TodayPlanResult.StorageUnavailable, repository.findLatest("profile-1"))
        context.deleteDatabase(name)
        Unit
    }

    /**
     * Contract (AGENTS.md: "学习反馈必须先成功写入本地"): a reported success must be readable back.
     *
     * On Room 2.8.4 a suspended `@Transaction` write on a closed instance reopens the database
     * file and persists, so [TodayPlanResult.Ready] here is truthful and there is no write-path
     * failure signal to map to [TodayPlanResult.StorageUnavailable]. Reads on the same closed
     * instance still fail with a `JobCancellationException` while the caller's coroutine is
     * active (raw evidence: verification-logs/30c-closed-db-probe-isolated.log), so the plan is
     * read back through a reopened instance. Both branches assert, so neither passes vacuously.
     */
    @Test
    fun closedDatabaseNeverReportsAPlanThatWasNotPersisted(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "closed-today-plan-save-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        database.openHelper.writableDatabase // force Room to actually open the connection
        val repository = RoomTodayPlanRepository(database, Dispatchers.Unconfined)
        database.close()

        val todayPlan = plan(newCards = listOf("new-1"), dueCards = listOf("due-1"))
        val result = repository.saveIfAbsent(todayPlan)

        val reopened = openDatabase(context, name)
        try {
            val readBack = RoomTodayPlanRepository(reopened, Dispatchers.Unconfined)
            val found = readBack.find(todayPlan.profileId, todayPlan.localDate)
            if (result is TodayPlanResult.Ready) {
                assertEquals("a reported save must be readable back", TodayPlanResult.Ready(todayPlan), found)
            } else {
                assertEquals(TodayPlanResult.StorageUnavailable, result)
                assertEquals("a reported failure must not have persisted anything", TodayPlanResult.NotFound, found)
            }
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
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
