package com.example.englishlearning.core.storage

import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteException
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * SP-B 决策（2026-10-15 §3）：Room 建库是惰性的——迁移与 schema 校验发生在**首次打开**。
 * 这里包住「build + 首次打开」，失败以 [Result.failure] 如实返回：
 * **绝不删除数据库文件、绝不 fallbackToDestructiveMigration、绝不静默清数据**
 * （本地优先红线：数据在设备上就必须保留原样，呈现给用户去重试/导出留档）。
 */
object DatabaseStartup {
    /** 与 [com.example.englishlearning.backup.device.RoomRestoreDataWriter.DATABASE_FILE_NAME] 一致的生产库名。 */
    const val DATABASE_NAME = "english-learning.db"

    fun open(context: Context): Result<AppDatabase> {
        val file = context.getDatabasePath(DATABASE_NAME)
        return openWith(file) { AppDatabase.create(context, file) }
    }

    /**
     * 可测接缝：对给定 builder 产物触发首次打开（openHelper.writableDatabase 即执行
     * 迁移+校验）。失败只回报异常，不碰任何文件；[CancellationException] 原样透传。
     */
    fun openWith(databaseFile: File, build: () -> AppDatabase): Result<AppDatabase> = try {
        val database = build()
        database.openHelper.writableDatabase
        Result.success(database)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    /**
     * 面向用户的固定安全短句：不拼 message、不带路径与堆栈
     * （NeedsRecovery 的 reason 会直接进 UI 文案，异常原文可能含文件路径）。
     */
    fun reasonSummary(error: Throwable?): String = when (error) {
        null -> ""
        is SQLiteException -> "数据库文件无法打开"
        is SQLException -> "数据库文件无法打开"
        else -> "数据库结构迁移失败"
    }
}
