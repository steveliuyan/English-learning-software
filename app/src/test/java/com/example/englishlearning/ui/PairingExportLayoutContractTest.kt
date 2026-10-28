package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 5 Task 7（认证导出屏版式重做）的源码契约。
 *
 * 判据只钉「版式与信息架构」：页头归共享、遗忘警告只讲一次、口令框掩码、排印/墨色归 token、
 * 两分支可滚。**认证门控与配对码语义一条不动**（D-9/D-10/D-11、AC4-05/AC4-08）——
 * 那几笔由 `PairingExportQrFlowContractTest` 与 `BackupProtectedExportViewModelTest` 守着，
 * 本类只做正向补钉（揭示入口仍在、仍包在认证里、QR 仍是像素网格）。
 *
 * 与计划的差异（留因）：计划 Step 1 第 3 条要「`backup_pairing_export_reveal_qr` 退役为 0」，
 * 与 D-11「Done 阶段经再次认证重显同一配对码」直接冲突——那是安全语义，不在版式批次的可改范围。
 * 故本类改为正向钉住该入口（恰 1 处且在 `runWithMigrationAuth` 窗口内）。
 */
class PairingExportLayoutContractTest {

    private val source: String = stripComments(uiScreenSource("BackupPairingExportScreen.kt"))

    private val keptTags = listOf(
        "backup_pairing_export_screen",
        "backup_pairing_export_authenticate",
        "backup_pairing_export_qr",
        "backup_pairing_export_password",
        "backup_pairing_export_export",
        "backup_pairing_export_save",
        "backup_pairing_export_reveal_qr",
    )

    // ---------- ① 页头归共享 + 排印/墨色归 token ----------

