package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 我的页（spec §3.6）的源码契约：档案头 + 三格统计深链 + 学习记录行 + 设置四组十行（Task 22-B 加关于）。
 *
 * 为什么这层必须有：我的页是「设置 tab」的新根页，而它并不自己实现设置内容——四组十行仍归
 * `SettingsScreen.kt`。抽出 `SettingsGroupsContent` 后有两件容易发生的回归：
 *  ① 页头与滚动各抄一份（我的页与设置页各带一个 `settings_screen` 根、各滚各的），
 *  ② 统计格只画数字不接深链（用户点了没反应，还把「已学词」冤枉成一个死数字）。
 * 行为面由 connected `ProfileScreenTest` 与宿主 `AppScreenTest` 承重；本文件钉形状与接线。
 */
class ProfilePageContractTest {
    private val profile: String by lazy { stripComments(uiScreenSource("ProfileScreen.kt")) }
    private val host: String by lazy { stripComments(uiScreenSource("AppScreen.kt")) }
    private val settings: String by lazy { stripComments(uiScreenSource("SettingsScreen.kt")) }

    @Test
    fun `three stat cells each carry their own deep link`() {
        listOf("profile_stat_learned", "profile_stat_streak", "profile_stat_articles").forEach { tag ->
            assertEquals(1, countMatches(profile, Regex(Regex.escape(tag))), "统计格锚「$tag」必须恰一处")
        }
        listOf("onOpenWordBank", "onOpenCheckIn", "onOpenReadingHistory").forEach { cb ->
            assertTrue(cb in profile, "统计格回调「$cb」必须由本页接出，不许只画数字")
        }
        assertEquals(1, countMatches(profile, Regex("""\bStrongCard\(""")), "三格统计住唯一一枚强档卡（§4.1）")
        assertTrue("AppType.Headline" in profile, "统计数值用 Headline（§4.2），不许再上一枚 Display")
        assertTrue("AppType.Display" !in profile, "我的页不出现 Display：全屏只学习页与打卡页各一枚")
    }

    /**
     * 统计格的墨归容器，屏侧不再钉死 `onStrong`（2026-10-31 用户评审 ②）。
     *
     * 浅套强档卡底色从 `strongSurface`（深墨实底）改成 `tint2`（浅薄荷）之后，卡上那枚
     * `onStrong`（`#E9F2EC`，近白）压浅底等于不可读——墨必须跟着容器的分套走。`StrongCard`
     * 已经把分套后的前景提供成 `LocalContentColor`，屏侧读它即可，不必（也不许）自己再判主题。
     * 值 + 标签两格都要读，所以计数钉 2：只改一格会留下另一格压浅底。
     */
    @Test
    fun `the three stat cells take their ink from the container, not a pinned on_strong`() {
        assertEquals(
            0,
            countMatches(profile, Regex("""\bcolors\.onStrong\b""")),
            "我的页统计格不得再钉死 onStrong：浅套强档卡是 tint2 浅底，近白墨压它等于不可读",
        )
        assertEquals(
            2,
            countMatches(profile, Regex("""LocalContentColor\.current""")),
            "统计数值与统计标签两格都必须从容器内容色取墨（StrongCard 已按主题分套提供）",
        )
    }

    @Test
    fun `the settings ledger is reused not duplicated`() {
        assertEquals(1, countMatches(profile, Regex("""\bsettingsContent\(\)""")), "我的页必须调用宿主注入的设置内容槽")
        assertEquals(1, countMatches(settings, Regex("""internal fun SettingsGroupsContent\(""")), "设置内容必须抽成可复用的一段")
        assertTrue("fun SettingsScreen(" in settings, "旧 SettingsScreen 保留为装载缝（connected 设置页测试仍直接挂它）")
        // 设置 tab 只许一个根：我的页含设置内容，但宿主不得把两份都挂上去（同屏两个滚动容器、两枚页头）。
        assertEquals(
            0,
            countMatches(host, Regex("""(^|[^A-Za-z0-9_])SettingsScreen\(""")),
            "宿主的设置 tab 只挂 ProfileScreen；再挂一枚 SettingsScreen = 同一 tab 两页叠着",
        )
        assertEquals(
            1,
            countMatches(host, Regex("""(^|[^A-Za-z0-9_])ProfileScreen\(""")),
            "宿主必须恰挂一枚 ProfileScreen（0 = 设置 tab 还是没有根页，2 = 第二份我的页）",
        )
        assertTrue("profile_open_learning_records" in profile, "学习记录一行的锚必须存在")
        assertEquals(0, countMatches(profile, Regex("""Mint[A-Z]\w*""")), "我的页不得读 Mint* 静态 token")
        assertEquals(0, countMatches(profile, Regex("""Color\(0x""")), "我的页不得出现裸 hex")
    }

