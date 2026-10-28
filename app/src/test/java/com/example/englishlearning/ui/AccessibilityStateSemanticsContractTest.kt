package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * F5-03（决策 §4）源码契约：选中态 / Tab 角色 / 展开态 / 滑杆值必须写进语义——
 * TalkBack 只播报 stateDescription、role 与带值的 contentDescription，视觉强调
 * （选中卡底色、Tab 加粗、卡面「点按展开」文案）不构成播报。五个生产站点全部是
 * 正向存在性契约（Task 1–4 的禁令族是反向禁令：不许出现），结构沿用同一模板：
 *  规则1 AppScreen.WordBookCard：stateDescription = if (selected) "已选" else "未选"；
 *  规则2 AppBottomBar.TabSlot：role = Role.Tab 且 stateDescription = if (selected) …;
 *  规则3 LearningRecordsScreen TabRow：role = Role.Tab；
 *  规则4 LicensesScreen.NoticeCard 与 ReadingHistoryScreen 组卡：
 *         stateDescription = if (expanded) "已展开" else "已收起"；
 *  规则5 AppScreen.DailyTargetSection Slider：contentDescription = "每日新词目标，当前 ${displayedTarget} 词"。
 *  规则形状即 brief 的五个正则：它们不含任何通配段（只有 `\(`、`\{`、`\$` 转义的字面字符），
 *  故精确子串断言与正则形态等价，且更直白。
 *
 * 防线（与 Task 1–4 双轨样板一致）：
 *  1. 剥注释与规则判定收进纯函数（入参源码文本，返回 offender 列表，空 = 合规），
 *     每条规则有合成片段双边用例：合规形状放行，近miss形状（只有 contentDescription、
 *     常量 stateDescription 无 if 双值、role = Role.Button、写死假数值、声明藏在注释里）
 *     必须被抓；
 *  2. 真实文件走 scanUiSources(dir)：目录缺失或零 .kt 文件立即失败——walkTopDown()
 *     对缺失路径返回空序列，禁止契约静默空扫（包名重构 / 工作目录变化防线）；
 *  3. 每个站点先断言锚点存在（fun WordBookCard( / TabSlot( / TabRow( / fun NoticeCard( /
 *     reading_history_group_ / fun DailyTargetSection(），再断言规则 offender 为空：组件被
 *     改名或删除、semantics 块被整段搬走时，必须报「契约空转」而不是无声通过。
 *
 * 已知漏判/过判面（启发式局限，如实记录，不为未修复的面做假测试）：
 *  - 剥注释口径**已统一到共享夹具**（批 1 终审 F6）：本类私有正则实现已删除，改用 `SourceRatchet.kt`
 *    那份字符串感知的 `stripComments`，所以「字符串字面量里的 `//` 之后整行被抹掉」「字符串里的
 *    星号紧跟斜杠把其后真代码当块注释吞掉」两个失明窗口对本类不复存在。
 *    `docs/verification/f5-03-accessibility-hardening-2026-10.md` §1.1「契约测试匹配面局限」
 *    （债 M-B2）记的是当年的正则实现，保留作历史，勿再按它推断本类仍失明；
 *  - 规则锁**一种精确书写形状**：跨行换行放行（子串匹配不含换行的形状体），但改空格、
 *    把 if 换成 when、把 selected/expanded 改名都会触发契约——这是有意的刚性：重排版时
 *    契约与代码要有意识地一起改，而不是事后被误判了再无声放宽；
 *  - 正向存在性是**文件级**子串匹配，不是 AST 配对：若文件里别的组件复制了完全相同的
 *    字面形状，原站点丢失声明也会放行（中文字面量使该概率极低）；锚点断言只防组件删除/
 *    改名，防语义整体搬家，不防语义搬家到同文件另一处；
 *  - contains("role = Role.Tab") 不校验角色挂在哪个节点：把 Role.Tab 写到别的节点而 Tab
 *    本体未改会误放行——真实播报行为由设备 QA 第三轮核验（brief Step 4「以契约+编译为准」口径）。
 */
class AccessibilityStateSemanticsContractTest {
    private val uiDir = File(UI_STATE_SEMANTICS_SOURCE_PATH)

    /** 规则形状 = brief 正则锁定的精确字面文本（正则无通配段，子串匹配与之等价，见类 KDoc）。 */
    private val wordBookSelectedSemantics = "stateDescription = if (selected) \"已选\" else \"未选\""
    private val tabRoleSemantics = "role = Role.Tab"
    private val tabSelectedSemantics = "stateDescription = if (selected)"
    private val expandStateSemantics = "stateDescription = if (expanded) \"已展开\" else \"已收起\""
    private val sliderValueSemantics = "contentDescription = \"每日新词目标，当前 \${displayedTarget} 词\""

    /** 规则1 纯函数：WordBookCard 选中态必须进语义（正向存在性：缺则返回 offender，空 = 合规）。 */
    private fun wordBookSelectedStateOffenders(source: String): List<String> =
        if (stripComments(source).contains(wordBookSelectedSemantics)) emptyList()
        else listOf("缺 $wordBookSelectedSemantics —— 选中态只有底色/描边/「已选」标签，TalkBack 念不到已选/未选")

    /** 规则2 纯函数：TabSlot 必须同时具备 Tab 角色与选中态播报，两条各计一个 offender。 */
    private fun tabSlotOffenders(source: String): List<String> {
        val stripped = stripComments(source)
        return listOf(
            tabRoleSemantics to "TabSlot 缺 $tabRoleSemantics —— 底部导航被念成普通可点项，不是「标签页」",
            tabSelectedSemantics to "TabSlot 缺 $tabSelectedSemantics… —— 选中态只有加粗与深色 tint，不在语义里",
        ).filterNot { (shape, _) -> stripped.contains(shape) }.map { (_, message) -> message }
    }

    /** 规则3 纯函数：学习记录页 TabRow 的 Tab 必须带角色。 */
    private fun learningRecordsTabRoleOffenders(source: String): List<String> =
        if (stripComments(source).contains(tabRoleSemantics)) emptyList()
        else listOf("学习记录页缺 $tabRoleSemantics —— 「今日已学/历史记录/全部单词」不被播报为标签页")

    /** 规则4 纯函数：展开组件必须有已展开/已收起双值播报；组件名进 offender 便于定位站点。 */
    private fun expandStateOffenders(component: String, source: String): List<String> =
        if (stripComments(source).contains(expandStateSemantics)) emptyList()
        else listOf("$component 缺 $expandStateSemantics —— 展开态只有卡面文案/if 分支变化，语义里没有已展开/已收起")

    /** 规则5 纯函数：Slider 描述必须内嵌当前值模板（无值描述与写死数值都不放行）。 */
    private fun sliderValueOffenders(source: String): List<String> =
        if (stripComments(source).contains(sliderValueSemantics)) emptyList()
        else listOf("DailyTargetSection Slider 缺 $sliderValueSemantics —— 焦点落在滑杆上念不出当前目标词数")

    /** 扫描范围守卫：目录与 .kt 文件必须真实存在，否则契约测试静默空扫。 */
    private fun scanUiSources(dir: File = uiDir): List<File> {
        assertTrue(
            dir.isDirectory,
            "状态语义契约扫描目录缺失（期望 $UI_STATE_SEMANTICS_SOURCE_PATH）：${dir.path}，" +
                "相对 JVM 工作目录 ${File(".").absolutePath}；" +
                "walkTopDown() 对缺失路径返回空序列，会让契约测试无声通过——" +
                "包名重构或工作目录变化必须先修此处路径。",
        )
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "状态语义契约空扫：${dir.path} 下没有 .kt 文件（文件数 0 不是合规，是漏扫）。")
        return files
    }

    /** 按文件名取源码：缺失或被重命名立即失败，禁止契约对着隐形的文件空转。 */
    private fun sourceOf(fileName: String): String {
        val matches = scanUiSources().filter { it.name == fileName }
        assertTrue(
            matches.size == 1,
            "状态语义契约目标文件应恰好一个（$UI_STATE_SEMANTICS_SOURCE_PATH/$fileName），实际 ${matches.size} 个",
        )
        return matches.single().readText()
    }

    /** 站点用例模板：先锚点存在性（契约空转防线），再规则 offender 为空。 */
    private fun assertSite(fileName: String, anchors: List<String>, offenders: (String) -> List<String>) {
        val source = sourceOf(fileName)
        val stripped = stripComments(source)
        anchors.forEach { anchor ->
            assertTrue(
                stripped.contains(anchor),
                "$fileName 找不到锚点 \"$anchor\"：契约空转（组件或 semantics 声明被改名/删除必须显式失败，不能因文件仍在就无声通过）",
            )
        }
        val found = offenders(source)
        assertTrue(found.isEmpty(), "$fileName:\n${found.joinToString("\n")}")
    }

    @Test
    fun `ui scan scope resolves to real kotlin sources`() {
        val files = scanUiSources()
        val screens = files.filter { it.name.endsWith("Screen.kt") }
        assertTrue(
            screens.isNotEmpty(),
            "扫描到 ${files.size} 个 .kt 文件却不含任何 *Screen.kt，扫描范围疑似错位：$UI_STATE_SEMANTICS_SOURCE_PATH",
        )
    }

    @Test
    fun `missing scan directory fails loudly instead of passing on an empty scan`() {
        val missing = File("$UI_STATE_SEMANTICS_SOURCE_PATH/__not_a_real_package__")
        // 危险本体：无守卫时 walkTopDown() 只给空序列，真实文件用例会把空扫当成零违规通过。
        assertTrue(
            missing.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().isEmpty(),
            "walkTopDown() 对缺失路径本应给出空序列（静默空扫风险）",
        )
        // 守卫本体：同一缺失路径必须让扫描立即失败而不是返回空列表。
        val error = assertFailsWith<AssertionError> { scanUiSources(missing) }
        assertTrue(
            error.message?.contains(UI_STATE_SEMANTICS_SOURCE_PATH) == true,
            "守卫失败信息必须点名期望路径，实际：${error.message}",
        )
    }

    @Test
    fun `wordbook card announces selected state`() {
        assertSite("AppScreen.kt", listOf("fun WordBookTile("), ::wordBookSelectedStateOffenders)
    }

    @Test
    fun `bottom bar tabs have role and selected state`() {
        assertSite(
            "AppBottomBar.kt",
            listOf("TabSlot(", "contentDescription = tab.contentDescription"),
            ::tabSlotOffenders,
        )
    }

    @Test
    fun `learning records tabs declare tab role`() {
        assertSite(
            "LearningRecordsScreen.kt",
            listOf("TabRow(", "contentDescription = label"),
            ::learningRecordsTabRoleOffenders,
        )
    }

    @Test
    fun `expandable cards announce expand state`() {
        assertSite("LicensesScreen.kt", listOf("fun NoticeCard(", "contentDescription = title")) {
            expandStateOffenders("NoticeCard", it)
        }
        assertSite("ReadingHistoryScreen.kt", listOf("reading_history_group_")) {
            expandStateOffenders("ReadingHistoryScreen 组卡", it)
        }
    }

    @Test
    fun `daily target slider description embeds current value`() {
        // 批 2 Task 6：`DailyTargetCard` 撤卡壳后改名 `DailyTargetSection`，锚点随实现改，
        // 判据本身不变——仍要求 Slider 的 contentDescription 内嵌当前词数。
        assertSite("AppScreen.kt", listOf("fun DailyTargetSection(", "每日新词目标"), ::sliderValueOffenders)
    }

    @Test
    fun `wordbook selected state rule rejects near misses and accepts fixed shape`() {
        val rejected = listOf(
            // 修复前真实形状（AppScreen 词书卡 Row）：semantics 块只有 contentDescription
            """Row(modifier = Modifier.semantics { contentDescription = "选择词书" })""",
            // 常量播报：没有 if (selected) 双值分支，TalkBack 永远念「已选」
            """Modifier.semantics { contentDescription = "选择词书"; stateDescription = "已选" }""",
            // 播报写在块注释里 = 对 TalkBack 不存在，剥注释后必须判违规
            """Modifier.semantics { /* stateDescription = if (selected) "已选" else "未选" */ }""",
        )
        rejected.forEach { snippet ->
            val found = wordBookSelectedStateOffenders(snippet)
            assertTrue(found.isNotEmpty(), "状态语义契约漏判词书卡近miss形状：$snippet")
            assertTrue(
                found.single().contains(wordBookSelectedSemantics),
                "offender 应点名要求形状，实际：$found",
            )
        }
        // brief Step 3 目标形状（多行 semantics 块，子串匹配允许跨行 contentDescription 与声明分行）
        val fixed = """Modifier
            .clickable(onClick = onSelect)
            .semantics {
                contentDescription = "选择词书"
                stateDescription = if (selected) "已选" else "未选"
            }"""
        assertTrue(wordBookSelectedStateOffenders(fixed).isEmpty(), "状态语义契约误判词书卡合规形状：$fixed")
    }

    @Test
    fun `tab slot rule rejects button role and constant state and accepts fixed shape`() {
        val rejected = listOf(
            // 修复前真实形状（AppBottomBar:78）：contentDescription 之外什么都没有
            """Column(modifier = Modifier.clickable { onSelect(tab) }.semantics { contentDescription = tab.contentDescription })""",
            // 角色写错：Button 不是 Tab（选中态形状虽对，角色 offender 必须判违规）
            """Modifier.semantics { contentDescription = tab.contentDescription; role = Role.Button; stateDescription = if (selected) "已选中" else "未选中" }""",
            // 只有角色、状态是常量——两个分支缺一不可
            """Modifier.semantics { role = Role.Tab; stateDescription = "已选中" }""",
        )
        rejected.forEach { snippet ->
            val found = tabSlotOffenders(snippet)
            assertTrue(found.isNotEmpty(), "状态语义契约漏判 TabSlot 近miss形状：$snippet")
        }
        // brief Step 3 目标形状：角色 + 选中态双值同块声明
        val fixed = """Modifier
            .clickable { onSelect(tab) }
            .semantics {
                contentDescription = tab.contentDescription
                role = Role.Tab
                stateDescription = if (selected) "已选中" else "未选中"
            }"""
        assertTrue(tabSlotOffenders(fixed).isEmpty(), "状态语义契约误判 TabSlot 合规形状：$fixed")
    }

    @Test
    fun `learning records tab role rule rejects unroled semantics and accepts fixed shape`() {
        listOf(
            // 修复前真实形状（LearningRecordsScreen:86）：有标签、无角色
            """Tab(modifier = Modifier.semantics { contentDescription = label })""",
            // 角色写错
            """Modifier.semantics { contentDescription = label; role = Role.Button }""",
            // 假声明写在行注释里
            "// Modifier.semantics { contentDescription = label; role = Role.Tab }",
        ).forEach { snippet ->
            assertTrue(
                learningRecordsTabRoleOffenders(snippet).isNotEmpty(),
                "状态语义契约漏判学习记录 Tab 近miss形状：$snippet",
            )
        }
        assertTrue(
            learningRecordsTabRoleOffenders("""Modifier.semantics { contentDescription = label; role = Role.Tab }""").isEmpty(),
            "状态语义契约误判学习记录 Tab 合规形状",
        )
    }

    @Test
    fun `expand state rule rejects constant announcements and accepts fixed shape`() {
        listOf(
            // 修复前真实形状（NoticeCard / 组卡同款）：可点可展开、语义里没有状态
            """Column(modifier = Modifier.clickable { expanded = !expanded }.semantics { contentDescription = title })""",
            // 常量播报：永远念「已展开」，收起态仍不可知
            """Modifier.semantics { contentDescription = title; stateDescription = "已展开" }""",
            // 状态变量改名后未同步契约——锁 expanded 书写形状，必须显式判违规（见类 KDoc 刚性口径）
            """Modifier.semantics { stateDescription = if (isOpen) "已展开" else "已收起" }""",
        ).forEach { snippet ->
            assertTrue(
                expandStateOffenders("TestCard", snippet).isNotEmpty(),
                "状态语义契约漏判展开态近miss形状：$snippet",
            )
        }
        // LicensesScreen 目标形状（brief Step 3：contentDescription 与 stateDescription 同块）
        val fixedNotice = """Modifier
            .clickable { expanded = !expanded }
            .semantics {
                contentDescription = title
                stateDescription = if (expanded) "已展开" else "已收起"
            }"""
        assertTrue(expandStateOffenders("NoticeCard", fixedNotice).isEmpty(), "状态语义契约误判 NoticeCard 合规形状")
        // ReadingHistoryScreen 组卡目标形状（brief Step 3：修饰链单独一个只含 stateDescription 的块）
        val fixedGroup = """Modifier
            .fillMaxWidth()
            .testTag("reading_history_group_key")
            .semantics { stateDescription = if (expanded) "已展开" else "已收起" }"""
        assertTrue(
            expandStateOffenders("ReadingHistoryScreen 组卡", fixedGroup).isEmpty(),
            "状态语义契约误判组卡合规形状",
        )
    }

    @Test
    fun `slider rule rejects value-free and hard-coded descriptions and accepts embedded value`() {
        val rejected = listOf(
            // 修复前真实形状（AppScreen Slider）：焦点念得出控件但不知道当前目标
            "Modifier.semantics { contentDescription = \"每日新词目标\" }",
            // 写死的假数值：拖动后播报不跟随
            "Modifier.semantics { contentDescription = \"每日新词目标，当前 10 词\" }",
            // 插值变量改名——锁 displayedTarget 书写形状，必须显式判违规（见类 KDoc 刚性口径）
            "Modifier.semantics { contentDescription = \"每日新词目标，当前 \$dragValue 词\" }",
        )
        rejected.forEach { snippet ->
            val found = sliderValueOffenders(snippet)
            assertTrue(found.isNotEmpty(), "状态语义契约漏判 Slider 近miss形状：$snippet")
            assertTrue(
                found.single().contains(sliderValueSemantics),
                "offender 应点名要求形状，实际：$found",
            )
        }
        // brief Step 3 目标形状：描述内嵌当前值模板
        val fixed = "Slider(modifier = Modifier.semantics { contentDescription = \"每日新词目标，当前 \${displayedTarget} 词\" })"
        assertTrue(sliderValueOffenders(fixed).isEmpty(), "状态语义契约误判 Slider 合规形状")
    }
}

private const val UI_STATE_SEMANTICS_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui"
