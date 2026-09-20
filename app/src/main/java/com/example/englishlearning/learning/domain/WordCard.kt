package com.example.englishlearning.learning.domain

/**
 * Content shown on a single word card (spec F1-03).
 *
 * [example] and [inflections] are optional: they are only rendered when the
 * content source actually provides them.
 */
data class WordCard(
    val cardId: String,
    val wordBookId: String,
    val lemma: String,
    val ipa: String,
    val partOfSpeech: String,
    val meaningZh: String,
    val example: String? = null,
    val inflections: List<String> = emptyList(),
)
