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
 * 学习记录行。设置四组九行自 2026-10-31 起搬去独立的设置栏，不再由宿主注入本页。本轮补一处、记两处偏离：
 *
 *  1. **副标题改成原型那句**「连续打卡 N 天 · 累计学过 N 个词」（`streakDays` / `learnedWordCount`
 *     都是本页形参）。旧的「API Key 只保存在本机加密区。」不是页头身份行——它属于隐私口径，
 *     已在设置页与备份说明里出现，挂在名字下面既不回答问题也不构成信息。
 *  2. 「我的词汇」三行（收藏 / 生词本 / 易错词）**不搬成本页的入口行**：本仓三者是**词库页的四格分段**
 *     （`WordBankScreen` 的 收藏 / 生词 / 错词），再在本页放三条同一落点的行就是同一条通路的第二份入口。
 *  3. 三格中间那格的原型标名是「掌握词数」，本页仍用「已学词」：本仓没有「掌握」这一档口径
 *     （`learnedWordCount` 是已学词数），改名会把「学过」说成「掌握」。
 *
 * Task 22-M（#24 收口）补两处：
 *
 *  4. 原型第三格是「收藏」（`D.favorites`，点开进 `me-words/fav`），本页第三格仍是「文章」。
 *     收藏数在宿主侧读得到（`FavoritesUiState.Ready.words.size`），但它的出口就是词库页的
 *     收藏分段——Task 22-A 第 2 行已裁定那一段**是唯一出口**，再在本页放一枚同落点的数字格
 *     等于同一条通路开第二个入口。文章数没有这个重复问题：它指向阅读历史，且是真正的进度事实。
 *  5. 原型页头 `eyebrow: '我的'` **不搬**：一级 tab 的底部导航已经回答「在哪一页」，
 *     眉标在本仓留给会变的位置信息（今日学习页的计划日期），不重复 tab 名。
 *
 * 「应用」组的「关于与许可」不落在本页源码里，而是走 **设置页 →「外观与关于」组 → 关于** 这条通路
 * （2026-10-31 起设置独立为第五栏，本页不再内联设置内容）。本页再写一行就是同一落点的第二份入口；
 * 该接线的形状判据在 `ProfilePageContractTest`，行为判据在 connected `AppScreenTest`。
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

    /**
     * 原型第三格是「收藏」，本页第三格是「文章」。这不是「读不到」——收藏数在 `FavoritesUiState.Ready`
     * 里现算得到；不搬的理由是**落点重复**：收藏数的唯一出口是词库页收藏分段（Task 22-A 第 2 行），
     * 而在三格上再挂一枚通向同一分段的数字，就是那条通路开第二个入口。
     *
     * 反向钉住：一旦有人照原型把第三格改成收藏，这条会红，逼他先回答「那条唯一出口的裁决还成不成立」。
     */
    @Test
    fun `the third stat cell stays the article fact instead of the prototype favorite count`() {
        assertTrue("\"文章\"" in screen, "第三格标签是「文章」（阅读历史的真数，锁定态也读得到）")
        assertEquals(
            1,
            countMatches(screen, Regex(Regex.escape("profile_stat_articles"))),
            "第三格的锚是 `profile_stat_articles`：改名等于把既有 connected 判据与宿主接线静默断开",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""收藏""")),
            "收藏不在本页出现：它的出口是词库页收藏分段（Task 22-A 第 2 行），三格不得成第二入口",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""eyebrow\s*=""")),
            "原型 eyebrow「我的」不搬：一级 tab 的页名由底部导航回答，眉标留给会变的位置信息（Task 22-M 第 5 条）",
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
