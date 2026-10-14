package com.example.englishlearning.ui

import java.io.File
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * 认证宿主契约守卫（D-5，2026-10-14 设备 QA）。
 *
 * `BackupPairingExportScreen` 用 `context as? FragmentActivity` 拿 BiometricPrompt 宿主；
 * 当生产宿主 `MainActivity` 只是 `ComponentActivity` 时，这个 cast 恒为 null——真机上表现为
 * 点「开始（需系统认证）」静默无反应，弹窗、配对、恢复全链路不可达，且 instrumented 测试
 * 因自注入宿主而测不出来。与 `AppScreenProductionWiringTest` 同族：专盯生产组装点。
 */
class BiometricHostActivityContractTest {

    private val uiDir = moduleFile("src/main/java/com/example/englishlearning/ui")

    @Test
    fun `screens that cast context to FragmentActivity require a FragmentActivity host`() {
        val casters = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("as? FragmentActivity") }
            .toList()

        assertTrue(
            casters.isNotEmpty(),
            "已不存在 `as? FragmentActivity` 认证宿主模式——若宿主接线整体重做，请连同本契约一起删除并更新 QA 报告 §3.6",
        )

        val mainActivitySource = moduleFile("src/main/java/com/example/englishlearning/MainActivity.kt").readText()
        assertTrue(
            Regex("""class MainActivity\s*:\s*FragmentActivity\b""").containsMatchIn(mainActivitySource),
            "D-5 回归：MainActivity 必须直接继承 androidx.fragment.app.FragmentActivity，" +
                "否则 ${casters.joinToString { it.name }} 的 context cast 恒为 null，认证导出按钮静默失效",
        )
        assertTrue(
            mainActivitySource.contains("import androidx.fragment.app.FragmentActivity"),
            "MainActivity 必须显式 import androidx.fragment.app.FragmentActivity（BiometricPrompt 宿主类型），" +
                "不得退回 androidx.activity.ComponentActivity",
        )
    }

    /** 单元测试的工作目录是模块目录（app/），兼容仓库根目录起跑的写法。 */
    private fun moduleFile(relativePath: String): File =
        listOf(File(relativePath), File("app/$relativePath")).firstOrNull { it.exists() }
            ?: error("找不到源文件/目录：$relativePath")
}
