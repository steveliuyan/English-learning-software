package com.example.englishlearning

import android.app.Application
import com.example.englishlearning.notification.ReminderNotifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EnglishLearningApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // F4-02：通知渠道在应用启动时幂等创建；开关状态由偏好控制，渠道常在不打扰。
        ReminderNotifications.ensureChannel(this)
    }
}
