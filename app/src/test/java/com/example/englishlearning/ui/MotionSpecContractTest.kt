package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 批 A 动效基建源码契约（判据：`docs/decisions/2026-11-06-motion-foundation-and-system-reduced-motion.md`）。
 *
 * 四条禁令，全部来自 2026-11-06 的实测与 animation-core 字节码，不是审美偏好：
 *
 *  - **R1 双重缩放禁令**：生产源码不得再引用 `durationMs` / `animatorDurationScale`。
 *    Compose 自己已经读了 `animator_duration_scale`：字节码里 `doAnimationFrameWithScale` 写的是
 *    `if (durationScale == 0f) animation.durationNanos else (frameTimeNanos - startTimeNanos) / durationScale`，
 *    设备实测（emulator-5554，`ActivityScenario` + `ComposeView` 走真 WindowRecomposer）
 *    `tween(600)` 在 scale=0 时首帧即落目标值。谁把旧换算函数接回去，scale=0.5 就被平台乘一次、
 *    我们再接着乘一次 ⇒ 实际 0.25，且 scale=0 时「时长归零」会变成「时长 = 0 又除以 0」一类的双重口径。
 *  - **R2 token 层不碰缩放**：`theme/AppMotion.kt` 里不得出现 `scale` 字样——它是纯 token 表，
 *    不承担系统设置的读取与换算。（今天生产没有任何一处需要读这个设置：实测平台已经把循环动画也停了。）
 *  - **R3 缓动必须走 token**：每一枚 `tween(` 都要显式带 `easing = AppMotion.Easing…`。
 *    不写就吃 Compose 默认的 FastOutSlowIn，那是原型表里没有的第三条曲线。
 *  - **R4 进出场分缓动**：`fadeIn(`/`scaleIn(` 用 `EasingOut`，`fadeOut(`/`scaleOut(` 用 `EasingIn`。
 *    退场借用入场曲线是本批之前 7 个读点共有的旧账（`AppMotion.Easing` 别名），批 D 做转场前必须先归位。
 *
 * 形状沿用本目录既有源码契约（`PronunciationIdleFallbackContractTest` 等禁令族）：
 *  1. 路径常量 + 目录/文件存在性守卫（`walkTopDown()` 对缺失路径返回空序列，禁止契约静默空扫）；
 *  2. 覆盖面锚点（扫到的 `tween(` 站点数下限）——站点被整批改名/搬家时报「契约空转」而不是零违规通过；
 *  3. 合成双边用例：每条禁令的违例形状必须被命中（内存假源码，不碰生产文件），
 *     合规形状必须被放行；注释里的字面量必须被剥掉后判定；
 *  4. 真实文件零 offender 回归。
 *
 * 已知局限（如实记录，不为判不到的面做假测试）：
 *  - 行内子串匹配只锁字面形状：把 `easing = ` 换行、或写 `animationSpec = mySpec()` 间接传参都不在匹配面内；
 *    这类逃逸由代码评审与批 D 的转场判据兜住，此处不做 AST 解析。
 *  - 「减弱动效下骨架定格在周期末尾值」这条（实测结论）**不可源码判定**，归批 C 的截图取证，不写假用例。
 */
class MotionSpecContractTest {
    private val uiDir = File(MOTION_SPEC_CONTRACT_SOURCE_PATH)

    private val forbiddenScaleHelpers = listOf("durationMs", "animatorDurationScale")

    private val tweenCall = Regex("""(?<![A-Za-z0-9_])tween\(""")

    private val enterShapes = listOf("fadeIn(", "scaleIn(")

    private val exitShapes = listOf("fadeOut(", "scaleOut(")

