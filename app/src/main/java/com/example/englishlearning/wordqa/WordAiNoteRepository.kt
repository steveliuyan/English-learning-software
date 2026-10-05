package com.example.englishlearning.wordqa

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.entity.WordAiNoteEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher

/** 一条已保存的词问答笔记。[kind] 在边界上编码为固定枚举，杜绝任意字符串入库。 */
data class WordAiNote(
    val noteId: String,
    val profileId: String,
    val wordBookId: String = "",
    val cardId: String = "",
    val lemma: String,
    val kind: WordQaKind,
    val answer: String,
    val createdAtEpochMillis: Long,
)

interface WordAiNoteRepository {
    suspend fun save(note: WordAiNote): Result<Unit>

    /** 指定 profile 与词的笔记，按创建时间降序；跨 profile 不可见。 */
    suspend fun list(profileId: String, lemma: String, wordBookId: String = "", cardId: String = ""): Result<List<WordAiNote>>
}

class RoomWordAiNoteRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : WordAiNoteRepository {
    override suspend fun save(note: WordAiNote): Result<Unit> = runStorage {
        database.internalWordAiNoteDao().insert(note.toEntity())
    }

    override suspend fun list(profileId: String, lemma: String, wordBookId: String, cardId: String): Result<List<WordAiNote>> = runStorage {
        database.internalWordAiNoteDao().findForCard(profileId, wordBookId, cardId, lemma).map { it.toDomain() }
    }

    private suspend fun <T> runStorage(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(ioDispatcher) { block() })
    } catch (cancellation: CancellationException) {
        if (currentCoroutineContext().isActive) {
            Result.failure(AppErrorException(AppError.StorageUnavailable))
        } else {
            throw cancellation
        }
    } catch (_: Exception) {
        Result.failure(AppErrorException(AppError.StorageUnavailable))
    }

    private fun WordAiNote.toEntity() = WordAiNoteEntity(
        noteId = noteId,
        profileId = profileId,
        wordBookId = wordBookId,
        cardId = cardId,
        lemma = lemma,
        kind = kind.name,
        answer = answer,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    private fun WordAiNoteEntity.toDomain() = WordAiNote(
        noteId = noteId,
        profileId = profileId,
        wordBookId = wordBookId,
        cardId = cardId,
        lemma = lemma,
        kind = WordQaKind.valueOf(kind),
        answer = answer,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}
