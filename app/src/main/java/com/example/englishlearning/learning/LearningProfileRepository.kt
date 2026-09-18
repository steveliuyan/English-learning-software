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

interface LearningProfileRepository {
    suspend fun current(profileId: String): LearningProfile?

    suspend fun save(profile: LearningProfile)

    suspend fun listWordBooks(): List<WordBook>

    suspend fun findWordBook(id: String): WordBook?

    suspend fun upsertWordBook(wordBook: WordBook)
}
