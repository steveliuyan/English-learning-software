package com.example.englishlearning.learning

import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnlockPolicyTest {
    @Test
    fun `strict mode requires both snapshot groups and treats zero target as complete`() {
        val plan = plan(newTarget = 2, dueTarget = 1)
        assertFalse(UnlockPolicy.isUnlocked(plan, setOf("n1", "n2"), UnlockMode.Strict))
        assertTrue(UnlockPolicy.isUnlocked(plan, setOf("n1", "n2", "d1"), UnlockMode.Strict))
        assertTrue(UnlockPolicy.isUnlocked(plan(0, 1), setOf("d1"), UnlockMode.Strict))
    }

    @Test
    fun `relaxed mode only requires snapshot new cards`() {
        val plan = plan(newTarget = 1, dueTarget = 2)
        assertTrue(UnlockPolicy.isUnlocked(plan, setOf("n1"), UnlockMode.Relaxed))
        assertFalse(UnlockPolicy.isUnlocked(plan, emptySet(), UnlockMode.Relaxed))
    }

    @Test
    fun `completion counts only snapshot cards and deduplicates`() {
        val plan = plan(newTarget = 2, dueTarget = 2)
        val progress = UnlockPolicy.progress(plan, listOf("n1", "n1", "n2", "outside", "d1", "late"))
        assertTrue(progress.newDone == 2 && progress.dueDone == 1)
    }

    private fun plan(newTarget: Int = 0, dueTarget: Int = 0) = TodayPlan(
        "p", "profile", LocalDate.of(2026, 9, 19), "Asia/Shanghai", "book", newTarget, dueTarget,
        listOf("n1", "n2").take(newTarget), listOf("d1", "d2").take(dueTarget), "v1", Instant.EPOCH,
    )
}
