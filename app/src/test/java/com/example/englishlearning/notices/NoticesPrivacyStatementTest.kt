package com.example.englishlearning.notices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** F5-04：许可页固定隐私说明必须覆盖 Spec 五要点（无账户/无自动云备份/AI 自配/Key 不导出/出站确认）。 */
class NoticesPrivacyStatementTest {
    @Test
    fun statementsCoverTheFiveSpecPoints() {
        val text = thirdPartyPrivacyStatements().joinToString("\n")
        listOf("无账户", "无自动云备份", "自行配置", "API Key", "确认").forEach { keyword ->
            assertTrue(text.contains(keyword), "隐私声明缺少关键词覆盖: $keyword")
        }
        assertEquals(5, thirdPartyPrivacyStatements().size)
    }

    @Test
    fun noStatementLeaksSecretHandlingContradictions() {
        // 「应用内可查看 Key」一类文案会直接违反阶段 0 安全基线，此处反向钉死。
        val text = thirdPartyPrivacyStatements().joinToString("\n")
        assertTrue(!text.contains("可查看"), "隐私声明不得承诺应用内可查看密钥")
    }
}
