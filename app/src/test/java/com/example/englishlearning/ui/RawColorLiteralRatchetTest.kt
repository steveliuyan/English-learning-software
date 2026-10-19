package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AC-R-02 裸 hex 棘轮（批 1 Task 8 Step 2；修复轮 1 收 I1/I3/M-N2/M-N3/M-N5）。
 *
 * 管什么：`ui` 包里新增一处 `Color(0xAARRGGBB)` 裸字面量即当场红；存量站点由本文件的 seed 表逐文件登记，
 * 且两侧都不许偏——
 *  ① 命中行不在 seed 行集里 → 新增即挡；
 *  ② 每文件实际命中数 != 该文件 seed 计数，两个方向都挡：既挡「seed 有而文件已消失/改名」，
 *    也挡「实际减了而 seed 没跟着减」。
 * 所以 token 化掉一处裸 hex 时必须同步把本文件的 seed 减 1：seed 是债务台账，不是永久豁免。
 *
 * 还管 `ui/` 之外：`ui/` 是全仓唯一成片的裸 hex 聚集地，但它不是「唯一一处」。`launch/BrandLaunchActivity.kt`
 * 的三段品牌渐变是 `ui/` 之外全仓仅存的裸 hex，Task 4 已把它路由给本棘轮的台账/收口清理；故本文件另起一条
 * 同规则、扫描根换成 `src/main/java`（排除整个 `ui` 子树）的用例把它冻进 seed，免得它在 Task 26 收口时隐身。
 *
 * 不管什么：本棘轮不宣称「全站零裸 hex」。AC-R-02 的那个「零」由批 3 收口时把 seed 表清零、或只剩经计划
 * 修订确认的不可迁移站点（逐条写明理由）来兑现；批 1 只负责把债务冻住，并让它在批 2/批 3 的每屏迁移里单调递减。
 *
 * 主题侧豁免口径（修复轮 1 的 I1，替代旧版「整目录豁免 `ui/theme/`」）：
 *  - 旧版把 `ui/theme/` 整个目录划出视野，可洗债：往 `ui/theme/AppShape.kt` 这种「非定义文件」加一枚裸 hex，
 *    目录豁免让它对 `violations` 隐形，seed 又只覆盖 `ui` 非主题文件 → 16 条全绿，AC-R-02 出现可绕过缺口。
 *  - 现口径：豁免只对**实测含 hex 的三个定义文件**成立（`theme/AppPalette.kt`、`theme/AppColors.kt`、
 *    `theme/MintPalette.kt`，理由见 [RawHexThemeDefinitionFiles]）；其余 `ui/theme/` 文件一律恢复纳管，
 *    由 `non definition ui theme files hold no raw hex` 断言 0 命中（探针即在此变红）、
 *    由 `theme exemption is exactly the load bearing definition list` 双向钉住「名单 = 实际含 hex 的主题文件」
 *    （名单里某个文件跌到 0 命中 → 它已不再是定义文件 → 红，防名单固化，与深色棘轮裁决 2 同口径）。
 *  - 共享夹具 `violations` 的 `exemptDirNames` 仍按目录名工作（其签名语义本轮不改，见 M-N6）；主题侧的「收窄到定义
 *    文件名单」落在上面两条专属用例里，offenders 取数仍走目录豁免，二者互补而非重复。
 *
 * 口径（与共享夹具 `SourceRatchet` 一致）：
 *  - 先剥注释再匹配；offender 形如「文件名: 折叠空白的源码行」，不报行号（块注释剥离会把其后行
 *    折叠进同一行文本，此时行号已不可信）。
 *  - offender 取样不做 distinct：同一物理行含两处命中时，distinct 会把两次命中显示成一条、掩盖真实
 *    计数；「数量精确」由按文件计数用例保证，违规集只负责点名。
 *  - 规则口径 = 共享夹具唯一的 [rawColorLiteral]（批 1 终审 F2 对计划 Task 8 Step 2 原文规则的订正）：
 *    认 6 位与 8 位十六进制、容忍 Kotlin 整型字面量后缀与 `.toInt()/.toLong()/…` 窄化调用，
 *    因为 Compose 只有 `Color(Long)`，`Color(0xFF6B61L)` 这类换形**能编译**。
 *    `Color.argb(...)`、命名常量异名同色仍不在本棘轮视野内（那是禁令族与亮度复算契约的事）；
 *    超过 8 位的十六进制（`Color(0xFF11223344)`）也抓不到——那是规则的边界而不是夹具坏了，
 *    别拿它当「夹具不工作」的证据去放宽正则。
 */

