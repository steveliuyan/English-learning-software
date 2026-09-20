package com.example.englishlearning.learning

enum class UnlockMode { Strict, Relaxed }

data class TodayPlanProgress(val newDone: Int, val dueDone: Int) {
    val totalDone: Int get() = newDone + dueDone
}

object UnlockPolicy {
    fun progress(plan: TodayPlan, completedCardIds: Iterable<String>): TodayPlanProgress {
        val completed = completedCardIds.toSet()
        return TodayPlanProgress(
            plan.newCardIds.count { it in completed }.coerceAtMost(plan.newTarget),
            plan.dueCardIds.count { it in completed }.coerceAtMost(plan.dueTarget),
        )
    }

    fun isUnlocked(plan: TodayPlan, completedCardIds: Iterable<String>, mode: UnlockMode): Boolean {
        val progress = progress(plan, completedCardIds)
        val newComplete = progress.newDone == plan.newTarget
        val dueComplete = progress.dueDone == plan.dueTarget
        return newComplete && (mode == UnlockMode.Relaxed || dueComplete)
    }

    /**
     * Derive the unlock [UnlockMode] from a plan's [TodayPlan.ruleVersion] snapshot.
     *
     * [TodayPlan] intentionally has no live mode field: the rule version captured at
     * plan-generation time is the single source of truth, so the unlock rule must NOT be
     * overwritten by a live settings read (which would silently re-lock or unlock an
     * already-shipped day). Default is [UnlockMode.Strict]; future rule versions may opt
     * into [UnlockMode.Relaxed] via a recognized `-relaxed` suffix. The parser is
     * intentionally minimal and forward-compatible: any unknown version stays Strict.
     */
    fun modeForRuleVersion(ruleVersion: String): UnlockMode {
        if (ruleVersion.endsWith("-relaxed", ignoreCase = true)) return UnlockMode.Relaxed
        return UnlockMode.Strict
    }

    /** Reason shown when the plan is not yet unlocked, tailored to the active [mode]. */
    fun lockedReason(mode: UnlockMode): String =
        if (mode == UnlockMode.Relaxed) "完成新词后解锁文章" else "完成新词与复习后解锁文章"
}
