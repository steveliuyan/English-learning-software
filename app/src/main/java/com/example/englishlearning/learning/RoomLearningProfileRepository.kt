package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.LearningProfileEntity
import com.example.englishlearning.core.storage.entity.WordBookEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class RoomLearningProfileRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : LearningProfileRepository {
    override suspend fun current(profileId: String): LearningProfile? =
        withContext(ioDispatcher) {
            database.internalLearningProfileDao().findByProfileId(profileId)?.toDomain()
        }

    override suspend fun save(profile: LearningProfile) {
        withContext(ioDispatcher) {
            database.internalLearningProfileDao().upsert(profile.toEntity())
        }
    }

    override suspend fun listWordBooks(): List<WordBook> =
        withContext(ioDispatcher) {
            database.internalWordBookDao().listAll().map { it.toDomain() }
        }

    override suspend fun findWordBook(id: String): WordBook? =
        withContext(ioDispatcher) {
            database.internalWordBookDao().findById(id)?.toDomain()
        }

    override suspend fun upsertWordBook(wordBook: WordBook) {
        withContext(ioDispatcher) {
            database.internalWordBookDao().upsert(wordBook.toEntity())
        }
    }

    private fun LearningProfileEntity.toDomain() =
        LearningProfile(profileId, activeWordBookId, dailyNewTarget)

    private fun LearningProfile.toEntity() =
        LearningProfileEntity(profileId, activeWordBookId, dailyNewTarget)

    private fun WordBookEntity.toDomain() =
        WordBook(id, displayName, level, totalWords, dataVersion, sourceId)

    private fun WordBook.toEntity() =
        WordBookEntity(id, displayName, level, totalWords, dataVersion, sourceId)
}