    @Test
    fun `both branches wear the shared header and the hand rolled title retires`() {
        val headerSites = callSites(Regex("""\bAppScreenHeader\s*[({]"""))
        assertEquals(
            2,
            headerSites.size,
            "`AppScreenHeader` 调用点必须恰 2 处（现 ${headerSites.size}）：`viewModel == null` 的降级分支与" +
                "正常分支各一",
        )
        headerSites.forEach { site ->
            assertTrue(
                "onBack = " in callArgsFrom("AppScreenHeader", site),
                "两个分支的页头都必须把返回位接上（漏一处 = 那条路径上返回键消失）",
            )
        }
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("\"backup_pairing_export_back\""))),
            "旧返回锚 `backup_pairing_export_back` 必须退役：返回位固定 testTag `app_screen_header` + 可及名「返回」",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bTextButton\(""")),
            "自写 `TextButton(` 必须清零：返回位归页头，次操作归 `PillButton(PillStyle.Text)`",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\d+(\.\d+)?\.sp\b""")),
            "手搓 `sp` 必须为 0（现 ${countMatches(source, Regex("""\d+(\.\d+)?\.sp\b"""))}）：字号只走排印六档",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("MaterialTheme.typography"))),
            "`MaterialTheme.typography` 必须为 0：六档排印只从 `AppType` 取",
        )
        assertTrue(
            countMatches(source, Regex("""\bAppType\.\w+""")) >= 8,
            "`AppType.` 读点必须 ≥ 8（现 ${countMatches(source, Regex("""\bAppType\.\w+"""))}）",
        )
        assertTrue(
            countMatches(source, Regex(Regex.escape("appColors()"))) >= 4,
            "`appColors()` 读点必须 ≥ 4（现 ${countMatches(source, Regex(Regex.escape("appColors()")))}）：" +
                "墨色一律取具名 token，两套主题各自成立",
        )
    }

    // ---------- ② 遗忘警告只讲一次 ----------

    @Test
    fun `the forgotten password warning is stated exactly once`() {
        assertEquals(
            1,
            countMatches(source, Regex("遗忘")),
            "「遗忘」全文件必须恰 1 处（现 ${countMatches(source, Regex("遗忘"))}）：旧形态在引导句和输入框标签里" +
                "各讲一遍，同一句警告写两遍等于两处都可以被改漏",
        )
        assertEquals(
            0,
            countMatches(source, Regex("无法找回")),
            "「无法找回」不是本屏口径（硬件级诚实文案是「密码遗忘则备份无法恢复」）",
        )
        assertTrue(
            "密码遗忘则备份无法恢复" in source,
            "那一处警告必须逐字含「密码遗忘则备份无法恢复」（`RestoreNoBypassContractTest` 同一句）",
        )
    }

    // ---------- ③ 口令区 ----------

    @Test
    fun `the password is optional masked and its field keeps its anchor`() {
        assertEquals(
            1,
            countMatches(source, Regex("""\bCheckbox\(""")),
            "「设置备份密码」的勾选必须恰 1 枚 `Checkbox(`：口令是可选项，默认不设",
        )
        val tagAt = source.indexOf("\"backup_pairing_export_password\"")
        assertTrue(tagAt > 0, "源码里找不到 `backup_pairing_export_password`")
        val fieldArgs = callArgsFrom("OutlinedTextField", tagAt)
        assertTrue(fieldArgs.isNotEmpty(), "`backup_pairing_export_password` 必须挂在一枚 `OutlinedTextField(` 上")
        assertTrue(
            "PasswordVisualTransformation()" in fieldArgs,
            "口令框必须带 `visualTransformation = PasswordVisualTransformation()`（与恢复屏同一口径）：" +
                "这是备份包里唯一的机密，明文回显等于把它摆在肩窥视野里",
        )
    }

    // ---------- ④ 配对码：像素网格 + 双色 + D-11 揭示门 ----------

    @Test
    fun `the pairing code stays a pixel grid whose reveal is still gated by authentication`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"backup_pairing_export_qr\""))),
            "QR 锚必须恰 1 处（挂在 `QrCodeDisplay` 唯一施工点上）",
        )
        assertEquals(
            3,
            countMatches(source, Regex("""\bQrCodeDisplay\s*\(""")),
            "`QrCodeDisplay` 必须恰 3 处读点 = 1 处声明 + 生成态与完成态各一处调用",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("backup_pairing_export_reveal_qr"))),
            "D-11：Done 阶段「显示配对码（需再次认证）」入口必须恰 1 处——它是完成态回看同一枚码的唯一通道",
        )
        val revealAt = source.indexOf("backup_pairing_export_reveal_qr")
        val authBefore = source.lastIndexOf("runWithMigrationAuth(", revealAt)
        assertTrue(authBefore > 0, "揭示入口必须包在 `runWithMigrationAuth(` 里（D-9：每次敏感动作各自认证）")
        assertTrue(
            "onPairingRevealed" in source,
            "揭示回调必须回填 `onPairingRevealed`（不重发、不延长有效期）",
        )
        // QR 双色是扫描刚需（`RawColorLiteralRatchetTest` 白名单就为它开），逐字保留。
        listOf("QrForegroundColor", "QrBackgroundColor").forEach { token ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("private val $token"))),
                "QR 配色 `$token` 必须恰 1 处定义",
            )
        }
        assertTrue("Color.White" in source && "Color.Black" in source, "QR 必须仍是白底黑模块（对比度扫描刚需）")
    }

    // ---------- ⑤ 过程态与滚动 ----------

    @Test
    fun `both branches scroll and the two in flight phases show progress`() {
        assertEquals(
            2,
            countMatches(source, Regex("""\bCircularProgressIndicator\(""")),
            "进度环必须恰 2 处（现 ${countMatches(source, Regex("""\bCircularProgressIndicator\("""))}）：认证中与导出中各一",
        )
        assertEquals(
            2,
            countMatches(source, Regex("""\bverticalScroll\(""")),
            "`verticalScroll(` 必须恰 2 处（现 ${countMatches(source, Regex("""\bverticalScroll\("""))}）：" +
                "降级分支与正常分支各一——大字号下二维码 + 口令区 + 保存键会越过一屏",
        )
    }

    // ---------- ⑥ 页级动作归药片 ----------

    @Test
    fun `the page level actions become pills`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bButton\(""")),
            "M3 `Button(` 必须为 0（现 ${countMatches(source, Regex("""\bButton\("""))}）：" +
                "无认证 / 开始认证 / 导出 / 保存 / 显示配对码五枚页级动作一律走 `PillButton`",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bOutlinedButton\(""")),
            "M3 `OutlinedButton(` 必须为 0",
        )
    }

    // ---------- ⑦ 锚一个不少 ----------

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
            countMatches(source, Regex(Regex.escape("\"backup_pairing_export_screen\""))),
            "屏锚 `backup_pairing_export_screen` 必须恰 1 处（挂在唯一的根盒上）",
        )
    }

    // ---------- 夹具 ----------

    private fun callSites(pattern: Regex): List<Int> =
        pattern.findAll(source).map { it.range.first }.toList()

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
