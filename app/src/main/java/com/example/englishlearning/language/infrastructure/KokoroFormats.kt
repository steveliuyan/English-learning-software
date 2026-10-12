package com.example.englishlearning.language.infrastructure

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

internal object KokoroFormats {
    private const val PHONE_LABELS = "\u0000;:,.!?—…\"()“” ̃ʣʥʦʨᵝꭧAIOQSTWY" + "ᵊabcdefhijklmnopqrstuvwxyzɑɐɒæβɔɕçɖðʤəɚɛɜɟɡɥɨɪʝɯɰŋɳɲɴøɸθœɹɾɻʁɽʂʃʈʧʊʋʌɣɤχʎʒʔˈˌːʰʲ↓→↗↘ᵻʑ"
    fun graphemes(text: String): LongArray { val word = text.trim().lowercase(Locale.ROOT); require(word.length in 1..20 && Regex("[a-z]+(?:'[a-z]+)*").matches(word)); return LongArray(word.length) { if (word[it] == '\'') 1L else (word[it] - 'a' + 2).toLong() } }
    fun decode(logits: Array<Array<FloatArray>>): String { require(logits.size == 1 && logits[0].isNotEmpty()); val result = StringBuilder(); var previous = -1; for (row in logits[0]) { require(row.size == 116 && row.all { it.isFinite() }); var label = 0; for (index in 1..115) if (row[index] > row[label]) label = index; if (label != previous && label != 0) result.append(PHONE_LABELS[label]); previous = label }; require(result.isNotEmpty()); return result.toString() }
    fun vocab(json: String): Map<Int, Long> { val root = Json.parseToJsonElement(json) as? JsonObject; val mapping = root?.get("vocab") as? JsonObject; require(mapping != null && mapping.isNotEmpty()); return mapping.entries.associate { (symbol, value) -> val primitive = value as? JsonPrimitive; val id = primitive?.takeUnless { it.isString }?.longOrNull; require(symbol.codePointCount(0, symbol.length) == 1 && id != null && id in 1L..177L); symbol.codePointAt(0) to id } }
    fun npy(input: InputStream): FloatArray { val prefix = readExact(input, 10); require(prefix.copyOfRange(0, 8).contentEquals(byteArrayOf(0x93.toByte(), 78, 85, 77, 80, 89, 1, 0))); val headerSize = (prefix[8].toInt() and 255) or ((prefix[9].toInt() and 255) shl 8); require(headerSize == 118); val header = String(readExact(input, headerSize), Charsets.US_ASCII); val expected = "{'descr': '<f4', 'fortran_order': False, 'shape': (510, 1, 256), }".padEnd(117, ' ') + "\n"; require(header == expected); val buffer = ByteBuffer.wrap(readExact(input, 522240)).order(ByteOrder.LITTLE_ENDIAN); require(input.read() == -1); return FloatArray(510 * 256) { buffer.float.also { require(it.isFinite()) } } }
    fun voices(file: File): Map<String, FloatArray> = ZipFile(file).use { zip -> listOf("af_heart.npy", "bf_emma.npy").associateWith { name -> val entry = zip.getEntry(name); require(entry != null && !entry.isDirectory && entry.size == 522368L); zip.getInputStream(entry).use(::npy) } }
    fun wav(audio: FloatArray): ByteArray { require(audio.isNotEmpty() && audio.all { it.isFinite() }); val pcm = ShortArray(audio.size) { Math.rint((audio[it].coerceIn(-1f, 1f) * 32767f).toDouble()).toInt().toShort() }; require(pcm.any { it != 0.toShort() }); val payload = pcm.size * 2; return ByteBuffer.allocate(44 + payload).order(ByteOrder.LITTLE_ENDIAN).apply { put("RIFF".toByteArray()); putInt(36 + payload); put("WAVEfmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(24000); putInt(48000); putShort(2); putShort(16); put("data".toByteArray()); putInt(payload); pcm.forEach(::putShort) }.array() }
    private fun readExact(input: InputStream, size: Int): ByteArray { val bytes = ByteArray(size); var count = 0; while (count < size) { val read = input.read(bytes, count, size - count); require(read > 0); count += read }; return bytes }
}
