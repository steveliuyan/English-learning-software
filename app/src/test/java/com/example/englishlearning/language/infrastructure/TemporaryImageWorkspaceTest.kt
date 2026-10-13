package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.TemporaryImageWorkspace
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * AC3-05 lifecycle contract: captured images live only in the short-term workspace and every exit
 * path (success, cancel, failure, oversized input, crash residue) must leave zero files behind.
 */
class TemporaryImageWorkspaceTest {
    @TempDir
    lateinit var root: File

    private fun residue(): List<File> = root.walkBottomUp().filter { it.isFile }.toList()

    @AfterEach
    fun tearDown() {
        assertTrue(residue().isEmpty(), "Captured image residue after test: ${residue()}")
    }

    @Test
    fun `allocate creates one tracked file inside the workspace`() = runTest {
        val workspace = TemporaryImageWorkspace(root)

        val handle = workspace.allocate("jpg")

        assertEquals(1, residue().size)
        assertEquals(handle.file.parent, root.path)
        assertTrue(handle.file.name.endsWith(".jpg"))
        workspace.discard(handle)
    }

    @Test
    fun `write then read round-trips bytes and discard removes the file`() = runTest {
        val workspace = TemporaryImageWorkspace(root)
        val handle = workspace.allocate("jpg")

        workspace.write(handle, byteArrayOf(1, 2, 3, 4))

        assertIs<TemporaryImageWorkspace.WriteOutcome.Written>(workspace.write(handle, byteArrayOf(5)))
        assertEquals(listOf<Byte>(5), workspace.read(handle).toList())
        workspace.discard(handle)
        assertTrue(workspace.readOrNull(handle) == null)
    }

    @Test
    fun `oversized capture is rejected and leaves no file behind`() = runTest {
        val workspace = TemporaryImageWorkspace(root, maxBytes = 4L)
        val handle = workspace.allocate("jpg")

        val outcome = workspace.write(handle, ByteArray(5))

        assertIs<TemporaryImageWorkspace.WriteOutcome.RejectedTooLarge>(outcome)
        // Rejection discards the slot immediately: the caller never sees a half-written capture.
        assertTrue(residue().isEmpty())
    }

    @Test
    fun `useCapture cleans up after a successful recognition run`() = runTest {
        val workspace = TemporaryImageWorkspace(root)

        val result = workspace.useCapture("png") { handle ->
            workspace.write(handle, byteArrayOf(9, 9))
            "translated"
        }

        assertEquals("translated", result)
        assertTrue(residue().isEmpty())
    }

    @Test
    fun `useCapture cleans up when the caller cancels by throwing`() = runTest {
        val workspace = TemporaryImageWorkspace(root)

        val failure = runCatching {
            workspace.useCapture("jpg") { handle ->
                workspace.write(handle, byteArrayOf(1))
                throw IOException("user cancelled")
            }
        }.exceptionOrNull()

        assertIs<IOException>(failure)
        assertTrue(residue().isEmpty())
    }

    @Test
    fun `useCapture cleans up when the recognition engine crashes`() = runTest {
        val workspace = TemporaryImageWorkspace(root)

        val failure = runCatching {
            workspace.useCapture("jpg") { handle ->
                workspace.write(handle, byteArrayOf(1))
                error("native crash")
            }
        }.exceptionOrNull()

        assertIs<IllegalStateException>(failure)
        assertTrue(residue().isEmpty())
    }

    @Test
    fun `useCapture cleans up when the coroutine is cancelled`() = runTest {
        val workspace = TemporaryImageWorkspace(root)

        val job = launch {
            workspace.useCapture<Nothing>("jpg") { handle ->
                workspace.write(handle, byteArrayOf(1))
                throw kotlinx.coroutines.CancellationException("screen left")
            }
        }
        job.join()

        assertTrue(residue().isEmpty())
    }

    @Test
    fun `purgeAll sweeps crash residue including foreign files`() = runTest {
        val workspace = TemporaryImageWorkspace(root)
        val handle = workspace.allocate("jpg")
        workspace.write(handle, byteArrayOf(1, 2))
        // Simulate a previous process that died: an orphan the new workspace never tracked.
        File(root, "orphan-leftover.jpg").writeBytes(byteArrayOf(3))

        val purged = workspace.purgeAll()

        assertEquals(2, purged)
        assertTrue(residue().isEmpty())
    }

    @Test
    fun `handles from separate allocations never collide`() = runTest {
        val workspace = TemporaryImageWorkspace(root)

        val first = workspace.allocate("jpg")
        val second = workspace.allocate("jpg")
        workspace.write(first, byteArrayOf(1))
        workspace.write(second, byteArrayOf(2))

        assertEquals(listOf<Byte>(1), workspace.read(first).toList())
        assertEquals(listOf<Byte>(2), workspace.read(second).toList())
        workspace.discard(first)
        workspace.discard(second)
    }
}
