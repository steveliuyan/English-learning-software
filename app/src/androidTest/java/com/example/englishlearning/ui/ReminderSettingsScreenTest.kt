package com.example.englishlearning.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.notification.ReminderScheduler
import com.example.englishlearning.notification.domain.ReminderPreference
import com.example.englishlearning.notification.domain.ReminderPreferenceRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 批 5 Task 2：学习提醒页的 connected 契约（spec §5「开关统一 Switch / 时间改 Material TimePicker /
 * 加载失败带重试 / 整屏可滚」）。
 *
 * 本类用**真实 `ReminderSettingsViewModel`**（不是桩屏幕），因为这三条口径的落点都在
 * 「屏幕 ↔ VM ↔ 仓储」的接线上：Switch 的 `onCheckedChange` 要把「关」直接发给
 * `viewModel.setEnabled(false)`（「开」必须经宿主 `onEnableRequested` 走通知权限），
 * 对话框「确定」要把 `LocalTime` 真写进仓储，重试要让**同一条读取路径**重新跑起来
 * （`load()` 在 `loading == false` 时静默不动，这一条只有真 VM 才验得出来）。
 */
@RunWith(AndroidJUnit4::class)
class ReminderSettingsScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = FixedClockProvider(Instant.parse("2026-10-13T06:00:00Z"), zone)
    private val scheduler = object : ReminderScheduler {
        override suspend fun refresh() = Unit
    }

    private fun viewModel(preferences: ReminderPreferenceRepository) =
        ReminderSettingsViewModel(preferences, scheduler, ReminderTestPlans(), ReminderTestEvents(), clock)

    @Test
    fun switchReadsTheStoredStateAndRoutesTheOffBranchStraightToTheViewModel() {
        val preferences = ReminderTestPreferences().apply {
            saved.add(ReminderPreference(enabled = true, time = LocalTime.of(20, 0)))
        }
        val vm = viewModel(preferences)
        var enableRequests = 0
        composeRule.setContent {
            ReminderSettingsScreen(
                onBack = {},
                onEnableRequested = { enableRequests += 1 },
                viewModel = vm,
            )
        }
        composeRule.waitUntil(5_000) { !vm.state.value.loading }

        composeRule.onNodeWithTag("reminder_enabled_switch").performScrollTo().assertIsOn()
        composeRule.onNodeWithContentDescription("开启学习提醒").assertExists()

        // 关：直接落库（走 VM），不经宿主权限回调。
        composeRule.onNodeWithTag("reminder_enabled_switch").performClick()
        composeRule.waitUntil(5_000) { !vm.state.value.enabled }
        composeRule.onNodeWithTag("reminder_enabled_switch").assertIsOff()
        assertEquals(false, preferences.saved.last().enabled)
        assertEquals(0, enableRequests)
    }

    @Test
    fun enablingGoesThroughTheHostPermissionHookAndTimeConfirmWritesThePickedValue() {
        val preferences = ReminderTestPreferences()
        val vm = viewModel(preferences)
        var enableRequests = 0
        composeRule.setContent {
            ReminderSettingsScreen(
                onBack = {},
                onEnableRequested = { enableRequests += 1 },
                viewModel = vm,
            )
        }
        composeRule.waitUntil(5_000) { !vm.state.value.loading }

        // 「开」必须经宿主（通知权限在宿主侧判定）：屏幕只发一次请求，VM 保持关闭。
        composeRule.onNodeWithTag("reminder_enabled_switch").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { enableRequests == 1 }
        assertEquals(false, vm.state.value.enabled)
        composeRule.onNodeWithTag("reminder_time_button").assertDoesNotExist()

        // 宿主拿到授权后回灌（`AppScreen` 里那行 `reminderSettingsViewModel?.setEnabled(true)`）。
        composeRule.runOnIdle { vm.setEnabled(true) }
        composeRule.waitUntil(5_000) { vm.state.value.enabled && preferences.saved.isNotEmpty() }

        composeRule.onNodeWithTag("reminder_time_button").performScrollTo().assertExists()
        composeRule.onNodeWithTag("reminder_time_button").performClick()
        // Material `TimePicker` 对话框已挂上（整点网格 `reminder_time_$hour` 已退役，只剩一个确认键）。
        composeRule.onNodeWithTag("reminder_time_confirm").assertExists()
        composeRule.onNodeWithTag("reminder_time_confirm").performClick()
        composeRule.waitUntil(5_000) { preferences.saved.size >= 2 }
        val written = preferences.saved.last()
        assertTrue(written.enabled)
        assertEquals(LocalTime.of(20, 0), written.time)
    }

    @Test
    fun failureShowsARetryThatActuallyReRunsTheRead() {
        val preferences = FlakyReminderTestPreferences(failFirst = true)
        val vm = viewModel(preferences)
        composeRule.setContent {
            ReminderSettingsScreen(onBack = {}, onEnableRequested = {}, viewModel = vm)
        }
        composeRule.waitUntil(5_000) { vm.state.value.error != null }

        composeRule.onNodeWithTag("reminder_retry").performScrollTo().assertExists()
        preferences.succeed(
            ReminderPreference(enabled = true, time = LocalTime.of(21, 15)),
        )
        composeRule.onNodeWithTag("reminder_retry").performClick()
        composeRule.waitUntil(5_000) { vm.state.value.error == null && !vm.state.value.loading }

        // 读到失败时被卡住的那份偏好：重试真的把 `get()` 又跑了一遍。
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.enabled)
        assertEquals(LocalTime.of(21, 15), vm.state.value.time)
        composeRule.onNodeWithTag("reminder_enabled_switch").performScrollTo().assertIsOn()
    }
}