/**
 * 债务台账：`ui` 包内（`ui/theme/` 除外）裸 hex 的按文件计数，key 为相对 `ui` 根的路径。
 * 批 2/批 3 每屏迁移的收口证据就是这张表对应行减到 0；批 3 收口时清零，或只剩经计划修订确认的不可迁移站点。
 */
internal val RawColorSeedAllowlist: Map<String, Int> = mapOf(
    "AppScreen.kt" to 2,
    "TodayPlanScreen.kt" to 5,
    "ArticleReadingScreen.kt" to 1,
    "CheckInScreen.kt" to 1,
    "components/glass/GlassButton.kt" to 1,
    "mascot/AiMascot.kt" to 1,
)

/**
 * 同一批站点的「文件名: 折叠源码行」形状，供 `violations` 作行级 allowlist。
 * 与 [RawColorSeedAllowlist] 必须互相吻合（本测试当场核验）。吻合断言同时是「两条站点折叠后同文」
 * 的探测器：真出现同文站点，Set 形状的 allowlist 会静默少计一条，那条断言当场红——届时必须把这张表
 * 换成「行内容 → 应有次数」的 Map，而不是删掉重复条目蒙混过去。
 */
internal val RawColorSeedSiteLines: List<String> = listOf(
    "AppScreen.kt: colors = SwitchDefaults.colors(checkedTrackColor = MintPrimary, uncheckedTrackColor = Color(0xFFCDEFE1)),",
    "AppScreen.kt: inactiveTrackColor = Color(0xFFCDEFE1),",
    "ArticleReadingScreen.kt: style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF17342A)),",
    "CheckInScreen.kt: drawArc(color = Color(0xFFFFF1DB), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(strokeWidth, cap = StrokeCap.Round))",
    "TodayPlanScreen.kt: CountCard(\"今日新增 \${state.newTarget} 词\", \"今日新增 \${state.newTarget} 词\", \"today_plan_new_count\", Color(0xFF68BDA6))",
    "TodayPlanScreen.kt: CountCard(\"今日复习 \${state.dueTarget} 词\", \"今日复习 \${state.dueTarget} 词\", \"today_plan_due_count\", Color(0xFFC7A47D))",
    "TodayPlanScreen.kt: colors = ButtonDefaults.buttonColors(containerColor = AppPalette.Surface, contentColor = Color(0xFF795B36)),",
    "TodayPlanScreen.kt: private val WorkloadReviewColor = Color(0xFFC7A47D)",
    "TodayPlanScreen.kt: private val WorkloadNewColor = Color(0xFF68BDA6)",
    "GlassButton.kt: Brush.verticalGradient(listOf(AppleMintStart, AppleMintMiddle, Color(0xFF65D3B9)))",
    "AiMascot.kt: private val EyeColor: Color = Color(0xFF188F76)",
)

/**
 * 裸 hex 的主题侧豁免名单（修复轮 1 的 I1）：只有这三枚「令牌定义本体」被允许携带裸 hex，因为它们就是
 * 设计令牌取值的书写地（key 为相对 `ui` 根的路径，M-N2：与其余台账同一口径，按精确相等而非后缀匹配）。
 * 其余 `ui/theme/` 文件不在本名单，恢复纳管——它们若长出裸 hex，由 `non definition ui theme files hold no raw hex`
 * 当场判红，堵住旧版「整目录豁免」可把债务洗进 `ui/theme/` 非定义文件的路径。
 */
