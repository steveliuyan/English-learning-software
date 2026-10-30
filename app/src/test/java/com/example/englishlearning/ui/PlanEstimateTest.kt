package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 批 1 Task 12 的两枚纯函数（`ui/PlanEstimate.kt`）。
 *
 * 为什么单独测而不塞进布局契约：这两句是**会说谎的那一类文案**——它们把数字与流程讲成事实，
 * 而布局契约只认源码里出现了哪个字面量，判不出「4308 词还要 756 天」这种算错的数。
 * 原型的样例数字本来就不自洽（4308 ÷ 4 = 1077 而不是 756；756 × 4 = 3024，两个半句用的是不同的词量），
 * 所以本仓把这一句改成**由真数据现算**：读不到词量就整句不显示，绝不退化成样例数字
 * （AGENTS.md「不编造指标」+ 用户口径「不要编造原型里没有的指标、百分比或功能」）。
 *
 * 两枚函数都只吃参数、不读仓库与主题，所以断言可以直接比字符串逐字相等。
 */
class PlanEstimateTest {

    // ---------- planEstimateText：原型 `.caption` 那一句的真数据版 ----------

    @Test
    fun `the estimate uses the lowest and highest prototype rates and real word counts`() {
        // 4308 ÷ 3 = 1436（整除），4308 ÷ 20 = 215.4 → 216（不足一天按一天算，说「215 天」会少排一天）。
        assertEquals(
            "按 3 个/天算，大学英语四级剩下 4308 词大约还要 1436 天；按 20 个/天算约 216 天。",
            planEstimateText("大学英语四级", 4308),
        )
    }

    @Test
    fun `the estimate rounds up on both clauses`() {
        // 12 ÷ 5 = 2.4 → 3；12 ÷ 10 = 1.2 → 2。两侧同一条进位口径。
        assertEquals(
            "按 5 个/天算，A 剩下 12 词大约还要 3 天；按 10 个/天算约 2 天。",
            planEstimateText("A", 12, options = listOf(5, 10)),
        )
    }

    @Test
    fun `a single rate does not fabricate a second clause`() {
        assertEquals(
            "按 5 个/天算，A 剩下 12 词大约还要 3 天。",
            planEstimateText("A", 12, options = listOf(5)),
        )
    }

    @Test
    fun `the estimate disappears instead of inventing numbers when the data is missing`() {
        // 词量读不出（进度回填失败）或册名缺失：整句不显示。
        // 「还要 0 天」与「按 3 个/天算，剩下 0 词」都不是这句该说的话——前者谎称学完了，后者谎称有数。
        assertNull(planEstimateText("大学英语四级", null))
        assertNull(planEstimateText(null, 4308))
        assertNull(planEstimateText("", 4308))
        assertNull(planEstimateText("大学英语四级", 0))
        assertNull(planEstimateText("大学英语四级", -1))
    }

    // ---------- revealCaptionText：原型「当前的走向」那一句 ----------

    @Test
    fun `the reveal caption follows the known-rating switch and names the button the user actually sees`() {
        // 开：评「认识」后进的是释义详情页，那页的主按钮可见文案是「下一个单词」
        // （`CardDetailScreen`：text = "下一个单词"，contentDescription = "查看下一个单词"）。
        // 原型写「点「下一个词」继续」在那条流程里对应的是它的预览按钮，照抄会指错键。
        assertEquals(
            "当前的走向：评「认识」先展开释义，点「下一个单词」继续。",
            revealCaptionText(openDetailOnKnown = true),
        )
        // 关：停在原卡的回执上，回执那枚主按钮可见文案是「下一个词」（`WordCardScreen` 回执态）。
        assertEquals(
            "当前的走向：评「认识」不展开释义，回执上点「下一个词」继续。",
            revealCaptionText(openDetailOnKnown = false),
        )
    }

    @Test
    fun `the reveal caption does not promise a second appearance that the plan does not schedule`() {
        // 原型这一句后面还挂着「标记「忘记」的词会在本轮末尾再出现一次」。本仓不成立：
        // AGENTS.md 明写「每张卡提交一次有效的反馈即记为该计划项完成」，今日计划是不可变快照，
        // 评完的词当天不再出现（FSRS 的再次排期落在之后的日期）。所以这句整条不出现。
        listOf(true, false).forEach { known ->
            val caption = revealCaptionText(openDetailOnKnown = known)
            listOf("再出现一次", "本轮末尾", "忘记").forEach { retired ->
                assertEquals(
                    false,
                    retired in caption,
                    "走向句不得承诺本仓没有的调度行为或术语：$retired（实际文案：$caption）",
                )
            }
            // 三档术语与词卡屏一致：这一句只谈「认识」，因为另外两档在本屏各自有真开关。
            assertEquals(true, "认识" in caption, "走向句必须谈的是「认识」这一档：$caption")
        }
    }
}