    /** R1 + R3 + R4：一个文件剥注释后的全部 offender。 */
    private fun offenders(fileName: String, source: String): List<String> {
        val stripped = stripComments(source)
        val found = mutableListOf<String>()
        forbiddenScaleHelpers.forEach { helper ->
            val hits = stripped.lines().count { it.contains(helper) }
            if (hits > 0) {
                found += "$fileName: 出现 $helper 共 $hits 处——Compose 已按 animator_duration_scale 自行缩放时长" +
                    "（实测 + animation-core 字节码），再乘一次就是双重缩放；换算函数已从 AppMotion 删除，不得接回"
            }
        }
        stripped.lines().forEachIndexed { index, line ->
            val lineNumber = index + 1
            if (tweenCall.containsMatchIn(line) && !line.contains("AppMotion.Easing")) {
                found += "$fileName:$lineNumber 的 tween( 未显式给 token 缓动：会吃 Compose 默认 FastOutSlowIn，" +
                    "那是原型动效表里没有的第三条曲线"
            }
            enterShapes.forEach { shape ->
                if (line.contains(shape) && !line.contains("EasingOut")) {
                    found += "$fileName:$lineNumber 的 $shape 未用 EasingOut：入场/展开走 --ease-out"
                }
            }
            exitShapes.forEach { shape ->
                if (line.contains(shape) && !line.contains("EasingIn")) {
                    found += "$fileName:$lineNumber 的 $shape 未用 EasingIn：退场/收起借入场曲线是本批清掉的旧账"
                }
            }
        }
        return found
    }

    /** R2：token 表层禁令，单独一个纯函数（判据是整份源码，不是逐行）。 */
    private fun appMotionScaleOffenders(source: String): List<String> {
        val stripped = stripComments(source)
        val hits = listOf("scale", "Scale").mapNotNull { needle ->
            val count = Regex(needle).findAll(stripped).count()
            if (count > 0) "$needle ×$count" else null
        }
        return if (hits.isEmpty()) {
            emptyList()
        } else {
            listOf("AppMotion.kt 出现 ${hits.joinToString(" / ")}——纯 token 表不承担系统设置的读取与换算")
        }
    }

