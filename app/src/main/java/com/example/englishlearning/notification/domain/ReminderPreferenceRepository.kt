package com.example.englishlearning.notification.domain

import java.time.LocalTime

/**
 * F4-02 提醒偏好的持久化边界。默认关闭；实现必须保证设备级单份偏好。
 */
interface ReminderPreferenceRepository {
    suspend fun get(): Result<ReminderPreference>
    suspend fun save(preference: ReminderPreference): Result<Unit>
}

/** [ReminderPreference.time] 与当天分钟数互转，避免 LocalTime 序列化格式漂移。 */
fun ReminderPreference.toMinutesOfDay(): Int = time.hour * 60 + time.minute

fun reminderPreferenceFrom(minutesOfDay: Int, enabled: Boolean): ReminderPreference {
    val safe = minutesOfDay.coerceIn(0, 23 * 60 + 59)
    return ReminderPreference(enabled, LocalTime.of(safe / 60, safe % 60))
}
