package com.example.englishlearning.ui

import com.example.englishlearning.learning.UnlockMode
import com.example.englishlearning.learning.domain.DailyLearningHistory
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 原型对齐（`sMeRecords`，`assets/app.js:1304-1327`）：「本周」七格刻度 + 三格统计 + 每日进度条。
 *
 * 两半判据：
 *  ① **纯函数**（[learningWeek]）：周窗口固定周一到周日、单日状态 `完成 / 部分 / 空`、本周词数只加
 *     本窗口内的「新学 + 复习」完成数。原型格子读的是 `D.week[].on`、`D.records[].newDone + reviewDone`，
 *     本仓对应真值是 `DailyLearningHistory`（`historyRange` 逐日详展），不新造采集。
 *  ② **源码契约**：页头归共享 `AppScreenHeader`、统计只画两枚真数（原型「日均用时」在本仓没有任何采集，
 *     按「读不到就整枚不显示」不写）、每日行挂 4dp 进度条。
 *
 * 原型「本周」右侧那枚 `3 / 7 天` 是「本周完成天数 / 7」，由 [LearningWeek.doneDays] 派生，
 * 不是另抄一个常量。
 */
class LearningRecordsWeekContractTest {

    private val source: String by lazy { uiScreenSource("LearningRecordsScreen.kt") }

    private fun day(
        date: LocalDate,
        newDone: Int = 0,
        newTarget: Int = 0,
        dueDone: Int = 0,
        dueTarget: Int = 0,
        unlocked: Boolean = false,
    ) = DailyLearningHistory(
        localDate = date,
        activeWordBookId = "book",
        activeWordBookName = "大学英语四级",
        newDone = newDone,
        newTarget = newTarget,
        dueDone = dueDone,
        dueTarget = dueTarget,
        unlockMode = UnlockMode.Strict,
        unlocked = unlocked,
        articleGenerated = true,
        articleRead = false,
    )

    // ---------- ① 纯函数 ----------

