package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 深套选中标记闸（开关/滑杆轨道 + 复选/单选选中色；批 5 遗留 1 的收口，来源见
 * `docs/handoff/2026-11-02-layout-redesign-batch-5-report.md` §8 第 1 条与 §7 末条）。
 *
 * 管什么：**深套下不许用中性 `strongSurface` 画开关轨道、滑杆拇指/活动轨、复选/单选选中色**。
 *
 * 2026-11-02 收尾补入复选/单选：本闸首次落地时只管「轨道」，把 `WorksheetSettingsScreen` 的
 * `CheckboxDefaults.checkedColor` 与两枚 `RadioButtonDefaults.selectedColor` 留在视野外
 * （当时登记为待裁决项）。它们与开关轨道是同一条缺陷——深套 `strongSurface #22332C` 压在
 * `surface #17201C` 上几乎同色，「选中的那一枚」等于没画——差别只在控件形状，
 * 故按同一口径一并收口并入本闸，不再另立第二条规则。
 *
 * 起因是一次真实的「控件消失」：深套 `strongSurface = #22332C` 压在深套 `surface = #17201C` 上，
 * 两者只差一层几乎看不见的亮度，开关的「开」态轨道与滑杆拇指等于没画（`SpeechSettingsScreen.kt:255`
 * 的 KDoc 记着这次观感缺陷）。批 5 只修了语音合成页那两处，`DetailToggleRow`、`OptionSwitchRow`
 * 与学习设置页的滑杆同因未修——本闸把「同因站点」一次冻死，避免下一屏再抄一遍旧写法。
 *
 * 合法取值只有两种（第二条用例逐站点核验）：
 *  1. **深套三元式**：`if (LocalIsDark.current) <域色>.base else colors.strongSurface`——浅套观感逐值不变
 *     （签署过的浅套版式不动），深套换域色 `base`，在 `surface` 上重新可见；
 *  2. **始终域色**：`= <x>Ink.base` 一类（备份导出页、提醒设置页早已如此），浅套深套都用域色。
 *
 * 读法是源码文本（`uiScreenSource` / [violations] 已剥注释）：注释里贴的旧写法不算命中，
 * 真实的渲染色由 connected 侧截图取证，本闸只负责「源码里不许再出现第三种取值」。
 *
 * 台账（第三条用例）是**双向**的：新增一枚开关/滑杆必须同步登记，否则闸「按不到新站点」会退化成
 * no-op；反过来某站点被删也必须把台账减回去——两向偏移都会红。
 */
class SwitchTrackColorGuardTest {

    /** 站点实参名：开关轨道、滑杆拇指/活动轨、复选/单选选中色。 */
    private val siteNames = "(?:checkedTrackColor|activeTrackColor|thumbColor|checkedColor|selectedColor)"

    /** 站点：`<站点名> =`。`(?<![A-Za-z])` 是为了不吃 `uncheckedTrackColor` / `unselectedColor` 的后缀命中。 */
    private val trackSite = Regex("""(?<![A-Za-z])$siteNames\s*=""")

    /** 违规形状：无条件取中性深底。深套三元式的 else 分支不算（前面是 `if (`，本正则匹配不到）。 */
    private val neutralStrongSurface = Regex(
        """(?<![A-Za-z])$siteNames\s*=\s*(?:colors|appColors\(\))\.strongSurface"""
    )

    /** 合法形状 1：深套三元式。 */
    private val darkAwareForm = Regex(
        """(?<![A-Za-z])$siteNames\s*=\s*""" +
            """if \(LocalIsDark\.current\) \w+\.base else (?:colors|appColors\(\))\.strongSurface"""
    )

    /** 合法形状 2：始终域色（`settingsInk.base` / `settingsAccent.base` / `domain.base`）。 */
    private val alwaysDomainForm = Regex(
        """(?<![A-Za-z])$siteNames\s*=\s*\w+\.base\b"""
    )

