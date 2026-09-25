package com.example.englishlearning.reading

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.ConfirmationRequirement
import com.example.englishlearning.ai.requiredConfirmation
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiChatRequestBuilder
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleSource
import kotlinx.coroutines.CancellationException
import java.time.Instant

sealed interface GenerateArticleResult {
    data class Reused(val article: Article) : GenerateArticleResult
    data class Generated(val article: Article) : GenerateArticleResult

    /** 需要用户先给出站确认；界面拿 host 与 payloadKind 去弹确认框。 */
    data class NeedsConfirmation(val host: String, val payloadKind: AiPayloadKind) : GenerateArticleResult
    data class Failed(val failure: AiFailure) : GenerateArticleResult

    /**
     * Profile 或密钥不可用。与 [Failed] 分开：处置动作是「去配置」，不是「重试」。
     * [InvalidEndpoint] 表示 Endpoint 连域名都拆不出来——在保存时就被校验过，这里
     * 再失败只可能是数据被绕过校验写入，同样引导用户回配置页。
     */
    data class NotConfigured(val reason: NotConfiguredReason) : GenerateArticleResult

    /** 校验通过、请求也成功，但存储失败（Room 关闭库等）。与 AI 失败是两个维度，不得混用。 */
    data object StorageFailed : GenerateArticleResult
}

enum class NotConfiguredReason { NoProfile, NoKey, ProfileUnreadable, InvalidEndpoint }

/**
 * 文章生成用例：复用 / 换一篇 / 出站确认 / 失败映射。
 *
 * 编排顺序是需求本身，不可重排：**复用读（本地）→ 配置检查 → 出站确认（发请求前）→
 * 构造请求 → 出站 → 解析 → 质量闸 → 派生高亮 → 落库**。确认在任何网络字节出去之前；
 * 失败的任何分支都不写库（不产出伪造的「成功」）。
 *
 * `ArticleGenerationRequest` 是唯一输入：目标词卡与排除词由调用方（阅读入口）组装，
 * 本用例不重复收集——这保证了「提示词只由请求决定」这一纯函数性质。
 * Key 以 `CharArray` 流转，在 `finally` 中清零；它只进 `Authorization` 头，
 * 永不进落库文章与参数摘要。
 */
