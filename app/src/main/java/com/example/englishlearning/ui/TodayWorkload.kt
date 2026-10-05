package com.example.englishlearning.ui

import com.example.englishlearning.learning.WordBookProgress

/** 分段条的三节：已学 / 今日复习 / 今日新学。顺序即渲染顺序，也即学习流程顺序。 */
enum class WorkloadKind { LEARNED, REVIEW, NEW }

/**
 * 一节已经算好宽度的分段：[share] 是**相对占比**（0..1），[count] 是这一节代表的词数。
 *
 * 两者不总是相等（见 [TodayWorkload.MIN_SHARE]），所以分开存：宽度是给人看的，数字是给人读的。
 */
data class WorkloadSegment(val kind: WorkloadKind, val count: Int, val share: Float)

/**
 * 「开始学习」上方那条今日工作量分段条的模型。
 *
 * 它是纯 Kotlin 的（不碰 Compose），目的是让「剩余量还是目标量」「零值小节要不要画」
 * 这类语义能在 JVM 单元测试里被钉死——否则每条断言都得开一次 instrumentation 才能验证。
 *
 * ## 两条硬规则
 *
 * 1. **计数是剩余量**：`目标 − 已完成`，不是目标本身。条子回答「今天还要干多少」，
 *    已经干完的部分不能再占宽度，否则一天下来条子看起来纹丝不动。
 * 2. **零值小节彻底不画**：[segments] 里不会出现 `count == 0` 的节。
 *    「今天不用复习」和「今天要复习一点点」必须能在界面上区分开。
 */
data class TodayWorkload(
    val learned: Int,
    val reviewPending: Int,
    val newPending: Int,
    val bookTotal: Int,
    val segments: List<WorkloadSegment>,
) {
    /** 没有分节 = 没有可画的东西（词书总数未知，或今天什么都没得干）。 */
    val isEmpty: Boolean get() = segments.isEmpty()

    /** 轨道留白：`1 − 各节占比之和`。下限可能把各节抬到 1 以上，此时为 0，让三节自己铺满。 */
    val remainder: Float get() = (1f - segments.fold(0f) { acc, s -> acc + s.share }).coerceAtLeast(0f)

    /** 图例要三种都给：计数为 0 的也得显示成「今日复习 0」，而不是整条消失。 */
    fun countOf(kind: WorkloadKind): Int = when (kind) {
        WorkloadKind.LEARNED -> learned
        WorkloadKind.REVIEW -> reviewPending
        WorkloadKind.NEW -> newPending
    }

    companion object {
        /**
         * 非零小节的最小占比。
         *
         * 一本 3000 词的词书今天只到期 12 个词，真实比例是 0.4%——手机上不到一个像素，等于没画。
         * 给非零小节一个 3% 的下限，保证「今天还有活要干」永远看得见。图例写着精确数字，
         * 所以这点视觉夸大不会变成错误信息。
         */
        const val MIN_SHARE = 0.03f

        /**
         * @param bookProgress 当前词书的整册进度；`null` 表示还不知道词书多大，此时不画条子
         *   （分母未知，画出来只能是假的）。
         */
        fun of(
            bookProgress: WordBookProgress?,
            newTarget: Int,
            newDone: Int,
            dueTarget: Int,
            dueDone: Int,
        ): TodayWorkload {
            val learned = bookProgress?.learned ?: 0
            val total = bookProgress?.total ?: 0
            val reviewPending = (dueTarget - dueDone).coerceAtLeast(0)
            val newPending = (newTarget - newDone).coerceAtLeast(0)
            val counts = listOf(
                WorkloadKind.LEARNED to learned,
                WorkloadKind.REVIEW to reviewPending,
                WorkloadKind.NEW to newPending,
            )
            val segments = if (total <= 0) {
                emptyList()
            } else {
                counts.mapNotNull { (kind, count) ->
                    if (count <= 0) {
                        null
                    } else {
                        WorkloadSegment(kind, count, (count.toFloat() / total.toFloat()).coerceAtLeast(MIN_SHARE))
                    }
                }
            }
            return TodayWorkload(learned, reviewPending, newPending, total, segments)
        }
    }
}
