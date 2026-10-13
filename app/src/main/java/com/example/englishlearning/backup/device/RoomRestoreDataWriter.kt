package com.example.englishlearning.backup.device

import android.content.Context
import androidx.room.withTransaction
import com.example.englishlearning.backup.restore.RestoredDataWriter
import com.example.englishlearning.backup.restore.StagedRestoreSession
import com.example.englishlearning.backup.restore.SwapTarget
import com.example.englishlearning.core.storage.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Writes the staged plaintext into a fresh replacement database (same schema via
 * Room migrations) plus the assets directory. Replacement semantics ("替换本机
 * 数据", never merge): the replacement DB starts empty and receives exactly the
 * staged rows. The WAL sidecars are deleted after checkpoint so the trio is clean.
 */
@Singleton
class RoomRestoreDataWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val decoder: RestoreEntityDecoder,
) : RestoredDataWriter {

    override fun writeReplacement(session: StagedRestoreSession, workingDir: File): Map<String, File>? = try {
        val dbDir = File(workingDir, RoomRestoreDataWriter.DB_TARGET_NAME).apply { mkdirs() }
        val assetsDir = File(workingDir, RoomRestoreDataWriter.ASSETS_TARGET_NAME).apply { mkdirs() }
        val stagedDb = File(dbDir, DATABASE_FILE_NAME)
        listOf(stagedDb, File(dbDir, "$DATABASE_FILE_NAME-wal"), File(dbDir, "$DATABASE_FILE_NAME-shm")).forEach { it.delete() }

        val helper = androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, stagedDb.absolutePath)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        try {
            val rows = decoder.decode(session)
            runBlocking {
                helper.withTransaction {
                    val dao = helper.internalBackupRestoreDao()
                    dao.insertProfiles(rows.profiles)
                    dao.insertWordBooks(rows.wordBooks)
                    dao.insertVocabularyEntries(rows.vocabularyEntries)
                    dao.insertLearningProfiles(rows.learningProfiles)
                    dao.insertLearningSettings(rows.learningSettings)
                    dao.insertTodayPlans(rows.todayPlans)
                    dao.insertTodayPlanTasks(rows.todayPlanTasks)
                    dao.insertLearningEvents(rows.learningEvents)
                    dao.insertCardReviewStates(rows.cardReviewStates)
                    dao.insertMigrationAudits(rows.migrationAudits)
                    dao.insertArticles(rows.articles)
                    dao.insertReadingCompletions(rows.readingCompletions)
                    dao.insertReadingPreferences(rows.readingPreferences)
                    dao.insertSearchHistory(rows.searchHistory)
                    dao.insertWordAiNotes(rows.wordAiNotes)
                    dao.insertAssetRecords(rows.assetRecords)
                }
                helper.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            }
        } finally {
            helper.close()
        }
        File(dbDir, "$DATABASE_FILE_NAME-wal").delete()
        File(dbDir, "$DATABASE_FILE_NAME-shm").delete()

        session.staged.resources.forEach { resource ->
            File(assetsDir, resource.assetId).writeBytes(resource.bytes)
        }
        mapOf(RoomRestoreDataWriter.DB_TARGET_NAME to dbDir, RoomRestoreDataWriter.ASSETS_TARGET_NAME to assetsDir)
    } catch (_: Exception) {
        null
    }

    companion object {
        const val DATABASE_FILE_NAME = "english-learning.db"
        const val DB_TARGET_NAME = "db"
        const val ASSETS_TARGET_NAME = "assets"

        /** Formal swap targets: the Room databases directory and the private assets directory. */
        fun swapTargets(context: Context): List<SwapTarget> {
            val databasesDir = context.getDatabasePath(DATABASE_FILE_NAME).parentFile
                ?: File(context.applicationInfo.dataDir, "databases")
            return listOf(
                SwapTarget(DB_TARGET_NAME, databasesDir),
                SwapTarget(ASSETS_TARGET_NAME, File(context.filesDir, "assets")),
            )
        }
    }
}