class GenerateArticleUseCase(
    private val profiles: AiProfileRepository,
    private val secrets: AiProfileSecretUseCase,
    private val transport: AiHttpTransport,
    private val articles: ArticleRepository,
    private val ids: ArticleIdFactory,
    private val clock: () -> Instant,
) {
    suspend fun findReusable(request: ArticleGenerationRequest): Result<Article?> =
        articles.findLatest(request.profileId, request.localDate, request.wordBookId, request.articleType, request.length.tier)

    suspend fun generate(
        request: ArticleGenerationRequest,
        confirmedTextHost: String?,
        regenerate: Boolean = false,
    ): GenerateArticleResult {
        if (!regenerate) {
            findReusable(request).getOrElse { return GenerateArticleResult.StorageFailed }
                ?.let { return GenerateArticleResult.Reused(it) }
        }

        // AI 配置是设备级的（AiProfile.profileId 是配置自己的主键，与学习 profileId 无关）。
        // V1 默认策略：按保存顺序取第一套存有 Key 的配置；「默认 Profile 选择」（F2-02 残留）
        // 落地后换成用户指定的一套——领域模型不用动。
        val deviceProfiles = profiles.list().getOrElse {
            return GenerateArticleResult.NotConfigured(NotConfiguredReason.ProfileUnreadable)
        }
        if (deviceProfiles.isEmpty()) {
            return GenerateArticleResult.NotConfigured(NotConfiguredReason.NoProfile)
        }
        var selectedProfile: AiProfile? = null
        var selectedKey: CharArray? = null
        for (candidate in deviceProfiles) {
            val candidateKey = secrets.loadKey(candidate).getOrNull()
            if (candidateKey == null || candidateKey.isEmpty()) continue
            selectedProfile = candidate
            selectedKey = candidateKey
            break
        }
        val profile = selectedProfile
            ?: return GenerateArticleResult.NotConfigured(NotConfiguredReason.NoKey)
        val key = checkNotNull(selectedKey)

        try {
            // 出站确认在任何字节发出去之前。Endpoint 不合法时连「要确认哪个域名」都答不出。
            val confirmation = requiredConfirmation(profile, AiPayloadKind.Text, confirmedTextHost)
                .getOrElse { return GenerateArticleResult.NotConfigured(NotConfiguredReason.InvalidEndpoint) }
            when (confirmation) {
                is ConfirmationRequirement.Required ->
                    return GenerateArticleResult.NeedsConfirmation(confirmation.host, confirmation.payloadKind)
                is ConfirmationRequirement.Satisfied -> Unit
            }

            val prompt = ArticlePromptPolicy.build(request)
            val outbound = AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt, key)
                .getOrElse { return GenerateArticleResult.NotConfigured(NotConfiguredReason.InvalidEndpoint) }

            return when (val response = transport.send(outbound)) {
                is AiHttpResult.Responded -> handleResponse(request, profile, response)
                AiHttpResult.NetworkUnavailable -> GenerateArticleResult.Failed(AiFailure.NetworkUnavailable)
                AiHttpResult.TimedOut -> GenerateArticleResult.Failed(AiFailure.Timeout)
                AiHttpResult.ResponseTooLarge -> GenerateArticleResult.Failed(AiFailure.InvalidResponse)
                // 用户取消就是取消：吞成网络失败会把没坏的网络说成坏了。
                AiHttpResult.Cancelled -> throw CancellationException("generation cancelled")
            }
        } finally {
            key.fill('\u0000')
        }
    }

    private suspend fun handleResponse(
        request: ArticleGenerationRequest,
        profile: AiProfile,
        response: AiHttpResult.Responded,
    ): GenerateArticleResult {
        // 状态码先于解析（ArticleResponseParser 的约定）：401/429/5xx 的响应体不该被解析。
        val raw = ArticleResponseParser.parse(response.response.statusCode, response.response.body).getOrElse { thrown ->
            return GenerateArticleResult.Failed((thrown as? AiException)?.failure ?: AiFailure.InvalidResponse)
        }
        val validated = ArticleQualityPolicy.validate(raw, ArticleQualityPolicy.forAiGeneration(request.length))
            .getOrElse { return GenerateArticleResult.Failed(AiFailure.InvalidResponse) }

        // 高亮永远本地派生（spec F2-03）：这里只用 derive 拿 coveredLemmas 的稳定顺序，不落坐标。
        // coveredLemmas = 请求词表顺序里、真正出现在正文中的 lemma（与未覆盖词互补）。
        val coverage = ArticleHighlightPolicy.derive(validated.englishText, request.targetCards)
        val covered = coverage.highlights.mapTo(mutableSetOf()) { it.lemma }
        val coveredLemmas = request.targetCards.map { it.lemma }.filter { it in covered }

        val article = Article(
            articleId = ids.newId(),
            profileId = request.profileId,
            localDate = request.localDate,
            activeWordBookId = request.wordBookId,
            articleType = request.articleType,
            lengthTier = request.length.tier,
            version = 0, // saveNewVersion 分配真实版本号
            title = validated.title,
            englishText = validated.englishText,
            chineseText = validated.chineseText,
            generatedAtEpochMillis = clock().toEpochMilli(),
            coveredLemmas = coveredLemmas,
            source = ArticleSource.AiGenerated(
                modelName = profile.model,
                parameterSummary = parameterSummary(profile),
            ),
        )
        return articles.saveNewVersion(article).fold(
            onSuccess = { GenerateArticleResult.Generated(it) },
            onFailure = { GenerateArticleResult.StorageFailed },
        )
    }

    /**
     * 落库的生成参数摘要，供事后排查「当时用了什么配置」。**绝不拼入 endpoint 与 Key**：
     * 摘要会随文章持久化，任何运行时数据拼接都可能把敏感信息带进数据库。
     */
    private fun parameterSummary(profile: AiProfile): String {
        val p = profile.advancedParameters
        return "model=${profile.model} temperature=${p.temperature} top_p=${p.topP} " +
            "max_tokens=${p.maxTokens} timeout_seconds=${p.timeoutSeconds} template=${p.systemPromptTemplateId}"
    }
}
