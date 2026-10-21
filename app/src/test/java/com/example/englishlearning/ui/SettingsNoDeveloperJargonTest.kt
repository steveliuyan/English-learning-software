package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Task 19（AC-R-21）源码契约：设置页只许说用户语言。
 *
 * 「F4-04 / AI Profile / Salvage」这类内部术语曾以字符串字面量混进一级页文案
 * （`.elpbak` 副标题、配对导出与恢复的括号注、占位行副标题），源码文本即契约：
 * 先剥注释（共用夹具 [stripComments]，字符串字面量原样保留）再禁词扫描，
 * 注释里的历史说明不受本契约约束。占位行禁令对应 IA 决策文档裁决②：
 * 「后续版本 / 敬请期待 / 暂未开放」的假入口整族删除，不降级为灰行。
 * 第三条用例把裁决②/⑥ 的 IA 形状（4 组 9 行与逐行标题）钉在 [SettingsGroups] 台账上。
 */
private const val SETTINGS_FILE = "src/main/java/com/example/englishlearning/ui/SettingsScreen.kt"

class SettingsNoDeveloperJargonTest {
    @Test fun settings_screen_speaks_user_language() {
        val src = stripComments(File(SETTINGS_FILE).readText())
        listOf("F4-04", "AI Profile", "Salvage", "salvage").forEach { term ->
            assertFalse(src.contains(term), "设置页出现开发者术语「$term」（AC-R-21 要求用户语言）")
        }
    }

    @Test fun no_placeholder_next_version_rows() {
        val src = stripComments(File(SETTINGS_FILE).readText())
        assertFalse(
            Regex("""后续版本|敬请期待|暂未开放""").containsMatchIn(src),
            "设置页不得保留「后续版本」占位行（裁决②）",
        )
    }

    /**
     * IA 台账（AC-R-21 / 裁决②/⑥）：**恰 4 组、恰 9 行**，行标题与 tag 逐字钉死。
     * 直读生产码里的 [SettingsGroups]（纯数据 object，JVM 可加载），connected 侧
     * `SettingsScreenTest` 再核对这些 tag 真的渲染出来——两张脸对不上都会红。
     */
    @Test fun settings_groups_are_exactly_four_groups_nine_rows() {
        assertEquals(4, SettingsGroups.all.size, "设置页必须恰 4 组，实为 ${SettingsGroups.all.size} 组")
        assertEquals(
            listOf("学习", "AI 与语音", "外观与关于", "数据与安全"),
            SettingsGroups.all.map { it.title },
            "四个分组标题按裁决②钉死（「学习提醒」归「学习」组，不再有「账户」「阅读」组）",
        )
        val rows = SettingsGroups.all.flatMap { it.entries }
        assertEquals(9, rows.size, "设置页一级行必须恰 9 条，实为 ${rows.size} 条")
        assertEquals(
            listOf(
                "词书与每日目标", "学习提醒", "生成默写纸",
                "AI 服务与密钥", "语音朗读",
                "主题", "开源许可与隐私说明",
                "词书内容包", "备份与恢复",
            ),
            rows.map { it.title },
            "九行标题按稿 §7④ 与裁决②钉死",
        )
        val tags = rows.map { it.tag } + SettingsGroups.all.map { it.tag }
        assertEquals(tags.size, tags.toSet().size, "组与行的 testTag 不得重复：$tags")
        assertTrue(rows.none { it.title.isBlank() }, "行标题不许为空")
        // 除「主题」分段卡外，每一行都必须挂 AppIcon 白名单字形，禁文字徽标（R-E）。
        assertEquals(
            listOf("settings_theme_mode"),
            rows.filter { it.icon == null }.map { it.tag },
            "只许「主题」一行没有行首图标；其余行必须给 AppIcon 字形而不是文字徽标",
        )
    }
}
