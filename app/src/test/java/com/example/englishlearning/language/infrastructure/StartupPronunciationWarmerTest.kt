package com.example.englishlearning.language.infrastructure

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * 启动预热契约（2026-10-13 真机反馈：首次点发音要等资产物化 + ONNX 建会话，形同"没反应"）。
 *
 * 应用启动后在后台把 Kokoro 引擎 prepare() 好；这是尽力而为的旁路——
 * 任何失败都必须静默吞下（用户点击时会自然重试），绝不允许把异常抛回调用方或崩应用。
 */
class StartupPronunciationWarmerTest {

    @Test
    fun `warm prepares the engine once and reports ready on success`() = runTest {
        var calls = 0
        val warmer = StartupPronunciationWarmer {
            calls += 1
            Result.success(Unit)
        }

        assertTrue(warmer.warm())
        assertEquals(1, calls)
    }

    @Test
    fun `warm reports not-ready and stays silent when prepare fails`() = runTest {
        val warmer = StartupPronunciationWarmer { Result.failure(IllegalStateException("资产校验失败")) }

        assertFalse(warmer.warm())
    }

    @Test
    fun `warm never rethrows a crashing prepare`() = runTest {
        val warmer = StartupPronunciationWarmer { throw OutOfMemoryError("ONNX 建会话失败") }

        assertFalse(warmer.warm())
    }

    @Test
    fun `the application kicks the warmer in the background at startup`() {
        val source = listOf(
            File("src/main/java/com/example/englishlearning/EnglishLearningApplication.kt"),
            File("app/src/main/java/com/example/englishlearning/EnglishLearningApplication.kt"),
        ).first { it.isFile }.readText()

        assertTrue("StartupPronunciationWarmer" in source, "EnglishLearningApplication 未接线启动预热")
        assertTrue("engine.prepare()" in source, "预热必须调用 KokoroEngine.prepare()")
        assertTrue("Dispatchers.Default" in source, "预热必须在后台 dispatcher，不得阻塞主线程")
    }
}
