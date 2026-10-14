package com.example.englishlearning.backup.device

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import com.example.englishlearning.backup.restore.ProcessRestarter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 恢复提交后的进程重启（AC4-07）：经 PendingIntent 重新拉起 launcher，成功后杀死当前
 * 进程；拉起失败则保持现状（用户可自行重开），绝不静默丢数据——正式数据此时已在
 * 磁盘上完成替换或回滚。
 */
@Singleton
class AndroidProcessRestarter @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProcessRestarter {
    override fun relaunch() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pending = PendingIntent.getActivity(
            context,
            RESTART_REQUEST_CODE,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            pending.send()
        } catch (_: PendingIntent.CanceledException) {
            return
        }
        Process.killProcess(Process.myPid())
    }

    private companion object {
        const val RESTART_REQUEST_CODE = 0x5245
    }
}
