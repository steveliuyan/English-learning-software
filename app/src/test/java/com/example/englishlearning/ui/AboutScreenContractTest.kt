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
 * 另：隐私说明那一行在原型的动作是 `toast`，本仓没有对应承载（隐私口径分散在设置与备份说明里），
 * 所以它是**不可点的只读行**（`onClick = null`），不假装是个入口。
 */
class AboutScreenContractTest {

    private val screen: String by lazy { uiScreenSource("AboutScreen.kt") }

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
    fun `the three explanation rows keep their capability`() {
        listOf("about_privacy_row", "about_licenses_row", "about_backup_row").forEach { tag ->
            assertEquals(1, countMatches(screen, Regex(Regex.escape(tag))), "说明行锚「$tag」恰一处")
        }
        assertTrue("onClick = onOpenLicenses" in screen, "「开源许可与来源」接到既有许可页")
        assertTrue("onClick = onOpenBackup" in screen, "「备份与恢复」接到既有备份页")
    }

    @Test
    fun `the thanks section states only what this project can stand behind`() {
        assertTrue("SectionHeading(text = \"致谢\")" in screen, "节标题逐字")
        assertTrue("Newsreader" in screen, "字体名与许可（OFL）逐字")
        assertEquals(0, countMatches(screen, Regex("""本地模型""")), "「示例文章由本地模型提供」与本仓不符，不搬")
    }
}