    /**
     * 「关于与许可」（原型 `sMeHome` 的「应用」组末行）通过**设置内容槽**到达本页，不另立一行。
     *
     * 2026-10-29 用户拍板把「关于」放成设置「外观与关于」组的第 10 行（Task 22-B，AC-R-21 9→10）；
     * 而设置内容在本 tab 里正是我的页的 `settingsContent()`，所以原型那一行在页面上**已经存在**。
     * 再按原型在「应用」组补一条同落点的行，就是同一条通路的第二份入口（与「我的词汇」三行同口径）。
     *
     * 本条钉三层接线：本页不写这一行 → 设置内容里有这一行且指向回调 → 宿主把回调接到 About 分支。
     * 「点得到、点完回得来」的行为判据在 connected `AppScreenTest`，源码里看不到渲染。
     */
    @Test
    fun `the about page is reached through the injected settings ledger, not a second row`() {
        assertEquals(
            0,
            countMatches(profile, Regex(Regex.escape("关于与许可"))),
            "我的页源码不得出现原型的「关于与许可」行：关于入口在设置内容的「外观与关于」组里（Task 22-B 第 10 行）",
        )
        assertEquals(
            0,
            countMatches(profile, Regex(Regex.escape("onOpenAbout"))),
            "我的页不持有关于回调：它随 `settingsContent` 注入，本页不转发第二份",
        )
        assertEquals(
            1,
            countMatches(settings, Regex(Regex.escape("tag = \"settings_open_about\","))),
            "设置内容里「关于」行恰一处（0 = 关于页没有入口；2 = 两行都叫关于）",
        )
        assertEquals(
            1,
            countMatches(settings, Regex(Regex.escape("onClick = onOpenAbout,"))),
            "那一行必须接到 `onOpenAbout`（只画行不接回调 = 点不动的行）",
        )
        assertEquals(
            1,
            countMatches(host, Regex(Regex.escape("onOpenAbout = { showAbout = true },"))),
            "宿主必须把关于回调接到 About 分支（0 = 行在但点了没反应）",
        )
    }

    @Test
    fun `the host feeds the three numbers from their real sources`() {
        // 数字的派生在宿主层只写一次（学习页与我的页读同一份），页面拿到的必须是派生值而不是写死的 0：
        //  - 连续天数 / 已学词：打卡快照 CheckInUiState.Ready（Task 22 起统计职责就在这里）；
        //  - 文章数：阅读历史全量访问器（historyOrEmpty 与解锁状态正交，锁定态也读得到历史）。
        listOf(
            "val streakDays = checkInReady?.streakDays ?: 0",
            "val learnedWordCount = checkInReady?.learnedWordCount ?: 0",
            "articleCount = readingState.historyOrEmpty.size",
        ).forEach { wiring ->
            assertEquals(
                1,
                countMatches(host, Regex(Regex.escape(wiring))),
                "我的页取数接线必须恰一处：$wiring（0 = 那一格恒为 0，是假数字）",
            )
        }
        assertEquals(
            1,
            countMatches(host, Regex("""learnedWordCount = learnedWordCount,""")),
            "我的页必须拿到宿主派生的已学词数（0 = 页面自己又 as? Ready 一遍，兜底值各写各的）",
        )
        // 我的页与学习页共用同一份打卡快照：宿主必须把它提到 Scaffold 之前收一次，
        // 否则两个分支各收一份，页头的连续天数会随 tab 切换出现两份真源。
        assertEquals(
            1,
            countMatches(host, Regex("""val checkInState by checkInViewModel\?\.uiState\?\.collectAsState\(\)""")),
            "打卡快照必须在宿主层收一次（0 = 两个分支各收一份；2 = 两份真源）",
        )
    }
}
