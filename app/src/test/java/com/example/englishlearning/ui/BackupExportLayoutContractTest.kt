package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 5 Task 5：导出屏重排的源码契约（spec §5「导出」行 + 计划对账 7）。
 *
 * 该屏旧形态（`BackupExportScreen.kt` 120 行）的四处毛病：
 *  - 两处自写 `TextButton("返回")` + `Text("备份导出（数据留档）", fontSize = 22.sp)` 手搓页头，
 *    `appColors()` 读点为 **0**、`AppType` 读点为 **0**（整屏既不跟深色档、也不跟大字号分档）；
 *  - **同一件事讲两遍**：「包含 AI 音频文件」这句话既在披露卡的范围清单里（一行 `已缓存 AI 音频`
 *    ＋「可通过上方开关排除」），又在开关区下方重复一遍（「校验失败的音频会中止导出」），
 *    用户读到两处「音频」，不知道哪句才是例外条件；
 *  - 两枚 M3 `Button`（保存 / 生成）**都在正文流里、主次不分**：真正的主操作「保存到本地」
 *    要滚到屏底才看得见，而在它下面还压着一枚同样实底的「生成备份包」；
 *  - 根列没有 `verticalScroll`：大字号（验收口径 fontScale 2.0）下范围清单 + 开关 + 回执条
 *    直接把保存键挤出屏幕。
 *
 * 重做口径（spec §5 原话「包含／不包含分两段、音频例外只讲一次」）：
 *  - 页头换共享 `AppScreenHeader`（`viewModel == null` 的降级分支与正常分支各一，与批 5 Task 2
 *    同一口径——只换一处会让「功能暂不可用」那条路径留着自写返回键）；
 *  - **两段两卡**：段一「包含内容」= 诚实声明 + 四条 `BareRow` 范围行；段二「不包含」= 音频例外
 *    一句 + 那枚开关。音频例外**只在段二讲一次**（段一窗口内「音频」必须 0 次，正向断言）；
 *  - **主操作吸底**：「选择保存位置并导出」（`backup_export_save`，`PillStyle.Primary`）挂在不透明
 *    `Surface` 的底槽；「生成备份包」（`backup_export_prepare`）降为屏体次键（`PillStyle.Text`），
 *    两枚 M3 `Button` 一并下线——一屏一个主操作，且它必须是用户真要走的那一步；
 *  - 两分支都补 `verticalScroll`，手搓 `sp` 全归六档 `AppType`，色全归 `appColors()`。
 *
 * 读法是剥注释后的源文本（`uiScreenSource`），按符号与字符位置取窗口，不锚行号。
 */
class BackupExportLayoutContractTest {

    private val source: String by lazy { uiScreenSource("BackupExportScreen.kt") }

    /** 屏内保留锚：重排只许换承载控件，不许把锚弄丢（connected 与宿主用例全靠它们定位）。 */
    private val keptTags = listOf(
        "backup_export_screen",
        "backup_export_disclosure",
        "backup_export_scope_header",
        "backup_export_audio_note",
        "backup_export_audio_switch",
        "backup_export_busy",
        "backup_export_summary",
        "backup_export_filename",
        "backup_export_save",
        "backup_export_prepare",
    )

    /** 段一四条范围行的 tag 值：锚是 `backup_export_scope_${row.tag}` 拼出来的，故按值钉。 */
    private val scopeTagValues = listOf("config", "progress", "notes", "ai")

    // ---------- ① 页头（两分支都换） ----------

