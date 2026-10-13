package com.example.englishlearning.language.infrastructure

/**
 * 启动预热的尽力而为语义：把引擎 prepare() 的异常/失败统一收敛成一个布尔。
 *
 * 真机反馈（2026-10-13）：首次点发音要先物化 134MB 资产再建 ONNX 会话，
 * 用户感知就是"点了没反应"。预热带走这些一次性成本；失败绝不外抛——
 * 发音路径（KokoroPronunciationProvider）本来就会在点击时自行 prepare 兜底。
 */
class StartupPronunciationWarmer(private val prepare: suspend () -> Result<Unit>) {
    /** true = 引擎已就绪；任何失败或异常都返回 false 并保持静默。 */
    suspend fun warm(): Boolean = runCatching { prepare().isSuccess }.getOrDefault(false)
}
