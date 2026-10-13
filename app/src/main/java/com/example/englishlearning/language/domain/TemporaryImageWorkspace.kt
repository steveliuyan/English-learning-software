package com.example.englishlearning.language.domain

import java.io.File
import java.util.UUID

/**
 * Short-term workspace for camera/gallery captures used by photo translation (AC3-05).
 * Captured images are never persistent: every handle is discarded after use, rejection removes the
 * half-written file, and [purgeAll] sweeps crash residue when a session starts or ends.
 */
class TemporaryImageWorkspace(
    private val root: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    class Handle(
        val file: File,
    )

    sealed interface WriteOutcome {
        data object Written : WriteOutcome

        data object RejectedTooLarge : WriteOutcome
    }

    fun allocate(
        extension: String,
    ): Handle {
        root.mkdirs()
        val file = File(root, "${UUID.randomUUID()}.$extension")
        file.createNewFile()
        return Handle(file)
    }

    fun write(
        handle: Handle,
        bytes: ByteArray,
    ): WriteOutcome {
        if (bytes.size.toLong() > maxBytes) {
            discard(handle)
            return WriteOutcome.RejectedTooLarge
        }
        handle.file.writeBytes(bytes)
        return WriteOutcome.Written
    }

    fun read(
        handle: Handle,
    ): ByteArray = handle.file.readBytes()

    fun readOrNull(
        handle: Handle,
    ): ByteArray? = if (handle.file.isFile) handle.file.readBytes() else null

    fun discard(
        handle: Handle,
    ) {
        handle.file.delete()
    }

    /** Runs [block] with a fresh capture slot and guarantees the slot is gone on every exit path. */
    suspend fun <T> useCapture(
        extension: String,
        block:
            suspend (Handle) -> T,
    ): T {
        val handle = allocate(extension)
        return try {
            block(handle)
        } finally {
            discard(handle)
        }
    }

    /** Removes every file in the workspace, including orphans from a previous crashed process. */
    fun purgeAll(): Int {
        if (!root.isDirectory) return 0
        val files = root.walkBottomUp().filter { it.isFile }.toList()
        files.forEach { it.delete() }
        return files.count { !it.exists() }
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 20L * 1024 * 1024
    }
}
