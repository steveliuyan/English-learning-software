package com.example.englishlearning.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.notification.domain.ReminderDecision
import com.example.englishlearning.notification.domain.ReminderMessageText
import com.example.englishlearning.notification.domain.ReminderPreference
import com.example.englishlearning.notification.domain.ReminderPreferenceRepository
import com.example.englishlearning.notification.domain.ReminderSchedulingPolicy
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * F4-02 WorkManager 执行边界。非精确调度（`setInitialDelay` 由系统批处理，
 * 允许延迟）；同一唯一工作名 replace —— 「取消旧任务并重新安排唯一的下一次」
 * 由 [ExistingWorkPolicy.REPLACE] 幂等保证；每天最多一次、不补发由域层决策推导。
 */
interface ReminderScheduler {
    /** 按当前偏好+计划状态重算并应用（schedule 或 cancel）。 */
    suspend fun refresh()

    companion object {
        /** 设备重启后 WorkManager 自动恢复持久化任务，无需 BOOT_COMPLETED 接线。 */
        const val UNIQUE_WORK_NAME = "daily-learning-reminder"
    }
}

class WorkManagerReminderScheduler(
    private val appContext: Context,
    private val preferences: ReminderPreferenceRepository,
    private val plans: TodayPlanRepository,
    private val events: LearningEventRepository,
    private val clock: ClockProvider,
) : ReminderScheduler {

    override suspend fun refresh() {
        val preference = preferences.get().getOrDefault(ReminderPreference(enabled = false, time = DEFAULT_TIME))
        val latest = when (val result = plans.findLatest(PROFILE_ID)) {
            is com.example.englishlearning.learning.TodayPlanResult.Ready -> result.plan
            else -> null
        }
        val completed = when (val result = latest?.let { events.completedCardIds(it.planId) }) {
            is RepositoryResult.Success -> result.value
            else -> emptyList()
        }
        when (val decision = ReminderSchedulingPolicy.decide(preference, latest, completed, clock)) {
            is ReminderDecision.Schedule -> schedule(decision.triggerAt, decision.messageText)
            ReminderDecision.Cancel -> cancel()
        }
    }

    private fun schedule(triggerAt: Instant, messageText: String) {
        val delay = Duration.between(clock.instant(), triggerAt).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setInitialDelay(delay)
            .setInputData(workDataOf(ReminderNotificationWorker.KEY_MESSAGE to messageText))
            .addTag(ReminderNotificationWorker.TAG)
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(ReminderScheduler.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun cancel() {
        WorkManager.getInstance(appContext).cancelUniqueWork(ReminderScheduler.UNIQUE_WORK_NAME)
    }

    companion object {
        /**
         * V1 单本地档案（阶段 0 决策：无账户、单 profile）。提醒是设备级概念，
         * 取当前唯一档案即可；多 profile 属后续阶段范围。
         */
        private const val PROFILE_ID = "default"

        private val DEFAULT_TIME = java.time.LocalTime.of(20, 0)
    }
}

/**
 * 到点检查并弹通知。Worker 醒来时刻计划可能已完成/跨日——最终一次
 * [ReminderSchedulingPolicy.decide] 复查：完成则静默退出（顺带取消下一次），
 * 未完成则发通知。通知文案固定，不含任何学习数据。
 */
class ReminderNotificationWorker(
    context: Context,
    parameters: WorkerParameters,
) : androidx.work.CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val message = inputData.getString(KEY_MESSAGE) ?: ReminderMessageText
        val notification = androidx.core.app.NotificationCompat.Builder(
            applicationContext,
            ReminderNotifications.CHANNEL_ID,
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(message)
            .setAutoCancel(true)
            .build()
        ReminderNotifications.notify(applicationContext, notification)
        return Result.success()
    }

    companion object {
        const val KEY_MESSAGE = "message"
        const val TAG = "daily-learning-reminder"
    }
}

/** Android 13+ 通知权限检查边界：无权限时 Worker 静默跳过（不循环请求）。 */
object ReminderNotifications {
    const val CHANNEL_ID = "learning_reminders"

    fun ensureChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "学习提醒",
                android.app.NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "每日学习/复习任务的本地提醒（非精确，每天最多一次）" }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun notify(context: Context, notification: android.app.Notification) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        androidx.core.app.NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private const val NOTIFICATION_ID = 4002
}

/** LocalDateTime 便捷扩展（域层测试同款推导）。 */
private fun LocalDateTime.atOrAfter(other: LocalDateTime): Boolean = !isBefore(other)
