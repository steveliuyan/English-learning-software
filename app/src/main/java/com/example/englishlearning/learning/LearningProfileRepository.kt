package com.example.englishlearning.learning

data class LearningProfile(
    val profileId: String,
    val activeWordBookId: String,
    val dailyNewTarget: Int,
)

data class WordBook(
    val id: String,
    val displayName: String,
    val level: String,
    val totalWords: Int,
    val dataVersion: String,
    val sourceId: String,
)

sealed interface LearningProfileRepositoryError {
    data object StorageUnavailable : LearningProfileRepositoryError
}

sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>
    data class Failure(val error: LearningProfileRepositoryError) : RepositoryResult<Nothing>
}

interface LearningProfileRepository {
    suspend fun current(profileId: String): RepositoryResult<LearningProfile?>
    suspend fun save(profile: LearningProfile): RepositoryResult<Unit>
    suspend fun listWordBooks(): RepositoryResult<List<WordBook>>
    suspend fun findWordBook(id: String): RepositoryResult<WordBook?>
    suspend fun upsertWordBook(wordBook: WordBook): RepositoryResult<Unit>
}
