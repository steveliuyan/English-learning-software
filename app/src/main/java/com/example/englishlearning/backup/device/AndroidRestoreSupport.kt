package com.example.englishlearning.backup.device

import android.content.Context
import android.os.StatFs
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.RoomAiProfileRepository
import com.example.englishlearning.backup.restore.FormalDataHandle
import com.example.englishlearning.backup.restore.PostRestoreActions
import com.example.englishlearning.backup.restore.RestoreSpaceChecker
import com.example.englishlearning.backup.restore.StagedRestoreSession
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.learning.RefreshVocabularySearchIndexUseCase
import com.example.englishlearning.learning.RoomLearningEventRepository
import com.example.englishlearning.learning.RoomLearningProfileRepository
import com.example.englishlearning.learning.RoomTodayPlanRepository
import com.example.englishlearning.learning.RoomVocabularySearchIndexRepository
import com.example.englishlearning.notification.RoomReminderPreferenceRepository
import com.example.englishlearning.notification.WorkManagerReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Device-side formal data controller: checkpoints (WAL TRUNCATE) and closes the
 * Room database so its file trio can be snapshotted/renamed, then reopens and
 * verifies with `PRAGMA quick_check`.
 *
 * D-12: `database.close()` is deliberate and full—Room 2.8.4 then permanently
 * cancels the instance's coroutineScope, so the singleton's suspend DAOs are dead
 * for the rest of the process; maintenance writes go through fresh temp instances
 * in [AndroidPostRestoreActions] and the UI requires an app restart (AC4-07).
 */
@Singleton
class RoomFormalDataHandle @Inject constructor(
    private val database: com.example.englishlearning.core.storage.AppDatabase,
) : FormalDataHandle {
    override fun checkpointAndClose(): Boolean = try {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        database.close()
        true
    } catch (_: Exception) {
        false
    }

    override fun reopenAndQuickCheck(): Boolean = try {
        database.openHelper.writableDatabase.query("PRAGMA quick_check").use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == "ok"
        }
    } catch (_: Exception) {
        false
    }
}

/** Capacity gate over the private data partition; strict headroom (2× payload + 64 MiB floor). */@Singleton
class AndroidRestoreSpaceChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : RestoreSpaceChecker {
    override fun hasRoomFor(requiredBytes: Long): Boolean = try {
        val stats = StatFs(context.filesDir.absolutePath)
        val available = stats.availableBytes
        available >= requiredBytes * 2 + HEADROOM_BYTES
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val HEADROOM_BYTES = 64L * 1024 * 1024
    }
}

/**
 * Mandatory post-restore maintenance (decision doc §9.6; D-12 fix per
 * docs/decisions/2026-10-14-restore-commit-temp-instance-and-restart.md): the
 * process-level Room singleton is a zombie after `checkpointAndClose` (Room 2.8.4
 * `close()` cancels its coroutineScope permanently), so every maintenance write
 * runs against a freshly created temporary instance over the formal database
 * file—same pattern as `RoomRestoreDataWriter`, same schema via `AppDatabase.create`.
 * After success: derived search index rebuild, reminder reschedule, AI credential
 * purge (D4 — keys are deleted, never migrated), and a secrets-free restore audit
 * record (counts and timestamps only, no database needed).
 */
@Singleton
class AndroidPostRestoreActions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cards: com.example.englishlearning.learning.WordCardSource,
    private val bundledIds: com.example.englishlearning.learning.BundledWordBookIdSource,
    private val importedIds: com.example.englishlearning.learning.ImportedWordBookIdSource,
    private val clock: com.example.englishlearning.core.time.ClockProvider,
    private val aiSecrets: AiProfileSecretUseCase,
    @Named("io") private val io: kotlinx.coroutines.CoroutineDispatcher,
) : PostRestoreActions {

    override fun rebuildSearchIndex(): Boolean = withMaintenanceDatabase { db ->
        runBlocking {
            val refresh = RefreshVocabularySearchIndexUseCase(
                RoomLearningProfileRepository(db, io),
                cards,
                RoomVocabularySearchIndexRepository(db, io),
                bundledIds,
                importedIds,
            )
            when (refresh()) {
                is com.example.englishlearning.learning.RepositoryResult.Success -> true
                else -> false
            }
        }
    }

    override fun rescheduleReminders(): Boolean = withMaintenanceDatabase { db ->
        runBlocking {
            WorkManagerReminderScheduler(
                context,
                RoomReminderPreferenceRepository(db, io),
                RoomTodayPlanRepository(db, io),
                RoomLearningEventRepository(db, io),
                clock,
            ).refresh()
            true
        }
    }

    override fun eraseAiCredentials(): Boolean = withMaintenanceDatabase { db ->
        runBlocking {
            RoomAiProfileRepository(db, io).list().getOrNull()
                ?.forEach { profile -> aiSecrets.deleteKey(profile) }
            true
        }
    }

    override fun writeRestoreAudit(session: StagedRestoreSession): Boolean = try {
        File(context.filesDir, RESTORE_AUDIT_FILE).appendText(
            "restored session=${session.sessionId} records=${session.recordCount} media=${session.mediaCount} " +
                "bytes=${session.plaintextBytes} pairingIssuedAt=${session.pairingIssuedAtEpochSeconds}\n",
        )
        true
    } catch (_: Exception) {
        false
    }

    /** Opens the formal database file on a fresh instance, runs [block], then closes and scrubs WAL sidecars. */
    private fun withMaintenanceDatabase(block: (AppDatabase) -> Boolean): Boolean = try {
        val dbFile = context.getDatabasePath(RoomRestoreDataWriter.DATABASE_FILE_NAME)
        val db = AppDatabase.create(context, dbFile)
        try {
            block(db)
        } finally {
            db.close()
            File("${dbFile.path}-wal").delete()
            File("${dbFile.path}-shm").delete()
        }
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val RESTORE_AUDIT_FILE = "restore-audit.log"
    }
}
