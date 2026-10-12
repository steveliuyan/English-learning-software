package com.example.englishlearning.language.spike

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KokoroFormatsTest {
    @Test fun `graphemes preserve apostrophe and ascii boundaries`() {
        assertContentEquals(longArrayOf(2, 17, 17, 13, 6), KokoroFormats.graphemes(" Apple "))
        assertContentEquals(longArrayOf(4, 2, 15, 1, 21), KokoroFormats.graphemes("can't"))
        assertContentEquals(LongArray(20) { 2 }, KokoroFormats.graphemes("a".repeat(20)))
        assertContentEquals(longArrayOf(27), KokoroFormats.graphemes("Z"))
        for (text in listOf("", " ", "a".repeat(21), "a1", "two words", "é", "'a", "a'", "a''b", "a\uD83D\uDE00")) {
            assertFailsWith<IllegalArgumentException>(text) { KokoroFormats.graphemes(text) }
        }
    }

    @Test fun `ctc collapses adjacent labels before blank removal and ties use first`() {
        assertEquals("pp", KokoroFormats.decode(logits(45, 45, 0, 45, 45)))
        assertEquals("sˈɛʤul", KokoroFormats.decode(logits(48, 105, 69, 66, 50, 41)))
        val tied = logits(31); tied[0][0][32] = 10f
        assertEquals("a", KokoroFormats.decode(tied))
    }

    @Test fun `all 116 dense labels independently decode including final symbol`() {
        val labels = "\u0000;:,.!?—…\"()“” ̃ʣʥʦʨᵝꭧAIOQSTWY" +
            "ᵊabcdefhijklmnopqrstuvwxyzɑɐɒæβɔɕçɖðʤəɚɛɜɟɡɥɨɪʝɯɰŋɳɲɴøɸθœɹɾɻʁɽʂʃʈʧʊʋʌɣɤχʎʒʔˈˌːʰʲ↓→↗↘ᵻʑ"
        assertEquals(116, labels.length)
        for (id in 1..115) assertEquals(labels[id].toString(), KokoroFormats.decode(logits(id)), "label$id")
    }

    @Test fun `ctc rejects malformed shape blank and nonfinite nonwinning values`() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val data = logits(31); data[0][0][32] = value
            assertFailsWith<IllegalArgumentException> { KokoroFormats.decode(data) }
        }
        for (data in listOf(emptyArray(), arrayOf(emptyArray()), arrayOf(arrayOf(FloatArray(115))), arrayOf(arrayOf(FloatArray(117))), arrayOf(arrayOf(FloatArray(116)), arrayOf(FloatArray(116))), logits(0, 0))) {
            assertFailsWith<IllegalArgumentException> { KokoroFormats.decode(data) }
        }
    }

    @Test fun `vocab preserves sparse ids and supplementary codepoints`() {
        assertEquals(mapOf(97 to 43L, 0x1F600 to 177L), KokoroFormats.vocab("""{"vocab":{"a":43,"\uD83D\uDE00":177}}"""))
        for (json in listOf("{}", """{"vocab":{}}""", """{"vocab":{"ab":1}}""", """{"vocab":{"\uD800":1}}""", """{"vocab":{"a":0}}""", """{"vocab":{"a":178}}""", """{"vocab":{"a":1.5}}""", """{"vocab":{"a":"43"}}""")) {
            assertFailsWith<IllegalArgumentException>(json) { KokoroFormats.vocab(json) }
        }
    }

    @Test fun `npy reads exact little endian row values`() {
        val values = KokoroFormats.npy(ByteArrayInputStream(npy()))
        assertEquals(130560, values.size)
        assertEquals(512f, values[512])
        assertEquals(513f, values[513])
        assertEquals(130559f, values.last())
    }

    @Test fun `npy rejects shape endian fortran oversized header truncation trailing and nonfinite`() {
        val original = npy()
        val variants = mutableListOf(original.copyOf(522367), original + 0.toByte())
        variants += npy("{'descr': '>f4', 'fortran_order': False, 'shape': (510, 1, 256), }")
        variants += npy("{'descr': '<f4', 'fortran_order': True, 'shape': (510, 1, 256), }")
        variants += npy("{'descr': '<f4', 'fortran_order': False, 'shape': (509, 1, 256), }")
        variants += original.copyOf().also { it[8] = 0xff.toByte(); it[9] = 0x7f }
        variants += original.copyOf().also { it[0] = 0 }
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) variants += original.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putFloat(128, value) }
        for (bytes in variants) assertFailsWith<IllegalArgumentException> { KokoroFormats.npy(ByteArrayInputStream(bytes)) }
    }

    @Test fun `wav emits mono24k pcm16 clips and rounds ties to even`() {
        val wav = KokoroFormats.wav(floatArrayOf(0f, .25f, -.25f, 2f, -2f, .5f / 32767f, 1.5f / 32767f, 2.5f / 32767f))
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(60, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(52, b.getInt(4)); assertEquals("WAVEfmt ", String(wav, 8, 8, Charsets.US_ASCII))
        assertEquals(16, b.getInt(16)); assertEquals(1, b.getShort(20).toInt()); assertEquals(1, b.getShort(22).toInt())
        assertEquals(24000, b.getInt(24)); assertEquals(48000, b.getInt(28)); assertEquals(2, b.getShort(32).toInt()); assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII)); assertEquals(16, b.getInt(40))
        assertContentEquals(shortArrayOf(0, 8192, -8192, 32767, -32767, 0, 2, 2), ShortArray(8) { b.getShort(44 + it * 2) })
    }

    @Test fun `wav rejects empty nonfinite silence and quantized silence`() {
        for (audio in listOf(floatArrayOf(), floatArrayOf(Float.NaN), floatArrayOf(Float.POSITIVE_INFINITY), floatArrayOf(0f), floatArrayOf(1e-6f, -1e-6f))) {
            assertFailsWith<IllegalArgumentException> { KokoroFormats.wav(audio) }
        }
    }

    private fun logits(vararg ids: Int): Array<Array<FloatArray>> = arrayOf(Array(ids.size) { step -> FloatArray(116) { if (it == ids[step]) 10f else -10f } })
    private fun npy(fields: String = "{'descr': '<f4', 'fortran_order': False, 'shape': (510, 1, 256), }"): ByteArray =
        ByteBuffer.allocate(522368).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(byteArrayOf(0x93.toByte(), 78, 85, 77, 80, 89, 1, 0)); putShort(118)
            put((fields.padEnd(117, ' ') + "\n").toByteArray(Charsets.US_ASCII))
            repeat(510 * 256) { putFloat(it.toFloat()) }
        }.array()
}
