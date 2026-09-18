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
    ): SetupResult = when {
        dailyNewTarget < 1 -> SetupResult.InvalidDailyTarget
        repository.findWordBook(wordBookId).toDomainOrNull() == null -> SetupResult.UnknownWordBook
        else -> repository.save(LearningProfile(profileId, wordBookId, dailyNewTarget)).toSetupResult()
    }

    private fun RepositoryResult<WordBook?>.toDomainOrNull(): WordBook? =
        (this as? RepositoryResult.Success)?.value

    private fun RepositoryResult<Unit>.toSetupResult(): SetupResult =
        when (this) {
            is RepositoryResult.Success -> SetupResult.Saved
            is RepositoryResult.Failure -> SetupResult.StorageUnavailable
        }
}
