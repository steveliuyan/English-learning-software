package com.example.englishlearning.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * F4-02 本地提醒偏好。单行表（`reminderId = "device"`），与语音偏好同款装配。
 * 时间以当天内的分钟数存储（0..1439），避免序列化 `LocalTime` 的格式漂移。
 */
@Entity(tableName = "reminder_preferences")
data class ReminderPreferenceEntity(
    @PrimaryKey val reminderId: String = "device",
    val enabled: Boolean = false,
    /** 当地时区的当天分钟数；V1 只支持每日一次的时间点。 */
    @ColumnInfo(defaultValue = "1200") val minutesOfDay: Int = 20 * 60,
)
