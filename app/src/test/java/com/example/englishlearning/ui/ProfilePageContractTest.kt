package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 「我的」功能中心（hub）的源码契约。
 *
 * 2026-11-02 用户拍板（`docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`）：
 * 底导回四栏，「设置」降为「我的」里的子页，「我的」成为功能入口中心——
 * 档案头 + 三格统计 + 我的词汇 / 学习 / 应用 三组行。
 *
 * 这一层要防的回归：
 *  ① 台账长出第二套真值（渲染侧另抄一份行标题/图标/域色）；
 *  ② 行只画不接（点不动的死行，或者统计格只画数字）；
 *  ③ 设置内容被塞回本页（同屏两枚页头、两个滚动容器，正是被撤回的旧形态）；
 *  ④ 行的落点又绕开词库页分段（三行词汇各自手写 `showWordBank = true`）。
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
        listOf("onOpenWordBankSection", "onOpenCheckIn", "onOpenReadingHistory").forEach { cb ->
            assertTrue(cb in profile, "统计格/词汇行回调「$cb」必须由本页接出，不许只画数字")
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
     * hub 台账是**唯一真值**（沿用「台账不是第二套真值」的纪律）：
     * 三组 13 行的标题/图标/域色/testTag 只写在 [ProfileGroups] 一处，渲染侧按 tag 取。
     */
    @Test
    fun `the hub renders every ledger row through one assembly point`() {
        val rows = ProfileGroups.all.flatMap { it.rows }
        assertEquals(13, rows.size, "台账必须恰 13 行（决策 §2：3 + 5 + 5）")
        rows.forEach { row ->
            assertEquals(
                1,
                countMatches(profile, Regex(Regex.escape("\"${row.title}\""))),
                "行标题「${row.title}」必须只写在 ProfileGroups 一处（渲染侧抄第二份 = 第二套真值）",
            )
            assertEquals(
                1,
                countMatches(profile, Regex(Regex.escape("\"${row.tag}\""))),
                "行 tag「${row.tag}」必须只写一处（调用处只报 tag，渲染侧不得再抄）",
            )
        }
        ProfileGroups.all.forEach { group ->
            assertEquals(
                1,
                countMatches(profile, Regex(Regex.escape("\"${group.title}\""))),
                "分组标题「${group.title}」必须只写一处",
            )
        }
        assertTrue(
            "settingsEntry(" !in profile && "SettingsGroups" !in profile,
            "我的页不得再引用旧设置台账：设置已降为子页，hub 有且只有 ProfileGroups 一套台账",
        )
    }

    /** 我的页**不再**有设置内容槽：设置是「我的 → 设置」子页，同屏两枚页头是旧形态。 */
    @Test
    fun `the profile page no longer hosts the settings content`() {
        assertFalse("SettingsGroups" in profile, "我的页不得再挂设置台账")
        assertEquals(
            0,
            countMatches(profile, Regex(Regex.escape("settings_theme_"))),
            "我的页不得出现主题分段：外观归设置子页",
        )
        assertEquals(
            0,
            countMatches(profile, Regex(Regex.escape("SettingsScreen("))),
            "我的页不得直接挂设置根页：它是覆盖层，由宿主挂载",
        )
        assertEquals(
            1,
            countMatches(host, Regex("""(^|[^A-Za-z0-9_])ProfileScreen\(""")),
            "宿主必须恰挂一枚 ProfileScreen（0 = 我的栏没有根页，2 = 第二份我的页）",
        )
        assertEquals(
            1,
            countMatches(host, Regex("""(^|[^A-Za-z0-9_])SettingsScreen\(""")),
            "宿主必须恰挂一枚 SettingsScreen（0 = 设置子页没有挂载，2 = 设置被挂了两份）",
        )
        assertEquals(0, countMatches(profile, Regex("""Mint[A-Z]\w*""")), "我的页不得读 Mint* 静态 token")
        assertEquals(0, countMatches(profile, Regex("""Color\(0x""")), "我的页不得出现裸 hex")
    }

    /** 三组行必须**逐行接线**：13 个 tag 各有一次 `onClick`，一组行只接一半就是点不动的死行。 */
    @Test
    fun `every hub row is wired in the host`() {
        val profileStation = host.substringAfter("AppTab.PROFILE -> ProfileScreen(")
        listOf(
            "onOpenWordBankSection = { section -> wordBankSection = section; showWordBank = true }",
            "onOpenCheckIn = { showCheckIn = true }",
            "onOpenReadingHistory = { showReadingHistory = true }",
            "onOpenLearningRecords = { showLearningRecords = true }",
            "onOpenPlan = { showSetup = true }",
            "onOpenWorksheet = {",
            "onOpenWordBookTransfer = { showWordBookTransfer = true }",
            "onOpenAiProfiles = openAiProfilesOrSettings",
            "onOpenReminderSpeech = { showReminderSpeech = true }",
            "onOpenSettings = { showSettings = true }",
            "onOpenBackupHub = { showBackupHub = true }",
            "onOpenAbout = { showAbout = true }",
        ).forEach { wiring ->
            assertEquals(
                1,
                countMatches(profileStation, Regex(Regex.escape(wiring))),
                "我的 hub 的「$wiring」接线必须恰一处（0 = 那一行是死行，2 = 同屏两个真源）",
            )
        }
        assertTrue(
            "wordBookName = todayReady?.wordBookName" in profileStation,
            "「学习计划」行副标题必须拿到活动词书名（写死文案 = 假信息）",
        )
        assertTrue(
            "aiProfileSubtitle = aiProfiles?.let" in profileStation,
            "「AI 服务」行副标题必须优先用宿主摘要",
        )
    }

    /**
     * 每一枚子页开关在宿主的挂载链里**必须恰有一支，且那一支必须有内容**。
     *
     * 这一条是被现盘 bug 逼出来的（2026-11-02 本批实现期）：把「设置」分支往下挪去让位给两枚新子页时，
     * 上面留了一支**只剩注释**的 `else if (showSettings)`。`if / else if` 先声明先赢，于是点 hub 的
     * 「设置」行确实把 `showSettings` 置了真、挂载链也确实命中那一支，可那一支什么都不画——
     * 用户看到的是一整屏空白。当时 connected 侧仍走旧 tag，没抓到这一条，所以判据落回源码层：
     *  - **份数恰 1**：出现第二支就是「把真支顶掉的那支」；
     *  - **`{` 之后必须紧跟非空白代码**：[stripComments] 逐行剥注释、保留换行，因此「只剩注释」的分支
     *    剥完就是 `{ }`，正好被这条抓住（真分支哪怕只有一行实参也不会命中）。
     */
    @Test
    fun `each subpage flag is mounted exactly once and with a real body`() {
        listOf("showReminderSpeech", "showWordBookTransfer", "showSettings").forEach { flag ->
            assertEquals(
                1,
                countMatches(host, Regex("""else if \($flag\) \{""")),
                "$flag 的挂载分支必须恰一处（0 = 路由没有落点，2 = 先声明的那支顶掉后一支）",
            )
            assertFalse(
                Regex("""else if \($flag\) \{\s*\}""").containsMatchIn(host),
                "$flag 的挂载分支只剩注释 = 点进去一整屏空白（先声明先赢，真分支被顶掉）",
            )
        }
    }

    /**
     * 关于页只从**我的 hub** 进；许可只从**关于页**进。
     *
     * 2026-11-02 起「关于与许可」行住在 hub（应用组），设置子页不再有说明出口。
     */
    @Test
    fun `about is reached from the hub and licenses only from about`() {
        assertEquals(
            1,
            countMatches(profile, Regex(Regex.escape("\"profile_open_about\""))),
            "hub 的关于行必须恰一处",
        )
        assertEquals(
            1,
            countMatches(host, Regex(Regex.escape("onOpenAbout = { showAbout = true },"))),
            "宿主必须把关于回调接到 About 分支（0 = 行在但点了没反应）",
        )
        assertEquals(
            0,
            countMatches(settings, Regex(Regex.escape("settings_open_licenses"))),
            "设置子页不得有许可行：唯一入口在关于页",
        )
        assertEquals(
            0,
            countMatches(settings, Regex(Regex.escape("onOpenAbout"))),
            "设置子页不得持有关于回调：关于行住在我的 hub",
        )
        assertEquals(
            0,
            countMatches(about, Regex(Regex.escape("onOpenBackup"))),
            "关于页不得再挂备份入口：备份唯一入口在我的 hub → 数据与备份",
        )
        assertEquals(
            1,
            countMatches(host, Regex(Regex.escape("onOpenLicenses = { showAbout = false; showLicenses = true },"))),
            "许可的唯一入口必须接在关于页（0 = 关于页点了没反应；2 = 又长出第二份）",
        )
    }

    @Test
    fun `the host feeds the three numbers from their real sources`() {
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
    }
}
