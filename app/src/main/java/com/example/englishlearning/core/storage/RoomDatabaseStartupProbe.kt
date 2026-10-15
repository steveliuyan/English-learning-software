package com.example.englishlearning.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 进程级启动结论持有者：[com.example.englishlearning.di.AppModule.provideDatabase]
 * 在建库时记录首次打开结果；UI 状态机经 [DatabaseStartupProbe] 读取。
 * 重试走 [DatabaseStartup.open] 的同一失败关闭路径——恢复成功才清错误态，
 * 仍失败就更新摘要，任何分支都不删文件。
 */
@Singleton
class RoomDatabaseStartupProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) : DatabaseStartupProbe {
    @Volatile
    private var reason: String? = null

    override val failureReason: String? get() = reason

    fun record(openFailure: Throwable?) {
        reason = openFailure?.let(DatabaseStartup::reasonSummary)
    }

    override fun retry(): Boolean {
        val result = DatabaseStartup.open(context)
        val database = result.getOrNull()
        return if (database != null) {
            // 校验用的临时实例：确认能开就关闭，图里的单例稍后按惰性自会复用同一文件。
            database.close()
            reason = null
            true
        } else {
            reason = DatabaseStartup.reasonSummary(result.exceptionOrNull())
            false
        }
    }
}
