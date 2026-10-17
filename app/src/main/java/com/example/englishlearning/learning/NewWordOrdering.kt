package com.example.englishlearning.learning

import java.util.Locale
import java.util.PriorityQueue

/**
 * Deterministic presentation order for new cards (spec F1-02, decision 2026-10-17).
 *
 * The bundled books are alphabetical, so taking new cards in stored order makes the learner
 * march through the whole of A before ever meeting B. That is the worst shape for retention: the
 * entries kept back to back are precisely the ones that interfere with each other
 * (abandon / ability / able), and blocked practice feels better while retaining worse.
 *
 * The order guarantees that entries sharing the first three letters of their lemma stay at least
 * [minimumSpacing] positions apart — equivalently, every window of [minimumSpacing] consecutive
 * cards carries distinct prefixes. It is a pure function of the card ids: no clock, no random
 * seed, no per-user state, so the same book yields the same path across processes, devices and
 * app updates, and dropping reviewed cards leaves a subsequence instead of a reshuffle.
 */
object NewWordOrdering {
    /** Lemma prefix treated as "these two words will be confused for each other". */
    private const val PREFIX_LENGTH = 3

    private const val FNV_OFFSET_BASIS = -0x340D633AC5184B37L // 0xcbf29ce484222325
    private const val FNV_PRIME = 1099511628211L

    /** Ties are broken by content hash, so the result never depends on the incoming order. */
    private val byMostRemaining: Comparator<PrefixCursor> =
        compareByDescending<PrefixCursor> { it.remaining }.thenBy { it.prefixHash }.thenBy { it.prefix }

    private val byReleaseSlot: Comparator<PrefixCursor> =
        compareBy<PrefixCursor> { it.releaseAt }.thenBy { it.prefixHash }.thenBy { it.prefix }

    /**
     * @return a permutation of [cardIds] honouring the spacing promised by [minimumSpacing].
     *  Fewer than two entries are returned as they came in. A book whose entries all share one
     *  prefix degenerates to spacing 1 — nothing better is achievable — and must still come back
     *  as a permutation, because generating today's plan may not fail over the shape of a book.
     */
    fun order(cardIds: List<String>): List<String> {
        if (cardIds.size < 2) return cardIds.toList()

        val spacing = minimumSpacing(cardIds)
        val ready = PriorityQueue(byMostRemaining)
        val cooling = PriorityQueue(byReleaseSlot)
        cardIds
            .groupBy(::prefixOf)
            .mapTo(ready) { (prefix, ids) ->
                PrefixCursor(
                    prefix = prefix,
                    prefixHash = stableHash(prefix),
                    items = ids.sortedWith(compareBy({ stableHash(it) }, { it })),
                )
            }

        val ordered = ArrayList<String>(cardIds.size)
        var slot = 0
        while (ready.isNotEmpty() || cooling.isNotEmpty()) {
            while (cooling.isNotEmpty() && cooling.peek().releaseAt <= slot) ready.add(cooling.poll())
            // The greedy below is sufficient at this spacing, so `ready` should never be empty here.
            // If some future book shape breaks that, draining the cooldown queue keeps the result a
            // total permutation instead of throwing in the middle of plan generation.
            val cursor = ready.poll() ?: cooling.poll() ?: break
            ordered.add(cursor.items[cursor.next])
            cursor.next += 1
            if (cursor.remaining > 0) {
                cursor.releaseAt = slot + spacing
                cooling.add(cursor)
            }
            slot += 1
        }
        return ordered
    }

    /**
     * @return `floor(n / M)`: the book's entry count divided by its largest prefix group. This is
     *  both the strongest uniform spacing any ordering could promise and the one [order] achieves.
     */
    fun minimumSpacing(cardIds: List<String>): Int {
        if (cardIds.size < 2) return 1
        val largestGroup = cardIds.groupBy(::prefixOf).maxOf { (_, ids) -> ids.size }
        return (cardIds.size / largestGroup).coerceAtLeast(1)
    }

    /**
     * The lemma half of a card id, lowercased and cut to [PREFIX_LENGTH] characters. Bundled and
     * imported ids are validated to start with `<bookId>:` and to be longer
     * (`wordbook/WordBookPackage.kt`), so what follows the separator is the word itself; ids with
     * no separator fall back to the whole id rather than to the empty string.
     */
    private fun prefixOf(cardId: String): String =
        cardId.substringAfter(':').ifEmpty { cardId }.take(PREFIX_LENGTH).lowercase(Locale.ROOT)

    /**
     * FNV-1a over the characters of [value]. Deliberately not `String.hashCode()` and not `Random`:
     * plain wrapping arithmetic reproduces on every JVM and Android runtime, which is what makes a
     * book's path stable across devices and upgrades.
     */
    private fun stableHash(value: String): Long {
        var hash = FNV_OFFSET_BASIS
        value.forEach { char -> hash = (hash xor char.code.toLong()) * FNV_PRIME }
        return hash
    }

    /** One prefix cursor walking through its own queue; mutated only while outside a queue. */
    private class PrefixCursor(
        val prefix: String,
        val prefixHash: Long,
        val items: List<String>,
    ) {
        var next: Int = 0
        var releaseAt: Int = 0

        val remaining: Int
            get() = items.size - next
    }
}
