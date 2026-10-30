package com.example.englishlearning.ui

import com.example.englishlearning.learning.domain.DailyLearningHistory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 「本周」七格刻度条的单日状态（原型 `assets/app.css:681-690` 的 `.tick[data-on]`）。
 *
 * 三态与原型一一对应：`data-on='1'` → [Done]、`data-on='half'` → [Partial]、`data-on='0'` → [None]。
 */
enum class WeekDayState { Done, Partial, None }

/** 一格刻度：日期、标签（一…日）、状态、是否今天（原型 `.tick[data-today='1']`）。 */
data class WeekTick(
    val date: LocalDate,
    val label: String,
    val state: WeekDayState,
    val isToday: Boolean,
)

/**
 * 「本周」区块的数据模型（原型 `sMeRecords` 的 `section('本周', weekRow(), '3 / 7 天')`，
 * `assets/app.js:1308`）：
 *
 *  - [ticks] 恒为 7 格，周一到周日（原型的 `D.week` 首格就是「一」）；
 *  - [doneDays] 是右侧 `x / 7 天` 的分子，也是统计格「打卡天数」——两处共用同一枚派生值，
 *    不在渲染侧另数一遍；
 *  - [wordCount] 是「本周词数」：本窗口内 `newDone + dueDone` 之和，与原型 `D.records[].words`
 *    的口径相同（`words: 21` 当天的 `newDone 12 + reviewDone 9`）。
 *
 * 纯 Kotlin、不碰 Compose：周窗口与状态映射这类算术由 `LearningRecordsWeekContractTest` 在 JVM 侧钉死。
 */
data class LearningWeek(
    val ticks: List<WeekTick>,
    val doneDays: Int,
    val wordCount: Int,
) {
    /** 一条计划快照都没有：不画刻度条（分母未知，画出来只能是假的）。 */
    val isEmpty: Boolean get() = ticks.isEmpty()
}

/** 刻度标签，下标即 `DayOfWeek.value - 1`。 */
private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 把逐日详展聚合成「本周」区块。窗口按 [today] 所在的自然周（周一起）取 7 天。
 *
 * 状态判定：
 *  - 该日没有计划快照（不在 [stats] 里）→ [WeekDayState.None]，不臆造「那天没学」以外的含义；
 *  - 目标为 0 且已解锁（严格模式下两条目标都视为已满足）→ [WeekDayState.Done]；未解锁 → [WeekDayState.None]；
 *  - 完成数达到目标 → [WeekDayState.Done]；做了一部分 → [WeekDayState.Partial]；零完成 → [WeekDayState.None]。
 */
fun learningWeek(stats: List<DailyLearningHistory>, today: LocalDate): LearningWeek {
    if (stats.isEmpty()) return LearningWeek(emptyList(), 0, 0)
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val byDate = stats.associateBy { it.localDate }
    val ticks = (0L..6L).map { offset ->
        val date = weekStart.plusDays(offset)
        val day = byDate[date]
        val done = day?.let { it.newDone + it.dueDone } ?: 0
        val target = day?.let { it.newTarget + it.dueTarget } ?: 0
        val state = when {
            day == null -> WeekDayState.None
            target <= 0 -> if (day.unlocked) WeekDayState.Done else WeekDayState.None
            done >= target -> WeekDayState.Done
            done > 0 -> WeekDayState.Partial
            else -> WeekDayState.None
        }
        WeekTick(
            date = date,
            label = WEEKDAY_LABELS[date.dayOfWeek.value - 1],
            state = state,
            isToday = date == today,
        )
    }
    val weekDates = ticks.mapTo(mutableSetOf()) { it.date }
    return LearningWeek(
        ticks = ticks,
        doneDays = ticks.count { it.state == WeekDayState.Done },
        wordCount = stats.filter { it.localDate in weekDates }.sumOf { it.newDone + it.dueDone },
    )
}
