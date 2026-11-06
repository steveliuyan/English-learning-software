package com.example.englishlearning

import com.example.englishlearning.ui.stripComments
import java.io.File
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 B §10（2026-11-07 用户裁定）：横屏只在 `sw ≥ 600dp` 开放，手机形窗口锁竖屏。
 *
 * 这一族的判据全部是**源码/资源形状闸**，不是行为闸：`requestedOrientation` 只在真 Activity 生命周期里
 * 生效，JVM 侧造不出 Activity，connected 侧又缺 `ActivityScenario` 先例（全仓零处），所以「手机横屏真的
 * 转不过去」这条按本仓口径**如实降级成设备取证**（批 B-5：横持设备 + `dumpsys window | grep mCurrentFocus`
 * + 截图），本类只钉「代码与资源长对了、且不会静默退化」。
 *
 * 与 `BiometricHostActivityContractTest` 同族：范围按 `AndroidManifest` 声明的 Activity **矩阵**取，
 * 不点名具体类——新增一枚生产 Activity 却忘了接方向闸，会在这里当场红，而不是留下一台能转成 339dp 高
 * 横屏的手机。
 */
class AppOrientationPolicyContractTest {

    /**
     * 钉的破坏：① 新增 Activity 漏接方向闸；② 有人把 `applyTo` 从 Activity 里挪进别的调用链
     * （比如只在一个基类里调，而某枚 Activity 换了父类）；③ 同一处调两次（第二次会覆盖第一次，
     * 在 `configChanges` 缺席时看不出差别，但形状已经不对）。
     */
    @Test
    fun `every manifest activity applies the orientation policy exactly once`() {
        val hosts = manifestActivities()
        assertTrue(hosts.isNotEmpty(), "AndroidManifest 未解析出任何 <activity>——解析逻辑失效，本矩阵守卫不能作为证据")
        val missing = hosts.filter { (_, source) -> count(source, APPLY_TO_CALL) != 1 }
        assertTrue(
            missing.isEmpty(),
            "以下生产 Activity 没有恰好一次 `AppOrientationPolicy.applyTo(this)`：" +
                missing.joinToString { it.first } +
                "（0 处 = 该窗口允许被转到手机横屏的 339dp 高版面；2 处以上 = 生效顺序变成隐式约定）",
        )
        // 反空转（must-flag）：把调用摘掉的那枚源码必须被同一具计数器判 0，否则上面那条是恒真。
        assertEquals(0, count("class Foo : FragmentActivity() { override fun onCreate(b: Bundle?) { super.onCreate(b) } }", APPLY_TO_CALL))
        // 反空转（must-not-flag）：正向形状必须自命中，否则是「永远判不出命中」的另一种失灵。
        assertEquals(1, count("class Foo : FragmentActivity() { override fun onCreate(b: Bundle?) { $APPLY_TO_CALL } }", APPLY_TO_CALL))
    }

    /**
     * 钉的破坏：方向策略被改写成「无条件锁竖屏」（平板一起锁死）、或换成第三个方向常量
     * （`SENSOR_LANDSCAPE` 之类）绕过资源门。两枚方向常量各恰一处、且 `SCREEN_ORIENTATION_` 前缀
     * 在策略文件里总共只有两处——多一枚即红，不需要枚举名字。
     */
    @Test
    fun `the policy branches on the sw-qualified bool and offers exactly the two orientations`() {
        val policy = stripComments(policySource())
        assertEquals(1, count(policy, BOOL_READ), "方向策略必须恰一次读 `R.bool.allow_landscape_layout`：不读资源=分档失效，读两次=两档可能互相矛盾")
        assertEquals(1, count(policy, "SCREEN_ORIENTATION_UNSPECIFIED"), "大屏档必须取 UNSPECIFIED（跟随系统旋转设置），恰一处")
        assertEquals(1, count(policy, "SCREEN_ORIENTATION_PORTRAIT"), "手机档必须取 PORTRAIT，恰一处")
        assertEquals(2, count(policy, "SCREEN_ORIENTATION_"), "方向策略里 `SCREEN_ORIENTATION_` 前缀总共恰两处（两档各一枚）：出现第三枚即说明有人绕过资源门加了别的方向常量")
        // 反空转：无条件锁竖屏（本决定最容易被「简化」成的形状）必须不满足双常量形状。
        val alwaysPortrait = "activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT"
        assertEquals(1, count(alwaysPortrait, "SCREEN_ORIENTATION_PORTRAIT"))
        assertEquals(0, count(alwaysPortrait, "SCREEN_ORIENTATION_UNSPECIFIED"))
        assertEquals(0, count(alwaysPortrait, BOOL_READ))
        // 反空转：前缀计数必须数得出探针里的命中，否则那条「恰两处」是恒真。
        assertEquals(1, count("ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE", "SCREEN_ORIENTATION_"))
    }

