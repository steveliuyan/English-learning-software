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
        runStorage {
            database.internalVocabularySearchHistoryDao().list(profileId, limit.coerceAtLeast(0)).map { it.toVocabularySearchHistory() }
        }

    override suspend fun record(
        profileId: String,
        query: String,
        representative: SearchRepresentative?,
        at: Instant,
    ): RepositoryResult<Unit> = runStorage {
        val normalized = normalizeVocabularySearchQuery(query) ?: return@runStorage Unit
        database.withTransaction {
            val dao = database.internalVocabularySearchHistoryDao()
            dao.upsert(
                nextVocabularySearchHistoryEntity(
                    existing = dao.find(profileId, normalized),
                    profileId = profileId,
                    normalizedQuery = normalized,
                    displayQuery = query,
                    representative = representative,
                    at = at,
                ),
            )
        }
    }

    override suspend fun find(profileId: String, normalizedQuery: String): RepositoryResult<VocabularySearchHistory?> =
        runStorage { database.internalVocabularySearchHistoryDao().find(profileId, normalizedQuery)?.toVocabularySearchHistory() }

    override suspend fun clear(profileId: String): RepositoryResult<Unit> =
        runStorage { database.internalVocabularySearchHistoryDao().clear(profileId) }

    private suspend fun <T> runStorage(block: suspend () -> T): RepositoryResult<T> = try {
        RepositoryResult.Success(withContext(ioDispatcher) { block() })
    } catch (cancellation: CancellationException) {
        if (currentCoroutineContext().isActive) RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable) else throw cancellation
    } catch (_: Exception) {
        RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
    }
}

internal fun nextVocabularySearchHistoryEntity(
    existing: VocabularySearchHistoryEntity?,
    profileId: String,
    normalizedQuery: String,
    displayQuery: String,
    representative: SearchRepresentative?,
    at: Instant,
): VocabularySearchHistoryEntity {
    val display = displayQuery.trim().replace(Regex("\\s+"), " ")
    return existing?.copy(
        displayQuery = display,
        searchCount = existing.searchCount + 1,
        lastSearchedAtEpochMillis = at.toEpochMilli(),
        representativeWordBookId = representative?.wordBookId,
        representativeCardId = representative?.cardId,
    ) ?: VocabularySearchHistoryEntity(
        profileId = profileId,
        normalizedQuery = normalizedQuery,
        displayQuery = display,
        searchCount = 1,
        firstSearchedAtEpochMillis = at.toEpochMilli(),
        lastSearchedAtEpochMillis = at.toEpochMilli(),
        representativeWordBookId = representative?.wordBookId,
        representativeCardId = representative?.cardId,
    )
}

internal fun VocabularySearchHistoryEntity.toVocabularySearchHistory() = VocabularySearchHistory(
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
