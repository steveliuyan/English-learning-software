package com.example.englishlearning.core.storage

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class AndroidFileOpsReadTest {
    @Test
    fun `read returns the exact bytes written for an asset name`() {
        val dir = Files.createTempDirectory("elp-files").toFile()
        val ops = AndroidFileOps(dir)
        val payload = byteArrayOf(1, 2, 3, 4)
        ops.write("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", ByteArrayInputStream(payload))

        assertContentEquals(payload, ops.read("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
    }

    @Test
    fun `read of unknown asset name fails with io error`() {
        val dir = Files.createTempDirectory("elp-files").toFile()
        val ops = AndroidFileOps(dir)

        assertFailsWith<IOException> { ops.read("ffffffff-0000-0000-0000-000000000000") }
    }

    @Test
    fun `read rejects paths outside the asset naming rules`() {
        val dir = Files.createTempDirectory("elp-files").toFile()
        val ops = AndroidFileOps(dir)

        assertFailsWith<IllegalArgumentException> { ops.read("../secret") }
    }
}
