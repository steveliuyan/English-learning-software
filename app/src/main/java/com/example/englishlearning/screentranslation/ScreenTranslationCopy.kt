package com.example.englishlearning.screentranslation

/**
 * F3-05 的两句屏侧文案，做成纯函数好让 JVM 单测钉住（界面只负责调用，不写文案）。
 *
 * 三条口径：
 * - [noticeText] 必须**说清是谁的问题**：OCR 不可用绝不写成「翻译失败」（F3-03 / AC3-06 同一条线）；
 * - [costLine] 必须让成本可见：已发请求数 + 目标模型名，且不出现「免费」这类暗示；
 * - 文案里不出现「后台自动」「监控屏幕」这类越权读感：用户点开始才有会话，按停止就结束。
 */
object ScreenTranslationCopy {
    /** 会话级错误分类 → 用户可读的一句话。 */
    fun noticeText(notice: ScreenTranslationNotice): String = when (notice) {
        ScreenTranslationNotice.OverlayPermissionMissing ->
            "还没有「显示在其他应用上层」权限：到系统设置里允许本应用显示悬浮窗，再回来重试。"
        ScreenTranslationNotice.ProjectionDenied ->
            "截屏授权没有拿到或已被系统收回：屏幕翻译只在一次授权内有效，需要重新点一次开始。"
        ScreenTranslationNotice.OcrUnavailable ->
            "本机识别不可用（没有可用的识别引擎或模型未下载）。这不是翻译失败，翻译请求一条都没发出去。"
        ScreenTranslationNotice.OcrFailed ->
            "这一屏没读出文字：换一屏、或把要翻的内容放大后重试。"
        ScreenTranslationNotice.NotConfigured ->
            "还没有可用的 AI 服务：先在 AI 设置里配置一个文本模型，再回来开始。"
        ScreenTranslationNotice.TranslationFailed ->
            "翻译失败（网络中断、超时或返回内容不合格）：稍后重试。"
        ScreenTranslationNotice.TooManyBlocks ->
            "这一屏文字块太多，一次请求装不下：换一屏，或缩小要翻的范围后重试。"
        ScreenTranslationNotice.NeedsOutboundConfirmation ->
            "首次发送前需要在应用内确认要发去的域名与计费：确认后会重新申请一次截屏授权。"
    }

    /**
     * 成本句：已发请求数 + 模型名。**不写估算金额**——单价随供应商与模型版本变，写死就是假精确；
     * 能诚实给出的是「这次会话发了几次、发给谁」，金额仍由 AI 服务的计费页说了算。
     */
    fun costLine(
        sentRequests: Int,
        modelName: String?,
    ): String {
        val model = modelName?.takeIf { it.isNotBlank() } ?: "未配置模型"
        return "本次会话已发 $sentRequests 次请求 · 模型 $model · 按 AI 服务实际计费"
    }

    /** 悬浮条第二行的会话状态短语。 */
    fun phaseLabel(
        phase: ScreenTranslationPhase,
        mode: ScreenTranslationMode,
    ): String = when (phase) {
        ScreenTranslationPhase.Idle -> "未开始"
        ScreenTranslationPhase.Stopped -> "已停止"
        ScreenTranslationPhase.Running -> if (mode == ScreenTranslationMode.Once) "单次" else "连续"
    }
}
