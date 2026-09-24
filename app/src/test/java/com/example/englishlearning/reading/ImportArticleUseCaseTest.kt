package com.example.englishlearning.reading

import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportArticleUseCaseTest {
    private class FakeArticleRepository : ArticleRepository {
        val stored = mutableListOf<Article>()
        override suspend fun saveNewVersion(article: Article): Result<Article> {
            val saved = article.copy(version = stored.size + 1)
            stored += saved
            return Result.success(saved)
        }

        override suspend fun findLatest(profileId: String, localDate: String, activeWordBookId: String, articleType: ArticleType, lengthTier: ArticleLengthTier) = Result.success<Article?>(null)
        override suspend fun findHistory(profileId: String) = Result.success(stored.toList())
        override suspend fun findBySourceUrl(url: String) = Result.success<Article?>(null)
    }

    private class FakePlans(private val plan: TodayPlan?) : TodayPlanRepository {
        override suspend fun find(profileId: String, localDate: LocalDate): TodayPlanResult =
            plan?.takeIf { it.profileId == profileId && it.localDate == localDate }
                ?.let { TodayPlanResult.Ready(it) }
                ?: TodayPlanResult.NotFound

        override suspend fun findLatest(profileId: String): TodayPlanResult =
            plan?.let { TodayPlanResult.Ready(it) } ?: TodayPlanResult.NotFound

        override suspend fun saveIfAbsent(plan: TodayPlan): TodayPlanResult = TodayPlanResult.Ready(plan)
    }

    private class FakeEvents(private val completed: List<String>) : LearningEventRepository {
        override suspend fun append(event: LearningEvent, nextState: CardReviewState) = AppendEventResult.Appended(false)
        override suspend fun findEvent(eventId: String) = RepositoryResult.Success<LearningEvent?>(null)
        override suspend fun findCardState(cardId: String) = RepositoryResult.Success<CardReviewState?>(null)
        override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)
        override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(completed)
        override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())
        override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
    }

    private class FakeCards : WordCardSource {
        override suspend fun cardIds(wordBookId: String) = emptyList<String>()
        override suspend fun cards(cardIds: List<String>) = cardIds.mapNotNull { id ->
            when (id) {
                "c1" -> WordCard("c1", "cet4", "wilbur", "", "", "")
                "c2" -> WordCard("c2", "cet4", "airplane", "", "", "", inflections = listOf("airplanes"))
                else -> null
            }
        }
    }

    private val plan = TodayPlan(
        planId = "plan-1",
        profileId = "p1",
        localDate = LocalDate.parse("2026-09-24"),
        zoneId = "Asia/Shanghai",
        activeWordBookId = "cet4",
        newTarget = 5,
        dueTarget = 5,
        newCardIds = listOf("c1"),
        dueCardIds = listOf("c2"),
        ruleVersion = "v1",
        generatedAt = Instant.EPOCH,
    )
    private val context = ArticleContext(profileId = "p1", localDate = "2026-09-24", activeWordBookId = "cet4")

    private fun useCase(
        repository: FakeArticleRepository,
        completed: List<String> = listOf("c1", "c2"),
        plans: TodayPlanRepository = FakePlans(plan),
    ) = ImportArticleUseCase(
        articles = repository,
        plans = plans,
        events = FakeEvents(completed),
        cards = FakeCards(),
        ids = ArticleIdFactory { "article-1" },
        clock = { Instant.ofEpochMilli(1_000L) },
    )

    /** 44 个词、含今日完成词 wilbur 与 airplane。 */
    private fun englishBody(): String = "Wilbur built the first airplane " + List(40) { "word" }.joinToString(" ")

    @Test
    fun importsAWellFormedEnglishArticle() = runTest {
        val repository = FakeArticleRepository()

        val result = useCase(repository).import("My Flight Notes", englishBody(), context)

        val imported = result as ImportArticleResult.Imported
        assertEquals(ArticleSource.UserImported, imported.article.source)
        assertEquals("", imported.article.chineseText)
        assertEquals(ArticleType.STORY, imported.article.articleType)
        assertEquals("p1", imported.article.profileId)
        assertEquals("2026-09-24", imported.article.localDate)
        assertEquals("cet4", imported.article.activeWordBookId)
        assertEquals(1, imported.article.version)
        assertEquals(1, repository.stored.size)
    }

    @Test
    fun rejectsAScriptTag() = runTest {
        val repository = FakeArticleRepository()

        val result = useCase(repository).import("Sneaky", List(50) { "word" }.joinToString(" ") + " <script>alert(1)</script>", context)

        assertEquals(ImportArticleResult.Rejected(ImportRejection.DangerousMarkup), result)
        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun rejectsChinesePastedAsTheBody() = runTest {
        val repository = FakeArticleRepository()

        val result = useCase(repository).import("中文标题", "这是一段中文正文。".repeat(50), context)

        assertEquals(ImportArticleResult.Rejected(ImportRejection.NotEnglish), result)
        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun rejectsABodyThatIsTooShort() = runTest {
        val repository = FakeArticleRepository()

        val result = useCase(repository).import("Title", "way too short", context)

        assertEquals(ImportArticleResult.Rejected(ImportRejection.BodyTooShort), result)
        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun rejectsABodyOverTheCap() = runTest {
        val repository = FakeArticleRepository()

        val result = useCase(repository).import("Title", List(1201) { "word" }.joinToString(" "), context)

        assertEquals(ImportArticleResult.Rejected(ImportRejection.BodyTooLong), result)
        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun rejectsABlankTitle() = runTest {
        val repository = FakeArticleRepository()

        val result = useCase(repository).import("   ", englishBody(), context)

        assertEquals(ImportArticleResult.Rejected(ImportRejection.BlankTitle), result)
        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun importedArticleStillGetsHighlightsFromTodaysPlan() = runTest {
        val repository = FakeArticleRepository()

        val result = useCase(repository).import("My Flight Notes", englishBody(), context) as ImportArticleResult.Imported

        // 高亮派生与其他来源一致：今日完成词里出现在正文中的 lemma 进 coveredLemmas。
        assertTrue(result.article.coveredLemmas.contains("airplane"))
        assertTrue(result.article.coveredLemmas.contains("wilbur"))
    }

    @Test
    fun importsEvenWhenNoPlanExistsForToday() = runTest {
        val repository = FakeArticleRepository()

        val result = useCase(repository, completed = emptyList(), plans = FakePlans(null))
            .import("My Flight Notes", englishBody(), context) as ImportArticleResult.Imported

        // 没有今日计划也能导入：coveredLemmas 为空，导入不因词表缺失而失败。
        assertTrue(result.article.coveredLemmas.isEmpty())
        assertEquals(1, repository.stored.size)
    }

    @Test
    fun importedArticleHasNoOutboundDependency() {
        // 「用户导入不联网」必须是可断言的事实：构造函数里不允许出现任何网络类型。
        val paramTypes = ImportArticleUseCase::class.java.constructors.flatMap { it.parameterTypes.toList() }
        val networkTypes = paramTypes.filter { it.name.startsWith("com.example.englishlearning.ai.net") }
        assertTrue(networkTypes.isEmpty(), "constructor must not take network types, found: $networkTypes")
    }
}