    /**
     * 钉的破坏：两份资源文件值写反（手机开横屏、平板锁竖屏）、只留一份（缺省档变成 true 即全站放开横屏）、
     * 或限定目录改名（`values-sw600dp` → `values-w600dp` 会把「最小边」变成「当前宽度」，
     * 手机横屏立刻落进开放档——这正是本决定要避免的那个形状）。
     */
    @Test
    fun `the default resource locks landscape and only the sw-qualified one opens it`() {
        assertEquals(false, boolFrom("src/main/res/values/config.xml"), "缺省档（手机形窗口）必须是 false：写反=手机横屏重新开放")
        assertEquals(true, boolFrom("src/main/res/values-sw600dp/config.xml"), "sw ≥ 600dp 档必须是 true：写反=平板也被锁死竖屏")
        // 反空转：限定目录必须是 `sw` 前缀，不是 `w`。`values-w600dp` 按当前宽度匹配，
        // 手机横屏（w=731dp）会误命中开放档——本决定的全部意义就是不让它发生。
        assertTrue(
            !File("src/main/res/values-w600dp").exists(),
            "存在 `values-w600dp/`：那是按**当前宽度**匹配的限定符，手机横屏（w731dp）会落进开放档。方向门只认 `sw`（最小边）",
        )
    }

    /**
     * 钉的破坏：把方向锁写回 manifest。`android:screenOrientation` 不接受资源引用，写死等于
     * 对**所有**形态生效（平板一起锁），并且会盖过运行时的 `requestedOrientation` 分档。
     */
    @Test
    fun `the manifest declares no orientation so the sw gate stays the single source of truth`() {
        val manifest = stripComments(moduleFile("src/main/AndroidManifest.xml").readText())
        assertEquals(0, count(manifest, "android:screenOrientation"), "manifest 里不许出现 `android:screenOrientation`：它按资源分不了档，会把平板一起锁死")
        assertEquals(0, count(manifest, "android:configChanges"), "manifest 里不许出现 `android:configChanges`：跨 600dp 阈值按系统默认重建，不做静默热切（决策 §10.2）")
        // 反空转：同一具计数器必须数得出违规形状。
        assertEquals(1, count("""<activity android:name=".MainActivity" android:screenOrientation="portrait" />""", "android:screenOrientation"))
        assertEquals(1, count("""<activity android:configChanges="orientation|screenSize" />""", "android:configChanges"))
    }

    /**
     * 文档与代码同源（本仓口径：数值不许只在代码里活着）。钉的是决策 §10 与 Spec 00 矩阵里
     * 「手机锁竖屏」这条的**可检索形状**：资源限定名、方向常量、以及那两枚把结论钉死的实测读数。
     */
    @Test
    fun `the decision document and the spec matrix both record the phone portrait lock`() {
        val decision = stripComments(repoFile("docs/decisions/2026-11-06-adaptive-window-layout.md").readText())
        listOf(
            "values-sw600dp" to "开放档的资源限定目录名",
            "SCREEN_ORIENTATION_PORTRAIT" to "手机档用的方向常量",
            "smallestScreenWidthDp" to "判定面（最小边，与旋转无关）",
            "w731dp h339dp" to "手机横屏的实测读数——本决定的全部依据",
            "w1080dp h737dp" to "平板形窗口的实测读数——同一批用例只红 5 枚那一档",
        ).forEach { (needle, label) ->
            assertTrue(
                decision.contains(needle),
                "决策文档 §10 丢了$label（「$needle」不在文本里）：代码里的分档失去可追溯依据",
            )
        }
        val spec = repoFile("docs/specs/00-foundation-and-architecture.md").readText()
        assertTrue(
            spec.contains("手机横屏版面"),
            "Spec 00 的「不做」清单丢了「不做手机横屏版面」：范围变化必须先落在 Spec 上（AGENTS.md 工作方式第 5 条）",
        )
        // 反空转：矩阵的中等档若还把「横屏手机」写成典型形态，就是与 §10 自相矛盾的陈旧承诺。
        val mediumRow = spec.lineSequence().firstOrNull { it.contains("| 中等 |") } ?: ""
        assertTrue(
            !mediumRow.contains("横屏手机"),
            "Spec 00 矩阵中等档仍把「横屏手机」列为典型形态：与决策 §10 冲突，手机横屏已退场",
        )
        assertTrue(mediumRow.contains("平板"), "Spec 00 矩阵中等档必须写明新的典型形态（平板竖屏/折叠内屏），不能只删不加")
    }

