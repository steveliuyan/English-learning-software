package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * AC-R-01/AC-R-03：token 取值由源码 hex 字面量钉住，配对可用性由 WCAG 相对亮度当场计算。
 * 读源码而不是读渲染结果：口径见共享夹具 `SourceRatchet` 的头注释——JVM 单测只按需导入 compose 的
 * 纯数据符号（例如 `androidx.compose.ui.graphics.Color`，本类与夹具的亮度复算都要读它），
 * 不引入 robolectric 或渲染运行时；Compose 渲染类断言归 androidTest。
 *
 * 分节口径（批 1 预检裁决）：`AppColorLight` 与 `AppColorDark` 都含 `background = Color(0x…)`，
 * 全文件搜索会让浅套断言被深套块满足（反之亦然），断言 vacuous。故所有 token 查找先按锚点
 * `val AppColorLight` / `val AppColorDark` / `val DomainLight` / `val DomainDark` 切段，
 * 段尾到下一个锚点；锚点在剥注释文本中必须各出现且仅出现一次（本测试第 4 用例钉此事，
 * 并含「浅套缺字段、深套同名字段不得救活查找」的合成负例）。
 *
 * 查找正则形状（预检修正 1）：`(?<![\w.])token\s*=\s*Color\(\s*(0xRRGGBBAA)\s*\)`。
 * 反自匹配论证：token 名要求其后紧跟（可含换行的）空白与 `=`，故 `tint` 不会命中 `tint2 =`
 * （`tint` 后是 `2`）、`surface` 不会命中 `surfaceGlass =`（后是 `G`）、`outline` 不会命中
 * `outlineSoft =`、`error` 不会命中 `errorFill =`、`text1` 不会命中假想的 `text10 =`；
 * 前瞻侧 lookbehind `(?<![\w.])` 让 token 不能是更长标识符的后缀（小写的 `apptint =` 不命中 `tint`、
 * `webbackground =` 不命中 `background`；`appTint =` 同样不命中，但它靠的是大小写）
 * 也不是带点成员（`style.background = Color(...)` 不命中 `background`）。所有负例都在
 * `token lookup rule …` 用例里用合成片段当场证明，永久红的正则会在第一个 GREEN 前暴露。
 *
 * 取值断言以 Long（0xRRGGBBAA 无符号位形）比较剥段后 hex，alpha 位一并钉住
 * （`surfaceGlass 0xEFFFFFFF` / 深色 `0xE617201C` 的口径差异就在 alpha 位上）。
 * 比值注释只写 ADR/brief 已钉数值（复算口径见决策文档「背景」节公式，实现见共享夹具
 * `SourceRatchet.luminance`/`ratio`，测试运行时现算，注释数值仅是可读性标注）。
 */
class DesignTokenContractTest {
    private val themeDir = File(THEME_SOURCE_PATH)

    private val anchors = listOf("val AppColorLight", "val AppColorDark", "val DomainLight", "val DomainDark")

    // ---------- 读取与分节 ----------

