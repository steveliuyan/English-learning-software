package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 今日计划屏的**顶档形状**契约（QA3 A2.1，用户 2026-11-08 拍板「全部都做」）。
 *
 * 与既有三份的分工：[TodayPlanNumberAuthorityTest] 钉「数字的权威与排印档位」、
 * [TodayPlanBareRowContractTest] 钉「入口落点与枚数」、connected 的
 * `TodayPlanScreenTest.summaryCountRowStaysSingleLineAtTopFontScale` 钉「3.0 档实际排成几行」。
 * 本份钉的是中间那一环——**降级由谁决定、决定了几次**：形状判档必须是屏侧一处纯函数，
 * 「还剩 N 个词」这一枚必须只有一个渲染站点。缺了这份，connected 那条只能证明「顶档现在是好的」，
 * 证明不了「2.0 档没被顺手改掉」和「没有第二套判档」。
 *
 * 读法是源码文本（`uiScreenSource` 已剥注释），与 [TodayPlanBareRowContractTest] 同口径。
 */
class TodayPlanLayoutContractTest {

    private val source: String by lazy { uiScreenSource("TodayPlanScreen.kt") }

    /**
     * 判档口径沿用批 B 的「阈值严格大于钉档」：`2f` 是 AC-R-07 钉住的验收档，
     * 那一档的原型版面（行尾余量 + 参与权重的左列）是设计稿口径，不能被顶档整改带走；
     * 只有**超过**钉档才把余量搬到计数下面。
     *
     * 抽成 `internal` 纯函数是为了让这条算术在 JVM 侧逐档点名——connected 那侧只能验
     * 「顶档确实单行」，验不出「2.0 不许动」。
     */
    @Test
    fun `the remaining count moves below the number only above the pinned font tier`() {
        assertEquals(
            false,
            todayPlanRemainingMovesBelowCount(1f),
            "1.0 默认档是设计稿口径：行尾余量与左列互不侵犯，版面一字不许动",
        )
        assertEquals(
            false,
            todayPlanRemainingMovesBelowCount(2f),
            "2.0 是 AC-R-07 钉住的验收档，设备实测四枚字全单行，没有需要搬走的余量",
        )
        assertEquals(
            true,
            todayPlanRemainingMovesBelowCount(2.5f),
            "超过钉档就必须把「还剩 N 个词」搬到计数下面，否则左列被挤窄、大数字顶出首屏",
        )
        assertEquals(
            true,
            todayPlanRemainingMovesBelowCount(3f),
            "Android 字体大小顶档 3.0 必须搬（QA3 A2.1：264px 左列里眉标折两行、注脚折三行）",
        )
    }

    /**
     * 判档与渲染站点的枚数闸：一屏只许一套顶档判断、一枚余量文本。
     *
     * 反空转说明：`PlanRemainingCount(` 数出 3 是「定义 1 + 两个互斥分支各 1」，
     * 把两个分支合并成一处调用会让它掉到 2、把行尾那一处直接抄成内联 `Text(` 会让
     * `testTag("today_plan_remaining")` 涨到 2——后者正是 [TodayPlanNumberAuthorityTest]
     * 的窗口取样器最忌的「同一信息两处承载」。
     */
    @Test
    fun `the top tier shape has one decision and the footnote has one render site`() {
        assertEquals(
            2,
            countMatches(source, Regex(Regex.escape("todayPlanRemainingMovesBelowCount("))),
            "`todayPlanRemainingMovesBelowCount(` 必须恰两处（定义 + 屏侧唯一消费点）：" +
                "多出来的每一处都是第二套顶档判断",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("fontScale > 2f"))),
            "顶档阈值只许活在判档函数里（恰一处），屏侧再写一份 `> 2f` 就是两套口径",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""LocalDensity\.current\.fontScale""")),
            "字号档在本屏只读一次：多处各自读会各自判档，同一屏可能出现两种形状",
        )
        assertEquals(
            3,
            countMatches(source, Regex(Regex.escape("PlanRemainingCount("))),
            "「还剩 N 个词」必须恰三处（定义 + 顶档列下 + 钉档行尾），两个分支互斥且共用同一渲染站点",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("""testTag("today_plan_remaining")"""))),
            "`today_plan_remaining` 的标签只许一处：两处即同一数字两处承载，窗口取样器会取错站点",
        )
        assertTrue(
            "if (stackedHeader) PlanRemainingCount(remaining)" in source &&
                "if (!stackedHeader) PlanRemainingCount(remaining)" in source,
            "两个落点必须由同一枚档位量互斥选择（顶档列下 / 钉档行尾），" +
                "写成两处无条件渲染会让 1.0 档长出第二行余量",
        )
    }
}
