package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.LearningSettingsEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * 学习偏好（词卡屏的三档展开 + 查词历史计数 + 每日复习上限）。
 *
 * `dailyReviewLimit` 的默认值 30 逐字取原型 `sPlanEdit` 里 `S.planReview` 的初值，是**领域侧的缺省源**：
 * 屏侧只提供滑杆刻度（10/80/5），不再另写一份 30；实体侧因分层约束独立写死同值，理由见
 * `LearningSettingsEntity.dailyReviewLimit` 的 KDoc。
 * 决策口径见 `docs/decisions/2026-10-29-plan-review-limit-preference.md`：V1 存这个数并把它交给排期侧读取，
 * 但不改 FSRS 的硬上限规则（`docs/specs/01-vocabulary-learning-and-review.md` 仍写「V1 不设置复习硬上限」）。
 */
data class LearningSettings(
    val profileId: String,
    val openDetailOnKnown: Boolean,
    val openDetailOnFuzzy: Boolean,
    val openDetailOnForgotten: Boolean,
    val showVocabularySearchCount: Boolean = true,
    val dailyReviewLimit: Int = DEFAULT_DAILY_REVIEW_LIMIT,
) {
    companion object {
        const val DEFAULT_DAILY_REVIEW_LIMIT = 30

        fun defaults(profileId: String) =
            LearningSettings(profileId, false, true, true, true, DEFAULT_DAILY_REVIEW_LIMIT)
    }
}

sealed interface LearningSettingsRepositoryResult<out T> {
    data class Success<T>(val value: T) : LearningSettingsRepositoryResult<T>
    data object StorageUnavailable : LearningSettingsRepositoryResult<Nothing>
}

interface LearningSettingsRepository {
    suspend fun find(profileId: String): LearningSettingsRepositoryResult<LearningSettings?>
    suspend fun save(settings: LearningSettings): LearningSettingsRepositoryResult<Unit>
}

class RoomLearningSettingsRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : LearningSettingsRepository {
    override suspend fun find(profileId: String): LearningSettingsRepositoryResult<LearningSettings?> =
        runStorage { database.internalLearningSettingsDao().findByProfileId(profileId)?.toDomain() }

    override suspend fun save(settings: LearningSettings): LearningSettingsRepositoryResult<Unit> =
        runStorage { database.internalLearningSettingsDao().upsert(settings.toEntity()) }

    private suspend fun <T> runStorage(block: suspend () -> T): LearningSettingsRepositoryResult<T> =
        try {
            LearningSettingsRepositoryResult.Success(withContext(ioDispatcher) { block() })
        } catch (cancellation: CancellationException) {
            if (currentCoroutineContext().isActive) {
                LearningSettingsRepositoryResult.StorageUnavailable
            } else {
                throw cancellation
            }
        } catch (_: Exception) {
            LearningSettingsRepositoryResult.StorageUnavailable
        }

    private fun LearningSettingsEntity.toDomain() =
        LearningSettings(
            profileId,
            openDetailOnKnown,
            openDetailOnFuzzy,
            openDetailOnForgotten,
            showVocabularySearchCount,
            dailyReviewLimit,
        )

    private fun LearningSettings.toEntity() =
        LearningSettingsEntity(
            profileId,
            openDetailOnKnown,
            openDetailOnFuzzy,
            openDetailOnForgotten,
            showVocabularySearchCount,
            dailyReviewLimit,
        )
}
