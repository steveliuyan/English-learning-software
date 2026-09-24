package com.example.englishlearning.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleSourceRegistryTest {
    @Test
    fun registryOnlyContainsSourcesWithALedgerEntry() {
        // 没有台账条目的来源不得进入注册表（AGENTS.md：未通过许可核验的来源不得接入）。
        // JVM 测试的工作目录是模块目录 app/，台账在仓库根的 docs/ 下。
        val ledger = java.io.File("../docs/third-party-notices.md").readText()
        assertTrue(ArticleSourceRegistry.all.isNotEmpty(), "registry must not be empty")
        ArticleSourceRegistry.all.forEach { source ->
            assertTrue(
                ledger.contains("## ${source.ledgerEntryId}"),
                "ledger entry missing for ${source.sourceId}: ## ${source.ledgerEntryId}",
            )
        }
    }

    @Test
    fun byIdReturnsTheRegisteredSource() {
        assertEquals("voa-learning-english", ArticleSourceRegistry.byId("voa-learning-english")?.sourceId)
        assertNull(ArticleSourceRegistry.byId("not-registered"))
    }

    @Test
    fun rejectsAUrlWhoseHostIsNotWhitelisted() {
        assertNull(ArticleSourceRegistry.match("https://evil.test/a/x.html"))
    }

    @Test
    fun rejectsHttpEvenForAWhitelistedHost() {
        // 不因为 host 对就放行：http 明文传输会泄露阅读行为。
        assertNull(ArticleSourceRegistry.match("http://learningenglish.voanews.com/a/x.html"))
    }

    @Test
    fun rejectsALookalikeHost() {
        // 必须是精确相等，不是后缀匹配：一串 endsWith(host) 会放过 …voanews.com.evil.test。
        assertNull(ArticleSourceRegistry.match("https://learningenglish.voanews.com.evil.test/a/x.html"))
    }

    @Test
    fun rejectsAWhitelistedHostWithANonArticlePath() {
        assertNull(ArticleSourceRegistry.match("https://learningenglish.voanews.com/p/5373.html"))
    }

    @Test
    fun acceptsAWhitelistedArticleUrl() {
        // 实测样本 URL（2026-09-24 抓取）：/a/ 前缀 + .html。
        val matched = ArticleSourceRegistry.match(
            "https://learningenglish.voanews.com/a/wilbur-and-orville-wright-the-first-airplane/7998765.html",
        )
        assertEquals("voa-learning-english", matched?.sourceId)
    }
}
