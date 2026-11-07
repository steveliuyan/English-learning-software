package com.example.englishlearning.core.storage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

/**
 * 批 C-5 真预取契约（判据：`docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` 决定 4 第 1 条、
 * §4「预取零出站」那条与 `docs/specs/05-release-quality-and-compliance.md` F5-05 / AC5-06）。
 *
 * 要修的事实（批 C-5 开工实测）：`BrandLaunchActivity` 那 1.2 秒只放了一张品牌图，什么都没算；
 * 真正的建库 + Room 迁移发生在闪屏之后、`MainActivity` 的 Hilt 图第一次请求 `AppDatabase` 时
 * （`di/AppModule.kt` 的 `provideDatabase` 里那一句 `DatabaseStartup.open(context)`）。
 * 于是批 C-2/C-3 铺好的骨架至少白等一轮 IO——用户看到的「首帧有形状但一直空着」就是这段。
 * 本批把「请求 `AppDatabase` 单例」这一件事挪进那 1.2 秒，别的什么都不挪。
 *
 * 形状逐字照抄 `language/infrastructure/StartupPronunciationWarmer`（决策 §2.4 点名的唯一先例）：
 * 构造参数就是一枚挂起闭包、尽力而为、失败静默、返回值只给日志用不上 UI。
 *
 * 三条被钉住的性质：
 *  1. **只请求一次**：`AppDatabase` 是 `@Singleton`，预热重复调用没有意义，且第二次会撞 Dagger 的
 *     单例锁；「恰好一次」是可执行判据（[warm_requests_the_local_store_exactly_once_and_reports_success]）。
 *  2. **失败不许外抛**：建库失败在这里必须静默，因为呈现失败的地方是 `RecoveryScreen`，
 *     它读的是 `RoomDatabaseStartupProbe` 里由 `provideDatabase` 记下的同一份失败——预热再抛一次就是
 *     把「已经如实呈现的缺陷」变成「崩在后台线程」。
 *  3. **取消不算失败**：`warmUpScope` 被取消时 `CancellationException` 必须原样透传，
 *     不能被 `runCatching` 吞成 `false`。这条与 [com.example.englishlearning.core.storage.DatabaseStartup.openWith]
 *     同一口径（本仓对协作品取消的一致处理），差别一旦被抹平，将来「取消启动预热」会变成假成功。
 *
 * 「不触网」这一条怎么钉（决策 §4 第 4 条要求用假 provider 断言它不触网）：
 * 本类只有一个合作者，就是构造参数那一枚挂起闭包，**没有第二个依赖可递**，所以判据落在源码形状上：
 * [the_warmer_source_imports_nothing_that_could_reach_the_network] 把 import 名单收成闭集。
 * 长出任何 AI / 网络合作者必然先长出一行 import，那一行就是当场判红；
 * 合成双边（must-flag / must-not-flag）在 [import_allowlist_catches_a_network_seam_and_passes_the_local_one] 里自证判据不是空转。
 */
class StartupLocalDataWarmerTest {

    @Test
    fun `warm requests the local store exactly once and reports success`() = runTest {
        var calls = 0
        val warmer = StartupLocalDataWarmer { calls += 1 }

        assertTrue(warmer.warm(), "开库成功必须回报 true（回报只给日志用，但它是本批唯一的可观察量）")
        assertEquals(1, calls, "预热只许请求一次单例")
    }

    @Test
    fun `warm stays silent when opening the local store fails`() = runTest {
        val warmer = StartupLocalDataWarmer { throw IllegalStateException("数据库文件无法打开") }

        assertFalse(warmer.warm(), "开库失败要静默回报 false：呈现失败的位置是 RecoveryScreen，不是后台线程")
    }

    @Test
    fun `warm does not swallow coroutine cancellation`() = runTest {
        val warmer = StartupLocalDataWarmer { throw CancellationException("作用域已取消") }

        assertFailsWith<CancellationException> {
            warmer.warm()
        }
    }

    @Test
    fun `the warmer source imports nothing that could reach the network`() {
        val imports = importLines(sourceFile("core/storage/StartupLocalDataWarmer.kt"))
        val allowed = setOf("kotlinx.coroutines.CancellationException")
        val offenders = imports - allowed
        assertTrue(
            offenders.isEmpty(),
            "StartupLocalDataWarmer 的 import 名单越界：$offenders。本类按决定 4 只做「请求 AppDatabase 单例」一件事，" +
                "任何 AI/网络合作者都要先长出一行 import——这一行就是预取新增出站的现场（AGENTS.md：本地优先）。",
        )
    }

