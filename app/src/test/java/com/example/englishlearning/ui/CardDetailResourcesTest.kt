package com.example.englishlearning.ui

import com.example.englishlearning.R
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for the closed lemma → drawable mapping used by [CardDetailScreen].
 *
 * The mapping must be a pure function with no side effects (no decoding, no I/O):
 * a known bundled lemma resolves to its illustration resource, an unknown lemma
 * resolves to null (signalling "hide the image module"), and lookup is
 * case-insensitive.
 *
 * The assets themselves are lossless WebP bitmaps under `res/drawable-nodpi`, so
 * these tests can only assert the mapping — the bytes are verified by opening the
 * detail screen on a device, not here.
 */
class CardDetailResourcesTest {

    @Test
    fun `known placeholder lemma resolves to its illustration`() {
        assertEquals(R.drawable.illus_ability, lemmaToDrawableRes("ability"))
        assertEquals(R.drawable.illus_reduce, lemmaToDrawableRes("reduce"))
        assertEquals(R.drawable.illus_influence, lemmaToDrawableRes("influence"))
    }

    @Test
    fun `every placeholder lemma has a bundled illustration`() {
        listOf(
            "ability", "achieve", "benefit", "climate", "develop", "economy",
            "feature", "generous", "influence", "maintain", "obvious", "reduce",
        ).forEach { lemma ->
            assertNotNull(lemmaToDrawableRes(lemma), "no illustration mapped for placeholder lemma '$lemma'")
        }
    }

    @Test
    fun `unknown lemma resolves to null`() {
        assertNull(lemmaToDrawableRes("nonexistentword"))
        assertNull(lemmaToDrawableRes(""))
    }

    @Test
    fun `mapping is case insensitive`() {
        assertEquals(R.drawable.illus_ability, lemmaToDrawableRes("Ability"))
        assertEquals(R.drawable.illus_ability, lemmaToDrawableRes("ABILITY"))
    }
}
