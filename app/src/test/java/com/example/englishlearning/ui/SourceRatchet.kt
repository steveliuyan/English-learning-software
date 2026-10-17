package com.example.englishlearning.ui

import java.io.File
import kotlin.test.assertTrue

/**
 * 批 1 源码级契约测试共享夹具（计划 Task 2 建 `uiFiles`/`stripComments`/`countMatches` 三件；
 * Task 6 再在本文件补 `violations` 棘轮入口，届时不要另起第二份夹具）。
 *
 * 口径（有意为之，勿「顺手加严」）：
 *  - 本仓库 JVM 单测零 `androidx.compose` 导入，所以「源码文本即契约」：取值、形状、计数
 *    都以纯函数读 `src/main/java` 文本；Compose 渲染类断言归 androidTest。
 *  - 注释文本不计入契约：`stripComments` 形状逐字沿用 AccessibilityColorTokenContractTest:48-51。
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

/** 剥注释后的命中计数（锚点唯一性守卫与 Task 6 棘轮共用此入口）。 */
internal fun countMatches(source: String, regex: Regex): Int = regex.findAll(stripComments(source)).count()
