package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 5 Task 4：备份中心重排的源码契约（spec §5「备份与恢复」行）。
 *
 * 该屏旧形态是「三个自绘 `BackupHubSection`：节标题 + M3 `Card`（`surface` 底 + 18dp 圆角 +
 * `outlineSoft` 描边）+ 组内四枚 `EntryRow`（56dp + 图标底板），三节等权排列，删除行与另外三条
 * 入口长得一模一样」。重做口径：
 *  - 组壳换三档容器的软档 `SoftCard`（`surface` 实底 + 20dp 圆角、**不描边**），M3 `Card(` /
 *    `CardDefaults` / `RoundedCornerShape` / `border(` 全部归零——描边是「卡片汤」的起点；
 *  - 行级全部换成 `BareRow`（52dp + 发丝线），`EntryRow` 在本屏退役；四枚入口 tag 与四组
 *    标题/副标题文案**逐字不动**（`BackupEntryUnificationTest` 把这几句当唯一真值）；
 *  - 「两条导出分主次」：留档导出是本屏的推荐路径（尾部一枚「推荐」小字 + 独立锚），
 *    认证导出是次选。**不用 `PillButton` 表达主次**——见 `the two exports are ranked…` 的注释：
 *    这两条标题最长 13 个字，药片在 `AppType.Title` 下会折成两三行、48dp 下限被顶到 90dp，
 *    而且「路由行」摆实底大键会把「这里只是选路径」读成「这里就是执行」；
 *  - 危险区沉底：清空数据是唯一**会改用户数据**的入口，独占最后一节，且该节的话用
 *    `colors.error` 一档墨说出来（以前它和导出恢复同色同形，等于把不可撤销的操作伪装成普通入口）；
 *  - 删除回执 `InlineNotice` 排在危险区**之前**：危险区必须是全屏最后一段，回执不许把
 *    「清空数据」这一行挤到屏中间去。
 *
 * 读法是剥注释后的源文本（`uiScreenSource`），按符号与字符位置取窗口，不锚行号。
 */
class BackupHubLayoutContractTest {

    private val source: String by lazy { uiScreenSource("BackupHubScreen.kt") }

    // ---------- ① 页头 + 三个软档分区 ----------

