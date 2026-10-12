package com.example.englishlearning.language.spike

data class MemoryPeakSnapshot(
    val samples: Int = 0,
    val peakPssKb: Int? = null,
    val peakPssPhase: String? = null,
    val peakNativePssKb: Int? = null,
    val peakNativePssPhase: String? = null,
    val peakNativeHeapBytes: Long? = null,
    val peakNativeHeapPhase: String? = null,
    val peakJavaHeapBytes: Long? = null,
    val peakJavaHeapPhase: String? = null,
)

class MemoryPeakAccumulator {
    private var samples = 0
    private var peakPssKb: Int? = null
    private var peakPssPhase: String? = null
    private var peakNativePssKb: Int? = null
    private var peakNativePssPhase: String? = null
    private var peakNativeHeapBytes: Long? = null
    private var peakNativeHeapPhase: String? = null
    private var peakJavaHeapBytes: Long? = null
    private var peakJavaHeapPhase: String? = null

    @Synchronized
    fun record(phase: String, pssKb: Int, nativePssKb: Int, nativeHeapBytes: Long, javaHeapBytes: Long) {
        require(phase.isNotBlank())
        require(pssKb >= 0 && nativePssKb >= 0 && nativeHeapBytes >= 0 && javaHeapBytes >= 0)
        samples++
        if (peakPssKb == null || pssKb > peakPssKb!!) {
            peakPssKb = pssKb
            peakPssPhase = phase
        }
        if (peakNativePssKb == null || nativePssKb > peakNativePssKb!!) {
            peakNativePssKb = nativePssKb
            peakNativePssPhase = phase
        }
        if (peakNativeHeapBytes == null || nativeHeapBytes > peakNativeHeapBytes!!) {
            peakNativeHeapBytes = nativeHeapBytes
            peakNativeHeapPhase = phase
        }
        if (peakJavaHeapBytes == null || javaHeapBytes > peakJavaHeapBytes!!) {
            peakJavaHeapBytes = javaHeapBytes
            peakJavaHeapPhase = phase
        }
    }

    @Synchronized
    fun snapshot(): MemoryPeakSnapshot = MemoryPeakSnapshot(
        samples = samples,
        peakPssKb = peakPssKb,
        peakPssPhase = peakPssPhase,
        peakNativePssKb = peakNativePssKb,
        peakNativePssPhase = peakNativePssPhase,
        peakNativeHeapBytes = peakNativeHeapBytes,
        peakNativeHeapPhase = peakNativeHeapPhase,
        peakJavaHeapBytes = peakJavaHeapBytes,
        peakJavaHeapPhase = peakJavaHeapPhase,
    )
}