internal val RawHexThemeDefinitionFiles: Map<String, String> = mapOf(
    "theme/AppPalette.kt" to "浅套静态源与 DomainColors 定义本体：色值在这里写死，是 hex 的书写地",
    "theme/AppColors.kt" to "深浅两套 AppColorSet / DomainPalette 装配源：逐枚令牌以裸 hex 定义",
    "theme/MintPalette.kt" to "薄荷令牌静态源（AppleMint* 品牌渐变等）：品牌色在此以裸 hex 定稿",
)

/**
 * `ui/` 之外的全仓裸 hex 台账（修复轮 1 的 I3，承接 Task 4 交回 Task 8 的清算项）。
 * `launch/BrandLaunchActivity.kt` 是 `ui/` 之外唯一携带裸 hex 的文件：三段品牌渐变三端
 * （`AppleMintStart/Middle/End` 的逐字副本）。AppleMint* 保持静态是 Task 3 已验收的「品牌渐变模式不变」口径，
 * 本条只负责「别让它成为唯一没人管的裸 hex、并长出第二份副本」；具体迁法（改引 AppleMint* 还是 token 化）
 * 由批 3 的 Task 26 收口时定。三枚色值同在一条物理行上，故折叠后只有一条站点行，计数（3）以按文件计数为准。
 *
 * key 口径（批 1 终审 F8 的 M-3 前半）：**相对 `src/main/java/com/example/englishlearning` 根的精确路径**，
 * 与 [RawColorSeedAllowlist]（相对 `ui` 根）同形，避免 `ui/` 外未来同名 `.kt` 在 `associate` 里互相顶替；
 * [BrandLaunchSeedSiteLines] 保持「裸文件名: 折叠行」形状，因为那是共享夹具 [violations] 的 offender 形状，
 * 本轮不改夹具签名（`scan scope resolves to real ui sources` 那条重名守卫负责盯住它不冒名）。
 */
internal val BrandLaunchSeedAllowlist: Map<String, Int> = mapOf(
    "launch/BrandLaunchActivity.kt" to 3,
)

/** 与 [BrandLaunchSeedAllowlist] 同形状的「文件名: 折叠源码行」行集，喂 `violations` 作 `ui/` 外扫描的行级 allowlist。 */
internal val BrandLaunchSeedSiteLines: List<String> = listOf(
    "BrandLaunchActivity.kt: listOf(Color(0xFFA8F3C8), Color(0xFF5DDFB4), Color(0xFF35B9B5)),",
)

class RawColorLiteralRatchetTest {
    private val uiDir = File(UI_MAIN_SOURCE_PATH)
    private val appDir = File(APP_MAIN_SOURCE_PATH)
    private val themeDir = File(UI_THEME_SOURCE_PATH)

    /**
     * 检测口径 = 共享夹具唯一的 [rawColorLiteral]（批 1 终审 F2）。
     * 计划 Task 8 Step 2 的「逐字照用」原文规则本身**有缺陷**，本简报即为对该计划的订正：
     * 旧形状 `Color\(\s*0x[0-9A-Fa-f]{8}\s*\)` 要求十六进制紧跟右括号，而 Compose 只有 `Color(Long)`，
     * `Color(0xFF6B61L)`、`Color(0xFF6B61FFu.toLong())` 都能编译 —— 后缀与窄化调用一律躲过旧探针
     * （缺陷由批 1 终审 C2 席实测，变异证据见本轮报告）。
     */
    private val rule = rawColorLiteral

    private fun File.relativeUiPath(): String = relativePathFrom(uiDir)

    /** 「文件名: 行」形状要成立，文件名必须在扫描集内唯一，否则两个同名文件的站点会互相冒名顶替。 */
    private fun filesByName(): Map<String, File> = uiFiles(uiDir).associateBy { it.name }

    /** 主题侧排除走共享夹具入口（M-N3：不再在本文件另写一份目录判定，与 `violations` 同口径）。 */
    private fun File.isInThemeDir(): Boolean = isUnderDirNamed(uiDir, THEME_DIR_NAME)

