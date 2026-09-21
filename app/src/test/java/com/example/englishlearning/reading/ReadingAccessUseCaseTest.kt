package com.example.englishlearning.reading

import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanProgress
import com.example.englishlearning.learning.UnlockMode
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ReadingAccessUseCaseTest {
    private val plan = TodayPlan(
        planId = "plan-1",
        profileId = "p1",
        localDate = LocalDate.of(2026, 9, 22),
        zoneId = "Asia/Shanghai",
        activeWordBookId = "cet4",
        newTarget = 10,
        dueTarget = 5,
        newCardIds = emptyList(),
        dueCardIds = emptyList(),
        ruleVersion = "v1",
        generatedAt = Instant.EPOCH,
    )

    @Test
    fun strictLockedReportsMissingNewAndDue() {
        assertEquals(
            ReadingAccess.Locked(missingNew = 3, missingDue = 2, strict = true),
            ReadingAccessUseCase.evaluate(plan, TodayPlanProgress(7, 3), UnlockMode.Strict),
        )
    }

    @Test
    fun strictUnlockedWhenBothTargetsComplete() {
        assertEquals(
            ReadingAccess.Unlocked(plan.planId),
            ReadingAccessUseCase.evaluate(plan, TodayPlanProgress(10, 5), UnlockMode.Strict),
        )
    }

    @Test
    fun relaxedUnlockedWhenNewTargetComplete() {
        assertEquals(
            ReadingAccess.Unlocked(plan.planId),
            ReadingAccessUseCase.evaluate(plan, TodayPlanProgress(10, 0), UnlockMode.Relaxed),
        )
    }

    @Test
    fun relaxedLockedReportsOnlyMissingNew() {
        assertEquals(
            ReadingAccess.Locked(missingNew = 2, missingDue = 0, strict = false),
            ReadingAccessUseCase.evaluate(plan, TodayPlanProgress(8, 5), UnlockMode.Relaxed),
        )
    }
}
