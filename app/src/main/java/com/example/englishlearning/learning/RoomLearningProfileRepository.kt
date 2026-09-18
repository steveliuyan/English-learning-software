package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.LearningProfileEntity
import com.example.englishlearning.core.storage.entity.WordBookEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class RoomLearningProfileRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : LearningProfileRepository {
    override suspend fun current(profileId: String): RepositoryResult<LearningProfile?> =
        runStorage { database.internalLearningProfileDao().findByProfileId(profileId)?.toDomain() }

    override suspend fun save(profile: LearningProfile): RepositoryResult<Unit> =
        runStorage { database.internalLearningProfileDao().upsert(profile.toEntity()) }

    override suspend fun listWordBooks(): RepositoryResult<List<WordBook>> =
        runStorage { database.internalWordBookDao().listAll().map { it.toDomain() } }

    override suspend fun findWordBook(id: String): RepositoryResult<WordBook?> =
        runStorage { database.internalWordBookDao().findById(id)?.toDomain() }

    override suspend fun upsertWordBook(wordBook: WordBook): RepositoryResult<Unit> =
        runStorage { database.internalWordBookDao().upsert(wordBook.toEntity()) }

    private suspend fun <T> runStorage(block: suspend () -> T): RepositoryResult<T> =
        try {
            RepositoryResult.Success(withContext(ioDispatcher) { block() })
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
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
