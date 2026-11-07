package com.example.englishlearning

import android.app.Application
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.StartupLocalDataWarmer
import com.example.englishlearning.language.infrastructure.KokoroEngine
import com.example.englishlearning.language.infrastructure.StartupPronunciationWarmer
import com.example.englishlearning.notification.ReminderNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltAndroidApp
class EnglishLearningApplication : Application() {
    @Inject lateinit var engine: KokoroEngine

    /** 单例的惰性把手：预热只要「请求一次」，Room 的建库与迁移就由 `provideDatabase` 在那一次里跑完。 */
    @Inject lateinit var databaseProvider: Provider<AppDatabase>
    private val warmUpScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // F4-02：通知渠道在应用启动时幂等创建；开关状态由偏好控制，渠道常在不打扰。
        ReminderNotifications.ensureChannel(this)
        // 真机反馈（2026-10-13）：首次点发音要先物化 134MB 资产再建 ONNX 会话，形同"没反应"。
        // 启动即在后台预热；失败静默——点击发音时 provider 会自行 prepare 兜底。
        val warmer = StartupPronunciationWarmer { engine.prepare() }
        warmUpScope.launch { warmer.warm() }
        // 批 C-5 决定 4 第 1 条：建库 + 迁移原本排在闪屏之后（MainActivity 的 Hilt 图第一次要 AppDatabase 时
        // 才跑），于是批 C 铺好的首帧骨架白等一轮磁盘 IO。这里把那一次请求挪进 BrandLaunchActivity 的 1.2 秒，
        // 只做「请求单例」一件事：不读学习数据、不塞 VM 状态、不预跑备份/导出。
        // 失败照旧静默——呈现失败的位置仍是 RecoveryScreen，它读的是 provideDatabase 记进
        // RoomDatabaseStartupProbe 的同一份结果；预热再抛一次只会把缺陷变成后台线程崩溃。
        // 磁盘 IO 落 Dispatchers.IO：与 Kokoro 预热同处 Dispatchers.Default 是决策 §5 点名的竞争面。
        val localDataWarmer = StartupLocalDataWarmer { withContext(Dispatchers.IO) { databaseProvider.get() } }
        warmUpScope.launch { localDataWarmer.warm() }
    }
}
