package com.example.englishlearning.learning

import com.example.englishlearning.core.time.FixedClockProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

class GetOrCreateTodayPlanUseCaseTest {
    private val instant = Instant.parse("2026-09-19T15:30:00Z")
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun `existing local-date plan returns immediately without candidate calls`() = runTest {
        val existing = plan(localDate = LocalDate.of(2026, 9, 19))
        val planRepository = FakeTodayPlanRepository(findResult = TodayPlanResult.Ready(existing))
        val cardSource = FakePlanCardSource()

        val result = useCase(planRepository, cardSource)("profile")

        assertEquals(TodayPlanResult.Ready(existing), result)
        assertEquals(0, cardSource.dueCalls)
        assertEquals(0, cardSource.newCalls)
        assertEquals(0, planRepository.saveCalls)
    }

    @Test
    fun `new snapshot keeps all deduplicated due IDs and lowers new target to returned cards`() = runTest {
        val planRepository = FakeTodayPlanRepository(findResult = null)
        val cardSource = FakePlanCardSource(
            dueIds = listOf("due-1", "due-2", "due-1", "due-3"),
            newIds = listOf("new-1", "new-1", "new-2"),
        )

        val result = useCase(planRepository, cardSource)("profile")

        val created = (result as TodayPlanResult.Ready).plan
        assertEquals(listOf("due-1", "due-2", "due-3"), created.dueCardIds)
        assertEquals(3, created.dueTarget)
        assertEquals(listOf("new-1", "new-2"), created.newCardIds)
        assertEquals(2, created.newTarget)
        assertEquals("book", cardSource.dueWordBookId)
        assertEquals(instant, cardSource.dueNow)
        assertEquals(5, cardSource.newLimit)
    }

    @Test
    fun `empty new candidates create a valid plan with zero new target`() = runTest {
        val planRepository = FakeTodayPlanRepository(findResult = null)
        val cardSource = FakePlanCardSource(dueIds = listOf("due-1"), newIds = emptyList())

        val result = useCase(planRepository, cardSource)("profile")

        val created = (result as TodayPlanResult.Ready).plan
        assertEquals(emptyList(), created.newCardIds)
        assertEquals(0, created.newTarget)
    }

    @Test
    fun `missing learning setup does not read candidates or write plan`() = runTest {
        val planRepository = FakeTodayPlanRepository(findResult = null)
        val cardSource = FakePlanCardSource()
        val profiles = FakeLearningProfileRepository(currentResult = RepositoryResult.Success(null))

        val result = useCase(planRepository, cardSource, profiles)("profile")

        assertEquals(TodayPlanResult.MissingLearningSetup, result)
        assertEquals(0, cardSource.dueCalls)
        assertEquals(0, cardSource.newCalls)
        assertEquals(0, planRepository.saveCalls)
    }

    @Test
    fun `repository failures map to stable storage unavailable result`() = runTest {
        val sourceFailure = FakeLearningProfileRepository(
            currentResult = RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable),
        )
        assertEquals(
            TodayPlanResult.StorageUnavailable,
            useCase(FakeTodayPlanRepository(), FakePlanCardSource(), sourceFailure)("profile"),
        )

