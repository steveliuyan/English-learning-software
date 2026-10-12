package com.example.englishlearning.backup

import com.example.englishlearning.backup.domain.BackupMediaOutcome
import com.example.englishlearning.backup.infrastructure.FileOpsBackupMediaGateway
import com.example.englishlearning.core.storage.FileOps
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class MemoryFileOps(
    private var files: MutableMap<String, ByteArray> = mutableMapOf(),
    private val failReadWith: Exception? = null,
) : FileOps {
    var readCalls = 0
        private set

    override fun write(path: String, source: InputStream): Long {
        val bytes = source.readBytes()
        files[path] = bytes
        return bytes.size.toLong()
    }

    override fun sha256(path: String): String =
        MessageDigest.getInstance("SHA-256").digest(files.getValue(path)).joinToString("") { "%02x".format(it) }

    override fun atomicMove(source: String, destination: String) {
        files[destination] = files.getValue(source)
        files.remove(source)
    }

    override fun delete(path: String) {
        files.remove(path)
    }

    override fun exists(path: String) = path in files

    override fun read(path: String): ByteArray {
        readCalls += 1
        failReadWith?.let { throw it }
        return files[path] ?: throw FileNotFoundException(path)
    }
}

class FileOpsBackupMediaGatewayTest {
    private val asset = AssetRecordEntity(id = "asset-1", sha256 = sha256(PAYLOAD), relativePath = "assets/asset-1", byteSize = PAYLOAD.size.toLong(), createdAt = 1L)

    @Test
    fun `returns bytes when size and sha match the record`() = runTest {
        val gateway = FileOpsBackupMediaGateway(MemoryFileOps(mutableMapOf("asset-1" to PAYLOAD.copyOf(PAYLOAD.size))))

        val outcome = gateway.load(asset)

        val loaded = assertIs<BackupMediaOutcome.Loaded>(outcome)
        assertEquals(PAYLOAD.toList(), loaded.bytes.toList())
    }

    @Test
    fun `absent file reports missing`() = runTest {
        val gateway = FileOpsBackupMediaGateway(MemoryFileOps())

        assertIs<BackupMediaOutcome.Missing>(gateway.load(asset))
    }

    @Test
    fun `tampered bytes report mismatch instead of exporting wrong content`() = runTest {
        val gateway = FileOpsBackupMediaGateway(MemoryFileOps(mutableMapOf("asset-1" to byteArrayOf(9, 9, 9))))

        assertIs<BackupMediaOutcome.Mismatch>(gateway.load(asset))
    }

    @Test
    fun `size divergence reports mismatch even when sha is unverified`() = runTest {
        val gateway = FileOpsBackupMediaGateway(MemoryFileOps(mutableMapOf("asset-1" to PAYLOAD.copyOf(PAYLOAD.size + 1))))

        assertIs<BackupMediaOutcome.Mismatch>(gateway.load(asset))
    }

    @Test
    fun `io failure reports missing so the export fails closed`() = runTest {
        val gateway = FileOpsBackupMediaGateway(MemoryFileOps(failReadWith = FileNotFoundException("x")))

        assertIs<BackupMediaOutcome.Missing>(gateway.load(asset))
    }

    @Test
    fun `sha comparison is case insensitive`() = runTest {
        val upperAsset = asset.copy(sha256 = sha256(PAYLOAD).uppercase())
        val gateway = FileOpsBackupMediaGateway(MemoryFileOps(mutableMapOf("asset-1" to PAYLOAD.copyOf(PAYLOAD.size))))

        assertIs<BackupMediaOutcome.Loaded>(gateway.load(upperAsset))
    }

    @Test
    fun `empty asset record list still round-trips through write for fixtures`() {
        val ops = MemoryFileOps()
        ops.write("asset-2", ByteArrayInputStream(byteArrayOf(1)))
        assertTrue(ops.exists("asset-2"))
    }

    companion object {
        private val PAYLOAD = byteArrayOf(1, 2, 3)

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
