package com.example.englishlearning.learning

import androidx.room.withTransaction
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.VocabularySearchHistoryEntity
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class RoomVocabularySearchHistoryRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : VocabularySearchHistoryRepository {
    override suspend fun list(profileId: String, limit: Int): RepositoryResult<List<VocabularySearchHistory>> =
        runStorage { database.internalVocabularySearchHistoryDao().list(profileId, limit.coerceAtLeast(0)).map { it.toDomain() } }

    override suspend fun record(
        profileId: String,
        query: String,
        representative: SearchRepresentative?,
        at: Instant,
    ): RepositoryResult<Unit> = runStorage {
        val normalized = normalizeVocabularySearchQuery(query) ?: return@runStorage Unit
        database.withTransaction {
            val dao = database.internalVocabularySearchHistoryDao()
            val existing = dao.find(profileId, normalized)
            dao.upsert(
                if (existing == null) {
                    VocabularySearchHistoryEntity(
                        profileId = profileId,
                        normalizedQuery = normalized,
                        displayQuery = query.trim().replace(Regex("\\s+"), " "),
                        searchCount = 1,
                        firstSearchedAtEpochMillis = at.toEpochMilli(),
                        lastSearchedAtEpochMillis = at.toEpochMilli(),
                        representativeWordBookId = representative?.wordBookId,
                        representativeCardId = representative?.cardId,
                    )
                } else {
                    existing.copy(
                        displayQuery = query.trim().replace(Regex("\\s+"), " "),
                        searchCount = existing.searchCount + 1,
                        lastSearchedAtEpochMillis = at.toEpochMilli(),
                        representativeWordBookId = representative?.wordBookId,
                        representativeCardId = representative?.cardId,
                    )
                },
            )
        }
    }

    override suspend fun find(profileId: String, normalizedQuery: String): RepositoryResult<VocabularySearchHistory?> =
        runStorage { database.internalVocabularySearchHistoryDao().find(profileId, normalizedQuery)?.toDomain() }

    override suspend fun clear(profileId: String): RepositoryResult<Unit> =
        runStorage { database.internalVocabularySearchHistoryDao().clear(profileId) }

    private suspend fun <T> runStorage(block: suspend () -> T): RepositoryResult<T> = try {
        RepositoryResult.Success(withContext(ioDispatcher) { block() })
    } catch (cancellation: CancellationException) {
        if (currentCoroutineContext().isActive) RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable) else throw cancellation
    } catch (_: Exception) {
        RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
    }

    private fun VocabularySearchHistoryEntity.toDomain() = VocabularySearchHistory(
        profileId = profileId,
        normalizedQuery = normalizedQuery,
        displayQuery = displayQuery,
        searchCount = searchCount,
        firstSearchedAt = Instant.ofEpochMilli(firstSearchedAtEpochMillis),
        lastSearchedAt = Instant.ofEpochMilli(lastSearchedAtEpochMillis),
        representative = if (representativeWordBookId != null && representativeCardId != null) {
            SearchRepresentative(representativeWordBookId, representativeCardId)
        } else {
            null
        },
    )
}
