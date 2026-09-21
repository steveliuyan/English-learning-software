package com.example.englishlearning.learning

import com.example.englishlearning.core.time.ClockProvider
import java.util.UUID

class GetOrCreateTodayPlanUseCase(
    private val learningProfileRepository: LearningProfileRepository,
    private val todayPlanRepository: TodayPlanRepository,
    private val cardSource: PlanCardSource,
    private val clock: ClockProvider,
) {
    suspend operator fun invoke(profileId: String): TodayPlanResult {
        val generationInstant = clock.instant()
        val zoneId = clock.zoneId()
        val localDate = generationInstant.atZone(zoneId).toLocalDate()

        // The learning day is anchored to the profile's latest generated plan (the greatest
        // localDate), not to the current clock value: a clock value is not monotonic, and a
        // timezone/clock rollback must reuse the existing snapshot instead of issuing a second
        // batch of new words. Only a strictly later local date starts a new plan.
        when (val latest = todayPlanRepository.findLatest(profileId)) {
            is TodayPlanResult.Ready ->
                if (!localDate.isAfter(latest.plan.localDate)) return latest
            TodayPlanResult.NotFound -> Unit
            TodayPlanResult.StorageUnavailable -> return TodayPlanResult.StorageUnavailable
            TodayPlanResult.MissingLearningSetup -> return TodayPlanResult.StorageUnavailable
        }

        val profile = when (val result = learningProfileRepository.current(profileId)) {
            is RepositoryResult.Failure -> return TodayPlanResult.StorageUnavailable
            is RepositoryResult.Success -> result.value ?: return TodayPlanResult.MissingLearningSetup
        }
        val dueCardIds: List<String>
        val newCardIds: List<String>
        try {
            dueCardIds = cardSource.dueCardIds(profile.activeWordBookId, generationInstant).distinct()
            newCardIds = cardSource.newCardIds(profile.activeWordBookId, profile.dailyNewTarget).distinct()
        } catch (_: PlanCardSourceUnavailable) {
            // The plan is an immutable snapshot: never persist an empty one because the
            // event store happened to fail, or the whole day is lost.
            return TodayPlanResult.StorageUnavailable
        }
        val plan = TodayPlan(
            planId = UUID.randomUUID().toString(),
            profileId = profileId,
            localDate = localDate,
            zoneId = zoneId.id,
            activeWordBookId = profile.activeWordBookId,
            newTarget = newCardIds.size,
            dueTarget = dueCardIds.size,
            newCardIds = newCardIds,
            dueCardIds = dueCardIds,
            ruleVersion = "f1-v1",
            generatedAt = generationInstant,
        )
        return todayPlanRepository.saveIfAbsent(plan)
    }
}
