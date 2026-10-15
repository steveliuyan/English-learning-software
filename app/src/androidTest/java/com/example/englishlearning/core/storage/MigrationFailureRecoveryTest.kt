package com.example.englishlearning.core.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F5-02 / SP-B 决策 §3 真库验证（androidTest）：迁移/打开失败必须
 * ①以 Result.failure 如实回报、②数据库文件原样存活、③既有数据可再次读出——
 * 失败路径绝不删库、绝不 destructive fallback（本地优先红线）。
 *
 * 不造假 v29 文件（AppDatabase 声明 v31，Room 校验先拦），改用
 * 「哨兵表 + PRAGMA user_version=999999」：Room 首次打开即因未来版本失败，
 * 走的正是迁移/校验那条真实崩溃面。
 */
@RunWith(AndroidJUnit4::class)
class MigrationFailureRecoveryTest {
    @Test
    fun openFailureReportsFailureAndKeepsDatabaseFileAndDataIntact() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-failure-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name)
        try {
            // 手写「来自未来版本」的库：建哨兵表存一行数据，再把 user_version 抬到 Room 之上。
            val raw = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
            raw.execSQL("CREATE TABLE sentinel_data(value TEXT NOT NULL)")
            raw.execSQL("INSERT INTO sentinel_data(value) VALUES('keep-me')")
            raw.execSQL("PRAGMA user_version = 999999")
            raw.close()

            val result = DatabaseStartup.openWith(file) { AppDatabase.create(context, file) }

            assertTrue("未来版本库必须失败而不是静默降级: $result", result.isFailure)
            assertTrue("失败路径绝不删除数据库文件", file.exists())
            val reason = DatabaseStartup.reasonSummary(result.exceptionOrNull())
            assertNotNull("失败必须有可呈现的固定短句原因", reason)
            assertTrue(
                "原因是短句且不含路径: $reason",
                reason.isNotEmpty() && reason.length <= 40 && !reason.contains("/data/"),
            )

            // 哨兵数据在原始 SQL 层仍可读出——证明「数据仍在设备上」不是文案空头支票。
            val reread = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val cursor = reread.rawQuery("SELECT value FROM sentinel_data", null)
                try {
                    assertTrue(cursor.moveToFirst())
                    assertEquals("keep-me", cursor.getString(0))
                } finally {
                    cursor.close()
                }
            } finally {
                reread.close()
            }
        } finally {
            context.deleteDatabase(name)
            File("${file.absolutePath}-journal").delete()
        }
    }
}
