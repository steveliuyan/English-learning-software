package com.example.englishlearning.verification

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * R8 / 资源压缩的构建侧钉桩（决策 docs/decisions/2026-11-11-r8-minification.md）。
 *
 * 口径：
 *  - 开关是 Gradle 属性 `r8`，**默认开启**；`-Pr8=false` 回到未混淆产物用于二分定位 keep 规则。
 *  - release 必须同时开 `isMinifyEnabled` 与 `isShrinkResources`，并接上 `app/proguard-rules.pro`
 *    （AGP 不允许只 shrink 不 minify，两条绑定是刻意的）。
 *  - keep 规则文件必须存在，且必须逐条写着「为什么非留不可」——没有理由的 keep 就是没收到的收益。
 */
class ReleaseR8ContractTest {

    private val gradleFile = File("build.gradle.kts")
        .let { if (it.exists()) it else File("../app/build.gradle.kts") }

    private val rulesFile = File("proguard-rules.pro")
        .let { if (it.exists()) it else File("../app/proguard-rules.pro") }

    private val text: String by lazy { gradleFile.readText() }
    private val rules: String by lazy { rulesFile.readText() }

    @Test
    fun `r8 is gated by the r8 gradle property and defaults on`() {
        assertTrue(
            text.contains("gradleProperty(\"r8\")") && text.contains("?: \"true\""),
            "开关必须读 Gradle 属性 r8 且默认 true——默认关掉等于没做这轮",
        )
    }

    @Test
    fun `release minifies shrinks and points at the keep rules file`() {
        val releaseBlock = text.substringAfter("getByName(\"release\")")
        assertTrue(releaseBlock.contains("isMinifyEnabled = r8Enabled"), "release 必须开 R8")
        assertTrue(releaseBlock.contains("isShrinkResources = r8Enabled"), "release 必须开资源压缩")
        assertTrue(releaseBlock.contains("proguard-android-optimize.txt"), "用 optimize 档，不是默认档")
        assertTrue(releaseBlock.contains("\"proguard-rules.pro\""), "必须接上 app/proguard-rules.pro")
    }

    @Test
    fun `the keep rules file keeps the three classes of reflected entry points`() {
        assertTrue(rulesFile.isFile, "app/proguard-rules.pro 必须存在")
        listOf(
            "-keep class ai.onnxruntime.**",
            "-keep class org.bouncycastle.**",
            "-keep class com.google.crypto.tink.**",
            "-keep class com.googlecode.tesseract.android.**",
            "kotlinx.serialization.KSerializer serializer(...);",
            "-keepattributes SourceFile,LineNumberTable",
        ).forEach { needle ->
            assertTrue(rules.contains(needle), "keep 规则缺少：$needle（它断了会在真机上表现为启动即崩）")
        }
    }

    @Test
    fun `every keep block carries a reason`() {
        val sections = rules.split("\n# ---------------------------------------------------------------- ")
        assertTrue(sections.size >= 5, "规则文件应按库分节，每节写明理由")
        sections.drop(1).forEach { section ->
            val body = section.lines().filter { it.startsWith("#") && !it.startsWith("# ---") }
            assertTrue(body.isNotEmpty(), "分节缺少「为什么非留不可」的说明：${section.lineSequence().first()}")
        }
    }
}
