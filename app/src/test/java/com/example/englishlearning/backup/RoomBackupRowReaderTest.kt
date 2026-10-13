package com.example.englishlearning.backup

import com.example.englishlearning.backup.infrastructure.InternalBackupSourceDao
import com.example.englishlearning.backup.infrastructure.RoomBackupRowReader
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.LocalProfileEntity
import com.example.englishlearning.core.storage.entity.WordBookEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeBackupDao : InternalBackupSourceDao {
    val profiles = listOf(LocalProfileEntity("p-1", "Me", 1L))
    val wordBooks = listOf(WordBookEntity("wb-1", "Core", "cet4", 1, "1", "src"))
    val assets = listOf(AssetRecordEntity("a-1", "aa", "assets/a-1", 1L, 1L))

    override suspend fun allProfiles() = profiles
    override suspend fun allLearningProfiles() = emptyList<com.example.englishlearning.core.storage.entity.LearningProfileEntity>()
    override suspend fun allLearningSettings() = emptyList<com.example.englishlearning.core.storage.entity.LearningSettingsEntity>()
    override suspend fun allTodayPlans() = emptyList<com.example.englishlearning.core.storage.entity.TodayPlanEntity>()
    override suspend fun allTodayPlanTasks() = emptyList<com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity>()
    override suspend fun allLearningEvents() = emptyList<com.example.englishlearning.core.storage.entity.LearningEventEntity>()
    override suspend fun allCardReviewStates() = emptyList<com.example.englishlearning.core.storage.entity.CardReviewStateEntity>()
    override suspend fun allWordBooks() = wordBooks
    override suspend fun allVocabularyEntries() = emptyList<com.example.englishlearning.core.storage.entity.VocabularyEntryEntity>()
    override suspend fun allMigrationAudits() = emptyList<com.example.englishlearning.core.storage.entity.WordBookProgressMigrationAuditEntity>()
    override suspend fun allArticles() = emptyList<com.example.englishlearning.core.storage.entity.ArticleEntity>()
    override suspend fun allReadingCompletions() = emptyList<com.example.englishlearning.core.storage.entity.ReadingCompletionEntity>()
    override suspend fun allReadingPreferences() = emptyList<com.example.englishlearning.core.storage.entity.ReadingPreferenceEntity>()
    override suspend fun allSearchHistory() = emptyList<com.example.englishlearning.core.storage.entity.VocabularySearchHistoryEntity>()
    override suspend fun allWordAiNotes() = emptyList<com.example.englishlearning.core.storage.entity.WordAiNoteEntity>()
    override suspend fun allPhotoTranslations() = emptyList<com.example.englishlearning.core.storage.entity.PhotoTranslationRecordEntity>()
    override suspend fun allAssetRecords() = assets
}

class RoomBackupRowReaderTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `reader maps every whitelisted table into the snapshot rows without writes`() = runTest(dispatcher) {
        val dao = FakeBackupDao()
        val database = mockk<AppDatabase>()
        every { database.internalBackupSourceDao() } returns dao

        val rows = RoomBackupRowReader(database, dispatcher).readAll()

        assertEquals(dao.profiles, rows.profiles)
        assertEquals(dao.wordBooks, rows.wordBooks)
        assertEquals(dao.assets, rows.assetRecords)
        assertEquals(emptyList(), rows.articles)
        assertEquals(emptyList(), rows.learningEvents)
    }
}
