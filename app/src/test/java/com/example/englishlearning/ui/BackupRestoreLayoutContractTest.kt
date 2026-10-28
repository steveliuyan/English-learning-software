package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 5 Task 6（恢复屏版式重做）的源码契约。
 *
 * 判据只钉「版式与信息架构」这一层：页头归共享、口令框必须掩码、替换后果必须讲全、
 * 字号/色值归 token、两分支都能滚。数据通路（`BackupRestoreCoordinator` / SAF / CameraX）
 * 与无旁路口径（AC4-06）不在这里判——那两笔分别由 `BackupRestoreViewModelTest`、
 * `RestoreNoBypassContractTest`、`BackupCloseSemanticsContractTest` 守着，本类不重复也不放宽。
 */
class BackupRestoreLayoutContractTest {

    private val source: String = stripComments(uiScreenSource("BackupRestoreScreen.kt"))

    /** 屏内保留锚：重排只许换承载控件，不许把锚弄丢（宿主与法务用例全靠它们定位）。 */
    private val keptTags = listOf(
        "backup_restore_screen",
        "backup_restore_pick",
        "backup_restore_scan_box",
        "backup_restore_password",
        "backup_restore_password_submit",
        "backup_restore_confirm_replace",
        "backup_restore_cancel",
        "backup_restore_result",
        "backup_restore_restart_notice",
        "backup_restore_restart",
    )

    // ---------- ① 页头归共享 ----------

