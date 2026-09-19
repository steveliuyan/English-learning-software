package com.example.englishlearning.learning

import java.time.Instant
import java.time.LocalDate

data class TodayPlan(
    val planId: String,
    val profileId: String,
    val localDate: LocalDate,
    val zoneId: String,
    val activeWordBookId: String,
    val newTarget: Int,
    val dueTarget: Int,
    val newCardIds: List<String>,
    val dueCardIds: List<String>,
    val ruleVersion: String,
    val generatedAt: Instant,
)

sealed interface TodayPlanResult {
    data class Ready(val plan: TodayPlan) : TodayPlanResult
    data object NotFound : TodayPlanResult
    data object MissingLearningSetup : TodayPlanResult
    data object StorageUnavailable : TodayPlanResult
}

interface TodayPlanRepository {
    suspend fun find(profileId: String, localDate: LocalDate): TodayPlanResult

    suspend fun saveIfAbsent(plan: TodayPlan): TodayPlanResult
}
