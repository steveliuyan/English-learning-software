package com.example.englishlearning.backup.infrastructure

import com.example.englishlearning.backup.domain.BackupRowReader
import com.example.englishlearning.backup.domain.BackupSourceRows
import com.example.englishlearning.core.storage.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Read-only snapshot source for backup export. Uses the SELECT-only
 * [InternalBackupSourceDao]; never writes Room data.
 */
class RoomBackupRowReader(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : BackupRowReader {
    override suspend fun readAll(): BackupSourceRows = withContext(ioDispatcher) {
        val dao = database.internalBackupSourceDao()
        BackupSourceRows(
            profiles = dao.allProfiles(),
            learningProfiles = dao.allLearningProfiles(),
            learningSettings = dao.allLearningSettings(),
            todayPlans = dao.allTodayPlans(),
            todayPlanTasks = dao.allTodayPlanTasks(),
            learningEvents = dao.allLearningEvents(),
            cardReviewStates = dao.allCardReviewStates(),
            wordBooks = dao.allWordBooks(),
            vocabularyEntries = dao.allVocabularyEntries(),
            migrationAudits = dao.allMigrationAudits(),
            articles = dao.allArticles(),
            readingCompletions = dao.allReadingCompletions(),
            readingPreferences = dao.allReadingPreferences(),
            searchHistory = dao.allSearchHistory(),
            wordAiNotes = dao.allWordAiNotes(),
            assetRecords = dao.allAssetRecords(),
        )
    }
}
