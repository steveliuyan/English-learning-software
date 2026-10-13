package com.example.englishlearning.language.domain

/**
 * Ordered local OCR fallback chain (AC3-04): try each provider in order.
 * - A provider reporting [OcrOutcome.Unavailable] is skipped in favour of the next one.
 * - A provider that was attempted and returned [OcrOutcome.RecognitionFailed] disqualifies
 *   the run only when no later provider succeeds; the result stays an OCR category.
 * - When no provider could run at all, the fixed [OcrChainOutcome.OcrUnavailable] category
 *   is returned, which must never be presented as a translation failure.
 */
class OcrProviderChain(
    private val providers: List<OcrProvider>,
) {
    suspend fun recognize(image: OcrImage): OcrChainOutcome {
        val reasons = linkedMapOf<OcrEngineKind, String?>()
        for (provider in providers) {
            when (val outcome = provider.recognize(image)) {
                is OcrOutcome.Success -> return OcrChainOutcome.Recognized(outcome.result, outcome.engine)
                is OcrOutcome.RecognitionFailed -> reasons[provider.kind] = outcome.reason
                OcrOutcome.Unavailable -> Unit
            }
        }
        return if (reasons.isEmpty()) {
            OcrChainOutcome.OcrUnavailable
        } else {
            OcrChainOutcome.RecognitionFailed(reasons)
        }
    }
}

sealed interface OcrChainOutcome {
    data class Recognized(val result: OcrResult, val engine: OcrEngineKind) : OcrChainOutcome

    /** No OCR provider could run on this device. Fixed category, distinct from recognition and translation failures. */
    data object OcrUnavailable : OcrChainOutcome

    /** At least one provider ran but none succeeded. Reasons are keyed by engine for diagnostics only. */
    data class RecognitionFailed(val reasons: Map<OcrEngineKind, String?>) : OcrChainOutcome
}
