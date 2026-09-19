package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "today_plans",
    indices = [Index(value = ["profileId", "localDate"], unique = true)],
)
data class TodayPlanEntity(
    @PrimaryKey val planId: String,
    val profileId: String,
    val localDate: String,
    val zoneId: String,
    val activeWordBookId: String,
    val newTarget: Int,
    val dueTarget: Int,
    val ruleVersion: String,
    val generatedAtEpochMillis: Long,
)