    // ---------- 夹具 ----------

    private fun manifestActivities(): List<Pair<String, String>> {
        val manifest = moduleFile("src/main/AndroidManifest.xml").readText()
        val namespace = Regex("""namespace\s*=\s*"([^"]+)"""")
            .find(moduleFile("build.gradle.kts").readText())
            ?.groupValues?.get(1)
            ?: error("app/build.gradle.kts 缺少 namespace 声明")
        return Regex("""<activity\b[^>]*android:name\s*=\s*"([^"]+)"""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .map { qualified -> if (qualified.startsWith(".")) namespace + qualified else qualified }
            .map { fqcn ->
                val simpleName = fqcn.substringAfterLast('.')
                val packagePath = fqcn.substringBeforeLast('.').replace('.', '/')
                val path = "src/main/java/$packagePath/$simpleName.kt"
                simpleName to stripComments(
                    listOf(File(path), File("app/$path")).firstOrNull { it.isFile }
                        ?.readText()
                        ?: error("manifest 声明的 Activity $fqcn 找不到源码 $path（搬家/改名会让本矩阵守卫漏掉它）"),
                )
            }
            .toList()
    }

    private fun policySource(): String {
        val path = "src/main/java/$POLICY_RELATIVE_PATH"
        val candidates = listOf(File(path), File("app/$path")).filter { it.isFile }
        assertEquals(1, candidates.size, "方向策略文件应恰好一个，实际 ${candidates.size} 个（被改名/复制会让本契约读错文件）：$path")
        return candidates.single().readText()
    }

    /** 从指定资源文件里取 `allow_landscape_layout` 的字面布尔值；缺文件/缺键都点名失败。 */
    private fun boolFrom(relativePath: String): Boolean {
        val file = listOf(File(relativePath), File("app/$relativePath")).firstOrNull { it.isFile }
            ?: error("资源文件缺失：$relativePath（JVM 工作目录 ${File(".").absolutePath}）——方向分档失去缺省值")
        val value = Regex("""<bool\s+name="allow_landscape_layout"\s*>\s*(true|false)\s*</bool>""")
            .find(file.readText())
            ?.groupValues?.get(1)
            ?: error("$relativePath 里没有可解析的 `allow_landscape_layout` <bool>（写法变了？本契约的解析要跟着改）")
        return value.toBoolean()
    }

    private fun count(text: String, needle: String): Int = Regex(Regex.escape(needle)).findAll(text).count()

    /** 单元测试的工作目录是模块目录（app/），兼容仓库根目录起跑。 */
    private fun moduleFile(relativePath: String): File =
        listOf(File(relativePath), File("app/$relativePath")).firstOrNull { it.isFile }
            ?: error("找不到模块内文件：$relativePath（JVM 工作目录 ${File(".").absolutePath}）")

    /** 仓库根相对路径（文档取证用）。 */
    private fun repoFile(relativePath: String): File =
        listOf(File(relativePath), File("../$relativePath")).firstOrNull { it.isFile }
            ?: error("找不到仓库文件：$relativePath（JVM 工作目录 ${File(".").absolutePath}）")

    private companion object {
        const val APPLY_TO_CALL = "AppOrientationPolicy.applyTo(this)"
        const val BOOL_READ = "R.bool.allow_landscape_layout"
        const val POLICY_RELATIVE_PATH = "com/example/englishlearning/AppOrientation.kt"
    }
}