    private fun offenders(): List<String> =
        violations("裸 hex", rule, RawColorSeedSiteLines.toSet(), uiDir, exemptDirNames = setOf(THEME_DIR_NAME))

    private fun actualCounts(): Map<String, Int> = uiFiles(uiDir)
        .filterNot { it.isInThemeDir() }
        .associate { it.relativeUiPath() to countMatches(it.readText(), rule) }
        .filterValues { it > 0 }

    /**
     * `ui/` 之外的全仓裸 hex 按文件计数。
     * **M-3 前半（批 1 终审 F8）**：key 用相对 `app` 源码根的**精确路径**（`launch/BrandLaunchActivity.kt`），
     * 与 [actualCounts] 的相对 `ui` 根路径同形。旧版用裸文件名，而 [violations] 的 offender 形状也用裸文件名，
     * `ui/` 外一旦出现同名 `.kt`，`associate` 会静默覆盖、少计一处债务。
     */
    private fun nonUiActualCounts(): Map<String, Int> = uiFiles(appDir, expectedHint = "ui 外裸 hex：${appDir.path}")
        .filterNot { it.isUnderDirNamed(appDir, UI_DIR_NAME) }
        .associate { it.relativePathFrom(appDir) to countMatches(it.readText(), rule) }
        .filterValues { it > 0 }

    private fun nonUiOffenders(): List<String> =
        violations("ui 外裸 hex", rule, BrandLaunchSeedSiteLines.toSet(), appDir, exemptDirNames = setOf(UI_DIR_NAME))

    private fun tempSources(vararg sources: Pair<String, String>): File {
        val dir = java.nio.file.Files.createTempDirectory("raw-color-ratchet").toFile()
        sources.forEach { (path, text) ->
            val target = File(dir, path)
            target.parentFile?.mkdirs()
            target.writeText(text)
        }
        return dir
    }

    @Test
    fun `scan scope resolves to real ui sources and names files unambiguously`() {
        val files = uiFiles(uiDir)
        assertTrue(
            files.any { it.name.endsWith("Screen.kt") },
            "裸 hex 棘轮扫到 .kt 却不含任何 *Screen.kt，扫描范围疑似错位：$UI_MAIN_SOURCE_PATH"
        )
        val collidedNames = files.groupBy { it.name }.filterValues { it.size > 1 }
        assertTrue(
            collidedNames.isEmpty(),
            "offender 用「文件名: 行」形状，但扫描集里有重名文件，站点会互相冒名顶替：$collidedNames"
        )
        assertTrue(
            files.any { it.isInThemeDir() },
            "裸 hex 的 violations 目录豁免（用于把令牌定义文件排除出行级扫描）在扫描集里一个文件都没覆盖到，" +
                "豁免名疑似失效：$UI_THEME_SOURCE_PATH"
        )
    }

    @Test
    fun `seed tables agree with each other and keep every site line distinct`() {
        assertTrue(
            RawColorSeedSiteLines.size == RawColorSeedSiteLines.toSet().size,
            "seed 行集里有折叠后同文的两个站点：Set 形状的 allowlist 会静默少计一条，" +
                "此时必须把这张表换成「行内容 → 应有次数」的 Map，而不是删掉重复条目"
        )
        val byName = filesByName()
        val unknownNames = RawColorSeedSiteLines
            .map { it.substringBefore(SEED_LINE_SEPARATOR) }
            .distinct()
            .filterNot { it in byName }
        assertTrue(
            unknownNames.isEmpty(),
            "seed 行集点到扫描集里不存在的文件（改名/删除必须同步 seed）：$unknownNames"
        )
        val countsFromLines = RawColorSeedSiteLines
            .groupingBy { byName.getValue(it.substringBefore(SEED_LINE_SEPARATOR)).relativeUiPath() }
            .eachCount()
        assertEquals(
            RawColorSeedAllowlist,
            countsFromLines,
            "seed 行集与 seed 计数表不吻合：两张台账是同一批债务的两个面，必须同进同退"
        )
    }

