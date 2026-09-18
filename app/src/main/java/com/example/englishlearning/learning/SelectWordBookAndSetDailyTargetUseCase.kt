package com.example.englishlearning.learning

sealed interface SetupResult {
    data object Saved : SetupResult
    data object InvalidDailyTarget : SetupResult
    data object UnknownWordBook : SetupResult
    data object StorageUnavailable : SetupResult
}

class SelectWordBookAndSetDailyTargetUseCase(
    private val repository: LearningProfileRepository,
) {
    suspend operator fun invoke(
        profileId: String,
        wordBookId: String,
        dailyNewTarget: Int,
    ): SetupResult {
        if (dailyNewTarget < 1) return SetupResult.InvalidDailyTarget

        return when (val lookup = repository.findWordBook(wordBookId)) {
            is RepositoryResult.Failure -> SetupResult.StorageUnavailable
            is RepositoryResult.Success -> {
                if (lookup.value == null) SetupResult.UnknownWordBook else save(profileId, wordBookId, dailyNewTarget)
            }
        }
    }

    private suspend fun save(
        profileId: String,
        wordBookId: String,
        dailyNewTarget: Int,
    ): SetupResult =
        when (repository.save(LearningProfile(profileId, wordBookId, dailyNewTarget))) {
            is RepositoryResult.Success -> SetupResult.Saved
            is RepositoryResult.Failure -> SetupResult.StorageUnavailable
        }
}
