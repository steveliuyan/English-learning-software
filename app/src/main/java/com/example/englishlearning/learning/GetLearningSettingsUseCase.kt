package com.example.englishlearning.learning

class GetLearningSettingsUseCase(
    private val repository: LearningSettingsRepository,
) {
    suspend operator fun invoke(profileId: String): LearningSettingsRepositoryResult<LearningSettings> =
        when (val result = repository.find(profileId)) {
            is LearningSettingsRepositoryResult.Success ->
                LearningSettingsRepositoryResult.Success(result.value ?: LearningSettings.defaults(profileId))
            LearningSettingsRepositoryResult.StorageUnavailable ->
                LearningSettingsRepositoryResult.StorageUnavailable
        }
}

class SaveLearningSettingsUseCase(
    private val repository: LearningSettingsRepository,
) {
    suspend operator fun invoke(settings: LearningSettings): LearningSettingsRepositoryResult<Unit> =
        repository.save(settings)
}
