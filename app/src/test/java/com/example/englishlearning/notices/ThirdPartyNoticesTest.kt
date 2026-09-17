package com.example.englishlearning.notices

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ThirdPartyNoticesTest {
    @Test
    fun `every version catalog library and plugin alias maps to a notice ledger entry`() {
        val catalog = File("../gradle/libs.versions.toml").readText()
        val noticeIds =
            File("../docs/third-party-notices.md")
                .readLines()
                .filter { it.startsWith("## ") }
                .map { it.removePrefix("## ").trim() }
                .toSet()
        val aliasesBySection = versionCatalogAliasesBySection(catalog)
        val missingLedgerIds =
            aliasesBySection.flatMap { (section, aliases) ->
                aliases.filterNot { alias -> noticeIds.contains(noticeIdFor(section, alias)) }
            }

        assertTrue(missingLedgerIds.isEmpty(), "Missing notice entries: $missingLedgerIds")
    }

    @Test
    fun `notice entry missing data flow is rejected`() {
        assertContains(
            verifyThirdPartyNotices(validNotice("")),
            NoticeValidationError.MissingField("androidx-room", "数据流"),
        )
    }

    @Test
    fun `notice entry with blank data flow is rejected`() {
        assertContains(
            verifyThirdPartyNotices(validNotice("   ")),
            NoticeValidationError.MissingField("androidx-room", "数据流"),
        )
    }

    @Test
    fun `notice entry with blank license is rejected`() {
        assertContains(
            verifyThirdPartyNotices(validNotice("本地设备内数据，不传输网络", "   ")),
            NoticeValidationError.MissingField("androidx-room", "许可证"),
        )
    }

    private fun versionCatalogAliasesBySection(catalog: String): Map<String, Set<String>> =
        catalog
            .lineSequence()
            .fold(mutableMapOf<String, MutableSet<String>>() to "") { (aliasesBySection, section), line ->
                when {
                    line.matches(Regex("\\[[a-z]+]")) -> aliasesBySection to line.removeSurrounding("[", "]")
                    section in setOf("libraries", "plugins") &&
                        line.matches(Regex("[a-z0-9-]+\\s*=\\s*\\{.*")) -> {
                        aliasesBySection.getOrPut(section) { mutableSetOf() } += line.substringBefore("=").trim()
                        aliasesBySection to section
                    }
                    else -> aliasesBySection to section
                }
            }
            .first

    private fun noticeIdFor(
        section: String,
        alias: String,
    ): String =
        if (section == "plugins") {
            "plugin-$alias"
        } else {
            alias
        }

    private fun validNotice(
        dataFlow: String,
        license: String = "Apache-2.0",
    ): String =
        """
        ## androidx-room
        - 名称: Room
        - 版本: 2.x
        - 许可证: $license
        - 用途: 本地关系数据
        - 数据流: $dataFlow
        - NOTICE 位置: 本文件
        - 替代方案: SQLiteDatabase
        - 商业分发结论: 可商业分发
        """.trimIndent()
}
