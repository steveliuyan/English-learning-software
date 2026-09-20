package com.example.englishlearning.learning

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The placeholder content is what makes F1-03 reachable on a device while no licence-verified
 * word data may be bundled. These tests pin the contract the learning flow relies on: every
 * shipped word book has content, ids are stable and word-book scoped, and every card carries
 * the four fields the spec requires on screen.
 */
class PlaceholderWordCardSourceTest {
    private val source = PlaceholderWordCardSource()

    @Test
    fun `every shipped word book has placeholder content`() = runTest {
        PlaceholderWordCardSource.WORD_BOOK_IDS.forEach { wordBookId ->
            val ids = source.cardIds(wordBookId)

            assertTrue(ids.isNotEmpty(), "expected placeholder content for $wordBookId")
            assertEquals(ids.size, source.cards(ids).size, "every id of $wordBookId must resolve")
        }
    }

    @Test
    fun `an unknown word book has no content`() = runTest {
        assertEquals(emptyList<String>(), source.cardIds("not-a-word-book"))
    }

    @Test
    fun `cards keep the requested order and drop unknown ids`() = runTest {
        val ability = PlaceholderWordCardSource.cardId("cet4", "ability")
        val climate = PlaceholderWordCardSource.cardId("cet4", "climate")

        assertEquals(
            listOf("climate", "ability"),
            source.cards(listOf(climate, ability)).map { it.lemma },
        )
        assertEquals(
            listOf("ability", "climate"),
            source.cards(listOf(ability, "placeholder:cet4:not-a-word", climate)).map { it.lemma },
        )
    }

    @Test
    fun `every card carries the fields the spec requires on screen`() = runTest {
        source.cards(source.cardIds("cet6")).forEach { card ->
            assertTrue(card.lemma.isNotBlank(), "lemma must not be blank")
            assertTrue(card.ipa.isNotBlank(), "ipa of ${card.lemma} must not be blank")
            assertTrue(card.partOfSpeech.isNotBlank(), "part of speech of ${card.lemma} must not be blank")
            assertTrue(card.meaningZh.isNotBlank(), "meaning of ${card.lemma} must not be blank")
            assertEquals("cet6", card.wordBookId)
        }
    }

    @Test
    fun `the same spelling in two word books stays two independent cards`() = runTest {
        val cet4 = PlaceholderWordCardSource.cardId("cet4", "ability")
        val cet6 = PlaceholderWordCardSource.cardId("cet6", "ability")

        assertTrue(cet4 != cet6, "same spelling must not collapse into one card across word books")
        assertEquals("cet4", source.cards(listOf(cet4)).single().wordBookId)
        assertEquals("cet6", source.cards(listOf(cet6)).single().wordBookId)
    }

    @Test
    fun `inflections are optional and only present where provided`() = runTest {
        val cards = source.cards(source.cardIds("cet4")).associateBy { it.lemma }

        assertTrue(cards.getValue("ability").inflections.isEmpty(), "a noun without forms must stay empty")
        assertEquals(listOf("achieved", "achieving", "achieves"), cards.getValue("achieve").inflections)
    }
}
