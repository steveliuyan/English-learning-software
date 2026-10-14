package com.example.englishlearning.backup.device

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D-12（2026-10-14 第二轮设备 QA）：恢复提交的 commit 段必回滚。
 *
 * Room 2.8.4 的 `RoomDatabase.close()` 会取消实例内部 coroutineScope，重开底层连接
 * 并不重建该 scope——此后同一实例上的 suspend DAO 调用一律抛
 * [JobCancellationException]（见 [roomDatabaseCloseCancelsSuspendDaos]，机制钉桩）。
 * `openHelper.close()` 同样不是旁路：PassthroughConnectionPool 的 Lazy 连接关后不
 * 复位，重开抛 connection is closed。因此修复架构（docs/decisions/
 * 2026-10-14-restore-commit-temp-instance-and-restart.md）是关库时序不变、切换后
 * 维护改用按同一 Schema 新开的临时实例；[maintenanceSuspendDaoWorksOnFreshTempInstanceAfterCheckpointAndClose]
 * 钉住该语义。若未来升级 Room 使第一个测试转红（close 不再取消 scope），前提消失，
 * 届时连同修复点与 [BackupCloseSemanticsContractTest] 一起重评。
 */
@RunWith(AndroidJUnit4::class)
class RoomFormalDataHandleCloseSemanticsTest {

    private val dbName = "d12-close-semantics.db"

    private fun newDatabase(): AppDatabase {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return Room.databaseBuilder(app, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
    }

    @After
    fun deleteTestDatabase() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val main = app.getDatabasePath(dbName)
        listOf(main, File("${main.path}-wal"), File("${main.path}-shm")).forEach { it.delete() }
    }

    @Test
    fun roomDatabaseCloseCancelsSuspendDaos() {
        val database = newDatabase()
        try {
            runBlocking { database.internalWordBookDao().listAll() }
            database.close()
            database.openHelper.writableDatabase // 重开底层连接——Room 不重建 coroutineScope
            // 实际抛出的是其 internal 子类 JobCancellationException，公开超类断言等价。
            assertThrows(CancellationException::class.java) {
                runBlocking { database.internalWordBookDao().listAll() }
            }
        } finally {
            database.openHelper.close()
        }
    }

    @Test
    fun maintenanceSuspendDaoWorksOnFreshTempInstanceAfterCheckpointAndClose() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val database = newDatabase()
        val handle = RoomFormalDataHandle(database)
        assertTrue("checkpointAndClose must succeed", handle.checkpointAndClose())
        assertTrue("reopenAndQuickCheck must succeed", handle.reopenAndQuickCheck())

        // 修复语义：维护写入走按同一 Schema 新开的临时实例，suspend DAO 必须可用。
        val temp = AppDatabase.create(app, app.getDatabasePath(dbName))
        try {
            runBlocking { temp.internalWordBookDao().listAll() }
        } finally {
            temp.close()
            val main = app.getDatabasePath(dbName)
            listOf(File("${main.path}-wal"), File("${main.path}-shm")).forEach { it.delete() }
        }
    }
}
