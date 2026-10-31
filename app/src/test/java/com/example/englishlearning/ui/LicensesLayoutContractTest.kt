package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 5 Task 3：许可证重排的源码契约（spec §5「许可证」行 + 计划对账 2 的屏侧一半）。
 *
 * 该屏旧形态是「`‹` 方向字形借 `headlineSmall` 凑字号的裸页头（还带一层 `horizontalScroll`）+
 * 静态 `Mint*` 九枚直读 + 自写 `Text` 底板当条目卡 + 空态只有一句话、没有出路」。
 * 重做口径（spec §5 原话「空态带重试；入口名与页名统一「开源许可与隐私说明」」）：
 *  - 页头换共享 `AppScreenHeader`，标题逐字「开源许可与隐私说明」（与关于页入口名同一句），
 *    旧锚 `licenses_back` 退役（shared 页头的返回位固定 testTag `app_screen_header` + 可及名「返回」）；
 *  - `Mint*` 九枚与 `MaterialTheme.typography` 读点归零，色走 `appColors()`、排印走六档 `AppType`；
 *  - 空态补「重新读取」次键 `licenses_retry`，且它绑的 `onRetry` **没有默认值**——
 *    带默认值 `= {}` 的空实现等于给用户一枚假按钮（零假入口原则）；
 *  - 标题行的 `horizontalScroll` 下线：那是「标题被屏宽截断」的补丁，真正的解法是标题进页头后自动两行换行
 *    （`AppScreenHeader` 的标题 `maxLines = 2` + `Clip`），长文本（隐私说明与逐条台账）交给整列滚动；
 *  - 条目卡换三档容器（`SoftCard` 内一枚 `BareRow`），M3 `Card(` / 投影分层归零。
 *
 * `NoticeCard` 的名字、`contentDescription = title` 与展开态 `stateDescription` 是
 * `AccessibilityStateSemanticsContractTest` 的锚点（`expandable cards announce expand state`），
 * 本契约从另一侧把它钉住：重排可以换容器，不许把无障碍播报拆掉。
 *
 * 读法是剥注释后的源文本（`uiScreenSource`），按符号与字符位置取窗口，不锚行号。
 */
class LicensesLayoutContractTest {

    private val source: String by lazy { uiScreenSource("LicensesScreen.kt") }

    /** 旧静态桥的九枚 token：任何一个读点回来都是「屏侧又绕过 `appColors()`」。 */
    private val mintTokens = listOf(
        "MintBackground",
        "MintSurface",
        "MintOutline",
        "MintPrimary",
        "MintPrimaryDark",
        "MintTextMuted",
        "MintTint",
        "AppleMintLight",
        "AppleMintGradient",
    )

    // ---------- ① 页头 + 页名统一 ----------

