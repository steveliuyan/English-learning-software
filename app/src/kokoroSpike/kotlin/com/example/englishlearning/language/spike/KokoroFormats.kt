package com.example.englishlearning.language.spike

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.ZipFile
import java.nio.ByteOrder
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object KokoroFormats {
    // Phonemis MIT固定dense CTC labels，与Kokoro sparse vocab分别审计。
    private const val PHONE_LABELS = "\u0000;:,.!?—…\"()“” ̃ʣʥʦʨᵝꭧAIOQSTWY" +
        "ᵊabcdefhijklmnopqrstuvwxyzɑɐɒæβɔɕçɖðʤəɚɛɜɟɡɥɨɪʝɯɰŋɳɲɴøɸθœɹɾɻʁɽʂʃʈʧʊʋʌɣɤχʎʒʔˈˌːʰʲ↓→↗↘ᵻʑ"

    fun graphemes(text: String): LongArray {
        val word = text.trim().lowercase(Locale.ROOT)
        require(word.length in 1..20 && Regex("[a-z]+(?:'[a-z]+)*").matches(word)) { "单词必须为1至20个英语字母" }
        return LongArray(word.length) { if (word[it] == '\'') 1L else (word[it] - 'a' + 2).toLong() }
    }

    fun decode(logits: Array<Array<FloatArray>>): String {
        require(logits.size == 1 && logits[0].isNotEmpty()) { "frontend shape不合法" }
        val result = StringBuilder()
        var previous = -1
        for (row in logits[0]) {
            require(row.size == 116 && row.all { it.isFinite() }) { "frontend logits不合法" }
            var label = 0
            for (index in 1..115) if (row[index] > row[label]) label = index
            if (label != previous && label != 0) result.append(PHONE_LABELS[label])
            previous = label
        }
        require(result.isNotEmpty()) { "frontend只有blank" }
        return result.toString()
    }

    fun vocab(json: String): Map<Int, Long> {
        val root = Json.parseToJsonElement(json) as? JsonObject
        val mapping = root?.get("vocab") as? JsonObject
        require(mapping != null && mapping.isNotEmpty()) { "vocab缺失" }
        return mapping.entries.associate { (symbol, value) ->
            require(symbol.codePointCount(0, symbol.length) == 1) { "vocab键必须为单codepoint" }
            val point = symbol.codePointAt(0)
            require(point !in 0xD800..0xDFFF) { "vocab键不合法" }
            val primitive = value as? JsonPrimitive
            val id = primitive?.takeUnless { it.isString }?.longOrNull
            require(id != null && id in 1L..177L) { "vocab ID不合法" }
            point to id
        }
    }

    fun npy(input: InputStream): FloatArray {
        val prefix = readExact(input, 10)
        require(prefix.copyOfRange(0, 8).contentEquals(byteArrayOf(0x93.toByte(), 78, 85, 77, 80, 89, 1, 0))) { "NPY版本不支持" }
        val headerSize = (prefix[8].toInt() and 255) or ((prefix[9].toInt() and 255) shl 8)
        require(headerSize == 118) { "NPY header长度不合法" }
        val header = String(readExact(input, headerSize), Charsets.US_ASCII)
        val expected = "{'descr': '<f4', 'fortran_order': False, 'shape': (510, 1, 256), }".padEnd(117, ' ') + "\n"
        require(header == expected) { "NPY dtype/order/shape不合法" }
        val buffer = ByteBuffer.wrap(readExact(input, 522240)).order(ByteOrder.LITTLE_ENDIAN)
        require(input.read() == -1) { "NPY含尾随数据" }
        return FloatArray(510 * 256) {
            buffer.float.also { require(it.isFinite()) { "voice含非有限值" } }
        }
    }

    fun voices(file: File): Map<String, FloatArray> = ZipFile(file).use { zip ->
        val names = mutableSetOf<String>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) require(names.add(entries.nextElement().name)) { "NPZ条目重复" }
        listOf("af_heart.npy", "bf_emma.npy").associateWith { name ->
            val entry = zip.getEntry(name)
            require(entry != null && !entry.isDirectory && entry.size == 522368L) { "voice条目缺失或长度错误" }
            zip.getInputStream(entry).use { npy(it) }
        }
    }

    fun wav(audio: FloatArray): ByteArray {
        require(audio.isNotEmpty() && audio.all { it.isFinite() }) { "音频为空或非有限" }
        require(audio.size <= (Int.MAX_VALUE - 44) / 2) { "音频过大" }
        val pcm = ShortArray(audio.size) { Math.rint((audio[it].coerceIn(-1f, 1f) * 32767f).toDouble()).toInt().toShort() }
        require(pcm.any { it != 0.toShort() }) { "量化音频静音" }
        val payloadSize = pcm.size * 2
        return ByteBuffer.allocate(44 + payloadSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + payloadSize)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1); putShort(1); putInt(24000); putInt(48000); putShort(2); putShort(16)
            put("data".toByteArray(Charsets.US_ASCII)); putInt(payloadSize)
            pcm.forEach { putShort(it) }
        }.array()
    }

    private fun readExact(input: InputStream, size: Int): ByteArray {
        val bytes = ByteArray(size)
        var count = 0
        while (count < size) {
            val read = input.read(bytes, count, size - count)
            require(read > 0) { "NPY截断" }
            count += read
        }
        return bytes
    }
}
