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

    /**
     * F4-01 决策⑥：删词书 = 软删词书行 + 物理删词条与迁移审计，学习历史零触碰。
     *
     * 「删词书连带删 learning_events/card_review_states/today_plans/articles/
     * reading_completions/word_ai_notes/asset_records」是对 AGENTS.md「词书各自维护
     * 独立进度、切换不删除旧记录」的违反（原实现缺陷，androidTest
     * RoomLearningProfileRepositoryTest.deleteWordBookSoftDeletesBookAndKeepsEveryLearningHistoryRow
     * 在真库上钉死新语义）。事件日志与派生状态是本地学习的权威记录，词书只是内容源。
     */
    override suspend fun deleteWordBook(profileId: String, wordBookId: String): RepositoryResult<Unit> =
        runStorage {
            database.withTransaction {
                database.internalLearningProfileDao().isActiveWordBook(profileId, wordBookId).let { active ->
                    check(!active) { "cannot delete active word book" }
                }
                database.internalWordBookDao().markDeleted(wordBookId)
                database.internalVocabularyEntryDao().deleteForWordBook(wordBookId)
                database.internalWordBookProgressMigrationAuditDao().deleteForWordBook(wordBookId)
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
