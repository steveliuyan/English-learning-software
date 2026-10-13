package com.example.englishlearning.backup.restore

import java.io.File

/** Formal-data controller; the Room/file implementation lives in the device wiring layer. */
interface FormalDataHandle {
    /** Checkpoints (WAL flush) and closes the database so its files are safe to copy/rename. */
    fun checkpointAndClose(): Boolean

    /** Reopens the database and runs `PRAGMA quick_check`; false means the swapped database is bad. */
    fun reopenAndQuickCheck(): Boolean
}

/**
 * Materializes a staged session into a complete replacement data directory:
 * Room database files plus `assets/` media. Device implementation writes through
 * Room DAOs; the directory it produces is exactly what replaces the formal root.
 */
interface RestoredDataWriter {
    /** Returns the directory containing the finished replacement, or null on failure. */
    fun writeReplacement(session: StagedRestoreSession, workingDir: File): File?
}

/** Mandatory post-restore maintenance (§9.6); each returns false on failure. */
interface PostRestoreActions {
    fun rebuildSearchIndex(): Boolean
    fun rescheduleReminders(): Boolean
    fun eraseAiCredentials(): Boolean
    fun writeRestoreAudit(session: StagedRestoreSession): Boolean
}

/**
 * Rollback point + atomic directory swap (decision doc §9.4–9.5):
 * 1) build the replacement in an isolated working dir; 2) close the DB; 3) snapshot the
 * formal root into `rollback/<timestamp>` with a digest ledger; 4) rename-exchange the
 * directories; 5) reopen + quick_check; 6) run the post-restore maintenance.
 * Any failure at or after the snapshot step restores the rollback point; the formal
 * root is byte-identical to before unless [RestoreCommitResult.Restored] is returned.
 */
class SnapshotSwapRestoreCommitGateway(
    private val rollbackRoot: File,
    private val scratchRoot: File,
    private val formalRoot: File,
    private val database: FormalDataHandle,
    private val writer: RestoredDataWriter,
    private val postActions: PostRestoreActions,
    private val clockMillis: () -> Long,
) : RestoreCommitGateway {

    override fun commit(session: StagedRestoreSession): RestoreCommitResult {
        val working = File(scratchRoot, "commit-${session.sessionId}").apply { mkdirs() }
        val replacement = writer.writeReplacement(session, working)
            ?: return RestoreCommitResult.Failed(RestoreFailure.IntegrityFailure)

        val rollback = File(rollbackRoot, clockMillis().toString())
        if (!database.checkpointAndClose()) {
            working.deleteRecursively()
            return RestoreCommitResult.Failed(RestoreFailure.IntegrityFailure)
        }
        if (!snapshotFormalRoot(rollback)) {
            database.reopenAndQuickCheck()
            working.deleteRecursively()
            return RestoreCommitResult.Failed(RestoreFailure.IntegrityFailure)
        }

        val aside = File(formalRoot.parentFile, formalRoot.name + "-aside-${session.sessionId}")
        var swapped = false
        var verified = false
        var restored = false
        try {
            swapped = formalRoot.renameTo(aside) && replacement.renameTo(formalRoot)
            verified = swapped && database.reopenAndQuickCheck()
            if (verified) {
                restored = postActions.rebuildSearchIndex() &&
                    postActions.rescheduleReminders() &&
                    postActions.eraseAiCredentials() &&
                    postActions.writeRestoreAudit(session)
                if (restored) {
                    aside.deleteRecursively()
                    return RestoreCommitResult.Restored
                }
            }
        } finally {
            if (swapped && !restored) {
                restoreRollback(rollback)
                if (formalRoot.exists()) aside.deleteRecursively()
            }
            working.deleteRecursively()
        }
        database.reopenAndQuickCheck()
        return RestoreCommitResult.Failed(RestoreFailure.RollbackVerification)
    }

    /** Copies the formal root and writes a SHA-256 ledger for each contained file. */
    private fun snapshotFormalRoot(rollback: File): Boolean = try {
        formalRoot.copyRecursively(rollback, overwrite = true)
        val ledger = rollback.resolve(LEDGER_FILE)
        val entries = rollback.walkTopDown().filter { it.isFile && it != ledger }
            .sortedBy { it.relativeTo(rollback).invariantSeparatorsPath }
            .joinToString("\n") { file ->
                val digest = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    .joinToString("") { "%02x".format(it) }
                "${file.relativeTo(rollback).invariantSeparatorsPath}\t$digest"
            }
        ledger.writeText(entries + "\n", Charsets.UTF_8)
        true
    } catch (_: Exception) {
        rollback.deleteRecursively()
        false
    }

    private fun restoreRollback(rollback: File): Boolean = try {
        if (formalRoot.exists()) formalRoot.deleteRecursively()
        rollback.copyRecursively(formalRoot, overwrite = true)
        
        database.reopenAndQuickCheck()
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val LEDGER_FILE = "rollback-ledger.txt"
    }
}
