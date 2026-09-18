package com.example.englishlearning.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "learning_profiles",
    foreignKeys = [
        ForeignKey(
            entity = WordBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["activeWordBookId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["activeWordBookId"])],
)
data class LearningProfileEntity(
    @PrimaryKey val profileId: String,
    val activeWordBookId: String,
    @ColumnInfo(defaultValue = "1 CHECK(dailyNewTarget >= 1)")
    val dailyNewTarget: Int,
)
