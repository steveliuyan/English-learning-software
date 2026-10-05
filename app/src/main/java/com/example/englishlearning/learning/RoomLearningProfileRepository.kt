package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.LearningProfileEntity
import com.example.englishlearning.core.storage.entity.WordBookEntity
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
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

    override suspend fun deleteWordBook(profileId: String, wordBookId: String): RepositoryResult<Unit> =
        runStorage {
            database.withTransaction {
                database.internalLearningProfileDao().isActiveWordBook(profileId, wordBookId).let { active ->
                    check(!active) { "cannot delete active word book" }
                }
                database.internalLearningEventDao().deleteEvents(wordBookId)
                database.internalLearningEventDao().deleteReviewStates(wordBookId)
                database.internalVocabularyEntryDao().deleteForWordBook(wordBookId)
                database.internalWordBookProgressMigrationAuditDao().deleteForWordBook(wordBookId)
                database.internalWordAiNoteDao().deleteForWordBook(wordBookId)
                database.internalAssetDao().deleteForWordBook(wordBookId)
                database.internalWordBookDao().deleteById(wordBookId)
            }
        }

    private suspend fun <T> runStorage(block: suspend () -> T): RepositoryResult<T> {
        return try {
            RepositoryResult.Success(withContext(ioDispatcher) { block() })
        } catch (cancellation: CancellationException) {
            if (currentCoroutineContext().isActive) {
                RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            } else {
                throw cancellation
            }
        } catch (_: Exception) {
            RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
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
