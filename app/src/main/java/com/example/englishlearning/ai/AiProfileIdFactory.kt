package com.example.englishlearning.ai

import java.util.UUID

/**
 * Source of the id for a newly created AI profile.
 *
 * The id is also the seed of the profile's secret alias, so it must be chosen before the key is
 * written: a stable, collision-free id is what keeps two profiles from ever sharing a key slot.
 * It is a seam so tests can pin the id instead of asserting against a random UUID.
 */
fun interface AiProfileIdFactory {
    fun newId(): String

    companion object {
        val Random: AiProfileIdFactory = AiProfileIdFactory { UUID.randomUUID().toString() }
    }
}
