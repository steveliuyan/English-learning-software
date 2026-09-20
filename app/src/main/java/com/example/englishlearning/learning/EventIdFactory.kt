package com.example.englishlearning.learning

import java.util.UUID

/**
 * Source of the unique id attached to one feedback submission (spec F1-03).
 *
 * The id is the idempotency key, so the caller must be able to hold it steady across a
 * retry: reusing the id of a retried submission guarantees the retry cannot be counted
 * as a second submission (AC1-04). It is a seam so tests can drive the key explicitly.
 */
fun interface EventIdFactory {
    fun newId(): String

    companion object {
        val Random: EventIdFactory = EventIdFactory { UUID.randomUUID().toString() }
    }
}
