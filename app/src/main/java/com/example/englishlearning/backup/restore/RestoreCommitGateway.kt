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
 * One formal data tree that is replaced wholesale on restore (e.g. the Room
 * databases directory, the private assets directory). The directory must exist.
 */
data class SwapTarget(val name: String, val formalDir: File)

/**
 * Materializes a staged session into complete replacement directories keyed by
 * [SwapTarget.name] (e.g. `"db"` → directory holding the DB trio, `"assets"` →
 * directory holding the media files). Device implementation writes through Room.
 */
interface RestoredDataWriter {
    /** Returns the replacement directory per target name, or null on failure. */
    fun writeReplacement(session: StagedRestoreSession, workingDir: File): Map<String, File>?
}

/** Mandatory post-restore maintenance (§9.6); each returns false on failure. */
interface PostRestoreActions {
    fun rebuildSearchIndex(): Boolean
    fun rescheduleReminders(): Boolean
    fun eraseAiCredentials(): Boolean
    fun writeRestoreAudit(session: StagedRestoreSession): Boolean
}

/**
 * Rollback point + atomic swap (decision doc §9.4–9.5), per target tree:
 * 1) build replacements in an isolated working dir; 2) close the DB; 3) snapshot each
 * formal tree into `rollback/<timestamp>/<name>` with a SHA-256 ledger; 4) rename-exchange;
 * 5) reopen + quick_check; 6) run the post-restore maintenance.
 * Any failure at or after the snapshot step restores every rollback snapshot; the formal
 * trees are byte-identical to before unless [RestoreCommitResult.Restored] is returned.
 */
class SnapshotSwapRestoreCommitGateway(
    private val rollbackRoot: File,
    private val scratchRoot: File,
    private val targets: List<SwapTarget>,
    private val database: FormalDataHandle,
    private val writer: RestoredDataWriter,
    private val postActions: PostRestoreActions,
    private val clockMillis: () -> Long,
) : RestoreCommitGateway {

    override fun commit(session: StagedRestoreSession): RestoreCommitResult {
        val working = File(scratchRoot, "commit-${session.sessionId}").apply { mkdirs() }
        val replacements = writer.writeReplacement(session, working)
            ?: return RestoreCommitResult.Failed(RestoreFailure.IntegrityFailure)
        if (targets.any { !replacements.containsKey(it.name) }) {
            working.deleteRecursively()
            return RestoreCommitResult.Failed(RestoreFailure.IntegrityFailure)
        }

        val rollback = File(rollbackRoot, clockMillis().toString())
        if (!database.checkpointAndClose()) {
            working.deleteRecursively()
            return RestoreCommitResult.Failed(RestoreFailure.IntegrityFailure)
        }
        if (!targets.all { snapshot(it, File(rollback, it.name)) }) {
            database.reopenAndQuickCheck()
            working.deleteRecursively()
            return RestoreCommitResult.Failed(RestoreFailure.IntegrityFailure)
        }

        val asides = mutableMapOf<String, File>()
        var swapped = false
        var verified = false
        var restored = false
        try {
            swapped = targets.all { target ->
                val aside = File(target.formalDir.parentFile, target.formalDir.name + "-aside-${session.sessionId}")
                asides[target.name] = aside
                target.formalDir.renameTo(aside) && replacements.getValue(target.name).renameTo(target.formalDir)
            }
            verified = swapped && database.reopenAndQuickCheck()
            if (verified) {
                restored = postActions.rebuildSearchIndex() &&
                    postActions.rescheduleReminders() &&
                    postActions.eraseAiCredentials() &&
                    postActions.writeRestoreAudit(session)
                if (restored) {
                    asides.values.forEach { it.deleteRecursively() }
                    return RestoreCommitResult.Restored
                }
            }
        } finally {
            if (swapped && !restored) {
                targets.forEach { target -> restoreRollback(File(rollback, target.name), target.formalDir) }
                asides.values.forEach { if (it.exists()) it.deleteRecursively() }
            }
            working.deleteRecursively()
        }
        database.reopenAndQuickCheck()
        return RestoreCommitResult.Failed(RestoreFailure.RollbackVerification)
    }

    /** Copies the formal tree and writes a SHA-256 ledger for each contained file. */
    private fun snapshot(target: SwapTarget, rollbackDir: File): Boolean = try {
        target.formalDir.copyRecursively(rollbackDir, overwrite = true)
        val ledger = rollbackDir.resolve(LEDGER_FILE)
        val entries = rollbackDir.walkTopDown().filter { it.isFile && it != ledger }
            .sortedBy { it.relativeTo(rollbackDir).invariantSeparatorsPath }
            .joinToString("\n") { file ->
                val digest = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    .joinToString("") { "%02x".format(it) }
                "${file.relativeTo(rollbackDir).invariantSeparatorsPath}\t$digest"
            }
        ledger.writeText(entries + "\n", Charsets.UTF_8)
        true
    } catch (_: Exception) {
        rollbackDir.deleteRecursively()
        false
    }

    private fun restoreRollback(rollbackDir: File, formalDir: File): Boolean = try {
        if (formalDir.exists()) formalDir.deleteRecursively()
        rollbackDir.copyRecursively(formalDir, overwrite = true)
        true
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val LEDGER_FILE = "rollback-ledger.txt"
    }
}
