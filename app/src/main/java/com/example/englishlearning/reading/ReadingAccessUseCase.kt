package com.example.englishlearning.reading

import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanProgress
import com.example.englishlearning.learning.UnlockMode

object ReadingAccessUseCase {
    fun evaluate(
        plan: TodayPlan,
        progress: TodayPlanProgress,
        mode: UnlockMode,
    ): ReadingAccess {
        val missingNew = (plan.newTarget - progress.newDone).coerceAtLeast(0)
        val missingDue = (plan.dueTarget - progress.dueDone).coerceAtLeast(0)
        val strict = mode == UnlockMode.Strict
        return if (missingNew == 0 && (!strict || missingDue == 0)) {
            ReadingAccess.Unlocked(plan.planId)
        } else {
            ReadingAccess.Locked(
                missingNew = missingNew,
                missingDue = if (strict) missingDue else 0,
                strict = strict,
            )
        }
    }
}
