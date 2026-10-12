package com.example.englishlearning.language.spike

internal suspend fun <T> withKokoroRecordResources(
    closeSampler: () -> Unit,
    snapshot: () -> Unit,
    closeEngine: suspend () -> Unit,
    block: suspend () -> T,
): T {
    var primary: Throwable? = null
    try {
        return AutoCloseable {
            closeSampler()
            snapshot()
        }.use { block() }
    } catch (failure: Throwable) {
        primary = failure
        throw failure
    } finally {
        try {
            closeEngine()
        } catch (failure: Throwable) {
            if (primary == null) throw failure
            if (failure !== primary) primary.addSuppressed(failure)
        }
    }
}