    @Test
    fun `no switch track or slider paints the neutral strong surface unconditionally`() {
        val offenders = violations(
            label = "深套开关/滑杆轨道闸",
            rule = neutralStrongSurface,
            allowlist = emptySet(),
        )
        assertEquals(
            emptyList<String>(),
            offenders,
            "深套下 `strongSurface` 与 `surface` 几乎同色，开关轨道/滑杆拇指会「消失」。" +
                "按同口径改成 `if (LocalIsDark.current) <域色>.base else colors.strongSurface`" +
                "（模板见 `SpeechSettingsScreen.kt:258`）。违规站点：\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    fun `every track site reads either the dark aware ternary or a domain color`() {
        val sites = violations(
            label = "开关/滑杆轨道站点",
            rule = trackSite,
            allowlist = emptySet(),
        )
        // 台账外的新站点先在这里被点名（第二条断言会给「多出来的站点」兜底一次更直白的说明）。
        assertTrue(sites.isNotEmpty(), "轨道类实参一个都没扫到：符号被改名或扫描路径错位，本闸已空转。")
        val unrecognised = sites.filterNot { site ->
            darkAwareForm.containsMatchIn(site) || alwaysDomainForm.containsMatchIn(site)
        }
        assertEquals(
            emptyList<String>(),
            unrecognised,
            "轨道档只许两种取值：深套三元式（`if (LocalIsDark.current) <域色>.base else colors.strongSurface`）" +
                "或始终域色（`<x>Ink.base`）。其余取值（含 `colors.surface`、`colors.tint`）会让开关/滑杆" +
                "在某一套主题下与容器同色。未认领的站点：\n" + unrecognised.joinToString("\n"),
        )
    }

    @Test
    fun `the track site ledger matches the source in both directions`() {
        val actual = uiFiles()
            .map { file -> file.name to countMatches(file.readText(), trackSite) }
            .filter { (_, count) -> count > 0 }
            .toMap()
        assertEquals(
            TrackSiteLedger,
            actual,
            "台账与实际站点两向都不许偏：多一枚开关/滑杆没登记 → 闸按不到它；" +
                "某站点被删/搬走 → 台账必须同步减。改这里的同时请确认新站点走了两种合法取值之一。",
        )
        // 「合法形状」在台账里的分布：深套三元式的站点数与始终域色的站点数之和必须等于站点总数，
        // 否则说明有一枚站点既不是这两种形状（第二条用例已挡）——这里再钉一次总数，防台账自说自话。
        val darkAwareFiles = listOf("AppScreen.kt", "SpeechSettingsScreen.kt", "WorksheetSettingsScreen.kt")
        darkAwareFiles.forEach { name ->
            assertTrue(
                countMatches(uiScreenSource(name), darkAwareForm) > 0,
                "$name 的轨道必须走深套三元式（深套换域色 `base`，浅套保持签署过的 `strongSurface`）。",
            )
        }
    }

    companion object {
        /**
         * 站点台账（文件 → 轨道类实参枚数），口径 = 剥注释后的 `checkedTrackColor|activeTrackColor|thumbColor =`。
         *
         * 现值 12 枚：
         *  - `AppScreen.kt` 3：`DetailToggleRow` 开关轨道 + 每日新增滑杆的拇指与活动轨（学习设置页，Library 域）；
         *  - `BackupExportScreen.kt` 1：`settingsInk.base`（始终域色，本就是对的）；
         *  - `ReminderSettingsScreen.kt` 1：`settingsAccent.base`（同上）；
         *  - `SpeechSettingsScreen.kt` 3：语音合成页开关 + 其滑杆两枚（批 5 已修的三元式模板）；
         *  - `WorksheetSettingsScreen.kt` 4：`OptionSwitchRow` 开关轨道 + `DirectionRow` 复选选中色
         *    （`CheckboxDefaults.checkedColor`）+ `RangeSection` / `TemplateSection` 两枚单选选中色
         *    （`RadioButtonDefaults.selectedColor`），同为默写纸设置页的 Settings 域。
         *
         * 2026-11-02 收尾把复选/单选三枚并入本闸（原为待裁决项）：深套下 `checkedColor` /
         * `selectedColor` 取 `strongSurface` 与开关轨道是同一条缺陷，控件形状不同不构成放行理由。
         */
        private val TrackSiteLedger: Map<String, Int> = mapOf(
            "AppScreen.kt" to 3,
            "BackupExportScreen.kt" to 1,
            "ReminderSettingsScreen.kt" to 1,
            "SpeechSettingsScreen.kt" to 3,
            "WorksheetSettingsScreen.kt" to 4,
        )
    }
}