    /**
     * 目录守卫：措辞与失败信息形状照抄 AccessibilityColorTokenContractTest.scanMainSources，
     * 目录换 THEME_SOURCE_PATH（brief Step 1 原文要求）。本类的实际扫描一律走这个类内守卫——
     * AppColors.kt / AppPalette.kt / MintPalette.kt 的按名查找与「缺失目录大声失败」用例都基于它。
     * `SourceRatchet.uiFiles` 是同批建下的共享夹具（守卫形状同源，扫的是整个 `ui/` 包），
     * 由 Task 6 的 raw-color 棘轮消费，本任务不接它，两者互不依赖。
     */
    private fun scanThemeSources(dir: File = themeDir): List<File> {
        assertTrue(
            dir.isDirectory,
            "token 契约扫描目录缺失（期望 $THEME_SOURCE_PATH）：${dir.path}，" +
                "相对 JVM 工作目录 ${File(".").absolutePath}；" +
                "walkTopDown() 对缺失路径返回空序列，会让契约测试无声通过——包名重构 / 工作目录变化必须先修此处路径。"
        )
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "token 契约扫描空扫：${dir.path} 下没有 .kt 文件（文件数 0 不是合规，是漏扫）。")
        return files
    }

    private fun themeFileNamed(name: String): String {
        val files = scanThemeSources().filter { it.name == name }
        assertTrue(
            files.size == 1,
            "$name 应恰好一个，实际 ${files.size} 个（扫描错位？期望目录 $THEME_SOURCE_PATH，" +
                "相对 JVM 工作目录 ${File(".").absolutePath}）"
        )
        return stripComments(files.single().readText())
    }

    private fun appColorsText(): String = themeFileNamed("AppColors.kt")

    private fun appPaletteText(): String = themeFileNamed("AppPalette.kt")

    /** 按锚点切段：段起于锚点，段尾是下一个出现的锚点（无后续锚点则到文件尾）。 */
    private fun section(text: String, anchor: String): String {
        val idx = anchors.indexOf(anchor)
        assertTrue(idx >= 0, "未知锚点：$anchor（锚点表：$anchors）")
        val start = text.indexOf(anchor)
        assertTrue(start >= 0, "AppColors.kt 缺失锚点声明：$anchor")
        val end = anchors.drop(idx + 1)
            .map { text.indexOf(it, start + anchor.length) }
            .filter { it >= 0 }
            .minOrNull() ?: text.length
        return text.substring(start, end)
    }

    /**
     * hex 文本 → Long 位形（0xRRGGBBAA 无符号值）。Kotlin 的 toLong(16)/toULong(16) 都拒绝
     * `0x` 前缀（NumberFormatException: Invalid number format），这是 RED 轮实测教训，
     * 必须先剥前缀；大小写 hex（0X / a-f）同样兼容。
     */
    private fun hexValue(text: String): Long =
        text.removePrefix("0x").removePrefix("0X").toLong(16)

    /**
     * 预检修正 1 的查找形状；失败消息形状沿用 brief「token 未以 val … 形状声明」。
     *
     * **与 F2 口径的分工**（批 1 终审）：本函数与 `appPalette static light source calibrated to spec 2_1`
     * 里的同款正则是**读钉值**的解析器，只认「8 位十六进制、无后缀、紧跟右括号」这一种书写形，
     * 因为 spec §2.1 的钉值本来就是按这一形状落进 `AppPalette`/`AppColors` 的；
     * 「裸 hex 是否越界」的**探针**口径另有一份（共享夹具 [rawColorLiteral]，宽到能抓后缀与窄化调用），
     * 两者读的是不同问题，不许互相替换：拿解析器当探针会重演 F2 的可编译换形绕过，
     * 拿探针当解析器则 `\((?:…)?\)` 的可选组会把取值读成空。
     */
    private fun hexOf(sectionText: String, token: String, scope: String): Int {
        val m = Regex("""(?<![\w.])$token\s*=\s*Color\(\s*(0x[0-9A-Fa-f]{8})\s*\)""")
            .find(sectionText) ?: error("token 未以 val $token = Color(0x…) 形状声明：$token（区段 $scope）")
        return hexValue(m.groupValues[1]).toInt()
    }

    /** 域色条目子段：从 `AppDomain.<name>` 标记到下一个 `AppDomain.<...>` 标记（或段尾）。 */
    private fun domainEntry(sectionText: String, scope: String, domain: String): String {
        val m = Regex("""AppDomain\.$domain\b""").find(sectionText)
            ?: error("域色条目未以 AppDomain.$domain to DomainPalette(...) 形状声明（区段 $scope）")
        val next = Regex("""AppDomain\.\w+\b""").find(sectionText, m.range.last + 1)
        return sectionText.substring(m.range.first, next?.range?.first ?: sectionText.length)
    }

    private fun domainHexes(sectionText: String, scope: String, domain: String): Pair<Long, Long> {
        val entry = domainEntry(sectionText, scope, domain)
        fun hexOfField(token: String): Long =
            hexOf(entry, token, "$scope/$domain").toLong() and 0xFFFFFFFFL
        return hexOfField("base") to hexOfField("deep")
    }

    private fun unsigned(value: Int): Long = value.toLong() and 0xFFFFFFFFL

    // ---------- WCAG 计算（决策 2026-10-18 背景节公式的实现，运行时现算） ----------

    /**
     * 亮度与比值的纯函数住在共享夹具 `SourceRatchet.kt`（`luminance`/`ratio`，int 域，逐分量复算）：
     * Task 7 的 `IconBadgeContrastContractTest` 按同一口径复算容器对比，公式只能有一份
     * （批 1 Task 7 控制者裁决：从本文件的私有成员搬过去，函数体逐字未动）。
     */
    private fun assertAtLeast(label: String, fg: Int, bg: Int, floor: Double) {
        val r = ratio(fg, bg)
        assertTrue(r >= floor, "$label 实测 %.2f:1 低于 %.1f:1".format(r, floor))
    }

    // ---------- 钉值表（逐字来自 spec §2.1 与 brief；Long 位形含 alpha） ----------

    private val lightHex: Map<String, Long> = linkedMapOf(
        "background" to 0xFFF1FBF5L,
        "surface" to 0xFFFFFFFFL,
        "surfaceGlass" to 0xEFFFFFFFL,
        "tint" to 0xFFDDF7E8L,
        "tint2" to 0xFFDCEAE1L,
        "tint3" to 0xFFCDEDD9L,
        "outline" to 0xFFADE7D2L,
        "outlineSoft" to 0xFFE4EFE9L,
        "text1" to 0xFF1C1C1EL,
        "text2" to 0xFF4E756AL,
        "text3" to 0xFF7A9088L,
        "onMint" to 0xFF174C44L,
        "onBrand" to 0xFF04241CL,
        "error" to 0xFFB30000L,
        "errorFill" to 0xFFFF3B30L,
        "success" to 0xFF1B5E20L,
    )

    /**
     * 深套 16 值。brief §深套表逐字给出 15 项，`onBrand` 缺项——品牌渐变深浅两套同三段
     * （spec §2.1「品牌渐变保持现状不动」），裁决 3 只钉「渐变上的文字统一取 on-brand #04241C」，
     * 未给深色另立值，故深套沿用 0xFF04241C；深色渐变前景的最终核验归 AC-R-11 真机实测。
     * 该调和已记入 task-2 报告「计划缺陷」。
     */
    private val darkHex: Map<String, Long> = linkedMapOf(
        "background" to 0xFF0E1512L,
        "surface" to 0xFF17201CL,
        "surfaceGlass" to 0xE617201CL,
        "tint" to 0xFF1C2C26L,
        "tint2" to 0xFF22322BL,
        "tint3" to 0xFF27392FL,
        "outline" to 0xFF2E4238L,
        "outlineSoft" to 0xFF233029L,
        "text1" to 0xFFE9F2ECL,
        "text2" to 0xFF9FB3AAL,
        "text3" to 0xFF7F938AL,
        "onMint" to 0xFFDFF6ECL,
        "onBrand" to 0xFF04241CL,
        "error" to 0xFFFF8177L,
        "errorFill" to 0xFFFF6B61L,
        "success" to 0xFF6BD98AL,
    )

    /**
     * 「浅深两套逐字相同」豁免名单（`AppColorSet` 字段名，口径见用例
     * `neutral token identical in both sets is a dead token unless exempted by name`）。
     * 名单里每一项都必须在这里给出决策依据；名单与实测同值集合双向精确相等。
     *
     * `onBrand`：spec §2.1 明写「品牌渐变保持现状不动」，裁决 3 只钉「渐变上的文字统一取 `on-brand #04241C`」，
     * 未给深套另立值（深套沿用浅值，见 [darkHex] 的缺项调和说明）；品牌渐变两套同三段时前景本就同一枚墨，
     * 所以它是**有据可依的主题无关值**，不是无人认领的死 token。深色下玻璃面板上的前景最终核验归 AC-R-11 真机实测。
     */
    private val crossSetIdenticalNeutralTokenExemptions: Set<String> = setOf("onBrand")

    /** 域浅套：spec §2.1 域色表 / 既有 DomainColors 逐值（base, deep）。hex 串同时供正则拼接，避免 Long 十进制插值。 */
    private val domainLightHex: Map<String, Pair<String, String>> = linkedMapOf(
        "AiSpeech" to ("0xFF2EC99C" to "0xFF188F76"),
        "Learn" to ("0xFF0A84FF" to "0xFF075EB4"),
        "Review" to ("0xFFFF9F0A" to "0xFFAB5700"),
        "Library" to ("0xFFBF5AF2" to "0xFF8E34B8"),
        "Reading" to ("0xFFFF375F" to "0xFFC81E46"),
        "Danger" to ("0xFFFF3B30" to "0xFFB30000"),
        "Settings" to ("0xFF8E8E93" to "0xFF48484A"),
    )

    /** 域深套：deep = base（spec §2.1「深色下域色与 base 合一」）。 */
    private val domainDarkHex: Map<String, String> = linkedMapOf(
        "AiSpeech" to "0xFF35E0AC",
        "Learn" to "0xFF5AC0FF",
        "Review" to "0xFFFFB340",
        "Library" to "0xFFDA8CFF",
        "Reading" to "0xFFFF7A93",
        "Danger" to "0xFFFF6B61",
        "Settings" to "0xFFB0B0B5",
    )

    private val appPaletteCalibrated: Map<String, Long> = linkedMapOf(
        // Step 4 改值四项
        "Background" to 0xFFF1FBF5L,
        "TextSecondary" to 0xFF4E756AL,
        "Separator" to 0xFFE4EFE9L,
        "GlassFill" to 0xEFFFFFFFL,
        // Step 4 新增八项（浅套静态源）
        "Tint" to 0xFFDDF7E8L,
        "Tint2" to 0xFFDCEAE1L,
        "Tint3" to 0xFFCDEDD9L,
        "Outline" to 0xFFADE7D2L,
        "OutlineSoft" to 0xFFE4EFE9L,
        "TextTertiary" to 0xFF7A9088L,
        "OnBrand" to 0xFF04241CL,
        "ErrorFill" to 0xFFFF3B30L,
    )

    private val appPaletteUnchanged: Map<String, Long> = linkedMapOf(
        "Surface" to 0xFFFFFFFFL,
        "TextPrimary" to 0xFF1C1C1EL,
        "Scrim" to 0x66000000L,
        "GlassHighlight" to 0x59FFFFFFL,
    )

    // ---------- 用例 ----------

    @Test
    fun `theme scan scope resolves to real kotlin sources`() {
        val files = scanThemeSources()
        val names = files.map { it.name }
        assertTrue(
            "AppPalette.kt" in names && "MintPalette.kt" in names,
            "扫描到 ${files.size} 个 .kt 却缺 AppPalette.kt / MintPalette.kt，扫描范围疑似错位：$THEME_SOURCE_PATH"
        )
    }

    @Test
    fun `missing scan directory fails loudly instead of passing on an empty scan`() {
        val missing = File("$THEME_SOURCE_PATH/__not_a_real_package__")
        // 危险本体：无守卫时 walkTopDown() 只给空序列，真实文件契约用例会当成零违规通过。
        assertTrue(
            missing.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().isEmpty(),
            "walkTopDown() 对缺失路径本应给出空序列（静默空扫风险）"
        )
        // 守卫本体：同一缺失路径必须让扫描立即失败而不是返回空列表。
        val error = assertFailsWith<AssertionError> { scanThemeSources(missing) }
        assertTrue(
            error.message?.contains(THEME_SOURCE_PATH) == true,
            "守卫失败信息必须点名期望路径，实际：${error.message}"
        )
    }

    /**
     * 正则防「永久红」自检（预检修正 1 的验证义务）：合成正片段必须命中，
     * 相邻声明负例必须不命中。断言顺序：先证明规则「抓得住」，再证明它「不越界」。
     */
    @Test
    fun `token lookup rule works on synthetic snippets and cannot self match adjacent declarations`() {
        val positives = listOf(
            // 标准命名参数形状（AppColorSet 构造调用里的真实形状）
            "AppColorSet(\n    background = Color(0xFFF1FBF5),\n)" to ("background" to 0xFFF1FBF5L),
            // 跨行赋值与小写 hex：正则的 `\s*` 与 [0-9A-Fa-f] 必须容下
            "AppColorSet(\n    surface\n        =\n        Color(\n            0xffffffff\n        ),\n)" to ("surface" to 0xFFFFFFFFL),
            // 同行多字段，取第一个命中
            "DomainPalette(base = Color(0xFF2EC99C), deep = Color(0xFF188F76))" to ("base" to 0xFF2EC99CL),
        )
        positives.forEach { (snippet, pair) ->
            val (token, expected) = pair
            assertEquals(expected, unsigned(hexOf(snippet, token, "synthetic")), "合成正片段漏判：$token")
        }

        val negatives = listOf(
            // 前缀近名：`tint` 不得命中 `tint2 =`（token 后是 `2`，非空白/`=`）
            "AppColorSet(tint2 = Color(0xFFDCEAE1))" to "tint",
            // `surface` 不得命中 `surfaceGlass =`
            "AppColorSet(surfaceGlass = Color(0xEFFFFFFF))" to "surface",
            // `outline` 不得命中 `outlineSoft =`
            "AppColorSet(outlineSoft = Color(0xFFE4EFE9))" to "outline",
            // `error` 不得命中 `errorFill =`
            "AppColorSet(errorFill = Color(0xFFFF3B30))" to "error",
            // lookbehind：token 不得是更长标识符的后缀（本条实为大小写拦住：appTint 内没有小写 tint 子串）
            "AppColorSet(appTint = Color(0xFF112233))" to "tint",
            // lookbehind 的 `\w` 分支本体：全小写后缀嵌入，大小写给不出任何理由，只能由 (?<![\w.]) 拦下
            "AppColorSet(apptint = Color(0xFF112233))" to "tint",
            "AppColorSet(webbackground = Color(0xFF112233))" to "background",
            // lookbehind 的点：带点成员引用不算声明
            "style.background = Color(0xFFF1FBF5)" to "background",
            // 带类型注解的属性声明（`val x: Color = Color(...)`）不是构造命名参数，不得被形状误认
            "val background: Color = Color(0xFFF1FBF5)" to "background",
            // 注释里的同形代码不计入契约（剥注释后必须查不到）
            "// background = Color(0xFFF1FBF5)\nval unrelated = 1" to "background",
        )
        negatives.forEach { (snippet, token) ->
            val text = stripComments(snippet)
            val failure = assertFailsWith<IllegalStateException> {
                hexOf(text, token, "synthetic-negative")
            }
            assertTrue(
                failure.message?.contains("token 未以 val $token = Color(0x…) 形状声明") == true,
                "负例失败消息形状错误：${failure.message}"
            )
        }
    }

    /** 分节守卫（预检修正 2）：锚点唯一有序 + 同名字段深浅互不救活 + 深查 background 不得被浅值满足。 */
    @Test
    fun `light and dark token sets are sectioned by declaration anchors not whole file search`() {
        val text = appColorsText()
        anchors.forEach { anchor ->
            val occurrences = text.split(anchor).size - 1
            assertEquals(1, occurrences, "锚点在剥注释源码中应恰好出现一次（分节边界必须无歧义）：$anchor")
        }
        val positions = anchors.map { text.indexOf(it) }
        assertTrue(
            positions == positions.sorted(),
            "锚点出现顺序应为 $anchors，实际位置 $positions（乱序说明分节切错）"
        )

        val light = section(text, "val AppColorLight")
        val dark = section(text, "val AppColorDark")
        val lightBg = hexOf(light, "background", "AppColorLight")
        val darkBg = hexOf(dark, "background", "AppColorDark")
        assertEquals(0xFFF1FBF5L, unsigned(lightBg), "浅套 background 偏离 spec §2.1")
        assertEquals(0xFF0E1512L, unsigned(darkBg), "深套 background 偏离 spec §2.1")
        // 显式「不等于对侧值」：若未来有人把两块合并/复制，此处先于取值表爆掉。
        assertTrue(darkBg != lightBg, "深套 background 与浅套同值：分节已坍缩")
        assertTrue(unsigned(darkBg) != 0xFFF1FBF5L, "深套 background 查到了浅套值：全文件搜索冒充分节")
        assertTrue(unsigned(lightBg) != 0xFF0E1512L, "浅套 background 查到了深套值：全文件搜索冒充分节")

        // 合成负例：浅套块缺 background、深套块有——分节查找必须失败而非被深套救活。
        val synthetic = stripComments(
            """
            val AppColorLight = AppColorSet(
                surface = Color(0xFFFFFFFF),
            )
            val AppColorDark = AppColorSet(
                background = Color(0xFF0E1512),
            )
            """.trimIndent()
        )
        assertFailsWith<IllegalStateException> {
            hexOf(section(synthetic, "val AppColorLight"), "background", "synthetic-light")
        }
    }

    /**
     * 从**生产** `data class AppColorSet(` 的参数表解析字段名（m8，批 1 终审 Seat A M1 的修法）。
     *
     * 为什么要从生产解析：旧口径拿本文件自己的 `lightHex.size` 与常量 `16` 比，是**表自指**——
     * 表少写一格会红（能挡漏抄），但生产加第 17 枚字段永不红（挡不住「数据面长出没人钉的第 17 格」）。
     * 改成「解析出的字段名集合 == 钉值表 key 集合」的精确集合相等后，两个方向都挡：
     * 生产多一枚 → 红，钉值表多一枚（生产已删）→ 也红。
     * 取样前提是本仓字段逐行书写、右括号落在行首（`AppColors.kt` 现状），锚点由下一条断言负责。
     */
    private fun appColorSetFieldNames(text: String): Set<String> {
        val anchor = "data class AppColorSet("
        val start = text.indexOf(anchor)
        assertTrue(start >= 0, "生产 AppColors.kt 里找不到 $anchor 声明（改名/挪走/换写法都要先同步本夹具）")
        val end = text.indexOf("\n)", start)
        assertTrue(end > start, "$anchor 的参数表没有落在「行首右括号」形状上，解析前提失效")
        return Regex("""val\s+(\w+)\s*:\s*Color""")
            .findAll(text.substring(start + anchor.length, end))
            .map { it.groupValues[1] }
            .toSet()
    }

    /** 浅/深两套钉值表与生产字段名集合的精确相等（m8 的判据本体；`16` 只作为解析结果的下游断言）。 */
    private fun assertFieldTablesCoverProduction(table: Set<String>, setLabel: String) {
        val production = appColorSetFieldNames(appColorsText())
        assertTrue(
            production.isNotEmpty(),
            "$setLabel：从生产 AppColorSet 解析出 0 枚字段——解析前提或扫描面失效，空表会让本闸假绿"
        )
        assertEquals(production, table, "$setLabel 的钉值表与生产 data class AppColorSet 的字段名集合不符（双向精确相等，多一格少一格都红）")
    }

    @Test
    fun `light neutral set hexes match spec 2_1 verbatim`() {
        val light = section(appColorsText(), "val AppColorLight")
        assertFieldTablesCoverProduction(lightHex.keys, "浅套")
        assertEquals(16, appColorSetFieldNames(appColorsText()).size, "生产 AppColorSet 解析出的字段数应为 16（spec §2.1 中性 16 枚；真加第 17 枚要先改 spec 再改本条）")
        lightHex.forEach { (token, expected) ->
            assertEquals(expected, unsigned(hexOf(light, token, "AppColorLight")), "浅套 $token 偏离 spec §2.1 浅套钉值")
        }
    }

    @Test
    fun `dark neutral set hexes match spec 2_1 verbatim`() {
        val dark = section(appColorsText(), "val AppColorDark")
        assertFieldTablesCoverProduction(darkHex.keys, "深套")
        assertEquals(16, appColorSetFieldNames(appColorsText()).size, "生产 AppColorSet 解析出的字段数应为 16（与浅套用例同一口径）")
        darkHex.forEach { (token, expected) ->
            assertEquals(expected, unsigned(hexOf(dark, token, "AppColorDark")), "深套 $token 偏离 spec §2.1 深套钉值")
        }
    }

    /**
     * m8 的承重自证：解析器必须真能从生产文本里数出字段，且**生产多加一格时判据会翻**。
     * 合成片段拿生产文本改（保留原形状，只追加一枚 `val test17: Color`），断言它与钉值表 key 集合不再相等。
     */
    @Test
    fun `field name parser catches a seventeenth production field instead of self indexing`() {
        val production = appColorSetFieldNames(appColorsText())
        assertEquals(lightHex.keys, production, "解析结果与浅套钉值表不吻合（本用例的底本必须干净）")
        val drifted = appColorSetFieldNames(appColorsText().replace("    val success: Color,", "    val success: Color,\n    val test17: Color,"))
        assertTrue(
            drifted != production && "test17" in drifted,
            "合成负片段失控：生产多加一枚 test17 字段后解析结果没变，等于表自指没被修掉（$drifted）",
        )
        assertTrue(
            lightHex.keys != drifted,
            "合成负片段被放行：多加的第 17 枚字段没有让「钉值表 == 生产字段」判据翻红，m8 修法空转"
        )
    }

    /**
     * **防死 token 闸（批 1 终审 F4，控制者对 Seat A I-1 的改法）**：16 枚中性 token 中，
     * 凡浅套与深套取值**逐字相同**的即判红，豁免必须逐枚点名。
     *
     * 堵的是哪条路径：`AppColorSet` 的字段语义是「同一角色在两套主题里各有一份值」。浅深同值 = 换套时
     * 这一枚永不翻，成为一个「读得到、但深色下不生效」的死 token。Seat A 实测：把
     * `AppColorDark.surfaceGlass` 改成与浅值逐字相同，批 1 全部门禁（含 1646 例）全绿——
     * 本闸就是那条路径上唯一的承重断言（变异自证：改深套 surfaceGlass 为浅值 → 本用例红）。
     *
     * 豁免口径与深色令牌棘轮的蒙版台账同形但**独立**：这里钉的是 `AppColorSet` 字段（两套主题的数据面），
     * `DarkTokenConsumptionRatchetTest.ThemeInvariantMaskMembers` 钉的是 `AppPalette` 静态成员（蒙版/高光）。
     * 豁免只留给「按决策本就与主题无关」的语义：`onBrand` 随品牌渐变，spec §2.1 明写「品牌渐变保持现状不动」、
     * 裁决 3 只钉「渐变上的文字统一取 `on-brand #04241C`」而未给深套另立值（AC-R-11 真机核验前不预判）。
     * 名单与实测同值集合**双向精确相等**：多一项（那枚已不再同值）判红，少一项（长了新的同值）也判红。
     */
    @Test
    fun `neutral token identical in both sets is a dead token unless exempted by name`() {
        val light = section(appColorsText(), "val AppColorLight")
        val dark = section(appColorsText(), "val AppColorDark")
        val identical = lightHex.keys
            .filter { token -> hexOf(light, token, "AppColorLight") == hexOf(dark, token, "AppColorDark") }
            .toSet()
        val unexempted = identical - crossSetIdenticalNeutralTokenExemptions
        assertTrue(
            unexempted.isEmpty(),
            "浅深两套逐字相同的中性 token（深色下永不翻 = 死 token）：$unexempted —— " +
                "要么给深套一个真正随主题的值，要么先立决策再把它逐枚写进 " +
                "crossSetIdenticalNeutralTokenExemptions（并注明决策依据），本闸不接受无名豁免"
        )
        val stale = crossSetIdenticalNeutralTokenExemptions - identical
        assertTrue(
            stale.isEmpty(),
            "豁免名单里的 $stale 如今两套取值已不同：它不再需要豁免，必须从名单删掉（豁免是台账不是永久名额）"
        )
    }

    @Test
    fun `domain light map hexes match spec and equal existing DomainColors`() {
        val text = appColorsText()
        val lightSection = section(text, "val DomainLight")
        domainLightHex.forEach { (domain, expected) ->
            val (expBase, expDeep) = expected
            val actual = domainHexes(lightSection, "DomainLight", domain)
            assertEquals(hexValue(expBase), actual.first, "DomainLight.$domain.base 偏离 spec §2.1 浅套域色")
            assertEquals(hexValue(expDeep), actual.second, "DomainLight.$domain.deep 偏离 spec §2.1 浅套域色")
            // 「与既有 DomainColors 逐值相同（改了就是回归）」：同一批 hex 必须也钉在 AppPalette.kt。
            val declared = Regex(
                """val\s+$domain\s*=\s*DomainAccent\(\s*Color\(\s*$expBase\s*\)\s*,\s*Color\(\s*$expDeep\s*\)\s*\)""",
                RegexOption.IGNORE_CASE,
            )
            assertTrue(
                declared.containsMatchIn(appPaletteText()),
                "AppPalette.kt 的 DomainColors.$domain 偏离既有钉值：应声明为 DomainAccent(Color($expBase), Color($expDeep))"
            )
        }
    }

    @Test
    fun `domain dark map collapses deep to base at pinned hexes`() {
        val darkSection = section(appColorsText(), "val DomainDark")
        domainDarkHex.forEach { (domain, expected) ->
            val (base, deep) = domainHexes(darkSection, "DomainDark", domain)
            assertEquals(hexValue(expected), base, "DomainDark.$domain.base 偏离 spec §2.1 深套域色")
            assertEquals(base, deep, "DomainDark.$domain 应 deep = base（深色下避免暗底深字）")
        }
    }

    @Test
    fun `app domain enum names match DomainColors property names for by name migration`() {
        val enumBody = Regex("""enum class AppDomain \{([^}]*)\}""").find(appColorsText())
            ?: error("AppColors.kt 缺 enum class AppDomain { … } 声明")
        val enumNames = enumBody.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(
            listOf("AiSpeech", "Learn", "Review", "Library", "Reading", "Danger", "Settings"),
            enumNames,
            "AppDomain 条目名是 Task 3 按名迁移的映射键，必须与 brief Interfaces 逐字一致",
        )
        val domainColorsBody = Regex("""object DomainColors \{([\s\S]*?)\n\}""").find(appPaletteText())
            ?: error("AppPalette.kt 缺 object DomainColors { … } 声明")
        val propertyNames = Regex("""val\s+(\w+)\s*=\s*DomainAccent""")
            .findAll(domainColorsBody.groupValues[1])
            .map { it.groupValues[1] }
            .toList()
        assertEquals(enumNames, propertyNames, "AppDomain 条目必须与 DomainColors 属性名逐字同序（按名对位）")
    }

    @Test
    fun `composable access surface is declared per interfaces`() {
        val text = appColorsText()
        listOf(
            Regex("""data class AppColorSet\(""") to "AppColorSet 数据类",
            Regex("""data class DomainPalette\(val base: Color, val deep: Color\)""") to "DomainPalette(base, deep)",
            Regex("""val LocalAppColors\s*:\s*ProvidableCompositionLocal<AppColorSet>\s*=\s*compositionLocalOf""") to "LocalAppColors",
            Regex("""val LocalDomainColors\s*:\s*ProvidableCompositionLocal<Map<AppDomain, DomainPalette>>\s*=\s*compositionLocalOf""") to "LocalDomainColors",
            Regex("""val LocalIsDark\s*:\s*ProvidableCompositionLocal<Boolean>\s*=\s*compositionLocalOf""") to "LocalIsDark",
            Regex("""fun appColors\(\)\s*:\s*AppColorSet\s*=\s*LocalAppColors\.current""") to "appColors()",
            Regex("""fun appDomain\(domain: AppDomain\)\s*:\s*DomainPalette\s*=\s*LocalDomainColors\.current\.getValue\(domain\)""") to "appDomain(domain)",
        ).forEach { (regex, label) ->
            assertTrue(regex.containsMatchIn(text), "Interfaces 声明缺失或形状偏离：$label")
        }

        // 翻色链路（裁决 R1）：三个 Local 必须用 compositionLocalOf，不得回到 staticCompositionLocalOf。
        // staticCompositionLocalOf 的读取方在值变化时不失效重绘，主题切换后 Mint* 仍会读旧一套色；
        // 装配点 MintGlassTheme 提供的值来自组合期状态读取，只有 compositionLocalOf 能把它翻到消费方。
        // 此处把「工厂名写成 static 前缀」显式判红（负向钉），改回即本用例红；正向钉在上方逐条要求
        // compositionLocalOf，两条合起来把「只改一处」也判红。
        val staticFactory = Regex("""staticCompositionLocalOf""")
        assertTrue(
            !staticFactory.containsMatchIn(text),
            "AppColors.kt 仍有 staticCompositionLocalOf：静态 Local 的读取方在主题切换时不失效，" +
                "装配点提供的新一套色翻不到消费方（应全部为 compositionLocalOf）",
        )
        // 防「永久绿」自检：上面两条规则在合成片段上必须一个抓得住、一个不越界。
        assertTrue(
            Regex("""val LocalAppColors\s*:\s*ProvidableCompositionLocal<AppColorSet>\s*=\s*compositionLocalOf""")
                .containsMatchIn("val LocalAppColors: ProvidableCompositionLocal<AppColorSet> = compositionLocalOf { AppColorLight }"),
            "合成正片段漏判：compositionLocalOf 正向钉抓不住标准形状",
        )
        assertTrue(
            staticFactory.containsMatchIn("val LocalIsDark: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }"),
            "合成负片段漏判：staticCompositionLocalOf 反向钉抓不住改回静态的写法",
        )

        // 反向钉：三个 Local 的静态类型不得写成基类 `CompositionLocal<…>`（修复轮 1 的 Important）。
        // 上方正向匹配已要求 `Providable` 前缀，此处再把「宽化类型」显式判红，防止有人只改回一处
        // 或另写 `CompositionLocal<` 形状的等价声明：`provides` 只是 ProvidableCompositionLocal 的成员
        // （基类 `CompositionLocal` 只有 `current`），Task 4 装配点要写 `LocalAppColors provides set`，
        // 静态类型宽化即编译失败。
        // 两个负向 lookbehind 排除 Providable/Dynamic 两个子类型前缀；工厂函数名 `compositionLocalOf`
        // 同样不构成误报，因为那里 `CompositionLocal` 之后紧跟的是 `Of` 而非 `<`。
        val widenedType = Regex("""(?<!Providable)(?<!Dynamic)CompositionLocal<""")
        assertTrue(
            !widenedType.containsMatchIn(text),
            "Local 的静态类型被写成基类 CompositionLocal<…>：provides 不属于该类型，Task 4 装配点会编译不过（应为 ProvidableCompositionLocal<…>）",
        )
    }

    /** 配对断言（操作数从源码取值读——token 漂移会当场爆，而不是留下永远绿的字面量）。 */
    @Test
    fun `allowed pairs meet WCAG floors computed from source tokens`() {
        val text = appColorsText()
        val light = section(text, "val AppColorLight")
        val dark = section(text, "val AppColorDark")
        fun l(token: String) = hexOf(light, token, "AppColorLight")
        fun d(token: String) = hexOf(dark, token, "AppColorDark")

        // 正文族 ≥4.5
        assertAtLeast("text1/bg-light", l("text1"), l("background"), 4.5)   // 16.09
        assertAtLeast("text1/tint", l("text1"), l("tint"), 4.5)             // 15.02
        assertAtLeast("text2/surface", l("text2"), l("surface"), 4.5)       // 5.15
        assertAtLeast("text2/bg", l("text2"), l("background"), 4.5)         // 4.87
        assertAtLeast("text2/tint", l("text2"), l("tint"), 4.5)             // 4.55
        assertAtLeast("error/surface", l("error"), l("surface"), 4.5)       // 7.20
        assertAtLeast("success/surface", l("success"), l("surface"), 4.5)   // 7.87
        assertAtLeast("onMint/tint", l("onMint"), l("tint"), 4.5)           // 8.61（ADR 复算值）
        // 品牌渐变末端段不是 AppColorSet 字段（MintPalette.AppleMintEnd，取值在下方既有钉值用例），
        // 故操作数取 brief 钉值字面量（.toInt()：十六进制字面量默认 Long，Int 形参需显式截位）。
        assertAtLeast("onBrand/gradientEnd", l("onBrand"), 0xFF35B9B5.toInt(), 4.5) // 6.87
        // 深套
        assertAtLeast("dark text1/bg", d("text1"), d("background"), 4.5)    // 16.20
        assertAtLeast("dark text2/surface", d("text2"), d("surface"), 4.5)  // 7.54（本测试复算）
        assertAtLeast("dark error/surface", d("error"), d("surface"), 4.5)  // 6.87（本测试复算）
        assertAtLeast("dark onMint/tint", d("onMint"), d("tint"), 4.5)      // 12.89
    }

    /** brief「必须出现的配对断言」字面量原文：钉住比值表本身（文档事实层；.toInt() 见上方说明）。 */
    @Test
    fun `pinned ratio table holds as documented WCAG facts`() {
        // 正文族 ≥4.5
        assertAtLeast("text1/bg-light", 0xFF1C1C1E.toInt(), 0xFFF1FBF5.toInt(), 4.5)   // 16.09
        assertAtLeast("text1/tint", 0xFF1C1C1E.toInt(), 0xFFDDF7E8.toInt(), 4.5)       // 15.02
        assertAtLeast("text2/surface", 0xFF4E756A.toInt(), 0xFFFFFFFF.toInt(), 4.5)    // 5.15
        assertAtLeast("text2/bg", 0xFF4E756A.toInt(), 0xFFF1FBF5.toInt(), 4.5)         // 4.87
        assertAtLeast("text2/tint", 0xFF4E756A.toInt(), 0xFFDDF7E8.toInt(), 4.5)       // 4.55
        assertAtLeast("error/surface", 0xFFB30000.toInt(), 0xFFFFFFFF.toInt(), 4.5)    // 7.20
        assertAtLeast("success/surface", 0xFF1B5E20.toInt(), 0xFFFFFFFF.toInt(), 4.5)  // 7.87
        assertAtLeast("onMint/tint", 0xFF174C44.toInt(), 0xFFDDF7E8.toInt(), 4.5)      // 8.61
        assertAtLeast("onBrand/gradientEnd", 0xFF04241C.toInt(), 0xFF35B9B5.toInt(), 4.5) // 6.87
        // 深套
        assertAtLeast("dark text1/bg", 0xFFE9F2EC.toInt(), 0xFF0E1512.toInt(), 4.5)    // 16.20
        assertAtLeast("dark text2/surface", 0xFF9FB3AA.toInt(), 0xFF17201C.toInt(), 4.5)  // 7.54
        assertAtLeast("dark error/surface", 0xFFFF8177.toInt(), 0xFF17201C.toInt(), 4.5)  // 6.87
        assertAtLeast("dark onMint/tint", 0xFFDFF6EC.toInt(), 0xFF1C2C26.toInt(), 4.5) // 12.89

        // 禁配（本禁配由 Task 1 裁决 #1/#2/#3 立；字面量层与源码层双钉，见下一用例）
        assertTrue(ratio(0xFF7A9088.toInt(), 0xFFFFFFFF.toInt()) < 4.5, "text-3 若已合格，裁决 #1 需重议")   // 3.40
        assertTrue(ratio(0xFF7A9088.toInt(), 0xFFDDF7E8.toInt()) < 4.5)                                     // 3.00
        assertTrue(ratio(0xFF4E756A.toInt(), 0xFFDCEAE1.toInt()) < 4.5, "text-2 压 tint-2 若已合格，裁决 #2 需重议") // 4.15
        assertTrue(ratio(0xFF174C44.toInt(), 0xFF35B9B5.toInt()) < 4.5, "on-mint 压渐变末端若已合格，裁决 #3 需重议") // 4.07
    }

    /** 禁配表（源码操作数版）：谁把 token 调深到「合格」，这里当场要求重议裁决。 */
    @Test
    fun `banned pairs from source tokens stay below thresholds`() {
        val light = section(appColorsText(), "val AppColorLight")
        fun l(token: String) = hexOf(light, token, "AppColorLight")
        assertTrue(
            ratio(l("text3"), l("surface")) < 4.5,
            "text-3 若已合格，裁决 #1 需重议（实测 %.2f:1）".format(ratio(l("text3"), l("surface"))),
        )
        assertTrue(
            ratio(l("text3"), l("tint")) < 4.5,
            "text-3 压 tint 若已合格，裁决 #1 需重议（实测 %.2f:1）".format(ratio(l("text3"), l("tint"))),
        )
        assertTrue(
            ratio(l("text3"), l("background")) < 4.5,
            "text-3 压 bg 若已合格，裁决 #1 需重议（ADR 钉 3.22，实测 %.2f:1）".format(ratio(l("text3"), l("background"))),
        )
        assertTrue(
            ratio(l("text2"), l("tint2")) < 4.5,
            "text-2 压 tint-2 若已合格，裁决 #2 需重议（实测 %.2f:1）".format(ratio(l("text2"), l("tint2"))),
        )
        assertTrue(
            ratio(l("text2"), l("tint3")) < 4.5,
            "text-2 压 tint-3 若已合格，裁决 #2 需重议（ADR 钉 4.10，实测 %.2f:1）".format(ratio(l("text2"), l("tint3"))),
        )
        assertTrue(
            ratio(l("onMint"), 0xFF35B9B5.toInt()) < 4.5,
            "on-mint 压渐变末端若已合格，裁决 #3 需重议（实测 %.2f:1）".format(ratio(l("onMint"), 0xFF35B9B5.toInt())),
        )
        // 裁决 #2 的替代配对同口径钉住：text-1 压 tint-2/tint-3 必须合格（ADR 钉 13.69/13.54）。
        assertAtLeast("text1/tint2(裁决#2 替代配对)", l("text1"), l("tint2"), 4.5) // 13.69
        assertAtLeast("text1/tint3(裁决#2 替代配对)", l("text1"), l("tint3"), 4.5) // 13.54

        // ---------- F5（批 1 终审 Seat A I-2）：text-3 的**图形/非文本**下限方向 ----------
        // 上面三条只按正文 4.5 判「text-3 不合格」，从未按图形下限判过：裁决 1 允许 text-3 用于
        // 「纯图标描边」，而描边是非文本图形（Global Constraints #7：≥3:1），Task 10 又把装饰档局部收紧到
        // ≥3.1（「边界上的合格不算合格」）。text-3 压 tint 恰好卡在 3.0 线上、压 tint-2/tint-3 连 3:1 都不过，
        // 所以批 2 任何一枚照裁决 1 字面用 text-3 描边的图标都能穿过全部批 1 门禁 —— 这三条钉的就是那个缺口。
        assertTrue(
            ratio(l("text3"), l("tint")) < 3.1,
            "text-3 压 tint 已达 %.2f:1（≥3.1 装饰档下限）——裁决 1 的「图标描边可用底」需按 F5 重议，" +
                "描边可以放回 tint".format(ratio(l("text3"), l("tint"))),
        )
        assertTrue(
            ratio(l("text3"), l("tint2")) < 3.0,
            "text-3 压 tint-2 已达 %.2f:1（≥非文本 3:1）——F5 的禁配依据失效，需重议裁决 1 的可用底清单".format(
                ratio(l("text3"), l("tint2")),
            ),
        )
        assertTrue(
            ratio(l("text3"), l("tint3")) < 3.0,
            "text-3 压 tint-3 已达 %.2f:1（≥非文本 3:1）——F5 的禁配依据失效，需重议裁决 1 的可用底清单".format(
                ratio(l("text3"), l("tint3")),
            ),
        )
        // 同一条裁决的另一半：text-3 压 surface/bg 是**合格**的图形档，正向钉住，
        // 免得有人把「text-3 一律不合格」当口径把描边一并禁掉（两条正向钉的是既有取值事实，不随本轮改动漂移）。
        assertAtLeast("text3/surface(裁决#1 描边可用底)", l("text3"), l("surface"), 3.1)
        assertAtLeast("text3/background(裁决#1 描边可用底)", l("text3"), l("background"), 3.1)
    }

    @Test
    fun `existing AppPalette pinned values unchanged`() {
        val palette = appPaletteText()
        listOf(
            "OnMintPrimary" to "0xFF174C44",
            "ErrorText" to "0xFFB30000",
            "SuccessText" to "0xFF1B5E20",
        ).forEach { (token, hex) ->
            val declared = Regex("""val\s+$token\s*=\s*Color\(\s*$hex\s*\)""", RegexOption.IGNORE_CASE)
            assertTrue(
                declared.containsMatchIn(palette),
                "AppPalette.$token 缺失或偏离既有钉值：应声明为 val $token = Color($hex)"
            )
        }
        // 裁决 3 的渐变末端段操作数依赖：MintPalette.AppleMintEnd 必须是 #35B9B5。
        assertTrue(
            Regex("""val\s+AppleMintEnd\s*:\s*Color\s*=\s*Color\(\s*0xFF35B9B5\s*\)""")
                .containsMatchIn(themeFileNamed("MintPalette.kt")),
            "MintPalette.AppleMintEnd 偏离品牌渐变末端钉值 0xFF35B9B5（spec §2.1「品牌渐变保持现状不动」）",
        )
    }

    /**
     * 浅套静态源逐枚读钉值（spec §2.1）。
     * 这里的正则是**解析器**而不是探针：只认 8 位无后缀形状，口径分工见 [hexOf] 的 F2 说明；
     * 「新增裸 hex 判违规」的宽探针是共享夹具 [rawColorLiteral]。
     */
    @Test
    fun `appPalette static light source calibrated to spec 2_1`() {
        val palette = appPaletteText()
        (appPaletteCalibrated + appPaletteUnchanged).forEach { (token, expected) ->
            val declared = Regex(
                """val\s+$token\s*=\s*Color\(\s*(0x[0-9A-Fa-f]{8})\s*\)""",
                RegexOption.IGNORE_CASE,
            )
            val m = declared.find(palette)
                ?: error("AppPalette.$token 未以 val $token = Color(0x…) 形状声明")
            assertEquals(hexValue(m.groupValues[1]), expected, "AppPalette.$token 偏离 spec §2.1 浅套钉值")
        }
    }
}

private const val THEME_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/theme"
