package com.example.englishlearning.core.storage

import com.example.englishlearning.core.error.AppError
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrivateMediaStoreTest {
    @Test
    fun `hash mismatch leaves no final asset or temporary file`() =
        runTest {
            val files = FakeFileOps()
            val store = PrivateMediaStore(files, StandardTestDispatcher(testScheduler))

            val result =
                store.writeVerified(
                    assetId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    source = ByteArrayInputStream("tampered".encodeToByteArray()),
                    expectedSha256 = "00",
                )

            assertTrue(result.isFailure)
            assertEquals(emptySet(), files.finalFiles())
            assertEquals(emptySet(), files.temporaryFiles())
        }

    @Test
    fun `missing or hash mismatched asset is rebuildable`() {
        val files = FakeFileOps()
        val store = PrivateMediaStore(files, StandardTestDispatcher())

        assertEquals(
            MediaAvailability.UnavailableRebuildable,
            store.availability(AssetRecord("missing", sha256("content"))),
        )

        files.seed("present", "tampered".encodeToByteArray())
        assertEquals(
            MediaAvailability.UnavailableRebuildable,
            store.availability(AssetRecord("present", sha256("content"))),
        )
    }

    @Test
    fun `insufficient storage removes existing final and temporary file`() =
        runTest {
            val assetId = UUID.randomUUID()
            val files = FakeFileOps(writeFailure = IOException("no space"))
            files.seed(assetId.toString(), "previous".encodeToByteArray())
            val store = PrivateMediaStore(files, StandardTestDispatcher(testScheduler))

            val result = store.writeVerified(assetId, ByteArrayInputStream(byteArrayOf(1)), "00")

            assertEquals(AppError.StorageInsufficient(0), (result.exceptionOrNull() as AppErrorException).appError)
            assertEquals(emptySet(), files.finalFiles())
            assertEquals(emptySet(), files.temporaryFiles())
        }

    @Test
    fun `hash lookup failure after existence check is rebuildable`() {
        val files = FakeFileOps(hashFailure = IOException("removed"))
        files.seed("present", "content".encodeToByteArray())
        val store = PrivateMediaStore(files, StandardTestDispatcher())

        assertEquals(
            MediaAvailability.UnavailableRebuildable,
            store.availability(AssetRecord("present", sha256("content"))),
        )
    }
}

private class FakeFileOps(
    private val writeFailure: IOException? = null,
    private val hashFailure: IOException? = null,
) : FileOps {
    private val files = mutableMapOf<String, ByteArray>()

    override fun write(
        path: String,
        source: java.io.InputStream,
    ): Long {
        writeFailure?.let { throw it }
        files[path] = source.readBytes()
        return files.getValue(path).size.toLong()
    }

    override fun sha256(path: String): String {
        hashFailure?.let { throw it }
        return sha256(files.getValue(path))
    }

    override fun atomicMove(
        source: String,
        destination: String,
    ) {
        files[destination] = files.remove(source) ?: error("missing source")
    }

    override fun delete(path: String) {
        files.remove(path)
    }

    override fun exists(path: String): Boolean = path in files

    fun finalFiles(): Set<String> = files.keys.filterNot { it.endsWith(".tmp") }.toSet()

    fun temporaryFiles(): Set<String> = files.keys.filter { it.endsWith(".tmp") }.toSet()

    fun seed(
        path: String,
        content: ByteArray,
    ) {
        files[path] = content
    }
}

private fun sha256(value: String): String = sha256(value.encodeToByteArray())

private fun sha256(value: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(it) }
