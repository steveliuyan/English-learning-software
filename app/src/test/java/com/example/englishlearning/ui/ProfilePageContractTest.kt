package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 我的页（spec §3.6）的源码契约：档案头 + 三格统计深链 + 学习记录行。
 *
 * 2026-10-31 用户拍板「结构性重排」（`docs/decisions/2026-10-31-settings-as-fifth-tab.md`）：
 * 设置从本页拆出去成为第五个一级 tab，本页只留个人进度。于是这一层要防的回归变了：
 *  ① 设置内容被塞回本页（同屏两个滚动容器、两枚页头，齿轮又落到「我的」）；
 *  ② 统计格只画数字不接深链（用户点了没反应，还把「已学词」冤枉成一个死数字）。
 * 行为面由 connected `ProfileScreenTest` 与宿主 `AppScreenTest` 承重；本文件钉形状与接线。
 */
class ProfilePageContractTest {
    private val profile: String by lazy { stripComments(uiScreenSource("ProfileScreen.kt")) }
    private val host: String by lazy { stripComments(uiScreenSource("AppScreen.kt")) }
    private val settings: String by lazy { stripComments(uiScreenSource("SettingsScreen.kt")) }
    private val about: String by lazy { stripComments(uiScreenSource("AboutScreen.kt")) }

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

    /**
     * 2026-10-31 拆分后的形状：我的页**不再**有设置内容槽，设置页是它自己的根页。
     *
     * 三条各自关一条回归路径：
     *  - `settingsContent` 归零：参数回来 = 设置又被塞回「我的」底下，同一页两个滚动容器；
     *  - 宿主恰挂一枚 `ProfileScreen` 与一枚 `SettingsScreen`：0 = 某一栏没有根页，
     *    2 = 同一栏挂了两份（旧现场就是设置内容直接挂进我的页）；
     *  - `SettingsGroupsContent` 保留为装载缝：内容本体与页头分开，connected 测试可只挂内容。
     */
    @Test
    fun `the profile page no longer hosts the settings ledger`() {
        assertEquals(
            0,
            countMatches(profile, Regex(Regex.escape("settingsContent"))),
            "我的页不得再有设置内容槽：设置已独立为第五个一级 tab",
        )
        assertEquals(
            0,
            countMatches(profile, Regex(Regex.escape("settings_group_"))),
            "我的页不得出现任何设置分组",
        )
        assertEquals(
            1,
            countMatches(settings, Regex("""internal fun SettingsGroupsContent\(""")),
            "设置内容必须保持为可复用的一段（页头与内容本体分开）",
        )
        assertTrue("fun SettingsScreen(" in settings, "设置页是独立根页（页头「设置」+ 滚动容器）")
        assertEquals(
            1,
            countMatches(host, Regex("""(^|[^A-Za-z0-9_])ProfileScreen\(""")),
            "宿主必须恰挂一枚 ProfileScreen（0 = 我的栏没有根页，2 = 第二份我的页）",
        )
        assertEquals(
            1,
            countMatches(host, Regex("""(^|[^A-Za-z0-9_])SettingsScreen\(""")),
            "宿主必须恰挂一枚 SettingsScreen（0 = 设置栏没有根页，2 = 设置被挂了两份）",
        )
        assertTrue("profile_open_learning_records" in profile, "学习记录一行的锚必须存在")
        assertEquals(0, countMatches(profile, Regex("""Mint[A-Z]\w*""")), "我的页不得读 Mint* 静态 token")
        assertEquals(0, countMatches(profile, Regex("""Color\(0x""")), "我的页不得出现裸 hex")
    }

    /**
     * 关于页只从**设置页**进；许可只从**关于页**进。这是 2026-10-31 去重后的唯一通路。
     *
     * 三层接线一次钉住：设置页有且只有一条关于行 → 宿主把关于回调接到 About 分支 →
     * 设置页与宿主都不再持有许可回调（用户点评的「两处都有开源许可」正是旧的双入口）。
     * 「点得到、点完回得来」的行为判据在 connected `AppScreenTest` 与 `SettingsLicensesEntryTest`。
     */
    @Test
    fun `about is reached from settings and licenses only from about`() {
        assertEquals(
            1,
            countMatches(settings, Regex(Regex.escape("tag = \"settings_open_about\","))),
            "设置页的关于行渲染恰一处（0 = 关于没有入口；2 = 两行都叫关于）",
        )
        assertEquals(
            1,
            countMatches(settings, Regex(Regex.escape("onClick = onOpenAbout,"))),
            "关于行必须接到 `onOpenAbout`（只画行不接回调 = 点不动的行）",
        )
        assertEquals(
            1,
            countMatches(host, Regex(Regex.escape("onOpenAbout = { showAbout = true },"))),
            "宿主必须把关于回调接到 About 分支（0 = 行在但点了没反应）",
        )
        assertEquals(
            0,
            countMatches(settings, Regex(Regex.escape("settings_open_licenses"))),
            "设置页不得再有许可行：唯一入口在关于页",
        )
        assertEquals(
            0,
            countMatches(settings, Regex(Regex.escape("onOpenLicenses"))),
            "设置页不得持有许可回调（同落点的第二份入口）",
        )
        assertEquals(
            0,
            countMatches(profile, Regex(Regex.escape("onOpenAbout"))),
            "我的页不持有关于回调：它随设置页到达",
        )
        assertEquals(
            0,
            countMatches(about, Regex(Regex.escape("onOpenBackup"))),
            "关于页不得再挂备份入口：备份唯一入口在 设置 → 数据与安全",
        )
        assertEquals(
            1,
            countMatches(host, Regex(Regex.escape("onOpenLicenses = { showAbout = false; showLicenses = true },"))),
            "许可的唯一入口必须接在关于页（0 = 关于页点了没反应；2 = 又长出第二份）",
        )
        assertEquals(
            1,
            countMatches(host, Regex(Regex.escape("onOpenBackupHub = { showBackupHub = true },"))),
            "备份的唯一入口必须接在设置页（0 = 备份进不去；2 = 关于页又开了一个口）",
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