/** 测试用假仓储：域接口的最小内存实现（与 `ReminderSettingsViewModelTest` 的 JVM 版同形）。 */
class ReminderTestPreferences : ReminderPreferenceRepository {
    val saved = mutableListOf<ReminderPreference>()

    override suspend fun get(): Result<ReminderPreference> =
        Result.success(saved.lastOrNull() ?: ReminderPreference(false, LocalTime.of(20, 0)))

    override suspend fun save(preference: ReminderPreference): Result<Unit> {
        saved.add(preference)
        return Result.success(Unit)
    }
}

/** 第一次读失败、之后可切成功，用来验「重试走的是同一条读取路径」。 */
class FlakyReminderTestPreferences(private var failFirst: Boolean) : ReminderPreferenceRepository {
    val saved = mutableListOf<ReminderPreference>()
    private var stored: ReminderPreference? = null

    fun succeed(preference: ReminderPreference) {
        stored = preference
        failFirst = false
    }

    override suspend fun get(): Result<ReminderPreference> = if (failFirst) {
        Result.failure(IllegalStateException("boom"))
    } else {
        Result.success(stored ?: ReminderPreference(false, LocalTime.of(20, 0)))
    }

    override suspend fun save(preference: ReminderPreference): Result<Unit> {
        saved.add(preference)
        return Result.success(Unit)
    }
}

private class ReminderTestPlans : TodayPlanRepository {
    override suspend fun find(profileId: String, localDate: LocalDate) = TodayPlanResult.NotFound

    override suspend fun findLatest(profileId: String) = TodayPlanResult.NotFound

    override suspend fun replaceForDate(plan: TodayPlan) = TodayPlanResult.Ready(plan)

    override suspend fun saveIfAbsent(plan: TodayPlan) = TodayPlanResult.Ready(plan)
}

private class ReminderTestEvents : LearningEventRepository {
    override suspend fun append(event: LearningEvent, nextState: CardReviewState): AppendEventResult =
        AppendEventResult.Appended(duplicate = false)

    override suspend fun findEvent(eventId: String) = RepositoryResult.Success(null)

    override suspend fun findCardState(cardId: String) = RepositoryResult.Success(null)

    override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)

    override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(emptyList<String>())

    override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())

    override suspend fun dueCardIds(wordBookId: String, now: Instant): RepositoryResult<List<String>> =
        RepositoryResult.Success(emptyList())
}
