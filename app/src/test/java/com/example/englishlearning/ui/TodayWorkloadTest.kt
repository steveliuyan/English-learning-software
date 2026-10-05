package com.example.englishlearning.ui

import com.example.englishlearning.learning.WordBookProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 分段条的语义由这里钉住：剩余量还是目标量、零值小节画不画、第一节是「已学」还是「总词数」。
 *
 * 放在 JVM 侧是刻意的——这些规则每一条都必须能用变异测试证明「可变红」，
 * 而 instrumentation 的每一轮验证都要装包、跑真机，成本高到会让「证明可变红」这件事被跳过。
 */
class TodayWorkloadTest {

    private fun book(learned: Int, total: Int) = WordBookProgress(learned = learned, total = total)

    @Test
    fun `pending counts subtract what is already done today`() {
        val workload = TodayWorkload.of(book(100, 1000), newTarget = 10, newDone = 3, dueTarget = 12, dueDone = 5)
        assertEquals(7, workload.reviewPending)
        assertEquals(7, workload.newPending)
        assertEquals(100, workload.learned)
    }

    @Test
    fun `over finished targets never produce a negative section`() {
        val workload = TodayWorkload.of(book(100, 1000), newTarget = 10, newDone = 14, dueTarget = 12, dueDone = 20)
        assertEquals(0, workload.reviewPending)
        assertEquals(0, workload.newPending)
        assertTrue(workload.segments.map { it.kind } == listOf(WorkloadKind.LEARNED))
    }

    @Test
    fun `sections with zero count are dropped entirely`() {
        val workload = TodayWorkload.of(book(0, 4308), newTarget = 10, newDone = 0, dueTarget = 0, dueDone = 0)
        assertEquals(listOf(WorkloadKind.NEW), workload.segments.map { it.kind })
        assertEquals(10, workload.segments.single().count)
    }

    @Test
    fun `learned section carries the book progress, not the book total`() {
        val workload = TodayWorkload.of(book(2680, 3039), newTarget = 10, newDone = 0, dueTarget = 12, dueDone = 0)
        val learned = workload.segments.single { it.kind == WorkloadKind.LEARNED }
        assertEquals(2680, learned.count)
        assertEquals(2680f / 3039f, learned.share, 1e-6f)
    }

    @Test
    fun `a tiny non zero section keeps a visible minimum share`() {
        val workload = TodayWorkload.of(book(2680, 3039), newTarget = 0, newDone = 0, dueTarget = 12, dueDone = 0)
        val review = workload.segments.single { it.kind == WorkloadKind.REVIEW }
        assertEquals(12, review.count)
        assertTrue(review.share >= TodayWorkload.MIN_SHARE, "12/3039 不到一个像素，必须被抬到可见下限")
    }

    @Test
    fun `no book total means there is nothing to draw`() {
        assertTrue(TodayWorkload.of(null, newTarget = 10, newDone = 0, dueTarget = 12, dueDone = 0).isEmpty)
        assertTrue(TodayWorkload.of(book(0, 0), newTarget = 10, newDone = 0, dueTarget = 12, dueDone = 0).isEmpty)
    }

    @Test
    fun `a day with nothing at all means there is nothing to draw`() {
        assertTrue(TodayWorkload.of(book(0, 100), newTarget = 0, newDone = 0, dueTarget = 0, dueDone = 0).isEmpty)
    }

    @Test
    fun `the remainder leaves room for the part of the book that is untouched`() {
        val workload = TodayWorkload.of(book(100, 1000), newTarget = 0, newDone = 0, dueTarget = 0, dueDone = 0)
        assertFalse(workload.isEmpty)
        assertEquals(0.1f, workload.segments.single().share, 1e-6f)
        assertEquals(0.9f, workload.remainder, 1e-6f)
    }

    @Test
    fun `the legend reports every kind including the zero ones`() {
        val workload = TodayWorkload.of(book(100, 1000), newTarget = 10, newDone = 10, dueTarget = 4, dueDone = 0)
        assertEquals(100, workload.countOf(WorkloadKind.LEARNED))
        assertEquals(4, workload.countOf(WorkloadKind.REVIEW))
        assertEquals(0, workload.countOf(WorkloadKind.NEW))
    }
}
