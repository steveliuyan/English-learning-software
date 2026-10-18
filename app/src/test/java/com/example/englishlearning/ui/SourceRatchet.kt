package com.example.englishlearning.ui

import java.io.File
import kotlin.math.pow
import kotlin.test.assertTrue

/**
 * 批 1 源码级契约测试共享夹具（计划 Task 2 建 `uiFiles`/`stripComments`/`countMatches` 三件，
 * Task 7 起再加 `luminance`/`ratio` 亮度复算一对；
 * `violations` 棘轮入口由计划 Task 8「裸 hex / 深色令牌消费两件棘轮上线」在本文件补齐，
 * 第三件「组件份数」棘轮（顺延至 Task 12）同样走这个入口，届时不要另起第二份夹具）。
 *
 * 口径（有意为之，勿「顺手加严」）：
 *  - JVM 单测只按需导入 compose 的纯数据符号（如 `androidx.compose.ui.graphics.Color`，用来算亮度），
 *    不引入 robolectric 或渲染运行时，所以「源码文本即契约」：取值、形状、计数都以纯函数读
 *    `src/main/java` 文本；Compose 渲染类断言归 androidTest。
 *  - 注释文本不计入契约：`stripComments` 形状沿用 `AccessibilityColorTokenContractTest` 里的同名纯函数。
 *    实现文件的 KDoc 会合法复述同一批 hex（如「取值来自 spec §2.1」），不先剥注释的正则会被
 *    注释满足，契约就成空转——所有源码断言必须先过 stripComments 再匹配。
 *  - **字符串字面量里的注释标记不得当注释剥**（Task 12 修复轮 1 的 Fix 1）：SAF 的 MIME 数组写字面量
 *    「星号、斜杠、星号、斜杠」那一枚（`ui/BackupRestoreScreen.kt`、`ui/AppScreen.kt` 各一处），
 *    其中「斜杠紧跟星号」是合法的块注释起始序列，旧的纯正则剥法会从这一行一路吞到下一条行尾注释的
 *    「星号紧跟斜杠」闭合序列，把中间几十行真代码删掉，令「剥注释后才匹配」的源码棘轮读不到那几十行里的违规
 *    （本仓 skill `android-repo-session-handoff` §3.1 记过的同一枚陷阱）。故本函数是**字符串感知的扫描式实现**：
 *    双引号单行串、三引号原始串、单引号字符字面量整段原样保留，只有在字符串外才识别行注释（到行尾，保留换行）
 *    与块注释（惰性到闭合序列）；未闭合的块注释吞到文末。
 *    这条修复由 `SharedComponentUniquenessRatchetTest` 的合成夹具钉 + 真实文件锚双重钉住，勿改回正则版。
 *    （本条注释本身刻意不写出那两个序列的本体——写进块注释里会当场把本注释关掉。）
 *  - 测试文件本体不在扫描范围（扫描对象是 main 源码）。
 *  - `walkTopDown()` 对缺失路径返回空序列（静默空扫），故目录守卫必须内置在 `uiFiles`，
 *    并由 `violations` 复用同一入口而不是再写一份扫描。
 */

internal const val UI_MAIN_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui"

/** `ui` 包所在的源码根：裸 hex 棘轮扫描 `ui/` 之外的全仓裸 hex 站点时用它作扫描根。 */
internal const val APP_MAIN_SOURCE_PATH = "src/main/java/com/example/englishlearning"

/** 裸 hex 棘轮的主题侧豁免目标目录名；令牌定义文件集中于此，豁免按文件名名单收窄（见裸 hex 棘轮）。 */
internal const val THEME_DIR_NAME = "theme"

/** `ui` 包目录名：`ui/` 之外的全仓裸 hex 扫描用它把整个 `ui` 子树排除出视野。 */
internal const val UI_DIR_NAME = "ui"

/** `ui/theme/` 目录：令牌定义文件所在地，裸 hex 豁免名单与深色政策豁免都按此根定位。 */
internal const val UI_THEME_SOURCE_PATH = "$UI_MAIN_SOURCE_PATH/theme"

/**
 * 测试侧 `ui/` 源码根（相对模块目录）：F6 的「不得再长出私有剥注释实现」闸用它作扫描根。
 * 与 [UI_MAIN_SOURCE_PATH] 一样按相对路径写，JVM 单测的工作目录就是 `app/` 模块目录。
 */
internal const val TEST_UI_SOURCE_DIR_PATH = "src/test/java/com/example/englishlearning/ui"

