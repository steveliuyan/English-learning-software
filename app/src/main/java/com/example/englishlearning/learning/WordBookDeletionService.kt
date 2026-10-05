package com.example.englishlearning.learning

import java.io.File

sealed interface WordBookDeletionResult {
    data object Deleted : WordBookDeletionResult

    /** 数据库里没有这册（可能已被删除，或 id 从未存在）。 */
    data object NotFound : WordBookDeletionResult

    /** 随包内置的册，永不可删。 */
    data object ProtectedBuiltIn : WordBookDeletionResult

    /** 当前正在使用的册，必须先切换到别的册。 */
    data object ProtectedActive : WordBookDeletionResult

    data object StorageUnavailable : WordBookDeletionResult
}

/**
 * 删除用户导入的词书及其本地学习状态；内置册与当前活动册受保护。
 *
 * **写序**：先删资源目录，再删数据库行。理由是两个方向的失败代价不对称——
 * 目录删不掉时数据库保持完整，用户可以原样重试；反过来先删库再失败，就只剩一册
 * 「登记在案却读不出内容」的词书。删库本身是一个事务，内部所有清理要么全成、要么全不成。
 *
 * [builtInWordBookIds] 用函数而不是集合，是为了让生产侧按需读取 assets 清单，
 * 不在依赖注入阶段做 IO。
 */
class WordBookDeletionService(
    private val repository: LearningProfileRepository,
    private val importedRoot: File,
    private val builtInWordBookIds: () -> Set<String>,
) {
    suspend fun delete(profileId: String, wordBookId: String): WordBookDeletionResult {
        if (!SAFE_ID.matches(wordBookId)) return WordBookDeletionResult.NotFound
        if (wordBookId in builtInWordBookIds()) return WordBookDeletionResult.ProtectedBuiltIn
        return when (val current = repository.current(profileId)) {
            is RepositoryResult.Failure -> WordBookDeletionResult.StorageUnavailable
            is RepositoryResult.Success -> {
                if (current.value?.activeWordBookId == wordBookId) return WordBookDeletionResult.ProtectedActive
                when (val book = repository.findWordBook(wordBookId)) {
                    is RepositoryResult.Failure -> WordBookDeletionResult.StorageUnavailable
                    is RepositoryResult.Success -> {
                        if (book.value == null) {
                            WordBookDeletionResult.NotFound
                        } else {
                            deleteDirectoryThenRow(profileId, wordBookId)
                        }
                    }
                }
            }
        }
    }

    private suspend fun deleteDirectoryThenRow(profileId: String, wordBookId: String): WordBookDeletionResult {
        val directory = File(importedRoot, wordBookId)
        if (directory.exists() && !directory.deleteRecursively()) {
            return WordBookDeletionResult.StorageUnavailable
        }
        return when (repository.deleteWordBook(profileId, wordBookId)) {
            is RepositoryResult.Success -> WordBookDeletionResult.Deleted
            is RepositoryResult.Failure -> WordBookDeletionResult.StorageUnavailable
        }
    }

    private companion object {
        /** 与 `BundledWordBookSource` 同一套安全 id 规则，防止 `../` 之类的目录逃逸。 */
        val SAFE_ID = Regex("[a-z0-9][a-z0-9-]{0,63}")
    }
}
