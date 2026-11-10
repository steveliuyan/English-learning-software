package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 今日计划屏「当天固定」提示的契约（用户 2026-11-09 拍板「可以」）。
 *
 * 为什么要单独一份：这条提示承载的不是版面而是**规则的出口**——AGENTS.md《学习与解锁规则》
 * 把当日计划定为不可变快照（新到期词不加入、已提交反馈后不得静默重建），于是设备走查里
 * 必然出现「刚判了『不认识』、复习队列还是 0」的困惑。提示必须同时说清两件事：
 * ①这是规则（新到期的词不会自动加入）②唯一的显式重建出口（到「学习计划」重新选择词书并保存）。
 * 少了①就是抱怨，少了②就是把人堵在原地。另外它**不许**提遗忘预测——本仓没有那种模型。
 *
 * 读法与 [TodayPlanLayoutContractTest] 同口径：`uiScreenSource` 已剥注释，只对活代码取文本。
 */
class TodayPlanFixedHintContractTest {

    private val source: String by lazy { uiScreenSource("TodayPlanScreen.kt") }

    /** 整块形状：一枚 Footnote、次级色、专用 testTag；顺手把文案本身捕出来做内容钉。 */
    private val hintBlock = Regex(
        """Text\(\s*"([^"]+)",\s*style = AppType\.Footnote,\s*color = colors\.text2,\s*modifier = Modifier\.testTag\("today_plan_fixed_hint"\),\s*\)""",
    )

    private val hintTag = Regex(Regex.escape("""testTag("today_plan_fixed_hint")"""))

    @Test
    fun `the fixed plan hint is one footnote that names the rule and the way out`() {
        val matches = hintBlock.findAll(source).toList()
        assertEquals(
            1,
            matches.size,
            "「当天固定」提示必须恰一枚 Footnote 级 Text（今天是 ${matches.size} 枚）：" +
                "多一枚就是同一规则两处承载，零枚则规则又回到只有 AGENTS.md 知道",
        )
        val copy = matches.single().groupValues[1]
        assertTrue(
            "当天固定" in copy,
            "文案要点明这条规则是「当天固定」，而不只是陈述一句限制：$copy",
        )
        assertTrue(
            "不会自动加入" in copy,
            "文案必须说清「新到期的词不会自动加入」——这是用户看到「复习队列还是 0」时的唯一解释：$copy",
        )
        assertTrue(
            "学习计划" in copy && "重新选择词书并保存" in copy,
            "文案必须给出唯一合法的重建出口（到「学习计划」重新选择词书并保存）：" +
                "`GetOrCreateTodayPlanUseCase` 只认 `activeWordBookId` 变化，别的操作都不会重算：$copy",
        )
        assertTrue(
            "分钟" !in copy && "遗忘" !in copy && "小时" !in copy,
            "文案不许提遗忘预测：本仓没有遗忘曲线模型（10 分钟 / 23 小时是排期器给的回执口径，" +
                "不是可解释的预测），在这里复述会凭空造出一个承诺：$copy",
        )
    }

    /**
     * 落点枚数：标签恰一处，且必须长在「接下来」那张卡**之后**——卡内是今日词单/复习队列/文章/词书
     * 四条入口，提示是它们的脚注；塞进卡里会被当成第五条入口，塞到卡前（强卡与注脚之间）
     * 又会在「今天没有待办」分支里被顺手带出来，两处都不对。
     */
    @Test
    fun `the fixed plan hint sits once, right after the secondary card`() {
        assertEquals(
            1,
            countMatches(source, hintTag),
            "`today_plan_fixed_hint` 的标签必须恰一处：两处即同一提示两处承载",
        )
        val hintAt = source.indexOf("""testTag("today_plan_fixed_hint")""")
        val cardAt = source.indexOf("""testTag("today_plan_secondary_card")""")
        assertTrue(cardAt > 0, "「接下来」那张卡的标签 `today_plan_secondary_card` 读不到（卡片被改名？）")
        assertTrue(
            hintAt > cardAt,
            "提示必须落在「接下来」卡之后（当前索引 hint=$hintAt / card=$cardAt）：" +
                "卡前的位置在 `totalTarget == 0` 分支里也会渲染，提示会跟着出现在「今天没有待办」下面",
        )
    }

    /**
     * 反空转样本：正则必须能区分「形状还在」和「形状被改了一半」。
     * 少了这两行，上面两条断言可能对着一个永远匹配不到的式子绿着。
     */
    @Test
    fun `the hint shape regex is not vacuous`() {
        val good = """
            Text(
                "今日计划当天固定：新到期的词不会自动加入。",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("today_plan_fixed_hint"),
            )
        """.trimIndent()
        assertEquals(
            1,
            hintBlock.findAll(good).count(),
            "样本能匹配却数不出 1 枚，说明形状钉本身写错",
        )
        assertEquals(
            1,
            countMatches(good, hintTag),
            "标签钉对着样本必须数出 1 枚",
        )
        assertEquals(
            0,
            hintBlock.findAll(good.replace("AppType.Footnote", "AppType.Body")).count(),
            "换掉排印档（Footnote → Body）必须当场判红，否则这条钉只是长得像一条钉",
        )
        assertEquals(
            0,
            countMatches(good.replace("today_plan_fixed_hint", "today_plan_hint"), hintTag),
            "标签改名必须当场判红",
        )
    }
}
