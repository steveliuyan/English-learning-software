package com.example.englishlearning.notification.domain

import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.UnlockMode
import com.example.englishlearning.learning.UnlockPolicy
import com.example.englishlearning.learning.TodayPlan
import java.time.Instant
import java.time.LocalDateTime

/**
 * F4-02 提醒的调度决策，纯函数、无 Android 依赖。
 *
 * 决策只回答「此刻应处于什么状态」：[ReminderDecision.Schedule]（唯一一次）或
 * [ReminderDecision.Cancel]。调用方（WorkManager 调度器）以同一 tag 的
 * replace 语义执行——「取消旧任务并重新安排唯一的下一次」由幂等替换保证，
 * 这里不关心也不记忆已发历史；「不补发」由"下一次严格晚于 now"推导：
 * 当日时点已过时下一次必然落在明天。
 */
sealed interface ReminderDecision {
    data class Schedule(val triggerAt: Instant, val messageText: String) : ReminderDecision
    data object Cancel : ReminderDecision
}

/** 固定通知文案：不得携带 API Key、原图、OCR 文本或文章内容（Spec 04 F4-02）。 */
const val ReminderMessageText = "今日仍有学习/复习任务"

object ReminderSchedulingPolicy {

    fun decide(
        preference: ReminderPreference,
        plan: TodayPlan?,
        completedCardIds: Iterable<String>,
        clock: ClockProvider,
    ): ReminderDecision {
        if (!preference.enabled) return ReminderDecision.Cancel
        if (plan != null && isPlanComplete(plan, completedCardIds)) return ReminderDecision.Cancel

        val zone = clock.zoneId()
        val now = LocalDateTime.ofInstant(clock.instant(), zone)
        var next = now.toLocalDate().atTime(preference.time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return ReminderDecision.Schedule(next.atZone(zone).toInstant(), ReminderMessageText)
    }

    private fun isPlanComplete(plan: TodayPlan, completedCardIds: Iterable<String>): Boolean {
        // 提醒语义沿用当日计划快照的解锁规则：规则版本决定严格/宽松，
        // 宽松模式只看新词组（与 AGENTS.md 的解锁规则一致）。
        val mode = UnlockPolicy.modeForRuleVersion(plan.ruleVersion)
        return UnlockPolicy.isUnlocked(plan, completedCardIds, mode)
    }
}
