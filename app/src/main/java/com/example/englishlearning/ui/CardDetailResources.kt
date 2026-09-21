package com.example.englishlearning.ui

import com.example.englishlearning.R

/**
 * Maps a word lemma to its bundled offline illustration, or null when no
 * illustration exists for that lemma.
 *
 * The mapping is a closed, compile-time-known set over the shipped placeholder
 * vocabulary (see [com.example.englishlearning.learning.PlaceholderWordCardSource]).
 * Each entry points at a lossless 512x512 WebP asset in `res/drawable-nodpi`, so
 * the picture is an ordinary resource that `painterResource` resolves by itself:
 * no image-loading library, no decoding code of ours, no network access. Lookup
 * stays a pure function of the lemma string.
 *
 * Callers must treat a null result as "hide the image module entirely"
 * (spec F1-06 / AC1-12) — there is deliberately no fallback image and no "暂无"
 * placeholder.
 */
fun lemmaToDrawableRes(lemma: String): Int? = ILLUSTRATION_BY_LEMMA[lemma.lowercase()]

private val ILLUSTRATION_BY_LEMMA: Map<String, Int> = mapOf(
    "ability" to R.drawable.illus_ability,
    "achieve" to R.drawable.illus_achieve,
    "benefit" to R.drawable.illus_benefit,
    "climate" to R.drawable.illus_climate,
    "develop" to R.drawable.illus_develop,
    "economy" to R.drawable.illus_economy,
    "feature" to R.drawable.illus_feature,
    "generous" to R.drawable.illus_generous,
    "influence" to R.drawable.illus_influence,
    "maintain" to R.drawable.illus_maintain,
    "obvious" to R.drawable.illus_obvious,
    "reduce" to R.drawable.illus_reduce,
)
