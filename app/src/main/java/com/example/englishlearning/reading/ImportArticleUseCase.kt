package com.example.englishlearning.reading

import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import java.time.Instant
import java.time.LocalDate

sealed interface ImportArticleResult {
    data class Imported(val article: Article) : ImportArticleResult

    /** 文本未通过校验，仓库**没有写入**。原因具体到检查项，供界面逐条提示。 */
    data class Rejected(val reason: ImportRejection) : ImportArticleResult

    /** 校验通过但存储失败（Room 关闭库等）；文本本身没有问题。 */
    data class Failed(val failure: ImportStorageFailure) : ImportArticleResult
}

enum class ImportRejection { BlankTitle, BlankBody, BodyTooShort, BodyTooLong, NotEnglish, DangerousMarkup }

enum class ImportStorageFailure { StorageUnavailable }

/**
 * 用户粘贴导入。**全程不离开设备**：构造函数不含任何网络类型（
 * `importedArticleHasNoOutboundDependency` 用反射锁死），文本校验与 AI 生成、外刊抓取
 * 共用 `ArticleQualityPolicy` 的同一套检查，只换 `forImported` 这份约束数据。
 *
 * 没有译文也不伪造：`chineseText` 落空串，阅读页与其他无译文来源同样提示。
 * 高亮派生与其他来源一致：今日计划已完成的词卡里、出现在正文中的 lemma 进
 * `coveredLemmas`；计划或词表读不到就当空词表，导入不因此失败。
 */
class ImportArticleUseCase(
    private val articles: ArticleRepository,
    private val plans: TodayPlanRepository,
    private val events: LearningEventRepository,
    private val cards: WordCardSource,
    private val ids: ArticleIdFactory,
    private val clock: () -> Instant,
) {
    suspend fun import(title: String, body: String, context: ArticleContext): ImportArticleResult {
        val raw = RawArticle(title, body, "")
        // validate 是唯一闸门；失败时用 firstRejection 诊断出具体原因（同一套检查，不重复实现）。
        val validated = ArticleQualityPolicy.validate(raw, ArticleQualityPolicy.forImported).getOrElse {
            val rejection = checkNotNull(ArticleQualityPolicy.firstRejection(raw, ArticleQualityPolicy.forImported)) {
                "validate failed but firstRejection reported no rejection"
            }
            return ImportArticleResult.Rejected(rejection.toImportRejection())
        }

        val todayLemmas = completedLemmasOfToday(context)
        val coverage = ArticleHighlightPolicy.derive(validated.englishText, todayLemmas)

        val article = Article(
            articleId = ids.newId(),
            profileId = context.profileId,
            localDate = context.localDate,
            activeWordBookId = context.activeWordBookId,
            // 粘贴文本主题不限，STORY 是中性桶；同日重复导入走 saveNewVersion 的版本递增。
            articleType = ArticleType.STORY,
            lengthTier = ArticleLengthTier.LONG,
            version = 0, // saveNewVersion 分配真实版本号
            title = validated.title,
            englishText = validated.englishText,
            chineseText = validated.chineseText,
            generatedAtEpochMillis = clock().toEpochMilli(),
            coveredLemmas = coverage.highlights.map { it.lemma }.distinct(),
            source = ArticleSource.UserImported,
        )
        return articles.saveNewVersion(article).fold(
            onSuccess = { ImportArticleResult.Imported(it) },
            onFailure = { ImportArticleResult.Failed(ImportStorageFailure.StorageUnavailable) },
        )
    }


    /** 今日计划的已完成词卡。计划不存在或存储读不到 → 空表，导入照常。 */
    private suspend fun completedLemmasOfToday(context: ArticleContext) =
        when (val planResult = plans.find(context.profileId, LocalDate.parse(context.localDate))) {
            is TodayPlanResult.Ready -> {
                val cardIds = when (val completed = events.completedCardIds(planResult.plan.planId)) {
                    is RepositoryResult.Success -> completed.value
                    is RepositoryResult.Failure -> emptyList()
                }
                cards.cards(cardIds)
            }
            else -> emptyList()
        }

    private fun ArticleTextRejection.toImportRejection(): ImportRejection = when (this) {
        ArticleTextRejection.BlankTitle -> ImportRejection.BlankTitle
        ArticleTextRejection.BlankBody, ArticleTextRejection.BlankChineseBody -> ImportRejection.BlankBody
        ArticleTextRejection.TooFewWords -> ImportRejection.BodyTooShort
        // TooManyChars/ControlCharacter 对粘贴文本也意味着「超长/异常内容」，并入超长；
        // NotChineseBody 在 requireTranslation=false 下不可达，保穷尽性。
        ArticleTextRejection.TooManyWords, ArticleTextRejection.TooManyChars, ArticleTextRejection.ControlCharacter -> ImportRejection.BodyTooLong
        ArticleTextRejection.NotEnglish -> ImportRejection.NotEnglish
        ArticleTextRejection.NotChineseBody -> ImportRejection.BodyTooLong
        ArticleTextRejection.DangerousMarkup -> ImportRejection.DangerousMarkup
    }
}
