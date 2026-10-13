package com.example.englishlearning.backup.device

import android.content.Context
import android.os.StatFs
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.backup.restore.FormalDataHandle
import com.example.englishlearning.backup.restore.PostRestoreActions
import com.example.englishlearning.backup.restore.RestoreSpaceChecker
import com.example.englishlearning.backup.restore.StagedRestoreSession
import com.example.englishlearning.learning.RefreshVocabularySearchIndexUseCase
import com.example.englishlearning.notification.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Device-side formal data controller: checkpoints (WAL TRUNCATE) and closes the
 * Room database so its file trio can be snapshotted/renamed, then reopens and
 * verifies with `PRAGMA quick_check`.
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
 * Mandatory post-restore maintenance (decision doc §9.6): derived search index
 * rebuild, reminder reschedule, AI credential purge (D4 — keys are deleted, never
 * migrated), and a secrets-free restore audit record (counts and timestamps only).
 */
@Singleton
class AndroidPostRestoreActions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val indexRefresh: RefreshVocabularySearchIndexUseCase,
    private val reminderScheduler: ReminderScheduler,
    private val aiProfiles: AiProfileRepository,
    private val aiSecrets: AiProfileSecretUseCase,
) : PostRestoreActions {

    override fun rebuildSearchIndex(): Boolean = try {
        runBlocking {
            when (indexRefresh()) {
                is com.example.englishlearning.learning.RepositoryResult.Success -> true
                else -> false
            }
        }
    } catch (_: Exception) {
        false
    }

    override fun rescheduleReminders(): Boolean = try {
        runBlocking {
            reminderScheduler.refresh()
            true
        }
    } catch (_: Exception) {
        false
    }

    override fun eraseAiCredentials(): Boolean = try {
        runBlocking {
            aiProfiles.list().getOrNull()?.forEach { profile -> aiSecrets.deleteKey(profile) }
            true
        }
    } catch (_: Exception) {
        false
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

    private companion object {
        const val RESTORE_AUDIT_FILE = "restore-audit.log"
    }
}
