package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "today_plan_tasks",
    primaryKeys = ["planId", "cardId"],
    foreignKeys = [
        ForeignKey(
            entity = TodayPlanEntity::class,
            parentColumns = ["planId"],
            childColumns = ["planId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["planId"]), Index(value = ["planId", "ordinal"])],
)
data class TodayPlanTaskEntity(
    val planId: String,
    val cardId: String,
    val taskKind: String,
    val ordinal: Int,
)
