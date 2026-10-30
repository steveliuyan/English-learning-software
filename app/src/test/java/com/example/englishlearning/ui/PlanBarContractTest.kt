package com.example.englishlearning.ui

import com.example.englishlearning.ui.components.planBarLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 1（原型对齐）Task「今日计划进度条」的契约：`components/PlanBar.kt` 的 `PlanProgressBar`
 * 必须复刻原型 `assets/app.css` `.plan-bar` / `.plan-seg` 的**两级**语义。
 *
 * 原型 `assets/app.js:324-327` 的两段写法是：
 * ```
 * <span class="plan-seg" style="flex:0 0 newShare%"><i style="width:newPct%"></i></span>
 * <span class="plan-seg plan-seg-due" style="flex:0 0 dueShare%"><i style="width:duePct%"></i></span>
 * ```
 * 即 **段宽 = 目标份额**（`newTarget / total`），**段内填充 = 完成比**（`doneNew / newTarget`）。
 * 批 0 那版把两段都按完成比加权（`weight(newWidth)`），段宽随进度伸缩——那会让「还剩多少」读不出来，
 * 也与原型的 `flex:0 0 share%` 不同构。故本文件先钉纯函数 [planBarLayout] 的取值，再钉组件确实消费它。
 *
 * 为什么取值断言放 JVM（与 `SourceRatchet` 文件头同一口径）：份额与填充比是纯算术，JVM 直接问得出；
 * 真机侧只留「颜色/高度/间隙实际渲染」那一半，归 androidTest（批 1 屏级用例）。
 */
class PlanBarContractTest {

    /**
     * 段宽跟着**目标**走，不跟完成走。
     * 取 1 新 / 3 复习、新学已全做完：若实现按「完成比」加权会得到 100% / 0%，
     * 按原型口径应是 25% / 75%——这条在批 0 的旧实现上必红，是本次改写的核心判据。
     */
    @Test
    fun segment_widths_follow_the_target_share_not_the_completion_ratio() {
        val layout = planBarLayout(newDone = 1, newTarget = 1, dueDone = 0, dueTarget = 3)
        assertEquals(0.25f, layout.newShare, 0.0001f, "新学段宽 = 新学目标 / 总目标（原型 flex:0 0 newShare%）")
        assertEquals(0.75f, layout.dueShare, 0.0001f, "复习段宽 = 1 - 新学段宽（原型 dueShare = 100 - newShare）")
    }

    /** 段内填充按**各自的**完成比，两段互不串味：新学一半、复习全清。 */
    @Test
    fun each_segment_fills_by_its_own_completion() {
        val layout = planBarLayout(newDone = 2, newTarget = 4, dueDone = 4, dueTarget = 4)
        assertEquals(0.5f, layout.newFill, 0.0001f, "新学段内填充 = doneNew / newTarget")
        assertEquals(1f, layout.dueFill, 0.0001f, "复习段内填充 = doneReview / dueTarget")
    }

    /** 某一段目标为 0 时整段塌成 0 宽（原型 0% 不可见），另一段吃掉整根条；塌掉的段填充也为 0。 */
    @Test
    fun a_zero_target_segment_collapses_and_the_other_takes_the_whole_bar() {
        val onlyDue = planBarLayout(newDone = 0, newTarget = 0, dueDone = 2, dueTarget = 5)
        assertEquals(0f, onlyDue.newShare, 0.0001f)
        assertEquals(1f, onlyDue.dueShare, 0.0001f)
        assertEquals(0f, onlyDue.newFill, 0.0001f, "没有新学目标就没有可填的段（0/0 不得算成 100%）")

        val onlyNew = planBarLayout(newDone = 3, newTarget = 5, dueDone = 0, dueTarget = 0)
        assertEquals(1f, onlyNew.newShare, 0.0001f)
        assertEquals(0f, onlyNew.dueShare, 0.0001f)
        assertEquals(0f, onlyNew.dueFill, 0.0001f)
    }

    /** 两段目标都为 0（空计划）走原型缺省 50/50，不除零、也不假装完成。 */
    @Test
    fun an_empty_plan_falls_back_to_half_and_half_with_no_fill() {
        val layout = planBarLayout(newDone = 0, newTarget = 0, dueDone = 0, dueTarget = 0)
        assertEquals(0.5f, layout.newShare, 0.0001f)
        assertEquals(0.5f, layout.dueShare, 0.0001f)
        assertEquals(0f, layout.newFill, 0.0001f)
        assertEquals(0f, layout.dueFill, 0.0001f)
    }

    /** 超出目标（如临时多学）不得让填充比越界成 >1，否则 `fillMaxWidth(1.6f)` 会被静默截断或撑出轨道。 */
    @Test
    fun over_completion_clamps_fill_to_one() {
        val layout = planBarLayout(newDone = 5, newTarget = 2, dueDone = 9, dueTarget = 3)
        assertEquals(1f, layout.newFill, 0.0001f)
        assertEquals(1f, layout.dueFill, 0.0001f)
    }

    /** 负数（脏数据/快照回滚）按 0 处理：填充比不得为负，份额不得为负。 */
    @Test
    fun negative_counts_are_clamped_to_zero() {
        val layout = planBarLayout(newDone = -3, newTarget = 4, dueDone = 1, dueTarget = -2)
        assertEquals(0f, layout.newFill, 0.0001f)
        assertEquals(0f, layout.dueFill, 0.0001f)
        assertTrue(layout.newShare >= 0f && layout.dueShare >= 0f, "份额不得为负")
        assertEquals(1f, layout.newShare + layout.dueShare, 0.0001f, "两段份额恒和为 1")
    }

    /**
     * 组件侧：`PlanProgressBar` 必须真的用 [planBarLayout] 的结果，并保留原型的几何与动画。
     * 这条防的是「纯函数写对了、组件却还在用旧口径加权」——单看函数测不出来。
     */
    @Test
    fun the_component_consumes_the_pure_layout_and_keeps_the_prototype_geometry() {
        val source = uiComponentSource("PlanBar.kt")
        assertEquals(
            1,
            // 定义行的形参是 `planBarLayout(newDone: Int, …)`，调用行的实参是 `planBarLayout(newDone, …)`；
            // 锚点带上逗号只命中调用点，不会把纯函数自己的定义数进来。
            countMatches(source, Regex("""planBarLayout\(newDone,""")),
            "PlanProgressBar 必须恰一处调用 planBarLayout（第二份内联算术 = 两套口径）",
        )
        listOf(
            "height(10.dp)" to "原型 .plan-bar height:10px",
            "Arrangement.spacedBy(2.dp)" to "原型 .plan-bar gap:2px",
            "AppShape.Pill" to "原型 border-radius: var(--r-pill)",
            "copy(alpha = 0.14f)" to "原型轨道 rgba(233,242,236,.14)",
            "copy(alpha = 0.62f)" to "原型复习段 rgba(233,242,236,.62)",
            "colors.brand" to "原型新学段 background: var(--brand)",
            "AppMotion.Slow" to "原型 transition: width var(--dur-slow)",
        ).forEach { (needle, why) ->
            assertTrue(
                source.contains(needle),
                "PlanBar.kt 剥注释后缺 `$needle`——$why（原型几何被改写）",
            )
        }
        assertTrue(
            source.contains("weight("),
            "两段宽度必须走 weight（原型 flex:0 0 share%），旧版也是这样，缺它说明段宽又被写死",
        )
        assertTrue(
            source.contains("LocalContentColor.current"),
            "轨道与复习段取内容色：这根条住在强卡里，浅套（浅薄荷底 + text1 墨）与深套都要可读",
        )
    }
}
