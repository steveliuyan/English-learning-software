package com.example.englishlearning.backup

import com.example.englishlearning.backup.infrastructure.FileRestoreStagingWorkspace
import com.example.englishlearning.backup.restore.RestoredRecord
import com.example.englishlearning.backup.restore.RestoredResource
import com.example.englishlearning.backup.restore.StagedRestore
import com.example.englishlearning.backup.restore.StagedRestoreSession
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The device scratch zone mirrors package paths under an isolated session directory. */
class FileRestoreStagingWorkspaceTest {
    private val root = File(System.getProperty("java.io.tmpdir"), "restore-scratch-${System.nanoTime()}")

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    private fun session(id: String = "s-1") = StagedRestoreSession(
        sessionId = id,
        recordCount = 2,
        mediaCount = 1,
        plaintextBytes = 3,
        pairingIssuedAtEpochSeconds = 1L,
        pairingExpiresAtEpochSeconds = 2L,
        staged = StagedRestore(
            records = listOf(RestoredRecord("profiles", "p-1", "{\"id\":\"p-1\"}".toByteArray())),
            resources = listOf(RestoredResource("asset-1", byteArrayOf(9))),
        ),
    )

    @Test
    fun `staging writes records and resources into the isolated session directory`() {
        val workspace = FileRestoreStagingWorkspace(root)

        assertTrue(workspace.stage(session()))
        assertTrue(workspace.exists("s-1"))
        assertEquals(
            "{\"id\":\"p-1\"}",
            root.resolve("s-1/records/profiles/p-1.json").readText(),
        )
        assertTrue(root.resolve("s-1/resources/asset-1").exists())
    }

    @Test
    fun `restaging the same session id replaces the previous directory`() {
        val workspace = FileRestoreStagingWorkspace(root)
        workspace.stage(session())
        root.resolve("s-1/stale.txt").writeText("junk")

        assertTrue(workspace.stage(session()))
        assertFalse(root.resolve("s-1/stale.txt").exists())
    }

    @Test
    fun `clear removes the whole session directory`() {
        val workspace = FileRestoreStagingWorkspace(root)
        workspace.stage(session())

        workspace.clear("s-1")

        assertFalse(workspace.exists("s-1"))
    }
}