/**
 * 裸 hex 字面量的唯一匹配口径（批 1 终审 F2）。
 * 必须容忍 Kotlin 整型字面量后缀与窄化调用：Compose 只有 `Color(Long)`，
 * `Color(0xFF6B61L)` / `Color(0xFF6B61FFu.toLong())` 都是**能编译**的裸 hex 写法，
 * 只匹配「十六进制紧跟右括号」的老正则会被这类换形整体绕过。
 */
internal val rawColorLiteral = Regex(
    """Color\(\s*0x[0-9A-Fa-f]{6,8}[uUlL]*\s*(?:\.to(?:Int|Long|UShort|ULong|Float)\(\s*\))?\s*\)"""
)

/**
 * 主题目录排除的唯一入口（修复轮 1 的 N3：原先 `violations` 与调用方各写一份目录判定，批 2/批 3 改一处漏一处）。
 * `ui/` 与 `ui/theme/` 的豁免都走这里，按“文件相对扫描根所经过的目录名链”匹配，不含文件名本身。
 *
 * **口径收窄（批 1 终审 F8 的 M-3 第二半）**：只认「相对扫描根的**第一段**目录名」，不再是路径链上任一段。
 * 旧口径下 `feature/ui/`、`a/theme/` 这类未来目录会被整片静默豁免出扫描面，债务只要换个嵌套位置就隐形；
 * 现在的两处真实调用（`ui/` 根下的 `theme/`、`app` 根下的 `ui/`）第一段语义不变，负片段由
 * `RawColorLiteralRatchetTest.directory exemption covers exactly the first segment under the scan root` 钉住。
 */
internal fun File.dirChainFrom(root: File): List<String> =
    relativeTo(root).invariantSeparatorsPath.split('/').dropLast(1)

/** 文件是否**恰在扫描根下的那个目录名**之下（只看第一段，与 [violations] 的目录豁免同口径，见 [dirChainFrom]）。 */
internal fun File.isUnderDirNamed(root: File, dirName: String): Boolean = dirChainFrom(root).firstOrNull() == dirName

/** 文件相对扫描根的不变路径（正斜杠）；债务台账的 key 用它，免疫不同目录下同名文件互相冒名顶替。 */
internal fun File.relativePathFrom(root: File): String = relativeTo(root).invariantSeparatorsPath

/**
 * 目录守卫：目录与 .kt 文件必须真实存在，否则契约测试静默空扫。
 * `expectedHint`（修复轮 1 的 N5）让同一个入口服务多个扫描根时，失败消息点名的是**本次真正要扫的根**
 * 而不是写死的 `ui` 常量；缺省仍回落到 `ui`，保持既有调用点的文案不变。
 */
internal fun uiFiles(dir: File = File(UI_MAIN_SOURCE_PATH), expectedHint: String? = null): List<File> {
    assertTrue(
        dir.isDirectory,
        "UI 源码契约扫描目录缺失（期望 ${expectedHint ?: UI_MAIN_SOURCE_PATH}）：${dir.path}，" +
            "相对 JVM 工作目录 ${File(".").absolutePath}；" +
            "walkTopDown() 对缺失路径返回空序列，会让契约测试无声通过——包名重构 / 工作目录变化必须先修此处路径。"
    )
    val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    assertTrue(files.isNotEmpty(), "UI 源码契约扫描空扫：${dir.path} 下没有 .kt 文件（文件数 0 不是合规，是漏扫）。")
    return files
}

/** 块注释闭合序列（「星号紧跟斜杠」）与三引号：只在代码里按字符序列比较，绝不写进任何注释本体。 */
private const val BLOCK_COMMENT_CLOSE = "*/"
private const val BLOCK_COMMENT_START = "/*"
private const val TRIPLE_QUOTE = "\"\"\""

