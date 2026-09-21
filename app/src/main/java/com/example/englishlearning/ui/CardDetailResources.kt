package com.example.englishlearning.ui

import com.example.englishlearning.R

/**
 * Maps a word lemma to its bundled offline vector illustration, or null when no
 * illustration exists for that lemma.
 *
 * The mapping is a closed, compile-time-known set over the shipped placeholder
 * vocabulary (see [com.example.englishlearning.learning.PlaceholderWordCardSource]).
 * It performs no asset decoding, no bitmap construction and no network access: it
 * is a pure function of the lemma string. Callers must treat a null result as
 * "hide the image module entirely" (spec F1-06 / AC1-12) — there is deliberately
 * no fallback image and no "暂无" placeholder.
 */
fun lemmaToDrawableRes(lemma: String): Int? = ILLUSTRATION_BY_LEMMA[lemma.lowercase()]

private val ILLUSTRATION_BY_LEMMA: Map<String, Int> = mapOf(
    "ability" to R.drawable.ic_illus_ability,
    "achieve" to R.drawable.ic_illus_achieve,
    "benefit" to R.drawable.ic_illus_benefit,
    "climate" to R.drawable.ic_illus_climate,
    "develop" to R.drawable.ic_illus_develop,
    "economy" to R.drawable.ic_illus_economy,
    "feature" to R.drawable.ic_illus_feature,
    "generous" to R.drawable.ic_illus_generous,
    "influence" to R.drawable.ic_illus_influence,
    "maintain" to R.drawable.ic_illus_maintain,
    "obvious" to R.drawable.ic_illus_obvious,
    "reduce" to R.drawable.ic_illus_reduce,
)
