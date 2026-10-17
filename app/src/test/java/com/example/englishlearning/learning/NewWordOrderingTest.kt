package com.example.englishlearning.learning

import com.example.englishlearning.wordbook.WordBookPackageParser
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Test

/**
 * Spec F1-02 (AC1-23/AC1-24): new cards must not march through the word book in its stored
 * order — the bundled books are alphabetical, so "finish every A before the first B" is both
 * tedious and, by the interleaving literature, worse for retention because it piles up
 * confusable neighbours (abandon / ability / able) back to back.
 *
 * The contract under test is a *spacing guarantee*, not "looks random": entries sharing the
 * first three letters of their lemma must stay at least `floor(n / M)` positions apart
 * (n = book size, M = the largest prefix group), the order must be a permutation reproducible
 * from the content alone, and it must not depend on the order the ids arrive in.
 */
class NewWordOrderingTest {
    @Test
    fun `order is deterministic across calls and a permutation of input`() {
        val book = sampleBook()

        val first = NewWordOrdering.order(book)
        val second = NewWordOrdering.order(book)

        assertEquals(first, second, "the same book must produce the same order on every call")
        assertEquals(book.size, first.size, "no entry may be dropped or invented")
        assertEquals(book.size, first.distinct().size, "no entry may appear twice")
        assertEquals(book.sorted(), first.sorted(), "the output must be a permutation of the input")
    }

    @Test
    fun `order differs from the given order and spaces shared-prefix entries`() {
        val book = sampleBook()

        val ordered = NewWordOrdering.order(book)

        assertNotEquals(book, ordered, "a book fed in alphabetical order must not come out alphabetical")
        assertSpacingHolds(book, ordered)
    }

    @Test
    fun `order ignores the incoming order`() {
        val book = sampleBook()

        assertEquals(NewWordOrdering.order(book), NewWordOrdering.order(book.reversed()))
    }

    @Test
    fun `empty and single entry books are returned unchanged`() {
        assertEquals(emptyList(), NewWordOrdering.order(emptyList()))
        assertEquals(listOf("cet4:word"), NewWordOrdering.order(listOf("cet4:word")))
    }

    @Test
    fun `a book of one prefix group is still a permutation and never crashes`() {
        // M == n, so floor(n / M) == 1: spacing cannot be honoured, but the function must stay
        // total (a plan generation must never fail because a small imported book is all "con*").
        val book = (1..12).map { "cet4:con-$it" }

        val ordered = NewWordOrdering.order(book)

        assertEquals(book.sorted(), ordered.sorted())
    }

    @Test
    fun `a full sized book keeps its spacing guarantee`() {
        // Shaped like the real CET-4 book: thousands of entries, one dominant prefix group (84),
        // every other group of 3. Prefixes are built collision-free, so the group sizes below are
        // what the fixture actually has — the real books are asserted in the next test.
        val book = buildList {
            addAll((1..84).map { "cet4:con-item-$it" })
            addAll((1..48).map { "cet4:com-item-$it" })
            addAll((1..45).map { "cet4:pro-item-$it" })
            var group = 0
            while (size < 4308) {
                val prefix = threeLetters(group)
                addAll((1..3).map { "cet4:$prefix-item-$it" })
                group += 1
            }
        }.take(4308)

        assertEquals(84, book.groupBy(::prefixOf).maxOf { it.value.size }, "fixture lost its dominant group")

        val ordered = NewWordOrdering.order(book)

        assertEquals(4308, ordered.size)
        assertSpacingHolds(book, ordered)
    }

    @Test
    fun `bundled books are reordered and keep their spacing guarantee`() {
        val root =
            listOf(File("src/main/assets/wordbooks"), File("app/src/main/assets/wordbooks"))
                .firstOrNull(File::isDirectory)
                ?: fail("bundled word books not found from ${File(".").absolutePath}")
        val directories =
            root.listFiles { file -> file.isDirectory }
                .orEmpty()
                .sortedBy { directory -> directory.name }
        assertTrue(directories.isNotEmpty(), "no bundled word book directories under $root")

        // 逐册解析：任何一本解析失败都必须让用例失败，不许静默少测。
        val books =
            directories.map { directory ->
                val parsed = WordBookPackageParser.parse(directory).getOrThrow()
                parsed.metadata.id to parsed.cards.map { it.cardId }
            }

        assertTrue(
            books.count { (_, stored) -> stored.size / stored.largestGroup() >= 2 } >= 4,
            "expected at least 4 bundled books to carry a spacing claim",
        )

        books
            .filter { (_, stored) -> stored.size / stored.largestGroup() >= 2 }
            .forEach { (bookId, storedOrder) ->
                val ordered = NewWordOrdering.order(storedOrder)

                assertEquals(storedOrder.sorted(), ordered.sorted(), "$bookId must keep every entry")
                assertNotEquals(storedOrder, ordered, "$bookId came out in its stored alphabetical order")
                assertSpacingHolds(storedOrder, ordered)
            }
    }