    @Test
    fun `the screen wears the shared header under the unified page name`() {
        assertEquals(
            1,
            countMatches(source, Regex("""\bAppScreenHeader\(""")),
            "全文件 `AppScreenHeader(` 必须恰 1 处（这一屏只有一页页头）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"开源许可与隐私说明\""))),
            "页头标题必须逐字「开源许可与隐私说明」：spec §5 要求入口名与页名统一" +
                "（入口侧 `AboutScreen.kt` 已是这句，页面侧旧名「关于与开源许可」必须同批改）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("\"关于与开源许可\""))),
            "旧页名「关于与开源许可」必须清零：同一屏两个名字就是两条真值",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\blicenses_back\b""")),
            "旧返回锚 `licenses_back` 必须退役：页头返回位固定 testTag `app_screen_header` + 可及名「返回」",
        )
        val body = bodyOf(source, "fun LicensesScreen(")
        assertTrue(body.isNotEmpty(), "取不到 `LicensesScreen(` 本体，判据会空转")
        assertTrue("onBack = onBack" in body, "页头必须把 `onBack` 接进返回位")
    }

    // ---------- ② 静态桥与手搓排版清零 ----------

    @Test
    fun `the static mint bridge and the hand rolled type are gone`() {
        mintTokens.forEach { token ->
            assertEquals(
                0,
                countMatches(source, Regex("""\b$token\b""")),
                "`$token` 读点必须归零（含 import 行）：底色走 `appColors().background`、" +
                    "卡片走 `SoftCard`、墨走 `colors.text1`/`text2`",
            )
        }
        assertEquals(
            0,
            countMatches(source, Regex("""MaterialTheme\.typography""")),
            "排印必须走六档 `AppType`，不得回退 `MaterialTheme.typography`",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""Color\(0x""")),
            "不许私造颜色字面量：本屏一律 `appColors()`（全仓另有 `RawColorLiteralRatchetTest` 兜底）",
        )
        val colorReads = countMatches(source, Regex("""appColors\(\)"""))
        assertTrue(colorReads >= 3, "`appColors()` 至少 3 处读点（现 $colorReads）：空态分支与条目卡各自取色")
        val typeReads = countMatches(source, Regex("""AppType\."""))
        assertTrue(typeReads >= 6, "六档排印至少 6 处读点（现 $typeReads）：页名/说明/条目标题/副标题/展开正文各有所属")
    }

    // ---------- ③ 空态带重试 ----------

    @Test
    fun `the empty state offers a retry that actually re-reads`() {
        assertTrue(
            "onRetry: () -> Unit," in source,
            "`LicensesScreen` 必须收一枚 `onRetry: () -> Unit,` 形参（宿主重读 assets，对账 2 的屏侧一半）",
        )
        assertTrue(
            "onRetry: () -> Unit = {}" !in source,
            "`onRetry` 不许带默认空实现：空 lambda 会让「重新读取」变成一枚点了没反应的假按钮",
        )
        val unavailableAt = source.indexOf("\"licenses_unavailable\"")
        assertTrue(unavailableAt > 0, "源码里找不到 `licenses_unavailable` 锚点")
        // 窗口取 400 字符：空态是「一句说明 + 一枚次键」，中间夹着 `SoftCard`/`PillButton` 的参数表。
        val window = source.substring(unavailableAt, minOf(source.length, unavailableAt + 400))
        assertTrue(
            "licenses_retry" in window,
            "空态必须给出路：`licenses_unavailable` 之后 400 字符内要有 `licenses_retry`（窗口：${window.trim()}）",
        )
        assertTrue(
            "onRetry" in window,
            "重试键必须真的接到 `onRetry`（窗口：${window.trim()}）——连到空 lambda 就是假按钮",
        )
    }

    // ---------- ④ 长文本不再横滚 ----------

    @Test
    fun `the privacy statement no longer scrolls sideways and rides the ledger list`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bhorizontalScroll\(""")),
            "`horizontalScroll(` 必须为 0：横向滚动是「标题被屏宽截断」的旧补丁，长文本的正确出路是换行 + 纵向滚动",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"licenses_privacy_statement\""))),
            "隐私说明锚 `licenses_privacy_statement` 必须恰 1 处",
        )
        val listAt = source.indexOf("LazyColumn(")
        val statementAt = source.indexOf("\"licenses_privacy_statement\"")
        assertTrue(listAt > 0, "源码里找不到 `LazyColumn(`：隐私说明必须落在可滚容器内")
        assertTrue(
            statementAt > listAt,
            "隐私说明必须落在 `LazyColumn(` 之内（$statementAt vs $listAt）：" +
                "大字号下这段长文本要和台账一起滚动，而不是把屏撑爆",
        )
    }

    // ---------- ⑤ 条目卡的无障碍契约不变 ----------

    @Test
    fun `the notice card keeps its talkback contract inside a soft container`() {
        assertEquals(
            1,
            countMatches(source, Regex("""private fun NoticeCard\(""")),
            "私有 `NoticeCard(` 定义必须恰 1 处（`AccessibilityStateSemanticsContractTest` 按这个名字取站点）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("contentDescription = title"))),
            "条目卡的可及名必须仍是 `contentDescription = title`：TalkBack 只播这一句",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("stateDescription = if (expanded) \"已展开\" else \"已收起\""))),
            "展开态播报必须逐字保留「已展开」/「已收起」：换成 `assertIsOn` 之外的第二套词语等于静默改播报",
        )
        val cardBody = bodyOf(source, "private fun NoticeCard(")
        assertTrue(cardBody.isNotEmpty(), "取不到 `NoticeCard(` 本体，判据会空转")
        assertTrue("SoftCard(" in cardBody, "条目容器必须落三档之一的 `SoftCard(`：M3 `Card(` 已退役")
        assertTrue("BareRow(" in cardBody, "条目首行必须是 `BareRow(`：标题/副标题/尾部值的唯一写法")
    }

    // ---------- ⑥ 台账只有一个列表 ----------

    @Test
    fun `the ledger renders through exactly one lazy column`() {
        assertEquals(
            1,
            countMatches(source, Regex("""\bLazyColumn\(""")),
            "`LazyColumn(` 必须恰 1 处：隐私说明与台账条目同住这一个滚动容器",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""\bitems\(""")),
            "`items(` 必须恰 1 处：台账条目只有一处展开点",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"license_entry-"))),
            "条目动态锚前缀 `license_entry-` 必须恰 1 处（connected 用例按它逐条定位）",
        )
    }

    // ---------- ⑥b 原型 sMeLicenses 的行副标题与页尾（Task 22-G） ----------

    @Test
    fun `rows carry version and usage and the page closes with a true tail notice`() {
        assertTrue(
            """notice.field("用途")""" in source,
            "行副标题要按原型 `app.js:1395`「版本 · 用途」补上用途：台账里有这枚字段，不是编造",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("点开任意一条，可以查看用途、数据流与 NOTICE 位置。"))),
            "页尾提示条必须恰 1 处：原型那句「完整许可证文本在『设置 → 关于 → 开源许可』里可以逐条查看」" +
                "在本页是自我指涉（本页就是那一页），换成这一页真正提供的动作",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("设置 → 关于 → 开源许可"))),
            "原型页尾那句自我指涉不得抄进来（同一句话在这一页说会当场说假话）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("完整文本随安装包提供"))),
            "「完整文本随安装包提供」在全仓没有逐条许可证原文的打包证据（assets 只有 kokoro 的 notices），不写",
        )
    }

    // ---------- ⑦ 三档容器、零投影 ----------

    @Test
    fun `no shadow layer and the containers come from the shared tiers`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bShadow\b""")),
            "D 骨架不靠投影分层：`Shadow` 必须为 0",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\belevation\b""")),
            "`elevation` 必须为 0：层次由 `surface` 与 `background` 的明度差表达",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bCard\(""")),
            "M3 `Card(` 必须为 0：分组走 `SoftCard` / `BareRow` 三档容器",
        )
        assertTrue(
            countMatches(source, Regex("""\bSoftCard\(""")) >= 1,
            "至少要有一枚 `SoftCard(`：空态说明与条目卡都得落在三档容器上",
        )
        assertTrue(
            countMatches(source, Regex("""\bBareRow\(""")) >= 1,
            "至少要有一枚 `BareRow(`：条目首行不许自排行几何",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bborder\(""")),
            "软档卡禁描边（`ContainerContractTest` 同口径）：条目卡不再自画 1dp 边框",
        )
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
