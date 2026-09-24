package com.example.englishlearning.reading

/**
 * 注册表里的一个外刊来源。**白名单即台账条目**：[ledgerEntryId] 必须指向
 * `docs/third-party-notices.md` 中已完成核验的条目——没有台账的来源不得登记
 * （`ArticleSourceRegistryTest.registryOnlyContainsSourcesWithALedgerEntry` 会读真台账锁死）。
 */
data class RegisteredArticleSource(
    val sourceId: String,
    val displayName: String,
    val host: String,
    val feedPath: String,
    val articlePathPrefix: String,
    val attributionText: String,
    val licenseNote: String,
    val ledgerEntryId: String,
)

/**
 * 抓取目标的**唯一入口**。用户提供的任何 URL 一律不接受；候选链接只从来源自己的
 * RSS 索引里取，且发请求前再经 [match] 校验一次。
 *
 * [match] 的三条规则都不可放松：
 * 1. scheme 必须 `https`——不因 host 对就放行 http（明文会泄露阅读行为）；
 * 2. host 与白名单**精确相等**——后缀匹配会放过 `…voanews.com.evil.test`；
 * 3. path 必须以该来源的文章前缀开头——RSS、首页等一律不算文章。
 * 任一失败返回 `null`，调用方不得发请求。
 */
object ArticleSourceRegistry {
    // zoneid=1579（科学与技术）经 2026-09-24 代理实测可用；zoneid=965 返回空 channel，不要用。
    private val voaLearningEnglish = RegisteredArticleSource(
        sourceId = "voa-learning-english",
        displayName = "VOA Learning English",
        host = "learningenglish.voanews.com",
        feedPath = "/rss/?count=20&zoneid=1579",
        articlePathPrefix = "/a/",
        attributionText = "learningenglish.voanews.com",
        licenseNote = "VOA Learning English 文本属公有领域，可转载须署名 learningenglish.voanews.com",
        ledgerEntryId = "voa-learning-english",
    )

    val all: List<RegisteredArticleSource> = listOf(voaLearningEnglish)

    fun byId(sourceId: String): RegisteredArticleSource? = all.firstOrNull { it.sourceId == sourceId }

    fun match(url: String): RegisteredArticleSource? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        if (url.substring(0, schemeEnd) != "https") return null
        val rest = url.substring(schemeEnd + 3)
        val pathStart = rest.indexOf('/')
        if (pathStart < 0) return null
        val host = rest.substring(0, pathStart)
        val path = rest.substring(pathStart)
        val source = all.firstOrNull { host == it.host } ?: return null
        return if (path.startsWith(source.articlePathPrefix)) source else null
    }
}