    @Test
    fun `import allowlist catches a network seam and passes the local one`() {
        // 合成双边之一：把预取接上 AI 网关的形状必须被命中，否则上面的闭集判据是空转。
        val withNetworkSeam = """
            package com.example.englishlearning.core.storage
            import com.example.englishlearning.ai.net.AiHttpTransport
            import kotlinx.coroutines.CancellationException
        """.trimIndent()
        assertEquals(
            setOf("com.example.englishlearning.ai.net.AiHttpTransport"),
            importLines(withNetworkSeam) - setOf("kotlinx.coroutines.CancellationException"),
            "闭集判据漏判「预取长出网络合作者」：这条红线必须当场可红",
        )
        // 合成双边之二：现状形状必须放行，注释里的示例 import 不许算违例。
        val localOnly = """
            package com.example.englishlearning.core.storage
            // 曾考虑 import com.example.englishlearning.ai.net.AiHttpTransport——决定 4 明令排除，本行不算违例。
            import kotlinx.coroutines.CancellationException
        """.trimIndent()
        assertEquals(setOf("kotlinx.coroutines.CancellationException"), importLines(localOnly))
    }

    @Test
    fun `the application kicks the local warmer in the splash window on the io dispatcher`() {
        val source = sourceFile("EnglishLearningApplication.kt")
        assertTrue(
            "StartupLocalDataWarmer" in source,
            "EnglishLearningApplication 没接线批 C-5 本地预热：开库仍排在闪屏之后，骨架白等一轮 IO。",
        )
        assertTrue(
            "databaseProvider.get()" in source,
            "预热必须走「请求 AppDatabase 单例」这一条既有路径（决定 4：不得自己读学习数据塞进 VM 状态，" +
                "那会造出第二套数据权威）。",
        )
        assertTrue(
            "withContext(Dispatchers.IO)" in source,
            "建库是磁盘 IO，必须落在 IO 线程；与 Kokoro 预热同处 Dispatchers.Default 是决策 §5 点名的竞争面。",
        )
        val offenders = importLines(source) - APPLICATION_IMPORT_ALLOWLIST
        assertTrue(
            offenders.isEmpty(),
            "EnglishLearningApplication 的 import 名单越界：$offenders。启动路径上新增 AI/网络合作者" +
                "就是「为了预取打一次出站」，红线不允许。",
        )
    }

    @Test
    fun `both startup warmers run in the background scope and never on the main thread`() {
        val source = sourceFile("EnglishLearningApplication.kt")
        val kicks = Regex("""warmUpScope\.launch""").findAll(source).count()
        assertEquals(
            2,
            kicks,
            "onCreate 里应有两枚后台预热（Kokoro 引擎 + 本地开库）各用一次 warmUpScope.launch，实际 $kicks 次：" +
                "预取挪进主线程就是拿首帧换启动 ANR，多出来的一枚必须先说明它预热什么。",
        )
    }

    /** 应用启动路径的 import 闭集：新增一枚就要先在这里登记，登记不了就说明它本来不该出现。 */
    private val APPLICATION_IMPORT_ALLOWLIST = setOf(
        "android.app.Application",
        "com.example.englishlearning.core.storage.AppDatabase",
        "com.example.englishlearning.core.storage.StartupLocalDataWarmer",
        "com.example.englishlearning.language.infrastructure.KokoroEngine",
        "com.example.englishlearning.language.infrastructure.StartupPronunciationWarmer",
        "com.example.englishlearning.notification.ReminderNotifications",
        "dagger.hilt.android.HiltAndroidApp",
        "javax.inject.Inject",
        "javax.inject.Provider",
        "kotlinx.coroutines.CoroutineScope",
        "kotlinx.coroutines.Dispatchers",
        "kotlinx.coroutines.SupervisorJob",
        "kotlinx.coroutines.launch",
        "kotlinx.coroutines.withContext",
    )

    /**
     * import 行的取法：只认真正的 import 语句。
     * 行注释形态的 `// import x` 剥掉行首空白后以斜杠开头，不落在本匹配面内——
     * 这正是 [import_allowlist_catches_a_network_seam_and_passes_the_local_one] 那条合成双边要证的性质。
     */
    private fun importLines(source: String): Set<String> =
        source.lines().map { it.trim() }.filter { it.startsWith("import ") }
            .map { it.removePrefix("import ").substringBefore(" as ").trim() }.toSet()

    /** 读 main 源码；JVM 单测的工作目录是 `app/` 模块目录，两种相对形状都吃过一次亏，故两条都试。 */
    private fun sourceFile(relative: String): String {
        val candidates = listOf(File("src/main/java/com/example/englishlearning/$relative"), File("app/src/main/java/com/example/englishlearning/$relative"))
        val file = candidates.firstOrNull { it.isFile }
        assertTrue(
            file != null,
            "找不到源码 $relative（试过 ${candidates.joinToString { it.path }}，JVM 工作目录 ${File(".").absolutePath}）：" +
                "文件搬家或工作目录变化都会让本契约空扫，必须先修此处而不是放宽判据。",
        )
        return file!!.readText()
    }
}
