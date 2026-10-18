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
 *  - 已知仓库陷阱：含「块注释闭合序列」（星号紧跟斜杠）的字符串字面量会吞掉块注释正则。
 *    测试文件本体不在扫描范围（扫描对象是 main 源码），故 `stripComments` 自身的块注释正则
 *    字面量安全；后续任务勿在这些测试文件的注释或字面量里写出该闭合序列本体。
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
 * 主题目录排除的唯一入口（修复轮 1 的 N3：原先 `violations` 与调用方各写一份目录判定，批 2/批 3 改一处漏一处）。
 * `ui/` 与 `ui/theme/` 的豁免都走这里，按“文件相对扫描根所经过的目录名链”匹配，不含文件名本身。
 */
internal fun File.dirChainFrom(root: File): List<String> =
    relativeTo(root).invariantSeparatorsPath.split('/').dropLast(1)

/** 文件是否位于某个指定目录名下（路径链上任一段匹配即算，与 [violations] 的目录豁免同口径）。 */
internal fun File.isUnderDirNamed(root: File, dirName: String): Boolean = dirChainFrom(root).contains(dirName)

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

/** 剥注释样板（沿用禁令1/禁令2/禁令3）：注释里的示例代码不计入契约。 */
internal fun stripComments(text: String): String = text
    .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
    .replace(Regex("//.*"), "")

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
