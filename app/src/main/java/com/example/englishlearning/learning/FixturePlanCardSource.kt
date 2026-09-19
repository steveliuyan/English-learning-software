package com.example.englishlearning.learning

import java.time.Instant

class FixturePlanCardSource : PlanCardSource {
    override suspend fun dueCardIds(wordBookId: String, now: Instant): List<String> = emptyList()
    override suspend fun newCardIds(wordBookId: String, limit: Int): List<String> = emptyList()
}
