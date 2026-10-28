package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 5 Task 8（恢复异常屏版式重做）的源码契约。
 *
 * 这一屏是「全屏可恢复态」：数据还在，用户要么重试、要么先把留档导出去。
 * 判据钉两件事——**用户眼里不许出现技术串**（SQLite / Exception / 堆栈类词汇会让
 * 「数据还在」这句话失去可信度），**两条出路都必须存在**（重试 + 导出留档），
 * 且**永远不出现销毁入口**（SP-B 决策 §3：可恢复错误不诱导销毁数据）。
 *
 * 静态色桥（`Mint*`）在这一屏退役：它是批 0 的桥接令牌，深浅两套在它身上分不开。
 */
class RecoveryLayoutContractTest {

    private val source: String = stripComments(uiScreenSource("RecoveryScreen.kt"))

    /** 批 0 的静态色桥：八枚令牌，本屏一个都不许留。 */
    private val mintBridges = listOf(
        "MintBackground",
        "MintPrimary",
        "MintPrimaryDark",
        "MintTextMuted",
        "MintSurface",
        "MintOnPrimary",
        "MintOutline",
        "MintError",
    )

    // ---------- ① 标题去技术串 ----------

    @Test
    fun `no technical jargon reaches the user visible title`() {
        listOf("SQLite", "Exception", "database", "IllegalState", "IOException").forEach { word ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(word))),
                "用户可见文案不许出现技术串「$word」：这一屏要讲的是「数据还在、你可以重试」，" +
                    "不是引擎内部发生了什么",
            )
        }
        assertTrue(
            "数据" in source,
            "标题必须落到用户语言（「数据暂时打不开」这一族）",
        )
    }

    // ---------- ② reason 只在「详情」折叠区 ----------

    @Test
    fun `the raw reason is folded behind a details row`() {
        assertTrue("reason" in source, "签名里必须仍收到 `reason`（诊断信息不许丢，只是要收起来）")
        val reasonTextAt = source.indexOf("text = reason")
        assertTrue(reasonTextAt > 0, "源码里必须有一处 `text = reason`（详情区的那一行）")
        val revealAt = source.indexOf("showDetails")
        assertTrue(
            0 < revealAt && revealAt < reasonTextAt,
            "`text = reason` 必须落在 `showDetails` 折叠布尔体内（先判折叠、再渲染）：" +
                "原始原因默认不展开，展开动作由用户自己发起",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("text = reason"))),
            "`reason` 只许有一处渲染点（现 ${countMatches(source, Regex(Regex.escape("text = reason")))}）：" +
                "两处就会让「默认收起」失效",
        )
    }

    // ---------- ③ 静态色桥清零 + token 化 ----------

    @Test
    fun `the static mint bridge retires and the ink comes from tokens`() {
        mintBridges.forEach { token ->
            assertEquals(
                0,
                countMatches(source, Regex("""\b$token\b""")),
                "静态色桥 `$token` 必须退役：墨色一律取 `appColors()`，深浅两套各自成立",
            )
        }
        assertTrue(
            countMatches(source, Regex(Regex.escape("appColors()"))) >= 4,
            "`appColors()` 读点必须 ≥ 4（现 ${countMatches(source, Regex(Regex.escape("appColors()")))}）",
        )
        assertTrue(
            countMatches(source, Regex("""\bAppType\.\w+""")) >= 4,
            "`AppType.` 读点必须 ≥ 4（现 ${countMatches(source, Regex("""\bAppType\.\w+"""))}）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("ButtonDefaults.buttonColors"))),
            "`ButtonDefaults.buttonColors` 必须为 0：药片自己带墨，屏侧不再手动拼按钮配色",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bButton\(""")),
            "M3 `Button(` 必须为 0（现 ${countMatches(source, Regex("""\bButton\("""))}）：两条出路都是药片",
        )
    }

    // ---------- ④ 两条出路 ----------

    @Test
    fun `both ways out exist and the archive export is the primary one`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"recovery_export_archive\""))),
            "主操作锚 `recovery_export_archive` 必须恰 1 处：数据还在的时候，先把留档导出去才是真正保命的那一步",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"recovery_retry\""))),
            "次操作锚 `recovery_retry` 必须恰 1 处（`RecoveryScreenTest` 按它点按）",
        )
        val archiveArgs = callArgsFrom("PillButton", source.indexOf("\"recovery_export_archive\""))
        assertTrue("PillStyle.Primary" in archiveArgs, "「导出本机留档」必须是实底主档 `PillStyle.Primary`")
        val retryArgs = callArgsFrom("PillButton", source.indexOf("\"recovery_retry\""))
        assertTrue(
            "PillStyle.Secondary" in retryArgs || "PillStyle.Text" in retryArgs,
            "「重试」必须退成次档（`Secondary` / `Text`）：两枚实底会让用户分不清先做哪一步",
        )
        assertTrue(
            "onOpenBackupExport: () -> Unit" in source,
            "签名必须带上 `onOpenBackupExport: () -> Unit`：留档出口要由宿主接到备份导出屏",
        )
        assertTrue(
            "onOpenBackupExport()" in source,
            "主操作必须真的调 `onOpenBackupExport()`（不是只把形参接上）",
        )
    }

    // ---------- ⑤ 可滚 + 键盘让位 ----------

    @Test
    fun `the screen scrolls and still leaves room for the ime`() {
        assertTrue(
            countMatches(source, Regex("""\bverticalScroll\(""")) >= 1,
            "整屏必须可滚：大字号下标题 + 说明 + 两枚出路会越过一屏（旧形态是居中列，直接裁字）",
        )
        assertTrue(
            "imePadding()" in source,
            "`imePadding()` 必须保留：这是全屏态，键盘弹起时出路不许被顶出屏外",
        )
    }

    // ---------- ⑥ 永不出现销毁入口 ----------

    @Test
    fun `no data wipe entry is ever offered`() {
        listOf("清除数据", "删除数据", "清空数据", "重置").forEach { word ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(word))),
                "可恢复页不得提供「$word」类销毁入口（SP-B 决策 §3：诱导销毁数据不是「可恢复错误」）",
            )
        }
    }

    // ---------- ⑦ 文案不许空承诺 ----------

    @Test
    fun `the copy points at archiving instead of promising a missing backup`() {
        assertTrue(
            "留档" in source || "导出" in source,
            "文案必须把「先确认留档」这一层讲出来（「留档」/「导出」至少 1 次）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("未找到备份")),
            "不许出现「未找到备份」这类空承诺：留档是用户自己的动作，不是系统替他找出来的",
        )
        assertTrue(
            "仍在设备上" in source,
            "「你的学习数据仍在设备上」必须逐字保留：这一句是整屏的定心丸，也是「不要清数据」的正面理由",
        )
    }

    // ---------- 夹具 ----------

    private fun callArgsFrom(name: String, tagAt: Int): String {
        if (tagAt < 0) return ""
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
