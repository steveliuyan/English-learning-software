package com.example.englishlearning.language.spike

import android.os.Debug
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

internal class AndroidProcessMemorySampler(
    private val intervalMs: Long = 100,
) : AutoCloseable {
    private val accumulator = MemoryPeakAccumulator()
    private val phase = AtomicReference("startup")
    private val stopped = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "kokoro-memory-sampler").apply { isDaemon = true }
    }
    private val future: ScheduledFuture<*> = executor.scheduleAtFixedRate(
        { sample() },
        0,
        intervalMs,
        TimeUnit.MILLISECONDS,
    )

    fun markPhase(value: String) {
        require(value.isNotBlank())
        phase.set(value)
        sample()
    }

    fun snapshot(): MemoryPeakSnapshot = accumulator.snapshot()

    override fun close() {
        if (stopped.compareAndSet(false, true)) {
            future.cancel(false)
            sample(force = true)
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    private fun sample(force: Boolean = false) {
        if (stopped.get() && !force) return
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        val runtime = Runtime.getRuntime()
        accumulator.record(
            phase = phase.get(),
            pssKb = info.totalPss,
            nativePssKb = info.nativePss,
            nativeHeapBytes = Debug.getNativeHeapAllocatedSize(),
            javaHeapBytes = runtime.totalMemory() - runtime.freeMemory(),
        )
    }
}