    @Test
    fun `both branches wear the shared header and the self written back button retires`() {
        val headerSites = callSites(Regex("""\bAppScreenHeader\s*[({]"""))
        assertEquals(
            2,
            headerSites.size,
            "`AppScreenHeader` 调用点必须恰 2 处（现 ${headerSites.size}）：`viewModel == null` 的降级分支与" +
                "正常分支各一（本屏是双分支屏，与批 5 Task 2/5 同一口径）",
        )
        headerSites.forEach { site ->
            assertTrue(
                "onBack = " in callArgsFrom("AppScreenHeader", site),
                "两个分支的页头都必须把返回位接上（漏一处 = 那条路径上返回键消失，用户卡在恢复流里）",
            )
        }
        // 离开页前放弃暂存会话：旧形态把它写在自写返回键的 onClick 里，换页头后这层语义不许蒸发
        // （暂存工作区不解会留在磁盘上，直到下一次 cancel/替换）。
        assertTrue(
            countMatches(source, Regex(Regex.escape("viewModel.cancel()"))) >= 2,
            "取消/离开页必须调 `viewModel.cancel()` 放弃暂存会话（确认页的取消键 + 返回路径都要走它）",
        )
        assertTrue(
            countMatches(source, Regex(Regex.escape("onBack()"))) >= 1,
            "正常分支的返回路径必须真的调 `onBack()`（不是只把形参转手给页头）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("\"backup_restore_back\""))),
            "旧返回锚 `backup_restore_back` 必须退役：页头返回位固定 testTag `app_screen_header` + 可及名「返回」",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("\"返回\""))),
            "自写「返回」文案必须清零：返回位归页头，屏内不再出现第二枚返回键",
        )
    }

    /**
     * M3 `Button(` / `OutlinedButton(` 只剩相机授权那一枚，且必须落在 `QrScanBox` 体内。
     *
     * 取景框是相机助手（CameraX 三件套 + 权限态），不在本轮版式范围内；页级动作全部归药片。
     * 旧形态是 6 枚 M3 按钮散在流里（选文件 / 继续 / 确认替换 / 取消 / 返回 / 重启）。
     */
    @Test
    fun `the material buttons collapse to the camera grant key inside the scan box`() {
        val scanBox = bodyOf(source, "private fun QrScanBox(")
        assertTrue(scanBox.isNotEmpty(), "取不到 `QrScanBox(` 本体，判据会空转")
        val materialButtons = callSites(Regex("""\b(OutlinedButton|Button)\s*\("""))
        assertEquals(
            1,
            materialButtons.size,
            "M3 `Button(`/`OutlinedButton(` 全屏必须只剩 1 枚（现 ${materialButtons.size}）：页级动作一律走 `PillButton`，" +
                "留下的那一枚是取景框里的「授权相机」",
        )
        val scanBoxOffset = source.indexOf("private fun QrScanBox(")
        assertTrue(
            materialButtons.single() > scanBoxOffset,
            "那一枚 M3 按钮必须落在 `QrScanBox(` 之后（相机助手体内），不许回到屏体流里",
        )
    }

    // ---------- ② 口令框必须掩码 ----------

    @Test
    fun `the password field masks what the user types`() {
        val tagAt = source.indexOf("\"backup_restore_password\"")
        assertTrue(tagAt > 0, "源码里找不到 `backup_restore_password`")
        val fieldArgs = callArgsFrom("OutlinedTextField", tagAt)
        assertTrue(fieldArgs.isNotEmpty(), "`backup_restore_password` 必须挂在一枚 `OutlinedTextField(` 上")
        assertTrue(
            "PasswordVisualTransformation()" in fieldArgs,
            "口令框必须带 `visualTransformation = PasswordVisualTransformation()`：备份密码是这台机器的最后一道" +
                "防线，明文回显等于把它摆在肩窥视野里",
        )
    }

    // ---------- ③ 替换后果必须讲全 ----------

    @Test
    fun `the replace confirmation states both the overwrite and the no undo`() {
        val confirmAt = source.indexOf("\"backup_restore_confirm_replace\"")
        assertTrue(confirmAt > 0, "源码里找不到 `backup_restore_confirm_replace`")
        val cardStart = source.indexOf("最后一步 · 确认替换")
        assertTrue(cardStart in 1 until confirmAt, "确认段必须自带一段标题文案（取不到「最后一步 · 确认替换」）")
        // 窗口取「确认段整段」而不是固定字符数：判据要的是「这段文案里讲清了后果」，
        // 段内多一行摘要就不该让断言失准。
        val window = source.substring(cardStart, confirmAt)
        assertTrue("覆盖" in window, "确认段必须逐字出现「覆盖」：这是不可逆动作的后果本身")
        assertTrue(
            "无法恢复" in window || "不可撤销" in window,
            "确认段必须出现「无法恢复」或「不可撤销」：只讲「确认替换」一句话，用户读不出「旧数据一去不回」这一层",
        )
    }

    // ---------- ④ token 化 ----------

    @Test
    fun `the type scale and the token set replace hand rolled ink and sizes`() {
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("MaterialTheme.typography"))),
            "`MaterialTheme.typography` 必须为 0：六档排印只从 `AppType` 取",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""fontSize\s*=""")),
            "手写 `fontSize =` 必须为 0：字号只走排印六档",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\d+\.sp\b""")),
            "手搓 `sp` 必须为 0：字号只走排印六档",
        )
        assertTrue(
            countMatches(source, Regex("""\bAppType\.\w+""")) >= 8,
            "`AppType.` 读点必须 ≥ 8（现 ${countMatches(source, Regex("""\bAppType\.\w+"""))}）：" +
                "四态骨架的每个字级都要落到实处",
        )
        assertTrue(
            countMatches(source, Regex(Regex.escape("appColors()"))) >= 5,
            "`appColors()` 读点必须 ≥ 5（现 ${countMatches(source, Regex(Regex.escape("appColors()")))}）：" +
                "墨色一律取具名 token，两套主题各自成立",
        )
    }

    // ---------- ⑤ 两分支都能滚 ----------

    @Test
    fun `both branches scroll so the restart notice is never buried`() {
        assertEquals(
            2,
            countMatches(source, Regex("""\bverticalScroll\(""")),
            "`verticalScroll(` 必须恰 2 处（现 ${countMatches(source, Regex("""\bverticalScroll\("""))}）：" +
                "降级分支与正常分支各一——旧根列没有滚动壳，大字号下「立即重启应用」会被推出屏外",
        )
    }

    // ---------- ⑥ 锚一个不少 ----------

    @Test
    fun `every retained anchor still has exactly one home`() {
        keptTags.forEach { tag ->
            assertTrue(
                countMatches(source, Regex(Regex.escape("\"$tag\""))) >= 1,
                "锚「$tag」丢了：重排只许换承载控件，不许把既有锚弄丢（宿主与法务用例全靠它们定位）",
            )
        }
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"backup_restore_screen\""))),
            "屏锚 `backup_restore_screen` 必须恰 1 处（挂在唯一的根盒上；两分支各挂一枚会让「屏」变成两个真值）",
        )
    }

    // ---------- ⑦ 旁路口径与相机链路不动 ----------

    @Test
    fun `the no bypass wording and the camera chain stay untouched`() {
        listOf("skip_validation", "force_import", "skip_check", "restore_as_salvage").forEach { forbidden ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(forbidden))),
                "无旁路口径（AC4-06）不许在重排里松口：`$forbidden` 必须为 0",
            )
        }
        assertEquals(
            1,
            countMatches(source, Regex("""\bCircularProgressIndicator\(""")),
            "导入中的进度环必须恰 1 处：恢复是长任务，没有进度反馈用户会当成卡死",
        )
        assertEquals(
            2,
            countMatches(source, Regex("""\bQrScanBox\s*\(""")),
            "`QrScanBox` 必须恰 2 处读点 = 1 处声明 + 1 处调用（仅相机扫码，决策 D2 不给手动输入/相册识码）",
        )
        listOf("PreviewView", "ProcessCameraProvider", "ImageAnalysis").forEach { piece ->
            assertTrue(
                piece in source,
                "CameraX 链路「$piece」必须逐字保留：本轮只改版式，不碰取景实现",
            )
        }
        assertTrue(
            "onReplaceConfirmed" in source,
            "提交替换必须仍是唯一提交入口 `onReplaceConfirmed`（暂存会话之后那一步）",
        )
    }

    // ---------- 夹具 ----------

    /** 调用点起始下标表：`foo(` 与 `foo {` 都算一次调用（尾随 lambda 写法不许漏数）。 */
    private fun callSites(pattern: Regex): List<Int> =
        pattern.findAll(source).map { it.range.first }.toList()

    /** 取某个声明（[headline]）的函数体文本：声明后第一枚 `{` 起按花括号配对收尾。 */
    private fun bodyOf(text: String, headline: String): String {
        val declAt = text.indexOf(headline)
        if (declAt < 0) return ""
        val open = text.indexOf('{', declAt)
        if (open < 0) return ""
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(open + 1, index)
                }
            }
        }
        return ""
    }

    /** 取「包住 [tagAt] 的那一枚 `name(` 的实参表」：向前找最近的同名调用，向后按圆括号配对收尾。 */
    private fun callArgsFrom(name: String, tagAt: Int): String {
        val start = source.lastIndexOf("$name(", tagAt)
        if (start < 0) return ""
        val open = source.indexOf('(', start)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return source.substring(open + 1, index)
                }
            }
        }
        return ""
    }
}