    @Test
    fun `no raw hex outside the seed - a newly added literal fails the ratchet`() {
        val offenders = offenders()
        assertTrue(
            offenders.isEmpty(),
            "新增裸 hex（应改为 appColors().x / appDomain(AppDomain.X).deep 等 token；" +
                "若属令牌定义请把站点移进 ui/theme，若是确证的不可迁移站点，先修订计划再登记 seed）：\n" +
                offenders.joinToString("\n")
        )
    }

    @Test
    fun `per-file hit count equals the seed count in both directions`() {
        val actual = actualCounts()
        val staleSeed = RawColorSeedAllowlist.filterKeys { it !in actual }
        assertTrue(
            staleSeed.isEmpty(),
            "seed 登记的裸 hex 文件已不在命中集里（文件被改名/删除，或该文件的债务已 token 化完）：$staleSeed" +
                " —— 已迁移的必须把本文件 seed 一并删掉，棘轮不接受残留豁免"
        )
        val unseeded = actual.filterKeys { it !in RawColorSeedAllowlist }
        assertTrue(
            unseeded.isEmpty(),
            "出现未登记的裸 hex 文件：$unseeded —— 应改为 appColors().x / appDomain(AppDomain.X).deep，" +
                "并把本屏迁移交回批 2/批 3 的收口任务，不许在此处临时开豁免"
        )
        val drifted = actual.filter { (path, count) -> RawColorSeedAllowlist[path] != count }
        assertTrue(
            drifted.isEmpty(),
            "裸 hex 计数与 seed 不符（增即挡，减了不同步改 seed 也挡）：\n" +
                drifted.entries.joinToString("\n") { (path, count) ->
                    "$path 实际 $count 处，seed ${RawColorSeedAllowlist[path]} 处——迁走一处就把本文件 seed 减 1"
                }
        )
    }

    @Test
    fun `theme exemption is exactly the load bearing definition list`() {
        // I1 的「防名单固化」与「豁免 = 实际含 hex 的主题文件」双向钉死：
        //  ① 名单里某个定义文件跌到 0 命中（它已不再写 hex）→ 红，免得名单悄悄变成无人核验的盲区；
        //  ② 名单外某个 theme 文件长出裸 hex → 红，这正是旧版整目录豁免洗债的路径。
        val hexBearingThemeFiles = uiFiles(themeDir)
            .associate { it.relativeUiPath() to countMatches(it.readText(), rule) }
            .filterValues { it > 0 }
            .keys
        val staleEntries = RawHexThemeDefinitionFiles.keys.filterNot { it in hexBearingThemeFiles }
        assertTrue(
            staleEntries.isEmpty(),
            "裸 hex 豁免名单里的定义文件已不再含裸 hex（已换写法或已迁走）：$staleEntries —— " +
                "它不再需要豁免，必须从名单删掉，否则豁免区里多一个没人看的文件"
        )
        val unlisted = hexBearingThemeFiles.filterNot { it in RawHexThemeDefinitionFiles.keys }
        assertTrue(
            unlisted.isEmpty(),
            "ui/theme 里出现了名单之外的含裸 hex 文件：$unlisted —— 令牌定义之外的主题文件不该写裸 hex，" +
                "要么把它迁成 token 并登记进债务 seed，要么先修订计划再把它加进定义文件名单"
        )
    }

    @Test
    fun `non definition ui theme files hold no raw hex`() {
        // I1 的探针落点：往 ui/theme/AppShape.kt（非定义文件）加一枚裸 hex，本用例必须当场判红。
        val offenders = uiFiles(themeDir)
            .filterNot { it.relativeUiPath() in RawHexThemeDefinitionFiles.keys }
            .map { it.relativeUiPath() to countMatches(it.readText(), rule) }
            .filter { (_, count) -> count > 0 }
        assertTrue(
            offenders.isEmpty(),
            "ui/theme 的非定义文件里出现裸 hex $offenders —— AC-R-02 不接受把债务洗进主题目录的豁免区：" +
                "这些文件不写令牌定义，应改用 token；若确有主题定义需要落在此处，先修订计划再补进定义文件名单"
        )
    }

