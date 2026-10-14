package com.example.englishlearning.ui

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * D-10/D-11（AC4-08）源码契约锚：
 * - 配对码必须是方形像素 QR（Compose Canvas 网格绘制），禁止文本块近似；
 * - Done 阶段必须存在认证门控的"显示配对码"入口；
 * - 导出动作必须紧邻一次系统认证（认证窗口 30 秒约束，D-9）；
 * - 签名不可用的文案必须诚实提及认证，而非"加密组件不可用"。
 */
class PairingExportQrFlowContractTest {

    private val screen: String by lazy { mainSource("BackupPairingExportScreen.kt") }
    private val viewModel: String by lazy { mainSource("BackupProtectedExportViewModel.kt") }

    @Test
    fun `pairing qr is rendered as a square bitmap grid not text blocks`() {
        assertTrue(screen.contains("QRCodeWriter"), "必须使用 zxing-core 生成矩阵")
        assertTrue(screen.contains("aspectRatio(1f)"), "QR 必须是方形画布（两轴等比）")
        assertTrue(screen.contains("drawRect"), "逐模块像素绘制")
        assertFalse(screen.contains("'█'"), "禁止文本块近似 QR（D-10：不可解码）")
        assertFalse(screen.contains("fontSize = 7.sp"), "禁止 7.sp 文本 QR")
        assertTrue(screen.contains("backup_pairing_export_qr"), "testTag 保留")
    }

    @Test
    fun `done phase offers an authentication-gated reveal of the pairing code`() {
        assertTrue(
            screen.contains("backup_pairing_export_reveal_qr"),
            "D-11：Done 阶段必须有认证门控的显示配对码入口",
        )
        assertTrue(screen.contains("onPairingRevealed"), "reveal 必须回填 ViewModel")
        assertTrue(
            viewModel.contains("fun onPairingRevealed"),
            "ViewModel 必须实现 onPairingRevealed（仅在有已签发挑战时置可见）",
        )
    }

    @Test
    fun `export signing runs immediately after a fresh system authentication`() {
        val activeBlock = screen
            .substringAfter("ProtectedExportPhase.Active")
            .substringBefore("ProtectedExportPhase.Exporting")
        assertTrue(
            activeBlock.contains("runWithMigrationAuth") && activeBlock.contains("exportProtected"),
            "D-9：导出按钮必须紧邻一次系统认证（成功回调内再调 exportProtected）",
        )
        assertTrue(
            activeBlock.indexOf("runWithMigrationAuth") < activeBlock.indexOf("exportProtected"),
            "exportProtected 必须出现在认证包装的成功回调里",
        )
        assertTrue(
            viewModel.contains("签名不可用") && viewModel.contains("认证"),
            "D-9：CryptoUnavailable 文案必须诚实提及认证窗口",
        )
    }

    @Test
    fun `key generation failure is surfaced honestly and the screen scrolls on small displays`() {
        assertTrue(
            screen.contains("onMigrationKeyUnavailable"),
            "D-7：ensureKey 失败必须走可见错误路径，不得静默回到 Idle",
        )
        assertTrue(
            viewModel.contains("fun onMigrationKeyUnavailable") && viewModel.contains("锁屏"),
            "D-7：ViewModel 必须提供诚实的密钥不可用错误",
        )
        assertTrue(screen.contains("verticalScroll"), "D-8：导出页必须可纵向滚动（小屏/大字体）")
    }

    private fun mainSource(name: String): String =
        listOf(File("src/main/java/com/example/englishlearning/ui/$name"), File("app/src/main/java/com/example/englishlearning/ui/$name"))
            .firstOrNull { it.exists() }
            ?.readText()
            ?: error("找不到源文件：$name")
}
