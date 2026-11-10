package com.example.englishlearning.screentranslation

/**
 * 截帧门禁（F3-05 / AC3-12）：决定这一帧要不要花钱。
 *
 * 三条规则，顺序固定：
 * 1. 会话没在跑 → 不发；
 * 2. 手动触发 → 发（用户点的，节流不该拦住明确意图），自动轮询在单次模式下 → 不发；
 * 3. 连续模式的自动轮询 → 指纹与上次已发一致先判重，再看节流窗口。
 *
 * [decide] 只读不写；真正发出去后由调用方调 [markSent] 记住这一帧。这样「跳过的帧」不会污染节流基准。
 */
class ScreenCaptureGate(
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
) {
    private var lastSentFingerprint: Long? = null
    private var lastSentAtMillis: Long? = null

    fun decide(
        mode: ScreenTranslationMode,
        fingerprint: Long,
        nowMillis: Long,
        trigger: CaptureTrigger,
    ): CaptureDecision {
        if (mode == ScreenTranslationMode.Off) return CaptureDecision.skip(CaptureSkipReason.Stopped)
        if (trigger == CaptureTrigger.Manual) return CaptureDecision.Send
        if (mode == ScreenTranslationMode.Once) return CaptureDecision.skip(CaptureSkipReason.SingleShotMode)
        if (lastSentFingerprint == fingerprint) return CaptureDecision.skip(CaptureSkipReason.DuplicateFrame)
        val sentAt = lastSentAtMillis
        if (sentAt != null && nowMillis - sentAt < minIntervalMillis) {
            return CaptureDecision.skip(CaptureSkipReason.Throttled)
        }
        return CaptureDecision.Send
    }

    fun markSent(
        fingerprint: Long,
        nowMillis: Long,
    ) {
        lastSentFingerprint = fingerprint
        lastSentAtMillis = nowMillis
    }

    /** 换屏/会话重启时清基准：否则「和上次一样」会误伤新会话的第一帧。 */
    fun reset() {
        lastSentFingerprint = null
        lastSentAtMillis = null
    }

    companion object {
        /** 连续模式默认 3 秒最多一次（spec F3-05）。 */
        const val DEFAULT_MIN_INTERVAL_MILLIS = 3_000L
    }
}
