package com.example.englishlearning.backup.device

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * D-12 契约守卫（单测门禁侧，与设备侧 `RoomFormalDataHandleCloseSemanticsTest` 配对）。
 *
 * Room 2.8.4 中 `RoomDatabase.close()` 取消实例的 coroutineScope 且重开不重建，
 * suspend DAO 从此必抛 `JobCancellationException`；`openHelper.close()` 同样不可用
 * （PassthroughConnectionPool 的 Lazy 连接关后不复位，设备实测抛 connection is
 * closed，见 docs/decisions/2026-10-14-restore-commit-temp-instance-and-restart.md）。
 * 因此架构定论是：checkpointAndClose 保持完整 database.close()（时序与回滚协议
 * 语义不变），切换后的恢复维护必须改用按同一 Schema 新开的临时实例，而不是复用
 * 已关闭的进程级单例。这里用源码契约把接线钉在单测门禁里；语义级证明见设备侧测试。
 */
class BackupCloseSemanticsContractTest {

    private val restoreSupport: String by lazy {
        moduleFile("src/main/java/com/example/englishlearning/backup/device/AndroidRestoreSupport.kt").readText()
    }

    @Test
    fun `checkpointAndClose keeps the full RoomDatabase close semantics`() {
        val body = restoreSupport
            .substringAfter("override fun checkpointAndClose")
            .substringBefore("override fun reopenAndQuickCheck")

        assertTrue(
            body.contains("database.close()"),
            "关库时序契约：checkpointAndClose 必须完整关闭 RoomDatabase（swap 前文件三件套静止）",
        )
        assertFalse(
            body.contains("openHelper.close()"),
            "D-12 证伪结论：openHelper.close() 不是旁路——Room 的 PassthroughConnectionPool " +
                "缓存已关连接，重开后 suspend DAO 抛 connection is closed；禁止回退到该方案",
        )
    }

    @Test
    fun `post-restore maintenance builds a fresh temp instance not the closed singleton`() {
        val segment = restoreSupport.substringAfter("class AndroidPostRestoreActions")
        assertTrue(
            segment.contains("AppDatabase.create("),
            "D-12 修复语义：恢复维护必须通过 AppDatabase.create(...) 新开临时实例执行 DAO 写入",
        )
        assertTrue(
            segment.contains(".close()"),
            "临时实例用完必须关闭并清理 WAL（与 RoomRestoreDataWriter 先例一致）",
        )
        val ctor = restoreSupport
            .substringAfter("class AndroidPostRestoreActions")
            .substringBefore(") : PostRestoreActions")
        assertFalse(
            ctor.contains("AppDatabase"),
            "AndroidPostRestoreActions 不得注入长生命周期 AppDatabase——close 后即为僵尸实例",
        )
    }

    @Test
    fun `restore ui demands app restart once a commit attempt has begun`() {
        val vm = moduleFile("src/main/java/com/example/englishlearning/ui/BackupRestoreViewModel.kt").readText()
        val confirm = vm.substringAfter("fun onReplaceConfirmed").substringBefore("fun cancel")
        assertTrue(
            confirm.contains("appRestartRequired = true"),
            "AC4-07：commit 一经开始（成功或回滚）状态必须置 appRestartRequired",
        )
        assertTrue(vm.contains("restartNow") && vm.contains("ProcessRestarter"), "VM 必须提供 restartNow 并注入 ProcessRestarter")
        val screen = moduleFile("src/main/java/com/example/englishlearning/ui/BackupRestoreScreen.kt").readText()
        assertTrue(
            screen.contains("backup_restore_restart"),
            "AC4-07：恢复页必须有重启入口（testTag backup_restore_restart）",
        )
    }

    /** 单元测试的工作目录是模块目录（app/），兼容仓库根目录起跑的写法。 */
    private fun moduleFile(relativePath: String): File =
        listOf(File(relativePath), File("app/$relativePath")).firstOrNull { it.exists() }
            ?: error("找不到源文件/目录：$relativePath")
}
