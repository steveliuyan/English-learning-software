package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 2 Task 22-B：关于（原型 `sMeAbout`，`assets/app.js:1366-1384`）的源码侧契约。
 *
 * 原型骨架：`barTop('关于')` → 应用块（56px `books` 图标 + 应用名 + 「版本 2026.11 · 单机优先」）
 * → 一句 `.meta` → `section('说明')` 三行（隐私说明 / 开源许可与来源 / 备份与恢复）
 * → `section('致谢', .card-quiet)`。
 *
 * 三处按「读不到就整枚不显示、不写不成立的话」处理，逐条钉在这里：
 *  1. 版本号取 `versionName` **形参**（宿主给构建信息），屏内不写死 `2026.11`；
 *  2. 「N 项第三方组件」的 N 只在 `licenseCount` 非空时拼（由许可清单解析结果给）；
 *  3. 致谢只留本仓成立的两句（图标自制 / 字体 Newsreader·OFL）；原型「示例文章由本地模型提供」
 *     与本仓不符（文章由用户配置的 AI 服务按需生成），不搬。
 *
 * 2026-10-31 去重（`docs/decisions/2026-10-31-settings-as-fifth-tab.md`）：说明区从三行收成**一行**。
 *  - 「隐私说明」死行删除：它没有落点（`onClick = null`），而隐私说明本身是许可页的第一段；
 *  - 「备份与恢复」行删除：备份的唯一入口在 设置 → 数据与安全；
 *  - 「开源许可与来源」改名「开源许可与隐私说明」，与许可页标题逐字一致。
 */
class AboutScreenContractTest {

    private val screen: String by lazy { uiScreenSource("AboutScreen.kt") }
    private val code: String by lazy { stripComments(screen) }

    @Test
    fun `the about page keeps the shared header and the app block`() {
        assertEquals(1, countMatches(screen, Regex("""AppScreenHeader\(""")), "页头恰一处共享件（原型 `barTop`）")
        assertTrue("title = \"关于\"" in screen, "页名逐字")
        assertEquals(1, countMatches(screen, Regex("""Sticker\.Books""")), "应用块用原型那枚 `books` 贴纸")
        assertTrue("版本 ${'$'}versionName · 单机优先" in screen, "版本号取形参")
    }

    @Test
    fun `the version and the license count are never invented`() {
        assertEquals(0, countMatches(screen, Regex("""2026\.11""")), "原型 mock 的版本号不写死")
        assertTrue(
            "if (licenseCount != null)" in screen,
            "「N 项第三方组件」只在许可数读得到时出现，否则退成不带数字的那一句",
        )
        assertEquals(0, countMatches(screen, Regex("""6 项""")), "原型 mock 的「6 项」不写死")
    }

    @Test
    fun `the explanation section keeps exactly one entry and it opens the licenses page`() {
        assertEquals(
            1,
            countMatches(screen, Regex(Regex.escape("about_licenses_row"))),
            "许可入口锚「about_licenses_row」恰一处",
        )
        assertTrue("onClick = onOpenLicenses" in code, "那一行必须接到既有许可页（只画行不接回调 = 点不动的行）")
        assertTrue(
            "开源许可与隐私说明" in code,
            "行标题与许可页标题逐字一致：入口名与落地页名指向同一件事",
        )
        listOf("about_privacy_row", "about_backup_row").forEach { deadTag ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(deadTag))),
                "「$deadTag」已按 2026-10-31 去重删除：说明区只留许可一条入口",
            )
        }
        assertEquals(
            0,
            countMatches(screen, Regex(Regex.escape("onOpenBackup"))),
            "关于页不再持有备份回调：备份的唯一入口在 设置 → 数据与安全",
        )
    }

    @Test
    fun `the thanks section states only what this project can stand behind`() {
        assertTrue("SectionHeading(text = \"致谢\")" in screen, "节标题逐字")
        assertTrue("Newsreader" in screen, "字体名与许可（OFL）逐字")
        assertEquals(0, countMatches(screen, Regex("""本地模型""")), "「示例文章由本地模型提供」与本仓不符，不搬")
    }
}
