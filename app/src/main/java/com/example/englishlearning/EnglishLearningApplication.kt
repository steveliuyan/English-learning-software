package com.example.englishlearning

import android.app.Application
import com.example.englishlearning.language.infrastructure.KokoroEngine
import com.example.englishlearning.language.infrastructure.StartupPronunciationWarmer
import com.example.englishlearning.notification.ReminderNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class EnglishLearningApplication : Application() {
    @Inject lateinit var engine: KokoroEngine
    private val warmUpScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // F4-02：通知渠道在应用启动时幂等创建；开关状态由偏好控制，渠道常在不打扰。
        ReminderNotifications.ensureChannel(this)
        // 真机反馈（2026-10-13）：首次点发音要先物化 134MB 资产再建 ONNX 会话，形同"没反应"。
        // 启动即在后台预热；失败静默——点击发音时 provider 会自行 prepare 兜底。
        val warmer = StartupPronunciationWarmer { engine.prepare() }
        warmUpScope.launch { warmer.warm() }
    }
}
