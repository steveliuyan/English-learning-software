package com.example.englishlearning.language.spike

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class KokoroRecordResourcesTest {
    @Test
    fun `success returns body result and closes sampler before snapshot and engine`() = runTest {
        val events = mutableListOf<String>()
        val result = withKokoroRecordResources(
            closeSampler = { events += "sampler" },
            snapshot = { events += "snapshot" },
            closeEngine = { events += "engine" },
        ) {
            events += "body"
            42
        }
        assertEquals(42, result)
        assertEquals(listOf("body", "sampler", "snapshot", "engine"), events)
    }

    @Test
    fun `sampler close failure still closes engine and skips snapshot`() = runTest {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("sampler")
        val thrown = assertFailsWith<IllegalStateException> {
            withKokoroRecordResources(
                closeSampler = { events += "sampler"; throw failure },
                snapshot = { events += "snapshot" },
                closeEngine = { events += "engine" },
            ) { Unit }
        }
        assertEquals(listOf("sampler", "engine"), events)
        assertSame(failure, thrown)
    }

    @Test
    fun `snapshot failure still closes engine`() = runTest {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("snapshot")
        val thrown = assertFailsWith<IllegalStateException> {
            withKokoroRecordResources(
                closeSampler = { events += "sampler" },
                snapshot = { events += "snapshot"; throw failure },
                closeEngine = { events += "engine" },
            ) { Unit }
        }
        assertEquals(listOf("sampler", "snapshot", "engine"), events)
        assertSame(failure, thrown)
    }

    @Test
    fun `body failure remains primary when sampler and engine close both fail`() = runTest {
        val events = mutableListOf<String>()
        val primary = AssertionError("acceptance")
        val samplerFailure = IllegalStateException("sampler")
        val engineFailure = IllegalStateException("engine")
        val thrown = assertFailsWith<AssertionError> {
            withKokoroRecordResources(
                closeSampler = { events += "sampler"; throw samplerFailure },
                snapshot = { events += "snapshot" },
                closeEngine = { events += "engine"; throw engineFailure },
            ) { throw primary }
        }
        assertSame(primary, thrown)
        assertEquals(listOf(samplerFailure, engineFailure), thrown.suppressed.toList())
        assertEquals(listOf("sampler", "engine"), events)
    }

    @Test
    fun `body failure remains primary when snapshot and engine close both fail`() = runTest {
        val primary = AssertionError("acceptance")
        val snapshotFailure = IllegalStateException("snapshot")
        val engineFailure = IllegalStateException("engine")
        val thrown = assertFailsWith<AssertionError> {
            withKokoroRecordResources(
                closeSampler = {},
                snapshot = { throw snapshotFailure },
                closeEngine = { throw engineFailure },
            ) { throw primary }
        }
        assertSame(primary, thrown)
        assertEquals(listOf(snapshotFailure, engineFailure), thrown.suppressed.toList())
    }

    @Test
    fun `first cleanup failure remains primary when engine close also fails`() = runTest {
        for (failAtSnapshot in listOf(false, true)) {
            val first = IllegalStateException("first")
            val engineFailure = IllegalStateException("engine")
            val thrown = assertFailsWith<IllegalStateException> {
                withKokoroRecordResources(
                    closeSampler = { if (!failAtSnapshot) throw first },
                    snapshot = { if (failAtSnapshot) throw first },
                    closeEngine = { throw engineFailure },
                ) { Unit }
            }
            assertSame(first, thrown)
            assertEquals(listOf(engineFailure), thrown.suppressed.toList())
        }
    }

    @Test
    fun `engine close failure after successful body is propagated`() = runTest {
        val failure = IllegalStateException("engine")
        val thrown = assertFailsWith<IllegalStateException> {
            withKokoroRecordResources({}, {}, { throw failure }) { Unit }
        }
        assertSame(failure, thrown)
    }

    @Test
    fun `cancellation remains primary while both resources are closed`() = runTest {
        val events = mutableListOf<String>()
        val primary = CancellationException("cancelled")
        val cleanupFailure = IllegalStateException("sampler")
        val thrown = assertFailsWith<CancellationException> {
            withKokoroRecordResources(
                closeSampler = { events += "sampler"; throw cleanupFailure },
                snapshot = { events += "snapshot" },
                closeEngine = { events += "engine" },
            ) { throw primary }
        }
        assertSame(primary, thrown)
        assertEquals(listOf(cleanupFailure), thrown.suppressed.toList())
        assertEquals(listOf("sampler", "engine"), events)
    }
}
