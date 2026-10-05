package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.VocabularyEntryEntity
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class RoomVocabularyRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : VocabularyRepository {
    override suspend fun add(profileId: String, wordBookId: String, cardId: String, feedback: String, addedAt: Instant) {
        withContext(ioDispatcher) {
            database.internalVocabularyEntryDao().upsert(
                VocabularyEntryEntity(profileId, wordBookId, cardId, addedAt.toEpochMilli(), feedback),
            )
        }
    }

    override suspend fun remove(profileId: String, wordBookId: String, cardId: String) {
        withContext(ioDispatcher) { database.internalVocabularyEntryDao().remove(profileId, wordBookId, cardId) }
    }

    override suspend fun contains(profileId: String, wordBookId: String, cardId: String): Boolean =
        withContext(ioDispatcher) { database.internalVocabularyEntryDao().contains(profileId, wordBookId, cardId) }

    override suspend fun list(profileId: String, wordBookId: String?): RepositoryResult<List<VocabularyEntry>> =
        try {
            RepositoryResult.Success(
                withContext(ioDispatcher) {
                    (if (wordBookId == null) {
                        database.internalVocabularyEntryDao().findAll(profileId)
                    } else {
                        database.internalVocabularyEntryDao().findForWordBook(profileId, wordBookId)
                    }).map { it.toDomain() }
                },
            )
        } catch (cancellation: CancellationException) {
            if (currentCoroutineContext().isActive) RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable) else throw cancellation
        } catch (_: Exception) {
            RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
        }

    private fun VocabularyEntryEntity.toDomain() = VocabularyEntry(
        profileId = profileId,
        wordBookId = wordBookId,
        cardId = cardId,
        addedAt = Instant.ofEpochMilli(addedAtEpochMillis),
        lastFeedback = lastFeedback,
    )
}
