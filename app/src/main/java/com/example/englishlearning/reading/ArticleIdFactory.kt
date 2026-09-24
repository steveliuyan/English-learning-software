package com.example.englishlearning.reading

import java.util.UUID

/**
 * Source of the id for a newly stored article, whatever its provenance
 * (AI generation, web fetch or user import).
 *
 * It is a seam so tests can pin the id instead of asserting against a random UUID.
 */
fun interface ArticleIdFactory {
    fun newId(): String

    companion object {
        val Random: ArticleIdFactory = ArticleIdFactory { UUID.randomUUID().toString() }
    }
}
