package com.example.englishlearning.language.spike

import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class KokoroLexiconResolverTest {
    @Test fun `pinned record fixture exposes only two accents and two entries`() {
        val bytes = File("src/androidTest/assets/kokoro-lexicon-fixture/record-pos.json").readBytes()
        val entries = KokoroRecordFixture.parse(bytes)
        assertEquals(setOf("en-us", "en-gb"), entries.keys)
        for (accent in entries.keys) {
            assertEquals(setOf("record", "record|VERB"), entries.getValue(accent).keys)
        }
    }

    @Test fun `pinned record fixture rejects changed bytes`() {
        val bytes = File("src/androidTest/assets/kokoro-lexicon-fixture/record-pos.json").readBytes()
        bytes[0] = '['.code.toByte()
        assertFailsWith<IllegalArgumentException> { KokoroRecordFixture.parse(bytes) }
    }

    @Test fun `pinned record fixture rejects trailing data`() {
        val bytes = File("src/androidTest/assets/kokoro-lexicon-fixture/record-pos.json").readBytes()
        assertFailsWith<IllegalArgumentException> { KokoroRecordFixture.parse(bytes + byteArrayOf(32)) }
    }

    @Test fun `exact tagged key wins over family and bare keys`() {
        val input = KokoroWordInput(" Record ", "VB")
        val entries = mapOf("record|VB" to "exact", "record|VERB" to "family", "record" to "bare")
        assertEquals(
            KokoroLexiconResolution("exact", "record|VB"),
            KokoroLexiconResolver.resolve(input.text, "en-us", input.partOfSpeechTag, entries),
        )
    }

    @Test fun `family key wins over bare when exact tag is absent`() {
        val entries = mapOf("record|VERB" to "family", "record" to "bare")
        assertEquals(KokoroLexiconResolution("family", "record|VERB"), KokoroLexiconResolver.resolve("record", "en-us", "VB", entries))
    }

    @Test fun `audited record VB fixture resolves through VERB for both accents`() {
        val fixture = Json.parseToJsonElement(File("src/androidTest/assets/kokoro-lexicon-fixture/record-pos.json").readText()).jsonObject
        for (accent in listOf("en-us", "en-gb")) {
            val entries = fixture.getValue("entries").jsonObject.getValue(accent).jsonObject.mapValues { it.value.jsonPrimitive.content }
            assertEquals(setOf("record|VERB", "record"), entries.keys)
            assertEquals(KokoroLexiconResolution(entries.getValue("record|VERB"), "record|VERB"), KokoroLexiconResolver.resolve("record", accent, "VB", entries))
            assertEquals(KokoroLexiconResolution(entries.getValue("record"), "record"), KokoroLexiconResolver.resolve("record", accent, null, entries))
        }
    }

    @Test fun `all audited prefixes map to their POS family`() {
        for ((tag, family) in listOf("VBD" to "VERB", "VBG" to "VERB", "VBZ" to "VERB", "NNS" to "NOUN", "NNP" to "NOUN", "JJR" to "ADJ", "JJS" to "ADJ", "RBR" to "ADV", "RBS" to "ADV")) {
            val entries = mapOf("record|$family" to "family", "record" to "bare")
            assertEquals(KokoroLexiconResolution("family", "record|$family"), KokoroLexiconResolver.resolve("record", "en-gb", tag, entries), tag)
        }
    }

    @Test fun `exact inflected tag still wins over its mapped family`() {
        val entries = mapOf("record|VBD" to "exact", "record|VERB" to "family", "record" to "bare")
        assertEquals(KokoroLexiconResolution("exact", "record|VBD"), KokoroLexiconResolver.resolve("record", "en-us", "VBD", entries))
    }

    @Test fun `null tag uses bare word even when tagged rows exist`() {
        val entries = mapOf("record|VB" to "exact", "record|VERB" to "family", "record" to "bare")
        assertEquals(KokoroLexiconResolution("bare", "record"), KokoroLexiconResolver.resolve(" Record ", "en-gb", null, entries))
    }

    @Test fun `unknown valid tag tries exact then bare without guessing a family`() {
        val entries = mapOf("record|DT" to "exact", "record|NOUN" to "noun", "record|VERB" to "verb", "record" to "bare")
        assertEquals(KokoroLexiconResolution("exact", "record|DT"), KokoroLexiconResolver.resolve("record", "en-us", "DT", entries))
        assertEquals(KokoroLexiconResolution("bare", "record"), KokoroLexiconResolver.resolve("record", "en-us", "DT", entries - "record|DT"))
        assertNull(KokoroLexiconResolver.resolve("record", "en-us", "DT", entries - "record|DT" - "record"))
    }

    @Test fun `tagged lookup falls back to bare if exact and family are absent`() {
        assertEquals(KokoroLexiconResolution("bare", "record"), KokoroLexiconResolver.resolve("record", "en-us", "VB", mapOf("record" to "bare")))
    }

    @Test fun `missing word returns null for tagged and untagged inputs`() {
        for (tag in listOf(null, "VB", "DT")) {
            assertNull(KokoroLexiconResolver.resolve("record", "en-us", tag, emptyMap()))
            assertNull(KokoroLexiconResolver.resolve("record", "en-gb", tag, mapOf("other|VB" to "other")))
        }
    }

    @Test fun `normalization uses ROOT locale and frontend word boundaries`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(KokoroLexiconResolution("bare", "i"), KokoroLexiconResolver.resolve(" I ", "en-us", null, mapOf("i" to "bare")))
        } finally {
            Locale.setDefault(originalLocale)
        }
        assertEquals(KokoroLexiconResolution("bare", "can't"), KokoroLexiconResolver.resolve(" CAN'T ", "en-gb", null, mapOf("can't" to "bare")))
        assertEquals(KokoroLexiconResolution("bare", "aaaaaaaaaaaaaaaaaaaa"), KokoroLexiconResolver.resolve("aaaaaaaaaaaaaaaaaaaa", "en-us", null, mapOf("aaaaaaaaaaaaaaaaaaaa" to "bare")))
    }

    @Test fun `invalid word is rejected before any matching row is returned`() {
        for (word in listOf("", " ", "a".repeat(21), "a1", "two words", "é", "'a", "a'", "a''b", "a\uD83D\uDE00", "record|VB")) {
            assertFailsWith<IllegalArgumentException>(word) { KokoroLexiconResolver.resolve(word, "en-us", null, mapOf(word to "bare")) }
        }
    }

    @Test fun `unsupported accent is rejected even when word exists`() {
        for (accent in listOf("", "en", "en-US", "en-au", " en-us ")) {
            assertFailsWith<IllegalArgumentException>(accent) { KokoroLexiconResolver.resolve("record", accent, "VB", mapOf("record|VB" to "exact")) }
        }
    }

    @Test fun `invalid tag is rejected rather than silently normalized or ignored`() {
        for (tag in listOf("", " ", "vb", "Vb", " VB", "VB ", "VB|NOUN", "VB1", "V B", "V\nB", "动词")) {
            assertFailsWith<IllegalArgumentException>(tag) { KokoroLexiconResolver.resolve("record", "en-us", tag, mapOf("record" to "bare", "record|$tag" to "exact")) }
        }
    }

    @Test fun `empty pronunciation on winning row is rejected rather than falling back`() {
        assertFailsWith<IllegalArgumentException> { KokoroLexiconResolver.resolve("record", "en-us", "VB", mapOf("record|VB" to "", "record" to "bare")) }
    }
}
