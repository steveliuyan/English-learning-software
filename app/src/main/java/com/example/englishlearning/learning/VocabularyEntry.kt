package com.example.englishlearning.learning

import java.time.Instant

data class VocabularyEntry(
    val profileId: String,
    val wordBookId: String,
    val cardId: String,
    val addedAt: Instant,
    val lastFeedback: String,
)
