package com.example.englishlearning.ui

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.notification.ReminderScheduler
import com.example.englishlearning.notification.domain.ReminderPreference
import com.example.englishlearning.notification.domain.ReminderPreferenceRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = FixedClockProvider(Instant.parse("2026-10-13T06:00:00Z"), zone)

    private val preferences = ReminderPreferenceRepositoryFake()
    private var refreshCalls = 0
    private val scheduler = object : ReminderScheduler {
        override suspend fun refresh() {
            refreshCalls += 1
        }
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load surfaces stored preference`() = runTest(dispatcher) {
        preferences.saved.add(ReminderPreference(enabled = true, time = LocalTime.of(8, 30)))
        val vm = ReminderSettingsViewModel(preferences, scheduler, FakePlans(), FakeEvents(), clock)

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.enabled)
        assertEquals(LocalTime.of(8, 30), vm.state.value.time)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `set enabled persists and triggers exactly one scheduler refresh`() = runTest(dispatcher) {
        val vm = ReminderSettingsViewModel(preferences, scheduler, FakePlans(), FakeEvents(), clock)
        vm.load()
        dispatcher.scheduler.advanceUntilIdle()
        refreshCalls = 0

        vm.setEnabled(true)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.enabled)
        assertEquals(1, refreshCalls)
        assertEquals(ReminderPreference(true, LocalTime.of(20, 0)), preferences.saved.last())
    }

    @Test
    fun `time change persists immediately without losing enabled state`() = runTest(dispatcher) {
        val vm = ReminderSettingsViewModel(preferences, scheduler, FakePlans(), FakeEvents(), clock)
        vm.load()
        vm.setEnabled(true)
        dispatcher.scheduler.advanceUntilIdle()

        vm.setTime(LocalTime.of(7, 15))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReminderPreference(true, LocalTime.of(7, 15)), preferences.saved.last())
    }

    @Test
    fun `load failure shows error instead of a half-loaded state`() = runTest(dispatcher) {
        val failing = object : ReminderPreferenceRepository by preferences {
            override suspend fun get(): Result<ReminderPreference> = Result.failure(IllegalStateException("boom"))
        }
        val vm = ReminderSettingsViewModel(failing, scheduler, FakePlans(), FakeEvents(), clock)

        vm.load()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("无法读取提醒设置", vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }
}

/** 测试用假仓储：域接口的最小内存实现（与 learning 模块 fake 风格一致）。 */
class ReminderPreferenceRepositoryFake : ReminderPreferenceRepository {
    val saved = mutableListOf<ReminderPreference>()

    override suspend fun get(): Result<ReminderPreference> =
        Result.success(saved.lastOrNull() ?: ReminderPreference(false, LocalTime.of(20, 0)))

    override suspend fun save(preference: ReminderPreference): Result<Unit> {
        saved.add(preference)
        return Result.success(Unit)
    }
}

private class FakePlans : TodayPlanRepository {
    var plan: TodayPlan? = null

    override suspend fun find(profileId: String, localDate: LocalDate) =
        plan?.let { TodayPlanResult.Ready(it) } ?: TodayPlanResult.NotFound

    override suspend fun findLatest(profileId: String) =
        plan?.let { TodayPlanResult.Ready(it) } ?: TodayPlanResult.NotFound

    override suspend fun replaceForDate(plan: TodayPlan) = TodayPlanResult.Ready(plan)

    override suspend fun saveIfAbsent(plan: TodayPlan) = TodayPlanResult.Ready(plan)
}

private class FakeEvents : LearningEventRepository {
    override suspend fun append(event: LearningEvent, nextState: CardReviewState): AppendEventResult =
        AppendEventResult.Appended(duplicate = false)

    override suspend fun findEvent(eventId: String) = RepositoryResult.Success(null)

    override suspend fun findCardState(cardId: String) = RepositoryResult.Success(null)

    override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)

    override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(emptyList<String>())

    override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())

    override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
}