/**
 * 剥注释样板（沿用禁令1/禁令2/禁令3：注释里的示例代码不计入契约），**字符串感知**的扫描式实现。
 *
 * 为什么不用两条正则：见文件头 Fix 1 —— 字符串字面量里合法出现的「斜杠紧跟星号」会让
 * 块注释正则从那一行一路吞到下一条行尾注释的闭合序列，把中间几十行真代码当成注释删掉，
 * 所有源码棘轮随之读不到那几十行（假绿）。故这里逐字符走：
 *  - 三引号原始串、双引号单行串（认反斜杠转义、遇换行即判非法）、单引号字符字面量（含 `'\''` 与「单引号包双引号」）
 *    **整段原样保留**；
 *  - 只有在字符串外才识别行注释（剥到行尾但**保留换行**，行号与 `(?m)^` 锚点口径不变）
 *    与块注释（惰性到下一个闭合序列；未闭合则吞到文末）。
 *
 * 两条与旧正则同源、**故意不修**的遗留限制（修复轮 2 的 M-f 只做登记，改动前须先补合成用例）：
 *  - Kotlin 的块注释**可嵌套**（外层开→内层开→内层闭→外层闭），本实现按「惰性到第一个闭合序列」收尾，
 *    于是嵌套块多出来的那半条尾巴会漏回代码里；漏回来的是注释文本，最坏让源码棘轮多判一处命中（假红），
 *    不会把真代码吞掉（不失明）。
 *  - 三引号原始串**不认反斜杠转义**（Kotlin 语义如此，实现按「下一个三引号」收尾），
 *    串内用四连引号一类形状表达字面量三引号时会被提前判结束；本仓 main 源码不含该形状。
 */
internal fun stripComments(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val next = if (i + 1 < text.length) text[i + 1] else ' '
        when {
            c == '"' && next == '"' && i + 2 < text.length && text[i + 2] == '"' -> {
                val end = text.indexOf(TRIPLE_QUOTE, i + 3)
                if (end == -1) {
                    out.append(text, i, text.length)
                    i = text.length
                } else {
                    out.append(text, i, end + TRIPLE_QUOTE.length)
                    i = end + TRIPLE_QUOTE.length
                }
            }

            c == '"' -> {
                val end = singleQuotedStringEnd(text, i)
                if (end == -1) { out.append(c); i++ } else { out.append(text, i, end); i = end }
            }

            c == '\'' -> {
                val end = charLiteralEnd(text, i)
                if (end == -1) { out.append(c); i++ } else { out.append(text, i, end); i = end }
            }

            c == '/' && next == '/' -> {
                val newline = text.indexOf('\n', i)
                // 不 append 注释本体；把换行留给下一轮 append，行数与旧实现逐字一致。
                i = if (newline == -1) text.length else newline
            }

            c == '/' && next == '*' -> {
                val end = text.indexOf(BLOCK_COMMENT_CLOSE, i + BLOCK_COMMENT_START.length)
                i = if (end == -1) text.length else end + BLOCK_COMMENT_CLOSE.length
            }

            else -> { out.append(c); i++ }
        }
    }
    return out.toString()
}

/** 双引号单行串（认反斜杠转义）的收尾下标（闭合引号的下一位）；跨行即非法，返回 -1 让调用方按普通字符处理。 */
private fun singleQuotedStringEnd(text: String, start: Int): Int {
    var j = start + 1
    while (j < text.length) {
        when (text[j]) {
            '\\' -> j += 2
            '"' -> return j + 1
            '\n' -> return -1
            else -> j++
        }
    }
    return -1
}

/** 单引号字符字面量（`'a'`、转义 `'X'`、「单引号包单引号」）的收尾下标；形状不合法返回 -1。 */
private fun charLiteralEnd(text: String, start: Int): Int {
    var j = start + 1
    if (j >= text.length) return -1
    j = if (text[j] == '\\') j + 2 else j + 1
    return if (j < text.length && text[j] == '\'') j + 1 else -1
}

/** 剥注释后的命中计数（锚点唯一性守卫与 Task 8 的裸 hex / 份数棘轮共用此入口）。 */
internal fun countMatches(source: String, regex: Regex): Int = regex.findAll(stripComments(source)).count()

/** `ui/components/` 目录片段：按文件名取共享组件源码时的目录守卫目标（Windows 反斜杠路径先转正斜杠再比对）。 */
internal const val COMPONENTS_DIR_FRAGMENT = "/ui/components/"