    @Test
    fun `the week always runs monday to sunday whatever weekday today is`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val week = learningWeek(listOf(day(thursday)), thursday)
        assertEquals(7, week.ticks.size, "原型的刻度条是七天一格不少（app.css:681 .ticks 七个 .tick）")
        assertEquals(LocalDate.of(2026, 11, 9), week.ticks.first().date, "周窗口从周一起（原型 D.week 首格是「一」）")
        assertEquals(LocalDate.of(2026, 11, 15), week.ticks.last().date, "周窗口到周日止")
        assertEquals(
            listOf("一", "二", "三", "四", "五", "六", "日"),
            week.ticks.map { it.label },
            "刻度标签是「一…日」，原型 weekRow 的 labels 逐格取 d.d",
        )
        assertEquals(
            listOf(thursday),
            week.ticks.filter { it.isToday }.map { it.date },
            "今天只有一格（原型 .tick[data-today='1'] 只描一格）",
        )
    }

    @Test
    fun `done partial and empty days map onto the three tick states`() {
        val today = LocalDate.of(2026, 11, 12)
        val week = learningWeek(
            listOf(
                day(LocalDate.of(2026, 11, 9), newDone = 12, newTarget = 12, dueDone = 9, dueTarget = 9, unlocked = true),
                day(LocalDate.of(2026, 11, 12), newDone = 1, newTarget = 4, dueDone = 2, dueTarget = 2),
                day(LocalDate.of(2026, 11, 10), newDone = 0, newTarget = 10, dueDone = 0, dueTarget = 3),
            ),
            today,
        )
        assertEquals(WeekDayState.Done, week.ticks[0].state, "新学 + 复习都到目标 = 完成（data-on='1'）")
        assertEquals(WeekDayState.None, week.ticks[1].state, "有目标、零完成 = 空格（data-on='0'），不能画成部分")
        assertEquals(WeekDayState.Partial, week.ticks[3].state, "做了一部分 = 半格（data-on='half'）")
        assertEquals(WeekDayState.None, week.ticks[4].state, "没有计划快照的那天是空格，不臆造一个状态")
    }

    @Test
    fun `a day with zero targets counts as done only when the plan was unlocked`() {
        val today = LocalDate.of(2026, 11, 12)
        val unlocked = learningWeek(listOf(day(today, unlocked = true)), today)
        assertEquals(WeekDayState.Done, unlocked.ticks[3].state, "目标为 0 且已解锁 = 那天没有要做的（严格模式下两条目标都视为已满足）")
        val locked = learningWeek(listOf(day(today, unlocked = false)), today)
        assertEquals(WeekDayState.None, locked.ticks[3].state, "目标为 0 但未解锁：不能算作打卡")
    }

    @Test
    fun `week word count sums new and review done inside the window only`() {
        val today = LocalDate.of(2026, 11, 12)
        val week = learningWeek(
            listOf(
                // 上周日：在窗口外，必须不计入。
                day(LocalDate.of(2026, 11, 8), newDone = 99, newTarget = 99),
                day(LocalDate.of(2026, 11, 9), newDone = 12, newTarget = 12, dueDone = 9, dueTarget = 9, unlocked = true),
                day(LocalDate.of(2026, 11, 10), newDone = 10, newTarget = 10, dueDone = 8, dueTarget = 8, unlocked = true),
                day(LocalDate.of(2026, 11, 11), newDone = 0, newTarget = 5),
            ),
            today,
        )
        assertEquals(39, week.wordCount, "本周词数 = 本窗口内 (newDone + dueDone) 之和（原型 D.records[].words 就是这口径）")
        assertEquals(2, week.doneDays, "打卡天数 = 本周「完成」格数（原型右侧「3 / 7 天」的分子）")
    }

    @Test
    fun `no stats at all means no week block to draw`() {
        assertTrue(learningWeek(emptyList(), LocalDate.of(2026, 11, 12)).isEmpty, "一条计划快照都没有时不画空刻度条（分母未知，画出来只能是假的）")
        assertFalse(learningWeek(listOf(day(LocalDate.of(2026, 11, 12))), LocalDate.of(2026, 11, 12)).isEmpty)
    }

    // ---------- ② 源码契约 ----------

    @Test
    fun `the hand written back row retires in favour of the shared header`() {
        assertEquals(1, countMatches(source, Regex("""\bAppScreenHeader\(""")), "学习记录页头必须换共享 `AppScreenHeader(`（恰 1 处）")
        assertEquals(0, countMatches(source, Regex(Regex.escape("\"learning_records_back\""))), "手写「← 返回」的旧锚退役：返回位固定归共享页头（可及名「返回」+ 48dp 触控盒）")
        assertEquals(1, countMatches(source, Regex("""onBack = onBack""")), "`onBack` 必须接进共享页头的返回位")
    }

    @Test
    fun `the week strip and the two real stats are drawn, the mocked duration is not`() {
        assertTrue("\"本周\"" in source, "原型 section('本周') 的分组标题要在（SectionHeading 的 text 参数）")
        assertTrue("/ 7 天" in source, "分组标题右侧的「x / 7 天」必须由 doneDays 派生（原型 :1308 的第三参）")
        listOf("本周词数", "打卡天数").forEach { label ->
            assertEquals(1, countMatches(source, Regex(Regex.escape("\"$label\""))), "统计标签「$label」必须恰 1 处")
        }
        assertEquals(
            0,
            countMatches(source, Regex("日均用时")),
            "原型第三格「19 分 · 日均用时」在本仓没有任何采集（无单轮用时/日累计时长的数据源）：" +
                "按用户 2026-10-29 拍板「读不到的那枚整枚不显示」，整枚不画，也不改写成「约」「稍后」",
        )
        listOf("learning_records_week", "learning_records_stats").forEach { tag ->
            assertEquals(1, countMatches(source, Regex(Regex.escape("\"$tag\""))), "锚「$tag」必须恰 1 处")
        }
        assertEquals(1, countMatches(source, Regex("""\bLinearProgressIndicator\(""")), "每日记录行的进度条用 M3 `LinearProgressIndicator(`（恰 1 处，4dp 细条 = 原型 .meter-thin）")
        assertTrue("learning_records_day_meter_" in source, "每日行的进度条要有动态锚（按日期）")
    }

    @Test
    fun `the screen reads tokens and keeps the shared components`() {
        assertEquals(0, countMatches(source, Regex("""\bMint[A-Za-z0-9_]*""")), "`Mint*` 静态桥必须清零：底色/墨全部走 `appColors()`")
        assertEquals(0, countMatches(source, Regex("""MaterialTheme\.typography""")), "排印走六档 `AppType`")
        assertEquals(0, countMatches(source, Regex("""\bfontSize""")), "手搓字号必须清零")
        assertEquals(0, countMatches(source, Regex("""\bCard\(""")), "M3 `Card(` 必须为 0：统计格走共享 `AppCard`")
        assertTrue("AppCard(" in source, "统计格用共享 `AppCard`（原型 .card）")
        assertTrue("SectionHeading(" in source, "分组标题用共享 `SectionHeading`（原型 .sec-title）")
        assertEquals(0, countMatches(source, Regex("""\bStrongCard\(""")), "本屏零强卡（一屏至多一枚的额度不在这里）")
    }
}
