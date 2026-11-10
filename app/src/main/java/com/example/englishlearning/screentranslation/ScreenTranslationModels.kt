package com.example.englishlearning.screentranslation

/**
 * 悬浮窗屏幕翻译的三档（F3-05）：
 * - [Off]：不截屏、不发请求，悬浮控件不出现；
 * - [Once]：只在用户点「单次翻译当前屏」时截一帧；
 * - [Continuous]：用户按停止前一直看屏，靠节流与指纹去重控制请求次数。
 */
enum class ScreenTranslationMode {
    Off,
    Once,
    Continuous,
}

/** 一次截帧的触发者：自动轮询还是用户手动点。手动动作永远不走节流——那是用户的明确意图。 */
enum class CaptureTrigger {
    Automatic,
    Manual,
}

enum class CaptureSkipReason {
    /** 会话不在运行（[ScreenTranslationMode.Off]）。 */
    Stopped,

    /** 单次模式不吃自动轮询，否则就变成连续模式了。 */
    SingleShotMode,

    /** 指纹与上次已发的那一帧一致：同屏不重发（AC3-12）。 */
    DuplicateFrame,

    /** 距上次发送不足节流窗口（默认 3 秒）。 */
    Throttled,
}

/** 截帧门禁的结论：发不发、不发是因为哪个固定分类。[skipReason] 只用于界面提示，不参与计费口径。 */
data class CaptureDecision(
    val send: Boolean,
    val skipReason: CaptureSkipReason? = null,
) {
    companion object {
        val Send = CaptureDecision(send = true)

        fun skip(reason: CaptureSkipReason): CaptureDecision = CaptureDecision(send = false, skipReason = reason)
    }
}

/** 会话阶段。Idle 与 Stopped 都不截屏，区别只在界面文案（还没开始 / 刚停）。 */
enum class ScreenTranslationPhase {
    Idle,
    Running,
    Stopped,
}

/**
 * 会话级别的固定错误分类。每一项都要能对用户说清楚「是谁的问题」，
 * 尤其 [OcrUnavailable] 必须与翻译失败分开（F3-03 / AC3-06 同一条口径）。
 */
enum class ScreenTranslationNotice {
    /** 悬浮窗权限没开（SYSTEM_ALERT_WINDOW）。 */
    OverlayPermissionMissing,

    /** 用户在系统弹窗里拒绝了截屏，或系统随时回收了授权。 */
    ProjectionDenied,

    /** 本机没有能跑的 OCR（无 GMS、模型未下载）。绝不说成翻译失败。 */
    OcrUnavailable,

    /** OCR 跑了但没读出来。 */
    OcrFailed,

    /** 没有可用的默认文本 Profile，或 Key 读不出来。 */
    NotConfigured,

    /** 网络/超时/响应不合格。 */
    TranslationFailed,

    /** 这一屏块数超过单次请求上限，需要用户缩小范围或换屏。 */
    TooManyBlocks,

    /** 首次发送前需要出站确认：会话先停，等用户在应用内确认域名后重新开始。 */
    NeedsOutboundConfirmation,
}

/**
 * 悬浮窗屏幕翻译的界面可见状态（F3-05：成本必须可见——已发请求数、目标模型名、随时可停）。
 * 全部字段都是可展示的事实，不放内部句柄（VirtualDisplay 等只在服务里活）。
 */
data class ScreenTranslationSnapshot(
    val phase: ScreenTranslationPhase = ScreenTranslationPhase.Idle,
    val mode: ScreenTranslationMode = ScreenTranslationMode.Off,
    val sentRequests: Int = 0,
    val drawnBlocks: Int = 0,
    val skippedBlocks: Int = 0,
    val modelName: String? = null,
    val notice: ScreenTranslationNotice? = null,
    /** 首次发送前必须拿到出站确认（F2-02 按域名一次台账）：这里放等确认的域名。 */
    val pendingConfirmationHost: String? = null,
) {
    val running: Boolean get() = phase == ScreenTranslationPhase.Running
}

/** 会话状态迁移事件。Reducer 只做纯计算，服务与界面都往这里喂事件。 */
sealed interface ScreenTranslationEvent {
    data class Started(
        val mode: ScreenTranslationMode,
        val modelName: String?,
    ) : ScreenTranslationEvent

    /** 一帧翻译完成：请求数 +1，覆盖/跳过块数取最新一帧的结论。 */
    data class FrameTranslated(
        val drawnBlocks: Int,
        val skippedBlocks: Int,
    ) : ScreenTranslationEvent

    /** 帧被门禁跳过（不算请求、不动覆盖统计）。 */
    data class FrameSkipped(
        val reason: CaptureSkipReason,
    ) : ScreenTranslationEvent

    /** 一次失败。[countsAsRequest] 表示这次失败是否已经真的发出了一帧请求。 */
    data class Failure(
        val notice: ScreenTranslationNotice,
        val countsAsRequest: Boolean = false,
    ) : ScreenTranslationEvent

    data class ConfirmationRequired(
        val host: String,
    ) : ScreenTranslationEvent

    data class Stopped(
        val notice: ScreenTranslationNotice? = null,
    ) : ScreenTranslationEvent

    /**
     * 用户关掉了出站确认框（或点「取消」）：待确认的域名收掉。
     * 与 [Stopped] 分开是因为 Stopped **故意**保留待确认域名（用户还得据此再确认），
     * 只有明确的「放弃这次确认」才该把它清掉，否则对话框会永远挂在屏幕上。
     */
    data object ConfirmationDismissed : ScreenTranslationEvent
}

object ScreenTranslationReducer {
    fun reduce(
        state: ScreenTranslationSnapshot,
        event: ScreenTranslationEvent,
    ): ScreenTranslationSnapshot = when (event) {
        is ScreenTranslationEvent.Started ->
            state.copy(
                phase = ScreenTranslationPhase.Running,
                mode = event.mode,
                sentRequests = 0,
                drawnBlocks = 0,
                skippedBlocks = 0,
                modelName = event.modelName,
                notice = null,
                pendingConfirmationHost = null,
            )
        is ScreenTranslationEvent.FrameTranslated ->
            state.copy(
                sentRequests = state.sentRequests + 1,
                drawnBlocks = event.drawnBlocks,
                skippedBlocks = event.skippedBlocks,
                notice = null,
                pendingConfirmationHost = null,
            )
        is ScreenTranslationEvent.FrameSkipped -> state
        is ScreenTranslationEvent.Failure ->
            state.copy(
                sentRequests = if (event.countsAsRequest) state.sentRequests + 1 else state.sentRequests,
                notice = event.notice,
                pendingConfirmationHost = null,
            )
        is ScreenTranslationEvent.ConfirmationRequired ->
            state.copy(pendingConfirmationHost = event.host)
        is ScreenTranslationEvent.ConfirmationDismissed -> state.copy(pendingConfirmationHost = null)
        is ScreenTranslationEvent.Stopped ->
            // 等确认的域名要留着（用户还得据此确认），其余会话资源才算真的结束。
            state.copy(
                phase = ScreenTranslationPhase.Stopped,
                mode = ScreenTranslationMode.Off,
                notice = event.notice ?: state.notice,
            )
    }
}
