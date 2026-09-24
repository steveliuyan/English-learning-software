package com.example.englishlearning.reading

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import kotlinx.coroutines.CancellationException
import java.time.Instant

/**
 * 抓取落库的上下文：哪位用户、哪一天、哪本词书触发的抓取。外刊文章不消费词表
 * （那是 AI 生成的语义），但复用键与归属仍然需要这三元组。
 */
data class FetchContext(
    val profileId: String,
    val localDate: String,
    val activeWordBookId: String,
)

sealed interface FetchArticleResult {
    data class Fetched(val article: Article) : FetchArticleResult

    /** RSS 索引列表。发请求前已通过注册表校验。 */
    data class Listed(val sourceId: String, val items: List<FeedItem>) : FetchArticleResult

    /** 目标 URL 未通过白名单，请求**没有发出**。 */
    data class RejectedTarget(val reason: RejectedTargetReason) : FetchArticleResult

    data class Failed(val failure: FetchFailure) : FetchArticleResult
}

enum class RejectedTargetReason { NotWhitelisted, NotHttps, WrongPath }

enum class FetchFailure {
    NetworkUnavailable,

    /** 站点返回非 2xx、超时或响应超限——「这个来源此刻拿不到」。 */
    SourceUnreachable,

    /** 页面里没有正文容器（站点改版或链接失效）。 */
    BodyNotFound,

    BodyTooShort,

    /** 语言、安全或长度校验未通过（复用 `ArticleQualityPolicy`，不另造校验链路）。 */
    QualityRejected,
}

/**
 * 外刊抓取用例。
 *
 * 白名单是**双重的**：`list` 的来源必须登记在注册表；`fetch` 的候选链接在**发请求前**
 * 再经 `ArticleSourceRegistry.match` 校验一次——即使 RSS 里被注入了外站链接，这里也过不去。
 * 抓取请求一律 GET、零凭据：站点的公开内容不需要任何身份，带上密钥反而是泄露。
 *
 * 与 AI 生成共用同一套 `ArticleQualityPolicy`（参数不同而已，AGENTS.md：抓取内容同属
 * 不可信输入），不新建校验链路。落库的文章 `chineseText` 为空串——不伪造译文，
 * 阅读页由 Task E 显示「该来源无中文翻译」。
 */
class FetchArticleUseCase(
    private val transport: AiHttpTransport,
    private val articles: ArticleRepository,
    private val quality: ArticleQualityPolicy,
    private val ids: ArticleIdFactory,
    private val clock: () -> Instant,
) {
    /**
     * 外刊文章的接受词数区间。长度由站点内容决定，**不套词书区间**（那会给 AI 生成的
     * 短文用）；语言判定、危险标记、单字段上限等其余检查全部照走。
     */
    private val fetchAcceptableWords = ArticleLengthPolicy.Resolved(
        tier = ArticleLengthTier.LONG,
        targetWords = 100..2000,
        acceptedWords = 60..2000,
    )

    suspend fun list(sourceId: String): FetchArticleResult {
        val source = ArticleSourceRegistry.byId(sourceId)
            ?: return FetchArticleResult.RejectedTarget(RejectedTargetReason.NotWhitelisted)
        return when (val response = sendGet("https://${source.host}${source.feedPath}")) {
            is AiHttpResult.Responded -> ArticleFeedParser.parse(response.response.body).fold(
                onSuccess = { items -> FetchArticleResult.Listed(sourceId, items) },
                onFailure = { FetchArticleResult.Failed(FetchFailure.SourceUnreachable) },
            )
            else -> failedResult(response)
        }
    }

    suspend fun fetch(sourceId: String, item: FeedItem, context: FetchContext): FetchArticleResult {
        val source = ArticleSourceRegistry.byId(sourceId)
            ?: return FetchArticleResult.RejectedTarget(RejectedTargetReason.NotWhitelisted)
        // 白名单第二道闸：RSS 里的链接在发请求前再校验一次，用户/RSS 注入的外站 URL 到此为止。
        val reason: RejectedTargetReason? = if (!item.articleUrl.startsWith("https://")) {
            RejectedTargetReason.NotHttps
        } else {
            val matched = ArticleSourceRegistry.match(item.articleUrl)
            when {
                matched == null -> RejectedTargetReason.NotWhitelisted
                matched.sourceId != source.sourceId -> RejectedTargetReason.WrongPath
                else -> null
            }
        }
        if (reason != null) return FetchArticleResult.RejectedTarget(reason)

        // 同一 URL 已经抓过就直接复用：跨计划读同一篇外刊不该重复请求站点。
        articles.findBySourceUrl(item.articleUrl).getOrNull()?.let { existing ->
            return FetchArticleResult.Fetched(existing)
        }

        return when (val response = sendGet(item.articleUrl)) {
            is AiHttpResult.Responded -> {
                if (response.response.statusCode !in 200..299) {
                    return FetchArticleResult.Failed(FetchFailure.SourceUnreachable)
                }
                fetchAndStore(source, item, context, response.response.body)
            }
            else -> failedResult(response)
        }
    }

    private suspend fun fetchAndStore(
        source: RegisteredArticleSource,
        item: FeedItem,
        context: FetchContext,
        body: String,
    ): FetchArticleResult {
        val parsed = ArticlePageParser.parse(body).getOrElse {
            return FetchArticleResult.Failed(FetchFailure.BodyNotFound)
        }
        val validated = quality.validateFetched(RawArticle(parsed.title, parsed.body, ""), fetchAcceptableWords)
            .getOrElse { return FetchArticleResult.Failed(FetchFailure.QualityRejected) }

        val article = Article(
            articleId = ids.newId(),
            profileId = context.profileId,
            localDate = context.localDate,
            activeWordBookId = context.activeWordBookId,
            articleType = ArticleType.NEWS,
            lengthTier = fetchAcceptableWords.tier,
            version = 0, // saveNewVersion 分配真实版本号
            title = validated.title,
            englishText = validated.englishText,
            chineseText = "",
            generatedAtEpochMillis = clock().toEpochMilli(),
            coveredLemmas = emptyList(),
            source = ArticleSource.WebFetched(
                sourceId = source.sourceId,
                displayName = source.displayName,
                articleUrl = item.articleUrl,
                licenseNote = source.licenseNote,
                attributionText = source.attributionText,
            ),
        )
        return articles.saveNewVersion(article).fold(
            onSuccess = { FetchArticleResult.Fetched(it) },
            onFailure = { FetchArticleResult.Failed(FetchFailure.SourceUnreachable) },
        )
    }

    private suspend fun sendGet(url: String): AiHttpResult =
        transport.send(AiHttpRequest(url = url, headers = emptyMap(), body = "", timeoutSeconds = 30, method = "GET"))

    private fun failedResult(response: AiHttpResult): FetchArticleResult = when (response) {
        is AiHttpResult.Responded -> FetchArticleResult.Failed(FetchFailure.SourceUnreachable)
        AiHttpResult.NetworkUnavailable -> FetchArticleResult.Failed(FetchFailure.NetworkUnavailable)
        AiHttpResult.TimedOut -> FetchArticleResult.Failed(FetchFailure.SourceUnreachable)
        AiHttpResult.ResponseTooLarge -> FetchArticleResult.Failed(FetchFailure.SourceUnreachable)
        // 用户取消就是取消：吞成网络失败会把没坏的网络说成坏了，协程取消必须继续传播。
        AiHttpResult.Cancelled -> throw CancellationException("fetch cancelled")
    }
}
