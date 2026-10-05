package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 词 AI 问答笔记。只存 lemma/kind/回答/时间——Key、Endpoint、完整 Profile
 * 与出站确认状态一概不入库（AGENTS.md 备份与安全约束）。
 */
@Entity(
    tableName = "word_ai_notes",
    indices = [Index(value = ["profileId", "lemma"])],
)
data class WordAiNoteEntity(
    @PrimaryKey val noteId: String,
    val profileId: String,
    val wordBookId: String,
    val cardId: String,
    val lemma: String,
    val kind: String,
    val answer: String,
    val createdAtEpochMillis: Long,
)
