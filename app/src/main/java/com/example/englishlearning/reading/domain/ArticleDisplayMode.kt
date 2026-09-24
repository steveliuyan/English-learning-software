package com.example.englishlearning.reading.domain

/**
 * How the reading page shows the article by default (spec F2-04).
 *
 * Switching the mode only changes presentation: it never rewrites the stored article, and
 * it is the only thing the preference stores. [FULL_TRANSLATION] differs from the other two
 * only in its initial expanded state — the translation stays collapsible in every mode.
 */
enum class ArticleDisplayMode {
    /** English only until the reader asks for the translation. */
    ENGLISH_FIRST,

    /** English with the translation available right below, collapsed by default. */
    BILINGUAL,

    /** English with the translation already expanded. */
    FULL_TRANSLATION,
}