    private fun scanUiSources(dir: File = uiDir): List<File> {
        assertTrue(
            dir.isDirectory,
            "动效契约扫描目录缺失（期望 $MOTION_SPEC_CONTRACT_SOURCE_PATH）：${dir.path}，" +
                "相对 JVM 工作目录 ${File(".").absolutePath}；" +
                "walkTopDown() 对缺失路径返回空序列，会让契约测试无声通过——" +
                "包名重构或工作目录变化必须先修此处路径。",
        )
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "动效契约空扫：${dir.path} 下没有 .kt 文件（文件数 0 不是合规，是漏扫）。")
        return files
    }

    private fun sourceOf(directory: File, fileName: String): String {
        val matches = directory.walkTopDown().filter { it.isFile && it.name == fileName }.toList()
        assertTrue(
            matches.size == 1,
            "动效契约目标文件应恰好一个（$fileName 于 ${directory.path}），实际 ${matches.size} 个",
        )
        return matches.single().readText()
    }

    @Test
    fun `ui scan scope resolves to real kotlin sources`() {
        val files = scanUiSources()
        val tweenSites = files.filter { tweenCall.containsMatchIn(stripComments(it.readText())) }
        assertTrue(
            tweenSites.size >= MIN_TWEEN_HOST_FILES,
            "动效契约只扫到 ${tweenSites.size} 个含 tween( 的文件（下限 $MIN_TWEEN_HOST_FILES，" +
                "本仓现有 AppSwitch/GlassButton/GlassOverlay/PlanBar/SegmentedControl/SpeechSettingsScreen 六族）。" +
                "禁令对着空档放行不是合规：站点被整批改名或搬家必须显式改本契约。",
        )
    }

    @Test
    fun `missing scan directory fails loudly instead of passing on an empty scan`() {
        val missing = File("$MOTION_SPEC_CONTRACT_SOURCE_PATH/__not_a_real_package__")
        assertTrue(
            missing.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().isEmpty(),
            "walkTopDown() 对缺失路径本应给出空序列（静默空扫风险）",
        )
        val error = assertFailsWith<AssertionError> { scanUiSources(missing) }
        assertTrue(
            error.message?.contains(MOTION_SPEC_CONTRACT_SOURCE_PATH) == true,
            "守卫失败信息必须点名期望路径，实际：${error.message}",
        )
    }

    @Test
    fun `rule flags the forbidden helpers and the untokenized easings`() {
        val violating = """
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.97f else 1f,
                animationSpec = tween(AppMotion.durationMs(AppMotion.Fast, animatorDurationScale(context))),
            )
            AnimatedVisibility(
                enter = fadeIn(tween(AppMotion.Normal)),
                exit = fadeOut(tween(AppMotion.Normal, easing = AppMotion.EasingOut)),
            )
        """.trimIndent()
        val found = offenders("FakeScreen.kt", violating)
        assertTrue(found.isNotEmpty(), "动效契约漏判三条禁令的违例形状")
        assertTrue(found.any { it.contains("durationMs") }, "R1 未命中：${found.joinToString("\n")}")
        assertTrue(found.any { it.contains("animatorDurationScale") }, "R1 未命中：${found.joinToString("\n")}")
        assertTrue(
            found.any { it.contains("FakeScreen.kt:3") },
            "R3 未命中裸 tween（第 3 行没有 easing）：${found.joinToString("\n")}",
        )
        assertTrue(
            found.any { it.contains("FakeScreen.kt:6") && it.contains("fadeIn(") },
            "R4 未命中借曲线的入场（第 6 行 fadeIn 无 EasingOut）：${found.joinToString("\n")}",
        )
        assertTrue(
            found.any { it.contains("FakeScreen.kt:7") && it.contains("fadeOut(") },
            "R4 未命中借曲线的退场（第 7 行 fadeOut 用 EasingOut）：${found.joinToString("\n")}",
        )
        // 合规形状必须放行（含注释里出现同一字面量：剥注释后不得判成违例）。
        val compliant = """
            // 历史教训：绝不再写 durationMs / animatorDurationScale，平台已经按 scale 缩放过。
            val spec = tween(AppMotion.Fast, easing = AppMotion.EasingOut)
            AnimatedVisibility(
                enter = fadeIn(tween(AppMotion.Normal, easing = AppMotion.EasingOut)),
                exit = fadeOut(tween(AppMotion.Normal, easing = AppMotion.EasingIn)),
            )
        """.trimIndent()
        val clean = offenders("CompliantScreen.kt", compliant)
        assertTrue(clean.isEmpty(), "合规形状被误判：${clean.joinToString("\n")}")
    }

    @Test
    fun `token table keeps system scale out of it`() {
        val source = sourceOf(File(UI_THEME_SOURCE_PATH), "AppMotion.kt")
        assertTrue(source.isNotBlank(), "AppMotion.kt 读出来是空文件：契约空转")
        val found = appMotionScaleOffenders(source)
        assertTrue(found.isEmpty(), found.joinToString("\n"))
    }

    @Test
    fun `production motion specs carry no offenders today`() {
        val found = scanUiSources().flatMap { offenders(it.name, it.readText()) }
        assertTrue(found.isEmpty(), "动效规格违例 ${found.size} 处：\n${found.joinToString("\n")}")
    }

    @Test
    fun `migration alias is retired`() {
        // 钉值本身归 PrototypeTokenContractTest 的 `motion tokens match prototype durations and curves`，
        // 这里只锁「别名不得复活」这一条批 A 新增口径，避免同一钉值两处各写一份。
        val source = sourceOf(File(UI_THEME_SOURCE_PATH), "AppMotion.kt")
        assertTrue(source.isNotBlank(), "AppMotion.kt 读出来是空文件：契约空转")
        assertTrue(
            !stripComments(source).contains(Regex("""\bval\s+Easing\s*=""")),
            "AppMotion.Easing 别名已随批 A 退役：读点必须写明 EasingOut 还是 EasingIn，" +
                "留别名就是让「退场借入场曲线」重新长出来",
        )
    }
}

private const val MOTION_SPEC_CONTRACT_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui"

/** 覆盖面下限：本批清账后有 6 个宿主文件含 `tween(`；低于此数说明扫描错位或站点被整批搬走。 */
private const val MIN_TWEEN_HOST_FILES = 6
