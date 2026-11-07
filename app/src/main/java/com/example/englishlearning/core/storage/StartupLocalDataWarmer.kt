package com.example.englishlearning.core.storage

import kotlinx.coroutines.CancellationException

/**
 * 批 C-5 决定 4 第 1 条：闪屏那 1.2 秒预热本地盘。
 *
 * 要修的事实：`BrandLaunchActivity` 的 1.2 秒只放了一张品牌图；建库与 Room 迁移发生在闪屏之后、
 * `MainActivity` 的 Hilt 图第一次请求 `AppDatabase` 时（`di/AppModule.kt` 的 `provideDatabase` 里那一句
 * `DatabaseStartup.open(context)`）。批 C-2/C-3 铺好的骨架因此至少白等一轮磁盘 IO。
 * 本类把那一句挪进启动窗口，**只做「请求单例」这一件事**：不读学习数据、不塞任何 VM 状态
 * （那会造出第二套数据权威）、不预跑备份/导出等重 IO——三条都是决定 4 的原话。
 *
 * 形状照抄 `language/infrastructure/StartupPronunciationWarmer`（决策 §2.4 点名的唯一先例）：
 * 一枚挂起闭包当全部合作者、尽力而为、失败静默、返回值只给日志用不上 UI。
 * 也因此它**结构上无法出站**：没有第二个依赖可递，import 名单由 `StartupLocalDataWarmerTest` 收成闭集。
 *
 * 与先例的一处有意差别：协程取消**不算失败**。[com.example.englishlearning.core.storage.DatabaseStartup.openWith]
 * 对 `CancellationException` 是原样透传的，本类沿用同一口径；把它吞成 `false` 会让「作用域已取消」
 * 被读成「开库失败」，而这两种情况在启动路径上要做的事完全不同。
 */
class StartupLocalDataWarmer(private val openLocalStore: suspend () -> Unit) {

    /** true = 本地库已在启动窗口内打开；任何失败返回 false 并保持静默（呈现失败的位置是 RecoveryScreen）。 */
    suspend fun warm(): Boolean = try {
        openLocalStore()
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }
}
