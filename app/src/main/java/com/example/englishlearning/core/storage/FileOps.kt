package com.example.englishlearning.core.storage

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * File operations constrained to an app-private assets directory by [AndroidFileOps].
 * Paths passed through this boundary are relative asset names only.
 */
interface FileOps {
    fun write(
        path: String,
        source: InputStream,
    ): Long

    fun sha256(path: String): String

    fun atomicMove(
        source: String,
        destination: String,
    )

    fun delete(path: String)

    fun exists(path: String): Boolean
}

class AndroidFileOps(filesDir: File) : FileOps {
    private val assetsDir = File(filesDir, "assets").also { it.mkdirs() }

    override fun write(
        path: String,
        source: InputStream,
    ): Long = FileOutputStream(fileFor(path)).use { output -> source.copyTo(output) }

    override fun sha256(path: String): String =
        FileInputStream(fileFor(path)).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }

    override fun atomicMove(
        source: String,
        destination: String,
    ) {
        val sourceFile = fileFor(source)
        val destinationFile = fileFor(destination)
        try {
            Files.move(
                sourceFile.toPath(),
                destinationFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            throw UnsupportedOperationException("Atomic publication is unavailable")
        }
    }

    override fun delete(path: String) {
        fileFor(path).delete()
    }

    override fun exists(path: String): Boolean = fileFor(path).isFile

    private fun fileFor(path: String): File {
        require(path.matches(Regex("[0-9a-fA-F-]+(\\.tmp)?")))
        return File(assetsDir, path)
    }
}
