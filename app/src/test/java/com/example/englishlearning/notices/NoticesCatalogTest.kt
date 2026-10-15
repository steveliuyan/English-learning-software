package com.example.englishlearning.notices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

/**
 * SP-D 许可页数据层：与 root `verifyThirdPartyNotices` 同一 markdown 口径的展示侧解析器测试。
 * 真实台账用相对路径读取，与 ThirdPartyNoticesTest 读 libs.versions.toml 的既有模式一致。
 */
class NoticesCatalogTest {
    @Test
    fun parsesEntriesWithKeyedFieldsAndSkipsPreamble() {
        val markdown = """
            # 台账说明
            前言不是条目。
            ## lib-a
            - 名称: Library A
            - 许可证: Apache-2.0
            ## lib-b
            - 名称: Library B
            - 许可证: MIT
        """.trimIndent()
        val notices = parseThirdPartyNotices(markdown)
        assertEquals(2, notices.size)
        assertEquals("Library A", notices[0].field("名称"))
        assertEquals("MIT", notices[1].field("许可证"))
        assertEquals("", notices[1].field("替代方案"))
    }

    @Test
    fun realLedgerParsesWithAllRequiredFieldsPopulated() {
        val ledger = File("../docs/third-party-notices.md").readText(Charsets.UTF_8)
        val notices = parseThirdPartyNotices(ledger)
        assertTrue(notices.size >= 50, "解析条目数异常: ${notices.size}")
        val required = listOf("名称", "版本", "许可证", "用途", "数据流", "NOTICE 位置", "替代方案", "商业分发结论")
        notices.forEach { notice ->
            required.forEach { key ->
                assertTrue(notice.field(key).isNotEmpty(), "${notice.id} 缺字段 $key")
            }
        }
    }

    @Test
    fun malformedLinesAreIgnoredNotCrashing() {
        val markdown = "## only-entry\nplain line\n- 无冒号行\n- 名称: Ok\n- :空键\n"
        val notices = parseThirdPartyNotices(markdown)
        assertEquals(1, notices.size)
        assertEquals("Ok", notices[0].field("名称"))
    }
}