    @Test
    fun `raw hex outside ui is exactly the registered launch ledger`() {
        // I3：ui 之外全仓仅 launch/BrandLaunchActivity.kt 一段品牌渐变（3 枚同处一物理行）。
        val offenders = nonUiOffenders()
        assertTrue(
            offenders.isEmpty(),
            "ui 之外新增了未登记的裸 hex：\n${offenders.joinToString("\n")} —— " +
                "全仓裸 hex 只允许 launch 的三段品牌渐变（与 AppleMint* 重复，Task 4 路由给本棘轮），" +
                "新增副本会先撞这里；迁法由批 3 的 Task 26 收口时定"
        )
        val actual = nonUiActualCounts()
        val staleSeed = BrandLaunchSeedAllowlist.filterKeys { it !in actual }
        assertTrue(
            staleSeed.isEmpty(),
            "ui 外裸 hex 台账登记的文件已无命中（已迁走/改名）：$staleSeed —— 同步把本行 seed 删掉"
        )
        val unseeded = actual.filterKeys { it !in BrandLaunchSeedAllowlist }
        assertTrue(
            unseeded.isEmpty(),
            "ui 之外出现了台账未覆盖的裸 hex 文件：$unseeded —— 应登记进 BrandLaunchSeedAllowlist 或迁成 token"
        )
        val drifted = actual.filter { (name, count) -> BrandLaunchSeedAllowlist[name] != count }
        assertTrue(
            drifted.isEmpty(),
            "ui 外裸 hex 计数与台账不符（增即挡，减了不同步改也挡）：\n" +
                drifted.entries.joinToString("\n") { (name, count) ->
                    "$name 实际 $count 处，seed ${BrandLaunchSeedAllowlist[name]} 处"
                }
        )
        // 复用 I1 补好的目录守卫：ui 之外的扫描根必须解析得到文件，禁止静默空扫（launch 段消失即在此暴露）。
        assertTrue(
            uiFiles(appDir, expectedHint = "ui 外裸 hex：${appDir.path}").isNotEmpty(),
            "ui 之外的扫描根解析不到任何 .kt：${appDir.path} —— 包名重构 / 工作目录变化必须先修扫描路径"
        )
    }

