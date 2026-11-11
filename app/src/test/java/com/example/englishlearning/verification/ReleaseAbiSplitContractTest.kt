package com.example.englishlearning.verification

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 发布产物体积策略（决策 docs/decisions/2026-11-11-release-artifact-size-and-abi-strategy.md）的构建侧钉桩。
 *
 * 口径：
 *  - ABI 拆分**必须**由 Gradle 属性 `abiSplits` 开关，默认关闭——默认构建仍是四 ABI 的
 *    universal APK，因为模拟器（x86_64）的 instrumented 与真机（arm64-v8a）的安装口令都写在既有
 *    QA 文档里，默认改形状会让那些命令静默指向不存在的文件。
 *  - 打开时只发两块真实 ABI：arm64-v8a + armeabi-v7a；两个 x86 库只服务模拟器，不进交付产物。
 *  - R8/资源压缩由属性 `r8` 开关、默认开启（决策 docs/decisions/2026-11-11-r8-minification.md），
 *    见 `ReleaseR8ContractTest`。
 */
class ReleaseAbiSplitContractTest {

    private val gradleFile = File("build.gradle.kts")
        .let { if (it.exists()) it else File("../app/build.gradle.kts") }

    private val text: String by lazy { gradleFile.readText() }

    private fun splitsBlock(): String {
        val start = text.indexOf("splits {")
        assertTrue(start >= 0, "app/build.gradle.kts 必须声明 splits {} 块（ABI 拆分入口）")
        // 取到配平的右花括号
        var depth = 0
        var i = text.indexOf('{', start)
        val from = i
        while (i < text.length) {
            if (text[i] == '{') depth++
            if (text[i] == '}') {
                depth--
                if (depth == 0) return text.substring(from, i + 1)
            }
            i++
        }
        error("splits {} 块未配平")
    }

    @Test
    fun `abi splits are gated by the abiSplits gradle property`() {
        assertTrue(
            text.contains("\"abiSplits\""),
            "拆分开关必须读 Gradle 属性 abiSplits（默认 false，默认产物形状不变）",
        )
        val block = splitsBlock()
        assertTrue(
            block.contains("isEnable"),
            "splits.abi 必须由该属性驱动 isEnable，而不是无条件打开",
        )
    }

    @Test
    fun `splits ship only the two arm ABIs`() {
        val block = splitsBlock()
        assertTrue(block.contains("arm64-v8a"), "必须包含 arm64-v8a")
        assertTrue(block.contains("armeabi-v7a"), "必须包含 armeabi-v7a")
        assertEquals(
            false,
            block.contains("include(\"x86\")") || block.contains("\"x86\","),
            "两个 x86 库只服务模拟器，不得进交付拆分",
        )
    }

    @Test
    fun `abi splits and r8 stay independent switches`() {
        val releaseBlock = text.substringAfter("getByName(\"release\")")
        assertTrue(
            releaseBlock.contains("isMinifyEnabled = r8Enabled"),
            "R8 归 r8 属性开关，不该与 abiSplits 绑在一起",
        )
        assertTrue(
            releaseBlock.contains("isShrinkResources = r8Enabled"),
            "资源压缩必须跟着 R8 一起开关（关 R8 时 AGP 不接受单独 shrink）",
        )
    }
}
