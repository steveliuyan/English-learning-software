package com.example.englishlearning.ui

import java.io.File
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * 认证宿主矩阵守卫（D-5 推广，2026-10-14 设备 QA 后复审）。
 *
 * 事实模式：屏幕用 `context as? FragmentActivity` 拿 BiometricPrompt 宿主；宿主若是
 * `ComponentActivity`，该 cast 恒为 null——真机上表现为认证按钮静默无反应，且
 * instrumented 测试因自注入宿主而测不出来（D-5 的根因）。
 *
 * 守卫范围是矩阵：全仓（app/src/main）所有含 `as? FragmentActivity` 的屏幕 ×
 * AndroidManifest 声明的全部生产 Activity 宿主。每个宿主必须直接或经本仓库内的
 * 基类链继承 androidx.fragment.app.FragmentActivity——不点名具体类，新增宿主、
 * 新增 cast 屏幕、或把宿主改回 ComponentActivity 都会在这里被拦下。
 * 与 `AppScreenProductionWiringTest` 同族：专盯生产组装点。
 */
class BiometricHostActivityContractTest {

    @Test
    fun `the repo still uses the FragmentActivity biometric host pattern`() {
        val casters = castScreens()
        assertTrue(
            casters.isNotEmpty(),
            "全仓已不存在 `as? FragmentActivity` 认证宿主模式——若宿主接线整体重做，" +
                "请连同本契约一起删除并更新 QA 报告 §3.6",
        )
    }

    @Test
    fun `every manifest activity host is a FragmentActivity descendant`() {
        val casters = castScreens().map { it.name }
        val hosts = manifestActivities()
        assertTrue(
            hosts.isNotEmpty(),
            "AndroidManifest 未解析出任何 <activity>——解析逻辑失效，本矩阵守卫不能作为证据",
        )

        val offenders = hosts.filterNot { (name, source) ->
            extendsFragmentActivity(name, source)
        }
        assertTrue(
            offenders.isEmpty(),
            "以下生产宿主不是 FragmentActivity 的后代：${offenders.joinToString { it.first }}。" +
                "在它们下面，${casters.joinToString()} 的 `context as? FragmentActivity` 恒为 null，" +
                "BiometricPrompt 全链路静默不可达（D-5 同族回归）。",
        )
    }

    /** 全仓 app/src/main 下所有把 context cast 成 FragmentActivity 的屏幕文件。 */
    private fun castScreens(): List<File> =
        mainRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("as? FragmentActivity") }
            .toList()

    /** Manifest 声明的全部 Activity：返回 (简单类名, 源码文本)。 */
    private fun manifestActivities(): List<Pair<String, String>> {
        val manifest = moduleFile("src/main/AndroidManifest.xml").readText()
        val namespace = Regex("""namespace\s*=\s*"([^"]+)"""")
            .find(moduleFile("build.gradle.kts").readText())
            ?.groupValues?.get(1)
            ?: error("app/build.gradle.kts 缺少 namespace 声明")
        val root = mainRoot()
        return Regex("""<activity\b[^>]*android:name\s*=\s*"([^"]+)"""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .map { qualified -> if (qualified.startsWith(".")) namespace + qualified else qualified }
            .map { fqcn ->
                val simpleName = fqcn.substringAfterLast('.')
                val packagePath = fqcn.substringBeforeLast('.').replace('.', '/')
                val source = moduleFile("src/main/java/$packagePath/$simpleName.kt").readText()
                simpleName to source
            }
            .toList()
    }

    /**
     * 类声明的父类链是否落到 FragmentActivity。允许链上出现本仓库内定义的中间基类
     * （如未来的 BaseActivity），外部类型（ComponentActivity 等）即判失败。
     */
    private fun extendsFragmentActivity(simpleName: String, source: String): Boolean {
        var currentName = simpleName
        var currentSource = source
        repeat(8) {
            val superName = directSuperType(currentName, currentSource) ?: return false
            if (superName == "FragmentActivity") return true
            val superFile = findClassFile(superName) ?: return false
            currentName = superName
            currentSource = superFile.readText()
        }
        return false
    }

    /** 取 `class X : Super(...)` 的第一个直接父类简单名；找不到返回 null。 */
    private fun directSuperType(simpleName: String, source: String): String? =
        Regex("""class\s+$simpleName\b[^:{=]*?:\s*([\w.]+)""")
            .find(source)
            ?.groupValues?.get(1)?.substringAfterLast('.')

    private fun findClassFile(simpleName: String): File? =
        mainRoot().walkTopDown()
            .firstOrNull { it.isFile && it.extension == "kt" && it.name == "$simpleName.kt" &&
                Regex("""class\s+$simpleName\b""").containsMatchIn(it.readText()) }

    private fun mainRoot(): File = moduleDir("src/main")

    private fun moduleDir(relativePath: String): File =
        listOf(File(relativePath), File("app/$relativePath")).firstOrNull { it.isDirectory }
            ?: error("找不到目录：$relativePath")

    /** 单元测试的工作目录是模块目录（app/），兼容仓库根目录起跑的写法。 */
    private fun moduleFile(relativePath: String): File =
        listOf(File(relativePath), File("app/$relativePath")).firstOrNull { it.isFile }
            ?: error("找不到源文件/目录：$relativePath")
}