        assertEquals(
            TodayPlanResult.StorageUnavailable,
            useCase(
                FakeTodayPlanRepository(findResult = TodayPlanResult.StorageUnavailable),
                FakePlanCardSource(),
            )("profile"),
        )
    }

    @Test
    fun `unavailable plan cards do not persist an empty plan`() = runTest {
        val planRepository = FakeTodayPlanRepository(findResult = null)
        val cardSource = FakePlanCardSource(throwWhenReadingDueCards = true)

        val result = useCase(planRepository, cardSource)("profile")

        assertEquals(TodayPlanResult.StorageUnavailable, result)
        assertEquals(1, cardSource.dueCalls)
        assertEquals(0, cardSource.newCalls)
        assertEquals(0, planRepository.saveCalls)
        assertEquals(null, planRepository.savedPlan)
    }

    @Test
    fun `new plan captures local date zone generation instant and persisted conflict result`() = runTest {
        val conflictPlan = plan(localDate = LocalDate.of(2026, 9, 20), zone = "Pacific/Auckland")
        val planRepository = FakeTodayPlanRepository(
            findResult = null,
            saveResult = TodayPlanResult.Ready(conflictPlan),
        )

        val result = useCase(planRepository, FakePlanCardSource())("profile")

        assertEquals(TodayPlanResult.Ready(conflictPlan), result)
        val attempted = requireNotNull(planRepository.savedPlan)
        assertEquals(LocalDate.of(2026, 9, 19), attempted.localDate)
        assertEquals("Asia/Shanghai", attempted.zoneId)
        assertEquals(instant, attempted.generatedAt)
        assertEquals("f1-v1", attempted.ruleVersion)
        assertEquals(36, attempted.planId.length)
    }

    private fun useCase(
        planRepository: TodayPlanRepository,
        cardSource: PlanCardSource,
        profiles: LearningProfileRepository = FakeLearningProfileRepository(),
    ) = GetOrCreateTodayPlanUseCase(
        learningProfileRepository = profiles,
        todayPlanRepository = planRepository,
        cardSource = cardSource,
        clock = FixedClockProvider(instant, zoneId),
    )

    private fun plan(
        localDate: LocalDate,
        zone: String = "Asia/Shanghai",
    ) = TodayPlan(
        planId = "plan-id",
        profileId = "profile",
        localDate = localDate,
        zoneId = zone,
        activeWordBookId = "book",
        newTarget = 5,
        dueTarget = 2,
        newCardIds = listOf("new-1"),
        dueCardIds = listOf("due-1"),
        ruleVersion = "f1-v1",
        generatedAt = instant,
    )

    private class FakeTodayPlanRepository(
        private val findResult: TodayPlanResult? = null,
        private val saveResult: TodayPlanResult? = null,
    ) : TodayPlanRepository {
        var saveCalls = 0
        var savedPlan: TodayPlan? = null

        override suspend fun find(profileId: String, localDate: LocalDate): TodayPlanResult =
            findResult ?: TodayPlanResult.NotFound

        override suspend fun saveIfAbsent(plan: TodayPlan): TodayPlanResult {
            saveCalls++
            savedPlan = plan
            return saveResult ?: TodayPlanResult.Ready(plan)
        }
    }

    private class FakePlanCardSource(
        private val dueIds: List<String> = emptyList(),
        private val newIds: List<String> = emptyList(),
        private val throwWhenReadingDueCards: Boolean = false,
    ) : PlanCardSource {
        var dueCalls = 0
        var newCalls = 0
        var dueWordBookId: String? = null
        var dueNow: Instant? = null
        var newLimit: Int? = null

        override suspend fun dueCardIds(wordBookId: String, now: Instant): List<String> {
            dueCalls++
            dueWordBookId = wordBookId
            dueNow = now
            if (throwWhenReadingDueCards) {
                throw PlanCardSourceUnavailable()
            }
            return dueIds
        }

        override suspend fun newCardIds(wordBookId: String, limit: Int): List<String> {
            newCalls++
            newLimit = limit
            return newIds
        }
    }

    private class FakeLearningProfileRepository(
        private val currentResult: RepositoryResult<LearningProfile?> =
            RepositoryResult.Success(LearningProfile("profile", "book", 5)),
    ) : LearningProfileRepository {
        override suspend fun current(profileId: String): RepositoryResult<LearningProfile?> = currentResult
        override suspend fun save(profile: LearningProfile): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun listWordBooks(): RepositoryResult<List<WordBook>> = RepositoryResult.Success(emptyList())
        override suspend fun findWordBook(id: String): RepositoryResult<WordBook?> = RepositoryResult.Success(null)
        override suspend fun upsertWordBook(wordBook: WordBook): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
    }
}
