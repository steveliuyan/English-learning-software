package com.example.englishlearning.learning

import java.time.Instant

interface PlanCardSource {
    suspend fun dueCardIds(wordBookId: String, now: Instant): List<String>

    suspend fun newCardIds(wordBookId: String, limit: Int): List<String>
}
