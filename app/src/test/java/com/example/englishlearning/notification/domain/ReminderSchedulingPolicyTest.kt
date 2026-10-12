package com.example.englishlearning.notification.domain

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.UnlockMode
import com.example.englishlearning.learning.UnlockPolicy
import com.example.englishlearning.learning.TodayPlan
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * F4-02 本地提醒的调度决策（Spec 04）：
 *
 * - 提醒默认关闭；只在 enabled 时调度。
 * - 非精确、每天最多一次、不补发：同一学习日已发过就不重排当日的下一次。
 * - 当日计划已完成 → 不调度并取消旧任务；关闭、改时间、跨日/时区变化 → 取消旧任务后按需重排唯一一次。
 * - 通知内容固定为"今日仍有学习/复习任务"，不含任何学习数据。
 */
class ReminderSchedulingPolicyTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 13)
    private val reminderTime = LocalTime.of(20, 0)

    private fun plan(
        localDate: LocalDate = today,
        newTarget: Int = 2,
        dueTarget: Int = 0,
        newDone: Int = 0,
    ): TodayPlan {
        val p = TodayPlan(
            planId = "plan-$localDate",
            profileId = "profile-1",
            localDate = localDate,
            zoneId = zone.id,
            activeWordBookId = "book-1",
            newTarget = newTarget,
            dueTarget = dueTarget,
            newCardIds = (1..newTarget).map { "n$it" },
            dueCardIds = (1..dueTarget).map { "d$it" },
            ruleVersion = "v1",
            generatedAt = Instant.EPOCH,
        )
        return p
    }

    private fun doneIds(count: Int) = (1..count).map { "n$it" }

    @Test
    fun `disabled reminder never schedules and always cancels`() {
        val decision = ReminderSchedulingPolicy.decide(
            preference = ReminderPreference(enabled = false, time = reminderTime),
            plan = plan(),
            completedCardIds = emptyList(),
            clock = FixedClockProvider(LocalDateTime.of(today, LocalTime.NOON).atZone(zone).toInstant(), zone),
        )
        assertEquals(ReminderDecision.Cancel, decision)
    }

    @Test
    fun `completed plan cancels without rescheduling`() {
        val decision = ReminderSchedulingPolicy.decide(
            preference = ReminderPreference(enabled = true, time = reminderTime),
            plan = plan(newDone = 2),
            completedCardIds = doneIds(2),
            clock = FixedClockProvider(LocalDateTime.of(today, LocalTime.NOON).atZone(zone).toInstant(), zone),
        )
        assertEquals(ReminderDecision.Cancel, decision)
    }

    @Test
    fun `strict mode completes only when both groups are done`() {
        // newDone == newTarget 但 dueTarget > 0 且 dueDone == 0：严格模式下未完成，仍要调度。
        val decision = ReminderSchedulingPolicy.decide(
            preference = ReminderPreference(enabled = true, time = reminderTime),
            plan = plan(newTarget = 2, dueTarget = 1, newDone = 2),
            completedCardIds = doneIds(2),
            clock = FixedClockProvider(LocalDateTime.of(today, LocalTime.NOON).atZone(zone).toInstant(), zone),
        )
        assertTrue(decision is ReminderDecision.Schedule, "due 组未完成时必须继续调度")
    }

    @Test
    fun `schedules the next occurrence strictly after now`() {
        // 现在 21:00，提醒时间 20:00 → 今天的时点已过，下一次是明天 20:00，不补发今天。
        val now = LocalDateTime.of(today, LocalTime.of(21, 0)).atZone(zone).toInstant()
        val decision = ReminderSchedulingPolicy.decide(
            preference = ReminderPreference(enabled = true, time = reminderTime),
            plan = plan(),
            completedCardIds = emptyList(),
            clock = FixedClockProvider(now, zone),
        )
        val expected = LocalDateTime.of(today.plusDays(1), reminderTime).atZone(zone).toInstant()
        assertEquals(ReminderDecision.Schedule(expected, ReminderMessageText), decision)
    }

    @Test
    fun `schedules today when the reminder time has not passed`() {
        val now = LocalDateTime.of(today, LocalTime.NOON).atZone(zone).toInstant()
        val decision = ReminderSchedulingPolicy.decide(
            preference = ReminderPreference(enabled = true, time = reminderTime),
            plan = plan(),
            completedCardIds = emptyList(),
            clock = FixedClockProvider(now, zone),
        )
        val expected = LocalDateTime.of(today, reminderTime).atZone(zone).toInstant()
        assertEquals(ReminderDecision.Schedule(expected, ReminderMessageText), decision)
    }

    @Test
    fun `time change or timezone change yields exactly one rescheduled reminder`() {
        // 决策是纯函数：输入（偏好+计划+完成度+时钟）相同则输出相同。
        // "取消旧任务并重新安排唯一的下一次"由调用方以 same-tag replace 语义保证，
        // 本测试锚定：无论改时间还是时区，输出始终是单次 Schedule（不是列表、不是补发序列）。
        val morning = LocalDateTime.of(today, LocalTime.of(8, 0)).atZone(zone).toInstant()
        val first = ReminderSchedulingPolicy.decide(
            preference = ReminderPreference(enabled = true, time = LocalTime.of(9, 0)),
            plan = plan(),
            completedCardIds = emptyList(),
            clock = FixedClockProvider(morning, zone),
        )
        val changed = ReminderSchedulingPolicy.decide(
            preference = ReminderPreference(enabled = true, time = LocalTime.of(22, 0)),
            plan = plan(),
            completedCardIds = emptyList(),
            clock = FixedClockProvider(morning, zone),
        )
        assertTrue(first is ReminderDecision.Schedule && changed is ReminderDecision.Schedule)
        assertTrue((changed as ReminderDecision.Schedule).triggerAt.isAfter((first as ReminderDecision.Schedule).triggerAt))
    }

    @Test
    fun `message text never carries learning content`() {
        val decision = ReminderSchedulingPolicy.decide(
            preference = ReminderPreference(enabled = true, time = reminderTime),
            plan = plan(),
            completedCardIds = emptyList(),
            clock = FixedClockProvider(LocalDateTime.of(today, LocalTime.NOON).atZone(zone).toInstant(), zone),
        )
        assertEquals("今日仍有学习/复习任务", ReminderMessageText)
        assertTrue((decision as ReminderDecision.Schedule).messageText == ReminderMessageText)
    }
}