/**
 * 按文件名在 `ui/components/` 取**剥注释后**的源码，供源码级契约测试当唯一读法入口。
 *
 * 搬家说明（M-3，Task 11 复评结转、Task 12 兑现）：这个函数原先逐字重复了 4 份
 * （`SharedComponentGeometryContractTest`、`NotConfiguredCardContractTest`、`ConfirmDestructiveInkTest`、
 * `InlineNoticeActionContractTest` 各一份私有副本），第 5 份（`IconBadgeContrastContractTest.uiSource`，
 * 多一个 `dirFragment` 形参）现在也改成委托本函数。Task 12 的组件份数棘轮要读第 5、第 6 个组件文件，
 * 再抄一份就是 6 份同文——故提升成共享夹具，与 [uiFiles]/[stripComments]/[countMatches] 同处一文件。
 *
 * 两条断言与私有版逐字一致，都属于「契约不许空转」这一族：
 *  - **文件恰一个**：0 个 = 实现文件未创建/被改名，2 个 = 同名文件互相冒名顶替，两者都必须点名失败，
 *    不能静默返回空串让 `countMatches(...) == 0` 那类断言假绿；
 *  - **目录守卫**：文件必须真在 [dirFragment] 下，目录挪动会让契约读到别的文件。
 *
 * [dirFragment] 只为第 5 份（`IconBadgeContrastContractTest` 还要读 `ui/theme/` 下的文件）保留，
 * 缺省即组件目录；不要用它去读组件目录之外的组件文件——那是把守卫改成摆设。
 * 读 `ui/` **根**下的屏文件请改走 [uiScreenSource]（修复轮 2 的 Fix I-3：`dirFragment = "/"` 那两处已搬走，
 * 因为任何绝对路径都含 `/`，子串匹配在那两处上等于取消守卫）。
 */
internal fun uiComponentSource(name: String, dirFragment: String = COMPONENTS_DIR_FRAGMENT): String {
    val files = uiFiles().filter { it.name == name }
    assertTrue(
        files.size == 1,
        "$name 应恰好一个，实际 ${files.size} 个（实现文件未创建 / 被改名 / 扫描错位？" +
            "期望目录 $UI_MAIN_SOURCE_PATH，JVM 工作目录 ${File(".").absolutePath}）"
    )
    val file = files.single()
    assertTrue(
        file.path.replace('\\', '/').contains(dirFragment),
        "$name 不在 $dirFragment 下（目录挪动会让本契约读错文件）：${file.path}"
    )
    return stripComments(file.readText())
}

/**
 * 按文件名在 `ui/` **根目录**取剥注释后的源码（屏文件专用，与 [uiComponentSource] 区分）。
 * 守卫按「父目录恰等于 [UI_MAIN_SOURCE_PATH]」判，不做子串匹配：
 * 屏文件一旦搬家进 `ui/<子目录>/`，本夹具响亮失败，而不是像 `dirFragment = "/"` 那样静默放行。
 *
 * **m6（批 1 终审，Seat B M-6 同源）**：本入口只服务**屏文件**，所以再加一道文件名形状钉
 * （必须以 `Screen.kt` 结尾）。批 2 会新增屏文件，若把 `WordRow.kt`（组件）或 `AiFeature.kt`
 * （`ui/` 根下的非屏文件）误当屏文件读，契约就对着一个不是屏的文件绿；形状钉让这种误用在入口当场判红。
 * 钉在父目录守卫**之前**：同名的搬家文件也应先按「是不是屏」定性，再谈位置。
 */
internal fun uiScreenSource(name: String): String {
    assertTrue(
        name.endsWith("Screen.kt"),
        "$name 不是屏文件（uiScreenSource 只服务 `ui/` 根下文件名以 Screen.kt 结尾的屏）：" +
            "组件请走 uiComponentSource，非屏文件请显式按路径读取。"
    )
    val files = uiFiles().filter { it.name == name }
    assertTrue(
        files.size == 1,
        "$name 应恰好一个，实际 ${files.size} 个（实现文件未创建 / 被改名 / 扫描错位？期望目录 $UI_MAIN_SOURCE_PATH，" +
            "JVM 工作目录 ${File(".").absolutePath}）",
    )
    val file = files.single()
    assertTrue(
        file.parentFile.relativeTo(File(UI_MAIN_SOURCE_PATH)).path.isEmpty(),
        "$name 不在 $UI_MAIN_SOURCE_PATH 根下（屏文件搬家会让本契约读错文件）：${file.path}",
    )
    return stripComments(file.readText())
}

/**
 * WCAG 相对亮度，int 域（0xRRGGBB / 0xAARRGGBB 都只看低三字节，与 `DesignTokenContractTest` 的
 * hex 取值口径一致）；Global Constraints #7 的公式实现，逐分量复算，禁止估。
 *
 * 搬家说明（批 1 Task 7 控制者裁决）：这两个纯函数原先是 `DesignTokenContractTest` 的私有成员，
 * Task 7 的 `IconBadgeContrastContractTest` 要按同一口径复算容器对比，计划原文即要求「用 Task 2 的
 * luminance/ratio」——公式再抄一份就是两套亮度口径，故搬成共享夹具，函数体逐字未动。
 * `MaterialThemeAssemblyContractTest` 里那一对是 **Color 域**（读活符号的浮点分量）的另一份实现，
 * 按裁决保持私有、不在本次搬家范围内。
 */
