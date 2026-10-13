package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AC4-06 源码契约锚：恢复流不提供任何“跳过校验/强制导入/降级为 Salvage 恢复”入口。
 * 检查对象是标识符与 testTag（不含注释文本）；恢复 ViewModel 无 bypass 状态/方法，
 * Screen 无对应按钮；诚实文案必须存在。
 */
class RestoreNoBypassContractTest {
    private fun mainSource(name: String): String =
        File("src/main/java/com/example/englishlearning/ui/$name").readText()

    /** 去掉块注释与行注释后的代码体。 */
    private fun codeOf(name: String): String = mainSource(name)
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//.*"), "")

    @Test
    fun `restore view model has no bypass states or forced import paths`() {
        val code = codeOf("BackupRestoreViewModel.kt")
        listOf("bypass", "forceImport", "skipValidation", "skipCheck", "SalvageRestore")
            .forEach { forbidden ->
                assertTrue(!code.contains(forbidden), "restore ViewModel code must not contain '$forbidden'")
            }
        // the only v2 answer is the honest fixed category
        assertTrue(code.contains("SalvageNotRestorable"), "v2 packages must be answered with the fixed category")
    }

    @Test
    fun `restore and pairing screens render no bypass buttons`() {
        val restore = mainSource("BackupRestoreScreen.kt")
        val pairing = mainSource("BackupPairingExportScreen.kt")
        listOf(restore, pairing).forEachIndexed { index, code ->
            listOf("skip_validation", "force_import", "skip_check", "restore_as_salvage")
                .forEach { forbidden ->
                    assertTrue(!code.contains(forbidden), "screen #$index code must not contain bypass tag '$forbidden'")
                }
        }
        // confirm/replace is the only commit path, and it sits behind a staged session
        assertTrue(restore.contains("backup_restore_confirm_replace"), "restore confirm tag missing")
        assertTrue(restore.contains("onReplaceConfirmed"), "confirm handler missing")
        // D2: live camera scan only — no manual entry / gallery decode identifiers
        assertTrue(!restore.contains("ManualCodeEntry") && !restore.contains("GalleryDecode") && !restore.contains("pickImage"), "bypass entry found")
    }

    @Test
    fun `honest messaging is present for unrecoverable scenarios`() {
        val restore = mainSource("BackupRestoreViewModel.kt")
        assertTrue(restore.contains("密码遗忘则备份无法恢复"))
        val pairing = mainSource("BackupPairingExportScreen.kt")
        assertTrue(pairing.contains("密码遗忘则备份无法恢复"))
        assertTrue(pairing.contains("30 分钟"))
    }
}
