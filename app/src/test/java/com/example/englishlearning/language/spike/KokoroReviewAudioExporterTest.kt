package com.example.englishlearning.language.spike

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class KokoroReviewAudioExporterTest {
    @TempDir lateinit var root: File

    @Test
    fun `preserves legacy listening sample filenames`() {
        val bytes = validWav()
        for (word in listOf("apple", "schedule", "record", "workbuddy")) {
            for (accent in listOf("us", "gb")) {
                val output = KokoroReviewAudioExporter.write(root, "$word-$accent.wav", bytes)
                assertContentEquals(bytes, output.readBytes())
            }
        }
    }

    @Test
    fun `exports the four approved noun and verb review filenames`() {
        val bytes = validWav()
        for (filename in listOf("record-noun-us.wav", "record-verb-us.wav", "record-noun-gb.wav", "record-verb-gb.wav")) {
            val output = KokoroReviewAudioExporter.write(root, filename, bytes)
            assertTrue(output.isFile)
            assertContentEquals(bytes, output.readBytes())
        }
    }

    @Test
    fun `rejects unapproved filenames and refuses to overwrite`() {
        val bytes = validWav()
        for (filename in listOf("../outside.wav", "record-verb-xx.wav", "record-noun-us.wav/../outside.wav", "apple-verb-us.wav", "record-adverb-us.wav")) {
            assertFailsWith<IllegalArgumentException>(filename) {
                KokoroReviewAudioExporter.write(root, filename, bytes)
            }
        }
        val existing = File(root, "record-noun-us.wav")
        existing.writeBytes(byteArrayOf(9, 8, 7))
        assertFailsWith<IllegalStateException> {
            KokoroReviewAudioExporter.write(root, existing.name, bytes)
        }
        assertContentEquals(byteArrayOf(9, 8, 7), existing.readBytes())
    }

    private fun validWav(): ByteArray = ByteArray(46).apply {
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(this, 0)
        this[4] = 38
        "WAVEfmt ".toByteArray(Charsets.US_ASCII).copyInto(this, 8)
        this[16] = 16
        this[20] = 1
        this[22] = 1
        this[24] = 0x80.toByte()
        this[25] = 0x5d
        this[28] = 0
        this[29] = 0x77
        this[32] = 2
        this[34] = 16
        "data".toByteArray(Charsets.US_ASCII).copyInto(this, 36)
        this[40] = 2
        this[44] = 0x20
        this[45] = 0x10
    }


}