    @Test
    fun `the hub wears the shared header on three soft sections`() {
        assertEquals(
            1,
            countMatches(source, Regex("""\bAppScreenHeader\(""")),
            "全文件 `AppScreenHeader(` 必须恰 1 处（这一屏只有一页页头）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"备份与恢复\""))),
            "页头标题必须逐字「备份与恢复」（设置页那一行的入口名已同句）",
        )
        val body = bodyOf(source, "fun BackupHubScreen(")
        assertTrue(body.isNotEmpty(), "取不到 `BackupHubScreen(` 本体，判据会空转")
        assertTrue("onBack = onBack" in body, "页头必须把 `onBack` 接进返回位")
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"backup_hub_screen\""))),
            "整屏 tag `backup_hub_screen` 必须恰 1 处",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""\bSoftCard\(""")),
            "`SoftCard(` 必须恰 1 处：三节共用私有 `BackupHubSection` 这一处施工点（复制三份就等于把节壳形状拆成三份真值）",
        )
        assertEquals(
            4,
            countMatches(source, Regex("""\bBackupHubSection\(""")),
            "`BackupHubSection(` 必须恰 4 处：1 处定义 + 3 处调用（导出 / 恢复 / 危险区）",
        )
        val sectionBody = bodyOf(source, "private fun BackupHubSection(")
        assertTrue(sectionBody.isNotEmpty(), "取不到 `BackupHubSection(` 本体，判据会空转")
        assertTrue(
            "SoftCard(" in sectionBody,
            "节壳必须落三档容器的软档 `SoftCard(`：M3 `Card(` + 18dp 描边壳已退役",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bCardDefaults\b""")),
            "`CardDefaults` 必须为 0：M3 卡片配色表随 `Card(` 一起退役",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bRoundedCornerShape\(""")),
            "`RoundedCornerShape(` 必须为 0：圆角由 `SoftCard` 的 20dp 统一发，屏侧不自造第二档",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bborder\(""")),
            "软档卡禁描边（`ContainerContractTest` 同口径）：节壳不再自画 1dp `outlineSoft` 边框",
        )
    }

    // ---------- ② 两条导出分主次 ----------

    @Test
    fun `the two exports are ranked main versus secondary`() {
        val exportAt = source.indexOf("\"backup_hub_open_export\"")
        val pairingAt = source.indexOf("\"backup_hub_open_pairing_export\"")
        assertTrue(exportAt > 0, "源码里找不到留档导出锚 `backup_hub_open_export`")
        assertTrue(pairingAt > exportAt, "留档导出必须排在认证导出之前（主路径先读）")
        // 「推荐」只有一枚，且必须挂在留档导出那一行上（落在它的标题之后、认证导出之前）。
        // 取窗口用两枚**标题字面**，不取 tag：`BareRow` 的实参表里 `trailing` 排在 `modifier` 之前，
        // 按 tag 下标量会让「同一行内的徽标」被判成「行长在意料之外的更早处」（实测 2533 vs 2934）。
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"推荐\""))),
            "「推荐」小字必须恰 1 处：两条导出只能有一条是推荐路径，标两条等于没标",
        )
        val exportRowAt = source.indexOf("\"导出学习数据留档\"")
        val pairingRowAt = source.indexOf("\"认证导出（可用于新设备恢复）\"")
        assertTrue(
            exportRowAt > 0 && pairingRowAt > exportRowAt,
            "取不到两条导出的标题锚（$exportRowAt / $pairingRowAt）：留档导出必须排在认证导出之前",
        )
        val badgeAt = source.indexOf("\"推荐\"")
        assertTrue(
            badgeAt in (exportRowAt + 1) until pairingRowAt,
            "「推荐」必须挂在留档导出那一行（实测下标 $badgeAt，区间 $exportRowAt..$pairingRowAt）——" +
                "挂在认证导出上会把「换机专用、要先过系统认证」的路径说成默认推荐",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""\bbackup_hub_export_recommended\b""")),
            "推荐徽标要有自己的锚 `backup_hub_export_recommended`（恰 1 处）：connected 侧按它取证",
        )
        // 屏内不放实底大键：hub 是「选路径」屏，四枚入口都是导航，主操作在各自子屏里。
        // 主次由徽标 + 行位表达，实底 `PillStyle.Primary` 会把导航读成执行，还会与子屏里的
        // 真主键抢焦点（计划原稿写的是两枚药片 Primary/Secondary，实施时按这条改的口径）。
        assertEquals(
            0,
            countMatches(source, Regex("""\bPillButton\(""")),
            "本屏不许出现 `PillButton(`：四枚入口都是路由行，实底键会把「去哪」读成「就在这做」",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bPillStyle\.Primary\b""")),
            "`PillStyle.Primary` 必须为 0：hub 屏内没有主操作键（主操作在三个子屏的吸底条上）",
        )
    }

    // ---------- ③ 危险区沉底 + 错误墨 ----------

    @Test
    fun `the danger zone sinks below every entry and speaks in error ink`() {
        val deleteSectionAt = source.indexOf("\"backup_hub_section_delete\"")
        val deleteAt = source.indexOf("\"backup_hub_delete_all\"")
        assertTrue(deleteSectionAt > 0, "源码里找不到危险区节 tag `backup_hub_section_delete`")
        assertTrue(
            deleteAt > deleteSectionAt,
            "清空数据锚必须落在危险区节内（实测 $deleteAt vs $deleteSectionAt）",
        )
        // 危险行必须是最后一枚裸行：它下面不许再挂着任何入口。
        assertEquals(
            4,
            countMatches(source, Regex("""\bBareRow\(""")),
            "`BareRow(` 必须恰 4 处：留档导出 / 认证导出 / 恢复 / 清空，四枚入口各一行",
        )
        val lastRowAt = source.lastIndexOf("BareRow(")
        assertTrue(
            lastRowAt in 0 until deleteAt,
            "危险行必须是最后一枚 `BareRow(`（最后一枚下标 $lastRowAt，危险锚 $deleteAt）——" +
                "它之后还有行，就等于把不可撤销的操作埋进了普通列表中间",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bEntryRow\(""")),
            "`EntryRow(` 必须为 0：56dp 图标底板行在本屏退役，行级统一 52dp 裸行",
        )
        // 危险区这一段（节 tag 到文件尾）必须用 `colors.error` 说话，且全屏只此一处。
        val dangerTail = source.substring(deleteSectionAt)
        assertTrue(
            "colors.error" in dangerTail,
            "危险区必须取 `colors.error` 一档墨（窗口：${dangerTail.take(240).trim()}）——" +
                "与导出恢复同色同形的删除行，是把不可撤销的操作伪装成普通入口",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""\bcolors\.error\b""")),
            "`colors.error` 全屏恰 1 处：只有危险区配这档墨，别处借用会稀释它的信号",
        )
    }

    // ---------- ④ 排印与容器 ----------

    @Test
    fun `no strong card no material type and no raw font size`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bStrongCard\(""")),
            "`StrongCard(` 必须为 0：一屏至多一枚强档，本屏三个分组是并列信息，谁也不该压谁",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""MaterialTheme\.typography""")),
            "排印必须走六档 `AppType`，不得回退 `MaterialTheme.typography`",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bfontSize\b""")),
            "`fontSize` 必须为 0：字号只由 `AppType` 六档决定，屏侧不私设第五档",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""Color\(0x""")),
            "不许私造颜色字面量：本屏一律 `appColors()`（全仓另有 `RawColorLiteralRatchetTest` 兜底）",
        )
        val typeReads = countMatches(source, Regex("""AppType\."""))
        // 计划原稿写的是「≥ 8」：那是对「行级排印也在本文件里」的估计。实际行级排印在
        // `BareRow` / `AppScreenHeader` 内部，本屏自有的排印只有四处（三节标题 + 一节危险提示）；
        // 要凑到 8 只能把一句话拆成两句，那是给判据灌水。改成 ≥ 4 并把 `fontSize` 归零补上，
        // 「屏侧不自造字号」这条反而比原稿更硬。
        assertTrue(
            typeReads >= 4,
            "六档排印至少 4 处读点（现 $typeReads）：三节标题 + 危险提示",
        )
    }

    // ---------- ⑤ tag 台账 ----------

    @Test
    fun `all three section tags and four entry tags survive`() {
        listOf(
            "backup_hub_section_export",
            "backup_hub_section_restore",
            "backup_hub_section_delete",
            "backup_hub_open_export",
            "backup_hub_open_pairing_export",
            "backup_hub_open_restore",
            "backup_hub_delete_all",
            "backup_hub_export_recommended",
        ).forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$tag\""))),
                "tag「$tag」必须恰出现 1 次：它是 connected 判据的身份证" +
                    "（`BackupEntryUnificationContractTest.hub_screen_declares_each_tag_exactly_once` 同口径）",
            )
        }
    }

    // ---------- ⑥ 回执条 ----------

    @Test
    fun `the receipt notice is conditional and sits above the danger zone`() {
        // 期望值变化（1 → 2），不是放宽：原型 `sMeData` 本页末有一条**常驻**安全提示条
        // （「所有数据默认只在本机；卸载 App 会一并删除，请先导出备份。」），与条件回执**是两件事**；
        // 两条都走共享 `InlineNotice`，没有第二套提示形状（该口径原句仍成立）。
        // 「回执只有一枚」的实质由下面两条断言守住：条件渲染是 `deleteAllLearningDataNotice?.let`，
        // 且第一枚 `InlineNotice(` 必须排在危险区之前——页尾那条在危险区之后，不影响这两条。
        assertEquals(
            2,
            countMatches(source, Regex("""\bInlineNotice\(""")),
            "`InlineNotice(` 必须恰 2 处：条件回执 + 原型页尾常驻安全提示，都走共享组件（不许自写第二条形状）",
        )
        assertTrue(
            "deleteAllLearningDataNotice?.let" in source,
            "回执必须仍是 `deleteAllLearningDataNotice?.let` 条件渲染：`null` 时不占位，不是常驻空行",
        )
        val noticeAt = source.indexOf("InlineNotice(")
        val deleteSectionAt = source.indexOf("\"backup_hub_section_delete\"")
        assertTrue(
            noticeAt > 0 && deleteSectionAt > 0,
            "取不到回执/危险区的下标（$noticeAt / $deleteSectionAt），判据会空转",
        )
        assertTrue(
            noticeAt < deleteSectionAt,
            "回执条必须排在危险区之前（实测 $noticeAt vs $deleteSectionAt）：危险区是全屏最后一段，" +
                "回执不许把「清空数据」这行顶到屏幕中间",
        )
    }

    // ---------- ⑦ 原型的页尾安全提示与本机数据读数（Task 22-G） ----------

    @Test
    fun `the page closes with the local only notice and invents no storage numbers`() {
        assertTrue(
            "所有数据默认只在本机；卸载 App 会一并删除，请先导出备份。" in source,
            "原型 `app.js:1421` 的页尾提示条在本仓逐字成立（本地优先、无云同步、卸载即删），原样搬",
        )
        // 原型顶部那张卡是 mock：本仓没有「本机占用空间」的读数，也没有「最近一次备份时间」的记录，
        // 两格整枚不画（用户 2026-10-29 拍板「读不到的那枚整枚不显示」）。
        listOf("14.2 MB", "最近备份", "3 天前").forEach { mock ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(mock))),
                "原型 mock 读数「$mock」不得写死：本仓没有对应采集，画出来就是编造指标",
            )
        }
    }

    // ---------- 夹具 ----------

    /** 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。 */
    private fun bodyOf(text: String, signature: String): String {
        val start = text.indexOf(signature)
        if (start < 0) return ""
        val openParen = text.indexOf('(', start)
        if (openParen < 0) return ""
        var depth = 0
        var index = openParen
        while (index < text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            index++
        }
        if (index >= text.length) return ""
        val open = text.indexOf('{', index)
        if (open < 0) return ""
        var braces = 1
        for (cursor in open + 1 until text.length) {
            when (text[cursor]) {
                '{' -> braces++
                '}' -> {
                    braces--
                    if (braces == 0) return text.substring(open + 1, cursor)
                }
            }
        }
        return ""
    }
}
