package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 生产组装点的接线守卫：`AppScreen` 上所有「可空默认 null」的参数，都必须由
 * `MainActivity` 实际传进来。
 *
 * 为什么需要它：2026-09-28 真机走查发现 `wordQaViewModel` / `sentenceAnalysisViewModel` /
 * `imageStudioViewModel` 三个新 ViewModel 只加到了 `AppScreen` 签名，**没加**到 `MainActivity`。
 * 于是在真机上：返回按钮好使、输入框好使（都是屏幕本地状态），但一点「生成画面描述」毫无反应——
 * 因为屏幕拿到的是 `null`。而真机 instrumented 测试全绿，原因是测试自己把 ViewModel 注入了
 * `AppScreen`，**绕过了生产组装点**。这条测试专门盯住那一步，不依赖设备。
 */
class AppScreenProductionWiringTest {

    @Test
    fun `every optional AppScreen parameter is supplied by the production host`() {
        val appScreenSource = projectFile("src/main/java/com/example/englishlearning/ui/AppScreen.kt").readText()
        val mainActivitySource = projectFile("src/main/java/com/example/englishlearning/MainActivity.kt").readText()

        val signature = appScreenSource
            .substringAfter("fun AppScreen(")
            .substringBefore("\n) {")
        val optionalParameters = Regex("""^\s+(\w+): [\w.<>?]+ = null,$""", RegexOption.MULTILINE)
            .findAll(signature)
            .map { it.groupValues[1] }
            .toList()

        assertTrue(optionalParameters.size >= 3, "解析 AppScreen 签名失败，取到的可空参数：$optionalParameters")

        val productionCall = mainActivitySource.substringAfter("AppScreen(")
        val missing = optionalParameters.filterNot { productionCall.contains("$it = ") }
        assertTrue(
            missing.isEmpty(),
            "MainActivity 没有传入这些 AppScreen 参数：$missing —— 真机上表现为点了没反应（屏幕拿到 null）",
        )
    }

    /** 单元测试的工作目录是模块目录（app/），兼容直接给相对仓库路径的写法。 */
    private fun projectFile(relativePath: String): File =
        listOf(File(relativePath), File("app/$relativePath")).firstOrNull { it.isFile }
            ?: error("找不到源文件：$relativePath")
}
