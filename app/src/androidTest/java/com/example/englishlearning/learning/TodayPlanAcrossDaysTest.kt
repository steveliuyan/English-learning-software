package com.example.englishlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity
import com.example.englishlearning.core.time.ClockProvider
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Behavioural coverage for AC1-07 at the two boundaries a "today plan" must survive: a day
 * rollover and a process restart. Each physical learning day must expose exactly one plan,
 * and once generated that plan is an immutable snapshot — this asserts the second day does
 * not rewrite the first day's row or its tasks.
 *
 * Known gap (timezone): [GetOrCreateTodayPlanUseCase] recomputes
 * `clock.instant().atZone(clock.zoneId()).toLocalDate()` on every `invoke` (lines 13-15) and
 * the unique index only covers `(profileId, localDate)`, so a timezone change inside one
 * physical learning day invents a second local date and a second plan. The timezone test is
 * written against the intended behaviour and `@Ignore`-d until that product decision lands.
 */
@RunWith(AndroidJUnit4::class)
class TodayPlanAcrossDaysTest {
    private val profileId = "profile-1"
    private val wordBookId = "cet4"
    private val dailyNewTarget = 5
    private val zone = ZoneId.of("Asia/Shanghai")

    // Shanghai is UTC+8: 23:59 and 00:01 on consecutive local days.
    private val day1Instant = Instant.parse("2026-09-20T15:59:00Z")
    private val day2Instant = Instant.parse("2026-09-20T16:01:00Z")

    @Test
    fun sameLocalDateReusesPersistedPlanWithoutDuplicatingRows() = runBlocking {
        withDatabase { database ->
            seed(database)
            val useCase = useCase(database, MutableClockProvider(day1Instant, zone))

            val first = useCase.ready()
            val second = useCase.ready()

            assertEquals("a stable clock must reuse the same snapshot", first.planId, second.planId)
            assertEquals(1, planRowCount(database))
        }
    }

    @Test
    fun nextLocalDateCreatesNewPlanAndLeavesFirstDaySnapshotIntact() = runBlocking {
        withDatabase { database ->
            seed(database)
            val clock = MutableClockProvider(day1Instant, zone)
            val useCase = useCase(database, clock)

            val day1 = useCase.ready()
            val day1RowBefore = findPlan(database, day1.localDate.toString())
            val day1TasksBefore = findTasks(database, day1.planId)

            clock.instant = day2Instant
            val day2 = useCase.ready()

            assertNotEquals("the next local day needs its own plan", day1.planId, day2.planId)
            assertEquals(2, planRowCount(database))

            val day1RowAfter = findPlan(database, day1.localDate.toString())
            assertEquals("first day's row must not be replaced", day1.planId, day1RowAfter?.planId)
            assertEquals(
                "first day's snapshot must keep its generation instant",
                day1RowBefore?.generatedAtEpochMillis,
                day1RowAfter?.generatedAtEpochMillis,
            )
            assertEquals("first day's tasks must stay frozen", day1TasksBefore, findTasks(database, day1.planId))
        }
    }

    @Test
    fun restartOnSameLocalDateReusesTheDay2PlanWithoutThirdRow() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "today-plan-restart-${System.nanoTime()}.db"

        val day2PlanId: String
        val first = openDatabase(context, name)
        try {
            seed(first)
            val clock = MutableClockProvider(day1Instant, zone)
            val useCase = useCase(first, clock)
            useCase.ready()
            clock.instant = day2Instant
            day2PlanId = useCase.ready().planId
            assertEquals(2, planRowCount(first))
        } finally {
            first.close()
        }

        val reopened = openDatabase(context, name)
        try {
            val useCase = useCase(reopened, MutableClockProvider(day2Instant, zone))
            val afterRestart = useCase.ready()

            assertEquals("restart on the same day must reuse the persisted plan", day2PlanId, afterRestart.planId)
            assertEquals(2, planRowCount(reopened))
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    @Ignore(
        "AC1-07 timezone gap: GetOrCreateTodayPlanUseCase recomputes localDate from the current " +
            "zone on every call (lines 13-15) and the unique index only covers " +
            "(profileId, localDate), so one physical learning day forks into two plans across zones. " +
            "Asserted against the intended behaviour; enable once the product decision lands.",
    )
    @Test
    fun timezoneChangeWithinOneLearningDayDoesNotCreateASecondPlan() = runBlocking {
        withDatabase { database ->
            seed(database)
            // One instant, two zones, two different local dates: Shanghai 2026-09-21, LA 2026-09-20.
            val instant = Instant.parse("2026-09-20T16:30:00Z")
            val clock = MutableClockProvider(instant, ZoneId.of("Asia/Shanghai"))
            val useCase = useCase(database, clock)

            val planA = useCase.ready()
            clock.zone = ZoneId.of("America/Los_Angeles")
            val planB = useCase.ready()

            assertEquals("one physical day must not fork into two plans", planA.planId, planB.planId)
            assertEquals(1, planRowCount(database))
        }
    }

    private suspend fun seed(database: AppDatabase) {
        val profiles = RoomLearningProfileRepository(database, Dispatchers.Unconfined)
        profiles.upsertWordBook(WordBook(wordBookId, "四级", "基础", 12, "v1", "ngsl-nawl-1.2"))
        profiles.save(LearningProfile(profileId, wordBookId, dailyNewTarget))
    }

    private fun useCase(database: AppDatabase, clock: ClockProvider): GetOrCreateTodayPlanUseCase =
        GetOrCreateTodayPlanUseCase(
            learningProfileRepository = RoomLearningProfileRepository(database, Dispatchers.Unconfined),
            todayPlanRepository = RoomTodayPlanRepository(database, Dispatchers.Unconfined),
            cardSource = StoredPlanCardSource(
                content = PlaceholderWordCardSource(),
                events = RoomLearningEventRepository(database, Dispatchers.Unconfined),
            ),
            clock = clock,
        )

    private suspend fun GetOrCreateTodayPlanUseCase.ready(): TodayPlan {
        val result = invoke(profileId)
        assertTrue("expected Ready but was $result", result is TodayPlanResult.Ready)
        return (result as TodayPlanResult.Ready).plan
    }

    private suspend fun findPlan(database: AppDatabase, localDate: String): TodayPlanEntity? =
        database.internalTodayPlanDao().findPlan(profileId, localDate)

    private suspend fun findTasks(database: AppDatabase, planId: String): List<TodayPlanTaskEntity> =
        database.internalTodayPlanDao().findTasks(planId)

    private fun planRowCount(database: AppDatabase): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM today_plans")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }

    private suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "today-plan-across-days-${System.nanoTime()}.db"
        val database = openDatabase(context, name)
        try {
            block(database)
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(name)
        }
    }

    private fun openDatabase(context: Context, name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
}

private class MutableClockProvider(
    var instant: Instant,
    var zone: ZoneId,
) : ClockProvider {
    override fun instant(): Instant = instant

    override fun zoneId(): ZoneId = zone
}