    /**
     * The hash basis is part of this algorithm's identity: changing it rewrites every learner's
     * remaining path in every book, which is why the KDoc freezes it. A comment can be "tidied"
     * away; this literal cannot — it is the golden path of the sample book, and any change to the
     * hash, the prefix rule, or the tie-breakers has to be a deliberate decision that rewrites
     * existing users' sequences (record it in `docs/decisions/` before touching the expected list).
     */
    @Test
    fun `the frozen hash basis pins the sample book path`() {
        val golden = NewWordOrdering.order(sampleBook())

        assertEquals(
            listOf(
                "cet4:conversation",
                "cet4:computer",
                "cet4:station",
                "cet4:contribute",
                "cet4:complain",
                "cet4:state",
                "cet4:control",
                "cet4:above",
                "cet4:compose",
                "cet4:convenient",
                "cet4:unique",
                "cet4:produce",
                "cet4:congress",
                "cet4:status",
                "cet4:reason",
                "cet4:conduct",
                "cet4:receipt",
                "cet4:a",
                "cet4:constant",
                "cet4:ability",
                "cet4:about",
                "cet4:consider",
                "cet4:able",
                "cet4:abandon",
                "cet4:contact",
                "cet4:complete",
                "cet4:united",
                "cet4:product",
            ),
            golden,
        )
        // The literal above is the contract; these two lines keep it honest if the fixture itself
        // is ever edited (a golden list copied from a mutated fixture would pin the wrong path).
        assertEquals(sampleBook().sorted(), golden.sorted(), "the golden path must be a permutation of the fixture")
        assertSpacingHolds(sampleBook(), golden)
    }

    /**
     * Recomputes `floor(n / M)` from the fixture itself and asserts every same-prefix pair respects
     * it, so the guarantee is checked against the definition and not against the production helper.
     */
    private fun assertSpacingHolds(book: List<String>, ordered: List<String>) {
        val minimumSpacing = book.size / book.largestGroup()
        assertTrue(minimumSpacing >= 2, "fixture too lopsided to test spacing: floor(n/M)=$minimumSpacing")

        val positions = ordered.withIndex().groupBy({ prefixOf(it.value) }, { it.index })
        positions.forEach { (prefix, indexes) ->
            indexes.zipWithNext().forEach { (previous, next) ->
                assertTrue(
                    next - previous >= minimumSpacing,
                    "'$prefix' entries sit only ${next - previous} apart (< $minimumSpacing) at $previous/$next",
                )
            }
        }
    }

    /** Largest shared-prefix group of a book — the `M` in `floor(n / M)`, computed independently. */
    private fun List<String>.largestGroup(): Int = groupBy(::prefixOf).maxOf { (_, ids) -> ids.size }

    /** A collision-free three-letter prefix, so a synthetic book's group sizes are the stated ones. */
    private fun threeLetters(index: Int): String =
        "${'d' + index / 676 % 24}${'a' + index / 26 % 26}${'a' + index % 26}"

    private fun prefixOf(cardId: String): String =
        cardId.substringAfter(':').ifEmpty { cardId }.take(3).lowercase(Locale.ROOT)

    /** Alphabetical input, uneven prefix groups: the shape a bundled book actually arrives in. */
    private fun sampleBook(): List<String> =
        listOf(
            "cet4:a",
            "cet4:abandon",
            "cet4:ability",
            "cet4:able",
            "cet4:about",
            "cet4:above",
            "cet4:compose",
            "cet4:computer",
            "cet4:complain",
            "cet4:complete",
            "cet4:conduct",
            "cet4:congress",
            "cet4:constant",
            "cet4:contact",
            "cet4:contribute",
            "cet4:control",
            "cet4:convenient",
            "cet4:conversation",
            "cet4:consider",
            "cet4:state",
            "cet4:station",
            "cet4:status",
            "cet4:reason",
            "cet4:receipt",
            "cet4:product",
            "cet4:produce",
            "cet4:unique",
            "cet4:united",
        ).sorted()
}
