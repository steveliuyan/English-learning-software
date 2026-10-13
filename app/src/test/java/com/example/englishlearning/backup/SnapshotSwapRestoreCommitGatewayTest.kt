package com.example.englishlearning.backup.restore

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Rollback point + atomic swap anchors (§9.4–9.5): verification failure after the
 * swap restores the rollback snapshot; success runs the full maintenance list once.
 */
class SnapshotSwapRestoreCommitGatewayTest {
    private val temp = createTempDirectory("restore-commit")
    private val formalRoot = temp.resolve("formal").apply { mkdirs() }
    private val rollbackRoot = temp.resolve("rollback").apply { mkdirs() }
    private val scratchRoot = temp.resolve("scratch").apply { mkdirs() }

    private class FakeDatabase : FormalDataHandle {
        var closed = 0
        var opened = 0
        var quickCheckFails = false
        var checkpointFails = false

        override fun checkpointAndClose(): Boolean {
            if (checkpointFails) return false
            closed += 1
            return true
        }

        override fun reopenAndQuickCheck(): Boolean {
            opened += 1
            return !quickCheckFails
        }
    }

    private class FakeWriter : RestoredDataWriter {
        var fail = false

        override fun writeReplacement(session: StagedRestoreSession, workingDir: File): File? {
            if (fail) return null
            val dir = File(workingDir, "replacement").apply { mkdirs() }
            File(dir, "english-learning.db").writeText("restored-db")
            return dir
        }
    }

    private class FakeActions : PostRestoreActions {
        val calls = mutableListOf<String>()
        var failOn: String? = null

        private fun step(name: String): Boolean {
            calls += name
            return failOn != name
        }

        override fun rebuildSearchIndex(): Boolean = step("index")
        override fun rescheduleReminders(): Boolean = step("reminders")
        override fun eraseAiCredentials(): Boolean = step("keys")
        override fun writeRestoreAudit(session: StagedRestoreSession): Boolean = step("audit")
    }

    private fun session(id: String = "s-1") = StagedRestoreSession(
        sessionId = id,
        recordCount = 1,
        mediaCount = 0,
        plaintextBytes = 10,
        pairingIssuedAtEpochSeconds = 1L,
        pairingExpiresAtEpochSeconds = 2L,
        staged = StagedRestore(emptyList(), emptyList()),
    )

    private fun gateway(
        database: FakeDatabase = FakeDatabase(),
        writer: FakeWriter = FakeWriter(),
        actions: FakeActions = FakeActions(),
        clock: Long = 1_700L,
    ): SnapshotSwapRestoreCommitGateway = SnapshotSwapRestoreCommitGateway(
        rollbackRoot = rollbackRoot,
        scratchRoot = scratchRoot,
        formalRoot = formalRoot,
        database = database,
        writer = writer,
        postActions = actions,
        clockMillis = { clock },
    )

    private fun marker() = formalRoot.resolve("english-learning.db").apply { writeText("formal-db") }

    @Test
    fun `success swaps the replacement in and runs the maintenance list in order`() {
        val formal = marker()
        val actions = FakeActions()
        val database = FakeDatabase()

        val result = gateway(database = database, actions = actions).commit(session())

        assertEquals(RestoreCommitResult.Restored, result)
        assertEquals("restored-db", formalRoot.resolve("english-learning.db").readText())
        assertEquals(listOf("index", "reminders", "keys", "audit"), actions.calls)
        assertEquals(1, database.closed)
        assertTrue(rollbackRoot.resolve("1700").exists(), "rollback point is kept")
    }

    @Test
    fun `quick check failure after the swap rolls the formal root back byte for byte`() {
        val formal = marker()
        val database = FakeDatabase().apply { quickCheckFails = true }

        val result = gateway(database = database).commit(session())

        assertEquals(RestoreFailure.RollbackVerification, assertIs<RestoreCommitResult.Failed>(result).reason)
        assertEquals("formal-db", formal.readText())
        assertTrue(rollbackRoot.resolve("1700").exists())
    }

    @Test
    fun `maintenance failure also rolls back and reports rollback verification`() {
        val formal = marker()

        val result = gateway(actions = FakeActions().apply { failOn = "reminders" }).commit(session())

        assertEquals(RestoreFailure.RollbackVerification, assertIs<RestoreCommitResult.Failed>(result).reason)
        assertEquals("formal-db", formal.readText())
    }

    @Test
    fun `replacement build failure never touches the formal root`() {
        val formal = marker()
        val database = FakeDatabase()

        val result = gateway(writer = FakeWriter().apply { fail = true }, database = database).commit(session())

        assertEquals(RestoreFailure.IntegrityFailure, assertIs<RestoreCommitResult.Failed>(result).reason)
        assertEquals("formal-db", formal.readText())
        assertEquals(0, database.closed)
    }

    @Test
    fun `checkpoint failure leaves everything untouched`() {
        val formal = marker()

        val result = gateway(database = FakeDatabase().apply { checkpointFails = true }).commit(session())

        assertEquals(RestoreFailure.IntegrityFailure, assertIs<RestoreCommitResult.Failed>(result).reason)
        assertEquals("formal-db", formal.readText())
        assertTrue(rollbackRoot.listFiles().isNullOrEmpty())
    }

    @Test
    fun `rollback ledger records the snapshot digests`() {
        marker()
        formalRoot.resolve("assets").apply { mkdirs() }.resolve("asset-1").writeText("media")

        gateway().commit(session())

        val ledger = rollbackRoot.resolve("1700").resolve("rollback-ledger.txt").readText()
        assertTrue("english-learning.db" in ledger && "assets/asset-1" in ledger)
    }

    @Test
    fun `session id collisions get distinct commit working dirs`() {
        marker()
        assertEquals(RestoreCommitResult.Restored, gateway().commit(session("dup")))
        assertEquals(RestoreCommitResult.Restored, gateway(clock = 1_701L).commit(session("dup")))
    }

    private fun createTempDirectory(prefix: String): File =
        File(System.getProperty("java.io.tmpdir"), "$prefix-${System.nanoTime()}").apply { mkdirs() }
}
