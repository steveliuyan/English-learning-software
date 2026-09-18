package com.example.englishlearning.learning

sealed interface SetupResult {
    data object Saved : SetupResult

    data object InvalidDailyTarget : SetupResult

    data object UnknownWordBook : SetupResult
}

class SelectWordBookAndSetDailyTargetUseCase(
    private val repository: LearningProfileRepository,
) {
    suspend operator fun invoke(
        profileId: String,
        wordBookId: String,
        dailyNewTarget: Int,
    ): SetupResult =
        when {
            dailyNewTarget < 1 -> SetupResult.InvalidDailyTarget
            repository.findWordBook(wordBookId) == null -> SetupResult.UnknownWordBook
            else -> {
                repository.save(LearningProfile(profileId, wordBookId, dailyNewTarget))
                SetupResult.Saved
            }
        }
}
