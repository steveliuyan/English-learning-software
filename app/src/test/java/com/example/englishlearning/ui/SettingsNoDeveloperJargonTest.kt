package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Task 19（AC-R-21）源码契约：设置页只许说用户语言。
 *
 * 「F4-04 / AI Profile / Salvage」这类内部术语曾以字符串字面量混进一级页文案
 * （`.elpbak` 副标题、配对导出与恢复的括号注、占位行副标题），源码文本即契约：
 * 先剥注释（共用夹具 [stripComments]，字符串字面量原样保留）再禁词扫描，
 * 注释里的历史说明不受本契约约束。占位行禁令对应 IA 决策文档裁决②：
 * 「后续版本 / 敬请期待 / 暂未开放」的假入口整族删除，不降级为灰行。
 */
private const val SETTINGS_FILE = "src/main/java/com/example/englishlearning/ui/SettingsScreen.kt"

class SettingsNoDeveloperJargonTest {
    @Test fun settings_screen_speaks_user_language() {
        val src = stripComments(File(SETTINGS_FILE).readText())
        listOf("F4-04", "AI Profile", "Salvage", "salvage").forEach { term ->
            assertFalse(src.contains(term), "设置页出现开发者术语「$term」（AC-R-21 要求用户语言）")
        }
    }

    @Test fun no_placeholder_next_version_rows() {
        val src = stripComments(File(SETTINGS_FILE).readText())
        assertFalse(
            Regex("""后续版本|敬请期待|暂未开放""").containsMatchIn(src),
            "设置页不得保留「后续版本」占位行（裁决②）",
        )
    }
}
