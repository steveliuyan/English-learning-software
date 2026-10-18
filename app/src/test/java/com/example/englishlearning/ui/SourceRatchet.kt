package com.example.englishlearning.ui

import java.io.File
import kotlin.math.pow
import kotlin.test.assertTrue

/**
 * 批 1 源码级契约测试共享夹具（计划 Task 2 建 `uiFiles`/`stripComments`/`countMatches` 三件，
 * Task 7 起再加 `luminance`/`ratio` 亮度复算一对；
 * `violations` 棘轮入口由计划 Task 8「三件棘轮测试上线」在本文件补，届时不要另起第二份夹具）。
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
 *  - `walkTopDown()` 对缺失路径返回空序列（静默空扫），故目录守卫必须内置在 `uiFiles`。
 */

internal const val UI_MAIN_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui"

/** 目录守卫：目录与 .kt 文件必须真实存在，否则契约测试静默空扫。 */
internal fun uiFiles(dir: File = File(UI_MAIN_SOURCE_PATH)): List<File> {
    assertTrue(
        dir.isDirectory,
        "UI 源码契约扫描目录缺失（期望 $UI_MAIN_SOURCE_PATH）：${dir.path}，" +
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