    @Test
    fun `both branches wear the shared header and the text back button retires`() {
        val headerSites = callSites(Regex("""\bAppScreenHeader\s*[({]"""))
        assertEquals(
            2,
            headerSites.size,
            "`AppScreenHeader` 调用点必须恰 2 处（现 ${headerSites.size}）：`viewModel == null` 的降级分支与" +
                "正常分支各一（本屏是双分支屏，与批 5 Task 2 的学习提醒同一口径）",
        )
        headerSites.forEach { site ->
            assertTrue(
                "onBack = onBack" in callArgsFrom("AppScreenHeader", site),
                "两个分支的页头都必须把 `onBack` 接进返回位（漏一处 = 那条路径上返回键消失，用户卡在页里）",
            )
        }
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("\"backup_export_back\""))),
            "旧返回锚 `backup_export_back` 必须退役：页头返回位固定 testTag `app_screen_header` + 可及名「返回」",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bTextButton\(""")),
            "自写 `TextButton(` 必须清零：返回位归页头，次操作归 `PillButton(PillStyle.Text)`",
        )
    }

    // ---------- ② 手搓字号归零 / 令牌化 ----------

    @Test
    fun `the type scale and the token set replace hand rolled sizes and raw ink`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bfontSize""")),
            "`fontSize` 读点必须清零（改前 6 处）：手搓字号绕过六档 `AppType`，大字号档下也不跟系统缩放分档",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\d+\.sp""")),
            "`<数字>.sp` 必须清零：一切字号走 `AppType` 六档",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("import androidx.compose.ui.unit.sp"))),
            "`androidx.compose.ui.unit.sp` 的 import 也必须下线——留着它就是下次手搓字号的入口",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""MaterialTheme\.typography""")),
            "排印必须走六档 `AppType`，不得回退 `MaterialTheme.typography`",
        )
        val typeReads = countMatches(source, Regex("""AppType\."""))
        assertTrue(typeReads >= 8, "六档排印至少 8 处读点（现 $typeReads，改前 0）：页头/段标题/范围行/说明/回执各有所属")
        val colorReads = countMatches(source, Regex("""appColors\(\)"""))
        assertTrue(colorReads >= 4, "`appColors()` 至少 4 处读点（现 $colorReads，改前 0）：两分支各一处 + 吸底槽 + 次键区")
        assertEquals(
            0,
            countMatches(source, Regex("""Color\(\s*0x""")),
            "裸字面量颜色必须为 0：底色/墨/发丝线全部走 `appColors()`（全仓另有 `RawColorLiteralRatchetTest` 兜底）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bMint[A-Za-z0-9_]*""")),
            "`Mint*` 静态桥必须保持 0 读点（含 import）：本屏属设置域，强调墨走 `appDomain(AppDomain.Settings).deep`",
        )
    }

    // ---------- ③ 包含 / 不包含 分两段，音频例外只讲一次 ----------

    @Test
    fun `the scope splits into two soft cards and the audio exception is stated once`() {
        // 只在正常分支的函数体内数：降级分支（`viewModel == null`）也有一张软档卡，
        // 混在一起数会把「两段」读成三张。
        val mainBranch = bodyOf(source, "private fun BackupExportContent(")
        assertTrue(mainBranch.isNotEmpty(), "取不到 `BackupExportContent(` 本体，判据会空转")
        val cardSites = Regex("""\bSoftCard\s*[({]""").findAll(mainBranch).map { it.range.first }.toList()
        assertEquals(
            2,
            cardSites.size,
            "正常分支的 `SoftCard` 调用点必须恰 2 处（现 ${cardSites.size}）：段一「包含内容」与段二「不包含」各一张",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bStrongCard\b""")),
            "强档卡必须为 0：本屏是系统页，一屏至多一枚强卡的额度留给主任务页",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bCard\(""")),
            "M3 `Card(` 必须为 0：分组走 `SoftCard` / `BareRow` 三档容器",
        )

        val prepareTagAt = mainBranch.indexOf("\"backup_export_prepare\"")
        assertTrue(
            prepareTagAt > cardSites[1],
            "次键 `backup_export_prepare` 必须在段二之后（屏体流里），$prepareTagAt vs ${cardSites[1]}",
        )
        val segmentOne = mainBranch.substring(cardSites[0], cardSites[1])
        val segmentTwo = mainBranch.substring(cardSites[1], prepareTagAt)

        assertTrue(
            "包含内容" in segmentOne,
            "段一标题必须逐字「包含内容」（spec §5 要求把「包含」讲成一段）",
        )
        assertTrue(
            countMatches(segmentOne, Regex("""\bBareRow\(""")) >= 1,
            "段一的范围行必须走共享裸行 `BareRow(`：行级几何（52dp 下限 + 发丝线 + 52dp 缩进）只有这一处施工点，" +
                "旧形态是各屏自排「· 标题 —— 说明」的字符串清单",
        )
        assertEquals(
            4,
            // 只数「字面量打头的调用」，避开 `data class ExportScopeRow(` 那条声明。
            countMatches(source, Regex("ExportScopeRow\\(\\s*\"")),
            "范围条目必须逐条成数据、恰好 4 条（词书 / 学习进度 / 收藏与笔记 / AI 配置）：" +
                "旧清单 7 行里有 1 行讲音频、1 行讲「明确不包含」，两件事都被搬走了",
        )
        assertEquals(
            0,
            countMatches(segmentOne, Regex(Regex.escape("· "))),
            "段一不许再拼「· 标题 —— 说明」的字符串清单（`· ` 必须为 0）",
        )
        assertTrue(
            "backup_export_disclosure" in segmentOne,
            "诚实声明锚 `backup_export_disclosure` 必须落在段一：它讲的就是「包里有什么、不能干什么」",
        )
        assertTrue(
            "backup_export_scope_header" in segmentOne,
            "范围小标题锚 `backup_export_scope_header` 必须落在段一",
        )
        assertTrue(
            "不包含" in segmentTwo,
            "段二标题必须逐字「不包含」（spec §5「包含／不包含分两段」的另一半）",
        )
        assertTrue(
            "backup_export_audio_note" in segmentTwo,
            "音频例外锚 `backup_export_audio_note` 必须落在段二：例外条件只在「不包含」这一段讲",
        )
        assertEquals(
            0,
            countMatches(segmentOne, Regex("音频")),
            "段一窗口内「音频」必须 0 次（现 ${countMatches(segmentOne, Regex("音频"))}）：「音频例外只讲一次」" +
                "的正向断言——它只属于段二，段一再提一次就是同一件事讲两遍",
        )
        assertEquals(
            1,
            countMatches(source, Regex("校验失败的音频")),
            "例外句「校验失败的音频…」全文件必须恰 1 处：第二处就是旧形态的重复叙述",
        )
    }

    // ---------- ④ 主操作吸底、次操作退回屏体 ----------

    @Test
    fun `the primary action sticks to the bottom and the generator steps back to a text pill`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"backup_export_save\""))),
            "主操作锚 `backup_export_save` 必须恰 1 处：一屏一个主操作，不许出现两枚保存键",
        )
        val bottomAt = source.indexOf("Alignment.BottomCenter")
        val saveTagAt = source.indexOf("\"backup_export_save\"")
        assertTrue(bottomAt > 0, "源码里找不到 `Alignment.BottomCenter`：主操作必须挂在吸底槽")
        assertTrue(
            saveTagAt > bottomAt && saveTagAt - bottomAt < 500,
            "`backup_export_save` 必须落在吸底槽内（距 `Alignment.BottomCenter` ${saveTagAt - bottomAt} 字符）：" +
                "旧形态把保存键压在正文流末尾，大字号下要滚过整屏范围清单才看得见",
        )
        val saveCallArgs = callArgsFrom("PillButton", saveTagAt)
        assertTrue(saveCallArgs.isNotEmpty(), "`backup_export_save` 必须挂在一枚 `PillButton(` 上")
        assertTrue(
            "PillStyle.Primary" in saveCallArgs,
            "保存键必须是实底主档 `PillStyle.Primary`（吸底槽里唯一的那枚主操作）——" +
                "缺省档不写不等于主档：药片的默认实参改一次，这里就会静默换个外观",
        )
        val prepareTagAt = source.indexOf("\"backup_export_prepare\"")
        assertTrue(prepareTagAt > 0, "源码里找不到 `backup_export_prepare`")
        val prepareCallArgs = callArgsFrom("PillButton", prepareTagAt)
        assertTrue(prepareCallArgs.isNotEmpty(), "`backup_export_prepare` 必须挂在一枚 `PillButton(` 上")
        assertTrue(
            "PillStyle.Text" in prepareCallArgs,
            "「生成备份包」必须退回文字档次键 `PillStyle.Text`：它与保存键同实底时，用户分不清哪一步是真终点",
        )
        // 2026-11-02 真机复验追加：文字档 + 满宽 = 一条透明通栏，会被读成「第二枚快捷键」
        // （主键就在它下面的吸底槽里、同宽同高）。次键只贴自己的字宽，并贴右收窄。
        assertTrue(
            "fillMaxWidth" !in prepareCallArgs,
            "「生成备份包」不许 `fillMaxWidth()`：满宽的文字档会与吸底主键同宽同位，读成两枚并列快捷键；" +
                "次键的宽度必须由字宽决定",
        )
        assertTrue(
            "Alignment.End" in prepareCallArgs,
            "「生成备份包」必须贴右收窄（`Modifier.align(Alignment.End)`）：它是段二之后的局部修正动作，" +
                "不是一条通栏条",
        )
        // 2026-11-02 大字号复验追加：吸底槽的底色与屏体软卡同为 `colors.surface`，槽顶没有边界线时，
        // 2× 字号下卡内文字从槽下穿过会被读成「卡片内容被截断」（实测：包含内容段的长文正好断在
        // 「共 N 条记录」那一行上）。槽顶必须有一条发丝线交代「这里是固定区、上面是可滚区」。
        val footerView = source.substring(maxOf(0, bottomAt - 300), minOf(source.length, bottomAt + 900))
        assertTrue(
            "hairlineTop(" in footerView,
            "吸底槽顶必须带一条发丝线 `hairlineTop(`：槽色与软卡同为 `colors.surface`，" +
                "没有边界线时 2× 字号下屏体文字从槽下穿过会被读成卡片被截断",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bButton\(""")),
            "M3 `Button(` 必须为 0：操作面统一走三档药片 `PillButton`",
        )
    }

    // ---------- ⑤ 音频开关的锚与可及名逐字保留 ----------

    @Test
    fun `the audio switch keeps its anchor and its spoken label`() {
        assertEquals(
            1,
            countMatches(source, Regex("""\bSwitch\(""")),
            "音频开关必须是一枚 Material `Switch(`（恰 1 处）：spec §5「开关统一 Switch」",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"backup_export_audio_switch\""))),
            "开关锚 `backup_export_audio_switch` 必须恰 1 处（`AccessibilitySwitchLabelContractTest` 按它取站点）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("contentDescription = \"包含 AI 音频文件\""))),
            "开关可及名必须逐字保留「包含 AI 音频文件」：" +
                "`AccessibilitySwitchLabelContractTest` 规则 1 按这句取窗口，换字等于静默改播报",
        )
        val switchAt = source.indexOf("Switch(")
        assertTrue(switchAt > 0, "源码里找不到 `Switch(` 锚点")
        // 窗口取「`Switch(` 之后 400 字符」：全站约定 `Switch(...)` 在前、`modifier = Modifier.testTag(…).semantics { … }`
        // 在参数表尾部（`AccessibilitySwitchLabelContractTest` 规则 1 的 80 字符口径落在这条链上）。
        val window = source.substring(switchAt, minOf(source.length, switchAt + 400))
        assertTrue("backup_export_audio_switch" in window, "`Switch(` 参数表里必须带开关锚（窗口：${window.trim()}）")
        assertTrue("contentDescription" in window, "开关修饰链必须带 `contentDescription`（窗口：${window.trim()}）")
    }

    // ---------- ⑥ 整屏可滚（两分支） ----------

    @Test
    fun `both branches scroll so the sticky bar never buries content`() {
        assertEquals(
            2,
            countMatches(source, Regex("""\bverticalScroll\(""")),
            "`verticalScroll(` 必须恰 2 处（两分支各一）：大字号下「范围清单 + 开关 + 回执」会越过一屏，" +
                "而本屏没有任何别处的滚动壳可借",
        )
        assertTrue(
            countMatches(source, Regex("""\bweight\(1f\)""")) >= 1,
            "滚动体必须 `weight(1f)` 让位给吸底槽：条与内容是同级兄弟（内容在条上方结束），" +
                "而不是压在内容上的浮层——浮层要靠手算底部内边距躲字，字号一放大就必然叠字",
        )
    }

    // ---------- ⑦ 锚清单 ----------

    @Test
    fun `every retained anchor still has exactly one home`() {
        keptTags.forEach { tag ->
            assertTrue(
                countMatches(source, Regex(Regex.escape("\"$tag\""))) >= 1,
                "锚「$tag」丢了：重排只许换承载控件，不许把既有锚弄丢（connected 用例全靠它们定位）",
            )
        }
        assertTrue(
            "backup_export_scope_\${row.tag}" in source,
            "范围行锚必须仍是 `backup_export_scope_` 前缀拼 tag 值：connected 与像素用例按这个前缀逐行定位",
        )
        scopeTagValues.forEach { value ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$value\")"))),
                "范围行 tag 值「$value」必须恰 1 处：四条范围行各自一枚 `backup_export_scope_$value`，" +
                    "少一条就是把一类数据从「包含」里悄悄删掉",
            )
        }
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"backup_export_screen\""))),
            "屏锚 `backup_export_screen` 必须恰 1 处（两分支共用同一个根 tag 会数出 2——故只许挂在真分支根上）",
        )
    }

    // ---------- 夹具 ----------

    /**
     * 调用点的起始下标表。`SoftCard` 一族有「带实参」与「尾随 lambda」两种写法
     * （`SoftCard(contentPadding = …) { … }` / `SoftCard { … }`），只数 `SoftCard(` 会把尾随 lambda
     * 那一种数成 0——判据要数的是**调用点**，不是某一种写法。
     *
     * `\s*[({]` 不会误吞 import 行：`import …components.SoftCard` 后面跟的是换行 + 下一个
     * import 关键字，两样都不是括号。
     */
    private fun callSites(pattern: Regex): List<Int> =
        pattern.findAll(source).map { it.range.first }.toList()

    /**
     * 取某个声明（[headline]，如 `"private fun BackupExportContent("`）的函数体文本。
     * 从声明处往后找第一枚 `{`，再按花括号配对收尾（源码已剥注释，字符串里的 `${…}`
     * 也成对，故配对法够用）。
     */
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
