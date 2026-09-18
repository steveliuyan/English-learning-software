package com.example.englishlearning.notification.domain

import java.time.LocalTime

/** Boundary for a future local reminder implementation. */
interface NotificationProvider {
    fun capabilities(): Set<NotificationCapability>
}

enum class NotificationCapability {
    LocalReminder,
}

data class ReminderPreference(
    val enabled: Boolean,
    val time: LocalTime,
)
