package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 2 Task 22-A：「我的」根页（原型 `sMeHome`，`assets/app.js:1255-1282`）的源码侧契约。
 *
 * 原型骨架：`head({plain:true, eyebrow:'我的', title:名字, sub:'连续打卡 N 天 · 累计学过 N 个词'})`
 * → 三格统计卡 → `section('我的词汇')` 三行 → `section('学习')` 三行 → `section('应用')` 五行。
 *
 * 本页已具备：`hero` 页头（一级 tab 无返回位）、唯一一枚强档卡装三格统计（三格各是深链）、
 * 学习记录行、设置九行由宿主 `settingsContent` 注入。本轮补一处、记两处偏离：
 *
 *  1. **副标题改成原型那句**「连续打卡 N 天 · 累计学过 N 个词」（`streakDays` / `learnedWordCount`
 *     都是本页形参）。旧的「API Key 只保存在本机加密区。」不是页头身份行——它属于隐私口径，
 *     已在设置页与备份说明里出现，挂在名字下面既不回答问题也不构成信息。
 *  2. 「我的词汇」三行（收藏 / 生词本 / 易错词）**不搬成本页的入口行**：本仓三者是**词库页的四格分段**
 *     （`WordBankScreen` 的 收藏 / 生词 / 错词），再在本页放三条同一落点的行就是同一条通路的第二份入口。
 *  3. 三格中间那格的原型标名是「掌握词数」，本页仍用「已学词」：本仓没有「掌握」这一档口径
 *     （`learnedWordCount` 是已学词数），改名会把「学过」说成「掌握」。
 */
class ProfilePageHomeContractTest {

    private val screen: String by lazy { uiScreenSource("ProfileScreen.kt") }

    @Test
    fun `the header subtitle reads the two real facts the page receives`() {
        assertTrue(
            "连续打卡 ${'$'}streakDays 天" in screen,
            "副标题第一段取连续天数真值（原型 `.head-sub`）",
        )
        assertTrue(
            "累计学过 ${'$'}learnedWordCount 个词" in screen,
            "副标题第二段取累计学过词数真值（原型 `.head-sub`）",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""API Key""")),
            "页头副标题不再承担隐私口径：那属于设置页与备份说明（本页不重复）",
        )
    }

    @Test
    fun `the three stat cells keep their own anchors and honest labels`() {
        listOf("profile_stat_learned", "profile_stat_streak", "profile_stat_articles").forEach { tag ->
            assertEquals(1, countMatches(screen, Regex(Regex.escape(tag))), "统计格锚「$tag」恰一处")
        }
        assertTrue("\"已学词\"" in screen, "第一格标签是「已学词」（本仓没有「掌握」口径，不改名）")
        assertEquals(
            0,
            countMatches(screen, Regex("""掌握词数""")),
            "原型 mock 的「掌握词数」不搬（`learnedWordCount` 是已学词数）",
        )
    }

    @Test
    fun `the vocabulary group stays on the word bank page instead of duplicating entries`() {
        listOf("收藏的词", "生词本", "易错词").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "「$token」是词库页四格分段的入口，不在「我的」再挂第二份",
            )
        }
    }
}
