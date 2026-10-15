package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * F5-03 源码契约（决策 §3 禁令1）：ui 包内 contentDescription 字面量必须含中文——
 * TalkBack 会把英文 testTag 字符串原样念成内容描述（设备 QA 审计 blocker）。
 * 含插值但不含中文的字面量（如 "${filter.label()} $count"）同样违规：运行时中文
 * 不能替代静态可读性审查，修复方式是删除该 semantics 行（子节点合并朗读已足够）。
 */
class AccessibilityLabelContractTest {
    private val uiDir = File("src/main/java/com/example/englishlearning/ui")

    private fun codeOf(file: File): String = file.readText()
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//.*"), "")

    @Test
    fun `contentDescription literals in ui sources always contain cjk text`() {
        val literal = Regex("""contentDescription = "([^"]*)"""")
        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                literal.findAll(codeOf(file))
                    .filter { m -> !m.groupValues[1].any { ch -> ch.code in 0x4E00..0x9FFF } }
                    .map { m -> "${file.name}: contentDescription = \"${m.groupValues[1]}\"" }
                    .toList()
            }
            .toList()
        assertTrue(offenders.isEmpty(), "英文 contentDescription 字面量：\n${offenders.joinToString("\n")}")
    }

    @Test
    fun `no contentDescription is assigned from a raw tag variable`() {
        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { Regex("""contentDescription = tag\b""").containsMatchIn(codeOf(it)) }
            .map { it.name }
            .toList()
        assertTrue(offenders.isEmpty(), "contentDescription = tag 直赋：${offenders.joinToString()}")
    }
}
