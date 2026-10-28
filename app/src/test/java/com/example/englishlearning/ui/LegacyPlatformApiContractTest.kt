package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 生产源码禁用 API 34+ 的时间工厂 `ofInstant`（批 1 Task 9 真机取证后补的棘轮）。
 *
 * 为什么必须有一条源码棘轮而不是只修两处调用：
 *  - `java.time.LocalDate.ofInstant(Instant, ZoneId)` / `LocalDateTime.ofInstant(Instant, ZoneId)`
 *    是 **API 34** 才有的静态工厂，而本应用最低支持 Android 13（API 33）且仓库**没有开
 *    core library desugaring**。代码在 JVM 单测里永远是绿的（JVM 的 `java.time` 是 17+），
 *    到设备上才以 `java.lang.NoSuchMethodError` 当场崩。
 *  - 2026-10-25 批 1 Task 9 真机走查（`ro.build.version.sdk=33`）实测：打卡 → 历史记录页签，
 *    应用直接退出，crash buffer 原文
 *    `NoSuchMethodError: No static method ofInstant(Ljava/time/Instant;Ljava/time/ZoneId;)Ljava/time/LocalDate;`
 *    栈顶 `LearningRecordsViewModel$launchTab$2`（`LearningRecordsViewModel.kt:151`）。
 *  - 这不是第一次：`ReadingAccessViewModel.kt:315` 的 KDoc 已把「不用 `LocalDate.ofInstant`，
 *    那是 API 34+ 的方法，Android 13 真机直接 NoSuchMethodError」写死，决策
 *    `docs/decisions/2026-09-24-article-generation-and-display.md` 也登记过同类崩溃。
 *    上次只修了阅读链路，同类写法在别处复燃——单点注释挡不住第二次，故收敛成全场扫描。
 *
 * 口径：
 *  - 扫描根是**整个生产源码根** [APP_MAIN_SOURCE_PATH]，不是 `ui/`：`notification/` 下的
 *    `ReminderSchedulingPolicy.kt` 就是本次第二个站点，按 `ui` 扫描会正好漏掉它。
 *  - 规则只咬 `ofInstant(`，先剥注释（共享夹具 [violations] 内建），所以 KDoc 里写这个 API 名
 *    不会误红；`Instant.ofEpochMilli(...)` 之类**不**在视野内（那是 API 26 起就有、且本仓在用的写法）。
 *  - 空 allowlist：这是「平台兼容性」而不是分批偿还的样式债，不存在合法的存量豁免。
 *    确需豁免时先改本 KDoc 写明机型/API 依据，再动 allowlist——不要默默放宽。
 *
 * 替代写法（本仓既有方言，`ReadingCompletionRepository` / `CheckInViewModel` / `ReadingAccessViewModel`
 * 三处同形）：`clock.instant().atZone(clock.zoneId()).toLocalDate()`（要日期）或
 * `.toLocalDateTime()`（要时刻）。`atZone` 自 API 26 起可用。
 */
class LegacyPlatformApiContractTest {

    private val scanRoot = File(APP_MAIN_SOURCE_PATH)

    @Test
    fun `production code never calls the api 34 only ofInstant factory`() {
        val offenders = violations(
            label = "API 34+ ofInstant 工厂",
            rule = Regex("""\bofInstant\s*\("""),
            allowlist = emptySet(),
            scanDir = scanRoot,
        )
        assertEquals(
            emptyList<String>(),
            offenders,
            "生产源码出现 `ofInstant(`：它是 API 34+ 的静态工厂，Android 13（API 33）设备上会以 " +
                "NoSuchMethodError 崩溃（2026-10-25 批 1 真机实证）。请改成 " +
                "`instant.atZone(zone).toLocalDate()` / `.toLocalDateTime()`。命中：" +
                offenders.joinToString("; "),
        )
    }

    /**
     * 扫描面守卫：棘轮的价值 = 扫到的正是「全应用生产代码」。
     * 根路径被改窄（例如误回 `ui/`）、或模块目录改名，`uiFiles` 只保证「非空」，
     * 这两个锚让「扫描面缩水」当场红，而不是变成一条永远绿的假棘轮。
     */
    @Test
    fun `the scan root really covers ui and notification production sources`() {
        val files = uiFiles(scanRoot, expectedHint = "API 34+ ofInstant 工厂")
        val relative = files.map { it.relativePathFrom(scanRoot) }
        assertTrue(
            relative.any { it.startsWith("ui/") } && relative.any { it.startsWith("notification/") },
            "扫描根 $APP_MAIN_SOURCE_PATH 必须同时覆盖 ui/ 与 notification/（本次两处 ofInstant 站点分居两侧）：" +
                "实际 ${relative.size} 个 .kt，样例 ${relative.take(5)}",
        )
        assertTrue(
            relative.any { it == "ui/LearningRecordsViewModel.kt" } &&
                relative.any { it == "notification/domain/ReminderSchedulingPolicy.kt" },
            "两个已知站点必须都在扫描面内（路线改名/搬家时本棘轮会静默失效）：$relative",
        )
    }

    /**
     * 正半条：只断言「没有 `ofInstant`」的话，把整行删掉也能绿——那会把页签语义一起删掉。
     * 两处调用点必须仍在做同一件事（取「当前时刻」在用户时区里的日期/时刻），只是换成本地可用的写法。
     */
    @Test
    fun `both known call sites keep computing local date and time through atZone`() {
        val records = productionSource("ui/LearningRecordsViewModel.kt")
        assertTrue(
            "clock.instant().atZone(clock.zoneId()).toLocalDate()" in records,
            "LearningRecordsViewModel 的历史范围终点必须仍是「当前时刻在用户时区里的日期」：" +
                "换成 `LocalDate.now()` 会脱离注入时钟，测试与生产时区口径一起漂",
        )

        val reminder = productionSource("notification/domain/ReminderSchedulingPolicy.kt")
        assertTrue(
            "clock.instant().atZone(zone).toLocalDateTime()" in reminder,
            "提醒策略的 now 必须仍是「注入时钟 + 注入时区」的本地时刻：" +
                "换成 `LocalDateTime.now()` 会绕过 ClockProvider，FixedClock 测试夹具失效",
        )
    }

    private fun productionSource(relative: String): String {
        val file = File(APP_MAIN_SOURCE_PATH, relative)
        assertTrue(
            file.isFile,
            "生产源码缺失：${file.path}（相对 JVM 工作目录 ${File(".").absolutePath}）——" +
                "改名/搬家时本契约会读空，先修路径",
        )
        return stripComments(file.readText())
    }
}
