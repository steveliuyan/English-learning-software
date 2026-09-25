package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** F3-02：一篇文章的「完成阅读」记录。articleId 主键让重复点击天然幂等。 */
@Entity(tableName = "reading_completions")
data class ReadingCompletionEntity(
    @PrimaryKey val articleId: String,
    val profileId: String,
    val localDate: String,
    val completedAtEpochMillis: Long,
)
