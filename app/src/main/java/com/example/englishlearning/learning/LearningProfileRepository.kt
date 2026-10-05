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

sealed interface LearningProfileRepositoryError {
    data object StorageUnavailable : LearningProfileRepositoryError
}

sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>
    data class Failure(val error: LearningProfileRepositoryError) : RepositoryResult<Nothing>
}

interface LearningProfileRepository {
    suspend fun current(profileId: String): RepositoryResult<LearningProfile?>
    suspend fun save(profile: LearningProfile): RepositoryResult<Unit>
    suspend fun listWordBooks(): RepositoryResult<List<WordBook>>
    suspend fun findWordBook(id: String): RepositoryResult<WordBook?>
    suspend fun upsertWordBook(wordBook: WordBook): RepositoryResult<Unit>

    /**
     * 删除用户导入的词书及其本地学习数据（词书元数据、复习状态、学习事件、生词、笔记与迁移审计）。
     *
     * 默认实现**失败关闭**：返回 [LearningProfileRepositoryError.StorageUnavailable] 而不是假装成功，
     * 这样任何忘记实现它的仓储最多表现为「删不掉」，绝不会出现「界面说删了、数据还在」的假成功。
     *
     * 这一点很关键：默认返回成功会让所有测试替身在没有真正删除能力时照样绿灯。
     * 生产实现见 [RoomLearningProfileRepository]。
     */
    suspend fun deleteWordBook(profileId: String, wordBookId: String): RepositoryResult<Unit> =
        RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
}
