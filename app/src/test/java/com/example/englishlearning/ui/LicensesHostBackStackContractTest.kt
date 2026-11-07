package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * QA 第三轮 A1（用户 2026-11-08 拍板「全部都做」）：许可页的返回栈必须补齐「四件套」。
 *
 * 缺陷原状（走查册 `docs/verification/visual-walkthrough-2026-11-08.md` §A1，五份图证形状一致）：
 * `showLicenses` 是 Ready 态二级屏里**唯一**只有「状态位 + 挂载分支 + 页内返回」三件的屏——
 * 既不在 `overlayOpen` 串联里，也没有自己的 `BackHandler`。于是许可页上按系统返回键落到
 * 「不在学习 tab 就回学习 tab」那条最低优先级处理器：第一下人还停在许可页（等于没反应），
 * 第二下直接退出应用。次生现象：打开许可页时顺手 `showAbout = false`，所以页内那枚「返回」
 * 落回「我的」而不是「关于」。
 *
 * 修法（本契约钉的形状）：
 *  1. `showLicenses` 进 `overlayOpen` 串联；
 *  2. 补 `BackHandler(enabled = showLicenses) { showLicenses = false }`，且**声明在 `showAbout` 之后**
 *     （本文件的既有口径是「后声明者优先」，顺序写反会让关于页抢走返回键）；
 *  3. 打开许可页**不再清** `showAbout`——关于页留在栈下，页内返回与系统返回都自然落回关于页
 *     （同形于 hub 挂载分支那句「子屏 onBack 只置子屏 false，自然回到本页」）；
 *  4. 因为第 3 条会让两枚状态位同时为真，而挂载 `else if` **先声明者赢**，所以许可页分支必须
 *     排在关于页分支**之前**。
 *
 * 判据形状沿用 `BackupEntryUnificationContractTest` 的四件套成例：纯计数抓不住「删了一处、别处还在」，
 * 所以四枚站点各自钉形状；两条顺序判据（挂载先后、BackHandler 声明先后）各自钉下标，
 * 取不到下标（`indexOf` 回 -1）当场判红，不许静默退化。
 * 读码只走共享夹具 [uiScreenSource]（内置「文件恰一个 + 父目录守卫 + 剥注释」），本席不自写第二份剥注释实现。
 */
class LicensesHostBackStackContractTest {

    private val host = uiScreenSource("AppScreen.kt")

    /** 四件套之一：状态位。 */
    @Test
    fun licenses_overlay_keeps_its_saved_state_flag() {
        assertTrue(
            Regex("""var showLicenses by rememberSaveable""").containsMatchIn(host),
            "许可页必须持有 rememberSaveable 状态位（旋转/改字号后不该把用户弹回关于页）",
        )
    }

    /** 四件套之二：`overlayOpen` 串联里必须有它，否则二级屏开着时底栏那条低优先级返回处理器会越过它切 tab。 */
    @Test
    fun licenses_overlay_is_counted_by_the_shared_overlay_chain() {
        val overlay = host.substringAfter("val overlayOpen =").substringBefore("BackHandler(")
        assertTrue(
            overlay.contains("showAbout") && overlay.contains("selectedSearchCard != null"),
            "overlayOpen 取样失真：截取区间没落在真链上（关于项或链尾缺失，取样长度 ${overlay.length}）",
        )
        assertTrue(
            overlay.contains("showLicenses"),
            "overlayOpen 链缺 `showLicenses`——许可页开着时系统返回键会越过它落到切 tab 那条处理器上（A1 原始缺陷）",
        )
    }

    /** 四件套之三：专属 BackHandler，且关闭动作只置自己 false。 */
    @Test
    fun licenses_overlay_owns_its_back_handler() {
        val licensesBack = host.indexOf("BackHandler(enabled = showLicenses) { showLicenses = false }")
        assertTrue(
            licensesBack >= 0,
            "许可页没有形如 `BackHandler(enabled = showLicenses) { showLicenses = false }` 的专属返回处理器（A1 原始缺陷）",
        )
        val aboutBack = host.indexOf("BackHandler(enabled = showAbout) { showAbout = false }")
        assertTrue(aboutBack >= 0, "关于页的返回处理器被删或改名：本契约的优先级取样右界失效")
        assertTrue(
            aboutBack < licensesBack,
            "许可页的 BackHandler 必须声明在关于页**之后**（实测下标 $licensesBack vs $aboutBack）" +
                "——本文件按「后声明者优先」分派返回键，颠倒后在许可页按返回会先关掉关于页",
        )
    }

    /** 四件套之四：挂载分支在册，且因为关于页不再被清，许可页必须**先**声明才拿得到组合。 */
    @Test
    fun licenses_mount_branch_precedes_the_about_branch_it_overlays() {
        val licensesMount = host.indexOf("} else if (showLicenses) {")
        val aboutMount = host.indexOf("} else if (showAbout) {")
        assertTrue(licensesMount >= 0, "许可页挂载分支 `} else if (showLicenses) {` 不在册（被删或改了形状）")
        assertTrue(aboutMount >= 0, "关于页挂载分支 `} else if (showAbout) {` 不在册（被删或改了形状）")
        assertTrue(
            licensesMount < aboutMount,
            "许可页分支必须排在关于页分支**之前**（实测下标 $licensesMount vs $aboutMount）" +
                "——挂载链先声明者赢；打开许可页时关于页不再被清，两枚同时为真，" +
                "顺序写反会让用户点了许可行看到的还是关于页",
        )
    }

    /** 次生缺陷：入口接线不得再清关于页，否则页内返回落回「我的」而不是「关于」。 */
    @Test
    fun opening_licenses_leaves_the_about_page_underneath_it() {
        assertTrue(
            host.contains("onOpenLicenses = { showLicenses = true },"),
            "关于页的许可入口必须只置 `showLicenses = true`（旧写法顺手 `showAbout = false`，" +
                "于是从许可页用页内返回会落回「我的」而不是「关于」）",
        )
        assertTrue(
            !host.contains("onOpenLicenses = { showAbout = false; showLicenses = true },"),
            "不得从关于页的许可入口关关于页：关于页留在栈下才是「从哪来回哪去」",
        )
    }
}