    @Test
    fun `allowlisted site disappears from the violation set and an unlisted one stays`() {
        val dir = tempSources(
            "Plain.kt" to "private val a = Color(0xFF112233)\nprivate val b = Color(0xFF556677)\n",
        )
        try {
            val all = violations("合成裸 hex", rule, emptySet(), dir)
            assertEquals(
                listOf(
                    "Plain.kt: private val a = Color(0xFF112233)",
                    "Plain.kt: private val b = Color(0xFF556677)",
                ),
                all,
                "violations 必须逐命中给出一条「文件名: 行」，同一行的两处命中不得合并"
            )
            val afterExemption = violations("合成裸 hex", rule, setOf(all.first()), dir)
            assertEquals(listOf(all.last()), afterExemption, "allowlist 命中即从违规集里消掉，未登记的必须留下")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `sites spread over several lines are caught and exempted in their folded form`() {
        val spread = "private val palette = listOf(\n    Color(\n        0xFF123456\n    ),\n)\n"
        val dir = tempSources("Spread.kt" to spread)
        try {
            val offenders = violations("合成裸 hex", rule, emptySet(), dir)
            assertEquals(1, offenders.size, "换行 + 缩进的真实形态必须被抓：$offenders")
            val folded = offenders.single()
            // offender 取的是「命中所在的整行」（折叠空白后），命中右侧的尾随逗号也在行内——seed 抄行时必须连它一起抄。
            assertEquals("Spread.kt: Color( 0xFF123456 ),", folded, "offender 必须是折叠空白后的整行形状")
            assertTrue(
                violations("合成裸 hex", rule, setOf(folded), dir).isEmpty(),
                "折叠形状写进 allowlist 后必须豁免得掉，否则 seed 永远写不中"
            )
            assertTrue(
                violations("合成裸 hex", rule, setOf("Spread.kt:$SEED_LINE_SEPARATOR$spread"), dir).size == 1,
                "按原始未折叠文本写 allowlist 不得生效——seed 只能用折叠形状"
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * **M-3 第二半（批 1 终审 F8）：目录豁免只认「扫描根下的第一段」**。
     * 旧实现按路径链任一段匹配，`feature/ui/`、`a/theme/` 这类未来目录会被整片静默豁免出扫描面
     * （债务换个嵌套目录就隐形）。负片段用真实目录形状（`feature/theme/Deep.kt`）当场证明豁免面收窄。
     */
    @Test
    fun `directory exemption covers exactly the first segment under the scan root`() {
        val dir = tempSources(
            "theme/Defs.kt" to "private val b = Color(0xFF999999)\n",
            "feature/theme/Deep.kt" to "private val c = Color(0xFF888888)\n",
        )
        try {
            val files = uiFiles(dir)
            val firstSegment = files.single { it.relativePathFrom(dir) == "theme/Defs.kt" }
            val nested = files.single { it.relativePathFrom(dir) == "feature/theme/Deep.kt" }
            assertTrue(
                firstSegment.isUnderDirNamed(dir, THEME_DIR_NAME),
                "扫描根第一段的 theme 目录必须被豁免（否则主题定义文件全都判违规）"
            )
            assertFalse(
                nested.isUnderDirNamed(dir, THEME_DIR_NAME),
                "M-3：theme 出现在第二段（feature/theme/）不得被豁免——任一段匹配的旧口径会把 " +
                    "feature/ui/ 一类未来目录整片放出扫描面，债务只是换了个嵌套位置"
            )
            // 同一收窄口径必须在 violations 的 exemptDirNames 上成立：只有第一段的 theme 被吃掉。
            val offenders = violations("合成裸 hex", rule, emptySet(), dir, exemptDirNames = setOf(THEME_DIR_NAME))
            assertEquals(
                listOf("Deep.kt: private val c = Color(0xFF888888)"),
                offenders,
                "violations 的目录豁免必须只认第一段（嵌套同名目录里的裸 hex 仍是违规）"
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `directory exemption applies to the synthetic scan and a dead exemption fails`() {
        val dir = tempSources(
            "Plain.kt" to "private val a = Color(0xFF112233)\n",
            "theme/Defs.kt" to "private val b = Color(0xFF999999)\n",
        )
        try {
            val offenders = violations("合成裸 hex", rule, emptySet(), dir, exemptDirNames = setOf(THEME_DIR_NAME))
            assertEquals(listOf("Plain.kt: private val a = Color(0xFF112233)"), offenders, "整目录豁免只吃掉 theme 下的站点")
            val deadExemption = assertFailsWith<AssertionError> {
                violations("合成裸 hex", rule, emptySet(), dir, exemptDirNames = setOf("no_such_dir"))
            }
            assertTrue(
                deadExemption.message?.contains("no_such_dir") == true,
                "指向不存在目录的豁免必须当场判红并点名该豁免，实际：${deadExemption.message}"
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `missing scan directory fails loudly instead of passing on an empty scan`() {
        val missing = File("$UI_MAIN_SOURCE_PATH/__not_a_real_package__")
        assertTrue(
            missing.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().isEmpty(),
            "walkTopDown() 对缺失路径本应给出空序列（静默空扫风险）"
        )
        val error = assertFailsWith<AssertionError> { violations("裸 hex", rule, emptySet(), missing) }
        assertTrue(
            error.message?.contains("__not_a_real_package__") == true &&
                error.message?.contains(File(".").absolutePath) == true,
            "守卫失败信息必须点名本次真正请求的扫描路径与当前 JVM 工作目录（M-N5 参数化后不再写死 ui 常量），" +
                "实际：${error.message}"
        )
    }

    private companion object {
        const val SEED_LINE_SEPARATOR = ": "
    }
}
