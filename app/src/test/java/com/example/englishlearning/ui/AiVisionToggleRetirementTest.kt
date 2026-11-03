package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 「读图」假开关退役的源码契约（决策
 * `docs/decisions/2026-11-04-ai-profile-vision-and-connection-test.md` §1-§2）。
 *
 * 摘开关的**理由**是「没有任何请求构造读这枚能力」，本类把这条理由本身钉成判据，两种退化都要红：
 *  ① 开关长回来（`capability = AiCapability.Vision` 重新出现在屏上）；
 *  ② `ai/` 侧悄悄多出消费方（那时 UI 就不该继续没有开关，得回到决策里重开一次裁定）。
 *
 * 枚名本身**保留**（老行 `valueOf` 会炸整表，见 `AiCapabilityCompatContractTest`），所以本类不断言
 * `AiCapability.Vision` 全树归零：屏侧允许且只允许剩下能力中文名映射那一枚读点，
 * 列表页对老配置仍如实显示「读图」。
 */
class AiVisionToggleRetirementTest {

    private val screen: String by lazy { uiScreenSource("AiProfileSettingsScreen.kt") }

    @Test
    fun theEditorOffersThreeCapabilityTogglesAndNoneOfThemIsVision() {
        val wired = Regex("""capability = AiCapability\.\w+""").findAll(screen).map { it.value }.toList()

        assertEquals(
            listOf(
                "capability = AiCapability.Text",
                "capability = AiCapability.Speech",
                "capability = AiCapability.ImageGeneration",
            ),
            wired,
            "能力开关只剩三枚：读图开关按决策 §2 退役（枚举名逐枚点名、不许遍历 `entries`，理由见屏内注释）。" +
                "若这一条因为「重新加回读图」而红，必须先回到决策文档裁定消费方",
        )
        assertEquals(
            0,
            countMatches(screen, Regex(Regex.escape("AiCapability.Vision in draft.capabilities"))),
            "草稿上不许再出现对 `Vision` 的读写：那枚开关点下去改的是一个没人读的标志位",
        )
        assertEquals(
            0,
            countMatches(screen, Regex(Regex.escape("\"ai_profile_capability_vision\""))),
            "tag `ai_profile_capability_vision` 必须随开关一起下线：connected 侧的取样锚点不许留下悬空名字",
        )
    }

    @Test
    fun theCapabilityLabelStillCoversVisionForLegacyRows() {
        // `when` 必须穷尽，摘开关不等于摘中文名：老配置在列表里仍显示它声明过的能力。
        assertEquals(
            1,
            countMatches(screen, Regex(Regex.escape("AiCapability.Vision"))),
            "屏侧只许剩「能力名映射」这一枚 `Vision` 读点：多一枚 = 又长出入口；少一枚 = 老配置的声明被吞掉",
        )
        assertEquals(
            1,
            countMatches(screen, Regex(Regex.escape("""AiCapability.Vision -> "读图""""))),
            "「读图」映射必须逐字保留（`capabilitiesLabel` 对历史行的如实转述）",
        )
    }

    @Test
    fun noAiRequestPathConsumesTheVisionCapability() {
        val root = File("$APP_MAIN_SOURCE_PATH/ai")
        assertTrue(root.isDirectory, "取不到 `ai/` 源码目录，本判据会空转：${root.path}")
        val hits = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativePathFrom(root) to countMatches(stripComments(it.readText()), Regex(Regex.escape("AiCapability.Vision"))) }
            .filter { (_, count) -> count > 0 }
            .toList()

        assertEquals(
            emptyList(),
            hits.map { (path, count) -> "$path ×$count" },
            "V1 里 `ai/` 侧对 `Vision` 的**限定名读点**必须为零（枚名定义是裸 `Vision`，不在此匹配面内，见下一条断言）。" +
                "出现第二处 = 有了真消费方，那时「UI 不给开关」这个裁定就失效，" +
                "必须回决策 §2 重新拍板（详见 §6.2 拍照翻译那条）",
        )
        // 反空转：上面那条「读点为零」在枚名被删光时同样绿——那正是最危险的假绿（老行 valueOf 直接砸整表）。
        // 所以同一用例里把定义位钉回来，两条合起来才是「留枚名、去消费」这个裁定的完整形状。
        val definition = stripComments(File("$APP_MAIN_SOURCE_PATH/ai/domain/AiProvider.kt").readText())
        assertEquals(
            1,
            countMatches(definition, Regex("""enum class AiCapability\s*\{\s*Text,\s*Vision,""")),
            "`AiCapability.Vision` 枚名定义位丢失：本类的「零消费方」判据会因此假绿，" +
                "而老配置行的 `valueOf(\"Vision\")` 会当场炸掉整张 Profile 表（决策 §1 的数据兼容证据）",
        )
    }

    private fun File.relativePathFrom(root: File): String =
        path.removePrefix(root.path).trimStart('\\', '/').replace('\\', '/')
}