internal fun luminance(rgb: Int): Double {
    fun lin(c: Int): Double {
        val s = (c and 0xFF) / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * lin(rgb shr 16) + 0.7152 * lin(rgb shr 8) + 0.0722 * lin(rgb)
}

/** 前景压背景的对比比值（`(L1 + 0.05) / (L2 + 0.05)`，WCAG 2.1 contrast ratio 同式）。 */
internal fun ratio(fg: Int, bg: Int): Double {
    val hi = maxOf(luminance(fg), luminance(bg))
    val lo = minOf(luminance(fg), luminance(bg))
    return (hi + 0.05) / (lo + 0.05)
}

/**
 * 棘轮入口（计划 Task 8 Step 1）：返回 `scanDir` 下命中 `rule`、且未被 `allowlist` / `exemptDirNames`
 * 覆盖的站点，条目形状「文件名: 折叠空白后的源码行」，**不报行号**——块注释剥离会把其后的行折叠进同
 * 一段文本，此时行号与内容已经对不上（offender 口径逐字沿用 `AccessibilityColorTokenContractTest`）。
 * `allowlist` 条目必须是同一个「文件名: 折叠行」形状，否则豁免永远写不中（该风险由调用方的合成用例证）。
 *
 * 三条内置守卫，都属于「棘轮不许空转」这一族：
 *  - 目录守卫复用 [uiFiles]（缺失路径与零 `.kt` 当场失败，消息点名期望路径与当前 JVM 工作目录），
 *    不再手写第二份扫描；
 *  - `exemptDirNames` 非空时必须真的覆盖到文件：指向失效目录的豁免会让债务悄悄换扫法却依旧绿；
 *  - 豁免过滤后扫描集不得为空：不许把整批债务一次性豁免掉，让棘轮退化成 no-op。
 *
 * 返回集刻意不做 distinct：同一物理行含两处命中时（`if (...) DomainColors.X.deep else AppPalette.Y`），
 * distinct 会把两次命中显示成一条、掩盖真实计数。「数量精确」由调用方的按文件计数用例负责，
 * 本函数只负责点名与 allowlist 比对；调用方若要用行集当台账，须自带「条目两两互不相同」的核验。
 */
internal fun violations(
    label: String,
    rule: Regex,
    allowlist: Set<String>,
    scanDir: File = File(UI_MAIN_SOURCE_PATH),
    exemptDirNames: Set<String> = emptySet(),
): List<String> {
    val files = uiFiles(scanDir, expectedHint = "$label：${scanDir.path}")
    val exempt = files.filter { file -> exemptDirNames.any { name -> file.isUnderDirNamed(scanDir, name) } }
    if (exemptDirNames.isNotEmpty()) {
        assertTrue(
            exempt.isNotEmpty(),
            "$label：目录豁免 $exemptDirNames 在 ${scanDir.path}（相对 JVM 工作目录 ${File(".").absolutePath}）" +
                "下没覆盖到任何 .kt 文件——空转的豁免等于把债务换个地方漏扫，必须先修豁免名或修扫描路径。"
        )
    }
    val scanned = files - exempt.toSet()
    assertTrue(
        scanned.isNotEmpty(),
        "$label：豁免 $exemptDirNames 之后扫描集为空（${scanDir.path} 下 ${files.size} 个 .kt 全被豁免覆盖）——" +
            "棘轮不接受把自己整体豁免掉。"
    )
    return scanned
        .flatMap { file -> foldedHitLines(file.readText(), rule).map { hit -> "${file.name}: $hit" } }
        .filterNot { it in allowlist }
}

/** 命中所在行的「折叠空白」取样：先剥注释，再按命中位置回溯整行；跨行命中的多行文本一并折叠成一行。 */
private fun foldedHitLines(text: String, rule: Regex): List<String> {
    val stripped = stripComments(text)
    return rule.findAll(stripped).map { match ->
        val lineStart = stripped.lastIndexOf('\n', match.range.first) + 1
        val lineEnd = stripped.indexOf('\n', match.range.last).let { i -> if (i == -1) stripped.length else i }
        stripped.substring(lineStart, lineEnd).trim().replace(Regex("""\s+"""), " ")
    }.toList()
}
