package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 「我的」根页页头与三格统计（原型 `sMeHome`，`assets/app.js:1255-1282`）的源码侧契约。
 *
 * 原型骨架：`head({plain:true, eyebrow:'我的', title:名字, sub:'连续打卡 N 天 · 累计学过 N 个词'})`
 * → 三格统计卡 → `section('我的词汇')` 三行 → `section('学习')` 行 → `section('应用')` 行。
 * 台账、分组与逐行接线的判据住在 `ProfileHubLedgerTest` / `ProfilePageContractTest`；
 * 本文件只管**页头那两句真话**和**三格统计的形状**——它们是这一页唯一不属于台账的东西。
 *
 * 本轮（2026-11-02，`docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`）取代 Task 22-A 的第 2 条：
 *
 *  - 旧裁定说「我的词汇」三行不搬进本页，因为词库页四分段是唯一出口。用户拍板按参考重做后，
 *    这三行**就是** hub 的第一组，但落点仍是那四分段——搬的是入口，不是第二份通路。
 *    因此本文件的判据从「这些字不得出现」换成「这三行各自点名落在哪一段」：
 *    绕开 `onOpenWordBankSection` 自己 `showWordBank = true` 才是回归。
 *
 * 仍然成立、继续钉死的四条：
 *
 *  1. 副标题是原型那句「连续打卡 N 天 · 累计学过 N 个词」（`streakDays` / `learnedWordCount` 都是本页形参）；
 *     「API Key 只保存在本机加密区」是隐私口径，不属于页头身份行。
 *  2. 第一格标名是「已学词」而不是原型的「掌握词数」：本仓没有「掌握」这一档口径，改名会把「学过」说成「掌握」。
 *  3. 第三格仍是「文章」而不是原型的「收藏」：收藏数的出口是词库页收藏分段，而 hub 第一组已经有一行通往它，
 *     数字格再挂一枚同落点就是那条通路的第二个入口；文章数指向阅读历史，没有这个问题。
 *  4. 页头不带 `eyebrow`：一级 tab 的页名由底部导航回答，眉标在本仓留给会变的位置信息。
 */
class ProfilePageHomeContractTest {

    private val screen: String by lazy { uiScreenSource("ProfileScreen.kt") }

    /**
     * 三行 `ProfileStatCell(...)` 调用本体（本屏逐枚写成单行）。U-8 之后「收藏」在 hub 行合法在册，
     * 所以「统计格不许标收藏」这条只能扫这三行，不能扫整份源码。
     */
    private val statCellLines: List<String> by lazy {
        Regex("""(?m)^[ \t]*ProfileStatCell\(""")
            .findAll(screen)
            .map { match ->
                val lineEnd = screen.indexOf('\n', match.range.first).let { i -> if (i == -1) screen.length else i }
                screen.substring(match.range.first, lineEnd).trim()
            }
            .toList()
    }

    /**
     * 页头那两句仍是原型 `.head-sub` 的两句真话，但**拼法**随批 C-2 改过（`docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md`
     * 决定 3 / Spec 05 F5-05）：两枚数字改成可空形参，副标题由 `listOfNotNull` 只拼**读到的那几枚**，
     * 都没读到时整行不出现。旧判据钉的是「`$streakDays` 直接插进整句」，那正是首帧念出
     * 「连续打卡 0 天 · 累计学过 0 个词」的形状——把「还没读到」说成「一个都没学」。
     * pending 的三格占位与宿主接线由 `PrimaryTabSkeletonContractTest` 与 `ProfilePageContractTest` 承重。
     */
    @Test
    fun `the header subtitle reads the two real facts the page receives`() {
        assertTrue(
            "streakDays?.let { \"连续打卡 ${'$'}it 天\" }" in screen,
            "副标题第一段取连续天数真值（原型 `.head-sub`），且必须经 `?.let`——null 时整段不拼",
        )
        assertTrue(
            "learnedWordCount?.let { \"累计学过 ${'$'}it 个词\" }" in screen,
            "副标题第二段取累计学过词数真值（原型 `.head-sub`），且必须经 `?.let`——null 时整段不拼",
        )
        assertTrue(
            "listOfNotNull(" in screen,
            "两句必须由一份可空清单拼：写死的整句模板就是假 0 的入口",
        )
        assertEquals(
            0,
            countMatches(screen, Regex(Regex.escape("连续打卡 \$streakDays"))),
            "不得再把可空的 streakDays 直接插进整句（null 会念成「连续打卡 null 天」）",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""API Key""")),
            "页头副标题不再承担隐私口径：那属于设置子页与备份说明（本页不重复）",
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
     * 原型第三格是「收藏」，本页第三格是「文章」。这不是「读不到」——收藏数在宿主侧读得到；
     * 不搬的理由是**落点重复**：hub 第一组已经有一行通往词库收藏分段，
     * 三格上再挂一枚同落点的数字就是那条通路开第二个入口。
     *
     * 反向钉住：一旦有人照原型把第三格改成收藏，这条会红，逼他先回答「第二入口还成不成立」。
     *
     * 口径收窄（U-8，决策 2026-11-03 §5）：hub 那行本来就标作「收藏」——旧写法在整份源码扫
     * `"收藏"` 并要求归零，改名之后会把 hub 行本身判红，而它恰恰是这句理由里的**合法那一枚入口**。
     * 本条判据说的是「统计格不许长出一个收藏标签」，所以扫描面只能落在三行 `ProfileStatCell` 上；
     * 份数钉（恰 3 行）保证这条不会因为写法换行而空转。
     */
    @Test
    fun `the third stat cell stays the article fact instead of the prototype favorite count`() {
        assertTrue("\"文章\"" in screen, "第三格标签是「文章」（阅读历史的真数，锁定态也读得到）")
        assertEquals(
            1,
            countMatches(screen, Regex(Regex.escape("profile_stat_articles"))),
            "第三格的锚是 `profile_stat_articles`：改名等于把既有 connected 判据与宿主接线静默断开",
        )
        assertEquals(3, statCellLines.size, "三格统计必须恰 3 行可取（${statCellLines.size} 行＝取法失效，本条会空转）")
        statCellLines.forEach { line ->
            assertTrue(
                "\"收藏\"" !in line,
                "统计格不得有一枚标签就叫「收藏」：它的入口是 hub 第一组那行（落点重复才是要钉的东西）——命中行：$line",
            )
        }
        assertEquals(
            0,
            countMatches(screen, Regex("""eyebrow\s*=""")),
            "原型 eyebrow「我的」不搬：一级 tab 的页名由底部导航回答，眉标留给会变的位置信息",
        )
    }

    /**
     * 词汇三行的落点必须**点名段位**：收藏 → Favorite、生词 → New、错词 → Wrong，
     * 「词书」格与「词书」行 → Book。只赋 `showWordBank = true` 不赋段位＝点「错词」看到词书段第一页。
     */
    @Test
    fun `the vocabulary rows name their word bank section instead of landing on the default one`() {
        listOf("WordBankSection.Favorite", "WordBankSection.New", "WordBankSection.Wrong", "WordBankSection.Book").forEach { section ->
            assertTrue(
                screen.contains(section),
                "「$section」必须由本页的装配点点名（不点名就是落回默认段位的死接线）",
            )
        }
        assertEquals(
            0,
            countMatches(screen, Regex("""showWordBank""")),
            "本页不持有词库开关：段位与开关都在宿主那一层（决策 §2 的第 4 条纪律）",
        )
    }
}
