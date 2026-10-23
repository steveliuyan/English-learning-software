package com.example.englishlearning.ui

import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppleMintEnd
import com.example.englishlearning.ui.theme.AppleMintStart
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Task 23（裁决④ / AC-R-23）：AI 面「把文字当图标」的禁令 + 徽标墨色的复算。
 *
 * 为什么两张判据都在本文件（而不是 plan 原文那条 `"AI"` 字面量断言）：
 *  `grep -rn '"AI"' app/src/main/java/com/example/englishlearning` 现盘 **0 命中**，
 *  plan Step 1 那条 `assertFalse(Regex("\"AI\"").containsMatchIn(src))` 从写下那天起就是空转，
 *  删掉徽标也不会红、留着徽标也不会红——它证明不了任何事。spec §4.2 禁的是「文字当图标」这一**形状**，
 *  本仓 AI 面的真实形状是 `Text(feature.glyph, …)`（单字中文徽标，字形来自 `AiFeature.kt` 的 `glyph`
 *  字段：文/填/听/讲/析/拍/画）与行尾 `Text("›", …)`。本契约锚这两枚真实站点，
 *  并把取样切片**限定在这两枚 AI 文件内**——`Text("›")` 在 `GlobalVocabularySearchScreen.kt`
 *  另有站外站点，不属本批，写宽了会当场判红并把本席推向未授权扩面。
 *
 * 亮度侧按本仓口径（`SourceRatchet` 文件头 + Task 9 实测）：connected 读不到墨色，所以配对合法性
 *  由 JVM 侧从**活 token 常量**当场复算，注释与断言都不抄 hex 字面量——抄了字面量就退化成
 *  「token 改了测试还绿」。渐变站点保持静态（Task 3 口径），前景按 2026-10-18 裁决 #3 取 on-brand。
 */
class AiBadgeContrastFixTest {

    /** 单字中文徽标形状：`Text(feature.glyph …)`（含 `feature.glyph` 的任何写法都算命中）。 */
    private val textAsBadgeRule = Regex("""Text\(\s*feature\.g[A-Za-z]*""")

    /** 行尾箭头形状：`Text("›" …)`——箭头必须由字形渲染，不能是一个字符。 */
    private val textAsChevronRule = Regex("""Text\(\s*"›""")

    /** 本批的取样切片：只有这两枚 AI 屏（R-23.7 的两屏范围）。 */
    private val aiSurfaces = listOf("AiFeatureScreen.kt", "AiLearningScreen.kt")

    /**
     * RED-1：AI 面不得再有「文字当图标」。
     *
     * 现盘判红的两处（本席实跑读数）：`AiFeatureScreen.kt` 与 `AiLearningScreen.kt` 各一枚
     * `Text(feature.glyph` 站点，`AiLearningScreen.kt` 另有枚 `Text("›")`。
     */
    @Test
    fun `no letter is used as an icon anywhere on the AI surface`() {
        aiSurfaces.forEach { name ->
            val source = uiScreenSource(name)
            assertFalse(
                textAsBadgeRule.containsMatchIn(source),
                "$name 仍在把单字中文当图标（spec §4.2）：应换 AppGlyph(icon = AppIcon.Spark, …)",
            )
            assertFalse(
                textAsChevronRule.containsMatchIn(source),
                "$name 仍在用「›」这个字符当箭头（spec §4.2）：应换 AppGlyph(icon = AppIcon.ChevronRight, …)",
            )
        }
    }

    /**
     * 同一枚禁令的正向半边：AI 面必须真的走共享字形出口（不是把徽标整枚删掉了事）。
     */
    @Test
    fun `the AI surface draws the shared spark glyph instead of a letter`() {
        aiSurfaces.forEach { name ->
            val source = uiScreenSource(name)
            assertTrue(source.contains("AppGlyph("), "$name 未调用共享字形 AppGlyph(：徽标被删掉了而不是换掉了")
            assertTrue(
                source.contains("AppIcon.Spark"),
                "$name 的 AI 域锚点字形必须是 AppIcon.Spark（底导 AI 槽同一锚点）",
            )
        }
    }

    /**
     * 反空转钉：上面两条禁令的正则必须真的能抓到禁令本体。
     * 现盘形状（`Text(feature.glyph, …)` / `Text("›", …)`）作为合成样本喂进去必须命中；
     * 一旦规则被写宽/写窄到「什么都抓不到」，本用例当场判红。
     */
    @Test
    fun `the letter icon rules actually catch the forbidden shapes`() {
        val forbiddenBadge = "Text(feature.glyph, fontWeight = FontWeight.Bold, color = MintPrimaryDark)"
        val forbiddenChevron = "Text(\"›\", style = MaterialTheme.typography.titleLarge, color = MintTextMuted)"
        assertTrue(textAsBadgeRule.containsMatchIn(forbiddenBadge), "单字徽标规则抓不到禁令本体（空转）")
        assertTrue(textAsChevronRule.containsMatchIn(forbiddenChevron), "箭头规则抓不到禁令本体（空转）")
        // 反向：合规写法不得被误伤（否则「删掉箭头」也会被判红，判据就没有方向性）。
        assertFalse(textAsBadgeRule.containsMatchIn("AppGlyph(icon = AppIcon.Spark, tint = ink, size = 20.dp)"))
        assertFalse(textAsChevronRule.containsMatchIn("AppGlyph(icon = AppIcon.ChevronRight, tint = ink, size = 16.dp)"))
    }

    /**
     * on-brand 前景压品牌渐变的亮度复算（裁决 #3：渐变上一律 on-brand）。
     *
     * 读数只从 token 常量取：`AppColorLight.onBrand` / `AppColorDark.onBrand` 与两处渐变端点，
     * 断言里不出现 hex 字面量。深套 `onBrand` 与浅套同值是 AppColors.kt 文件头登记的裁决
     * （品牌渐变深浅两套同三段），这里复算它、防止有人「顺手」给深套换值而渐变侧没人核。
     */
    @Test
    fun `on_brand_foreground_clears_the_AA_floor_on_both_gradient_ends`() {
        assertEquals(
            AppColorLight.onBrand, AppColorDark.onBrand,
            "深套 on-brand 与浅套不同值：品牌渐变两套同三段，裁决 #3 只给了一个前景值，必须一起核验",
        )
        val foreground = AppColorLight.onBrand.toArgb()
        listOf("渐变起点" to AppleMintStart, "渐变终点" to AppleMintEnd).forEach { (label, stop) ->
            val value = ratio(foreground, stop.toArgb())
            assertTrue(
                value >= 4.5,
                "$label 上 on-brand 前景只有 ${"%.2f".format(value)}:1（AA 正文下限 4.5:1）",
            )
        }
    }

    /**
     * AI 两屏的静态令牌读点必须清零（R-23.6 授权的迁移动力）。
     *
     * 判据形状按符号写、不写数量：`AppPalette.` / `DomainColors.` 在这两枚文件里一处都不许剩，
     * 组合期一律走 `appColors()` / `appDomain(...)`。顶层 `Brush` 读的是 `AppleMint*`（Task 3 口径保持静态），
     * 本条规则看不见它，也不该看见。
     */
    @Test
    fun `the AI screens read tokens through the themed accessors only`() {
        val staticRead = Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")
        aiSurfaces.forEach { name ->
            val hits = staticRead.findAll(uiScreenSource(name)).map { it.value }.toList()
            assertTrue(
                hits.isEmpty(),
                "$name 仍有静态令牌读点 $hits（深色档会停在浅值）：应改 appColors() / appDomain(AppDomain.AiSpeech)",
            )
        }
        // 迁移的目标形状必须真的落在文件里，而不是「把静态读点删了、颜色也不给了」。
        val featureScreen = uiScreenSource("AiFeatureScreen.kt")
        assertTrue(
            featureScreen.contains("appColors()"),
            "AiFeatureScreen.kt 迁移后必须经 appColors() 取墨（徽标前景与按钮 contentColor 两处）",
        )
        val learningScreen = uiScreenSource("AiLearningScreen.kt")
        assertTrue(
            learningScreen.contains("appColors()") || learningScreen.contains("appDomain("),
            "AiLearningScreen.kt 迁移后必须经 appColors() / appDomain( 取墨",
        )
    }

    /** 渐变站点的持有符号名（MintPalette KDoc 的三处静态 Brush 之一）：本批不得把它改成组合期求值。 */
    @Test
    fun `the brand gradient stays a top level static brush`() {
        val source = File("$UI_MAIN_SOURCE_PATH/AiFeatureScreen.kt").readText()
        val declaration = Regex("""^private val AiFeatureGradient = Brush\.linearGradient\(""", RegexOption.MULTILINE)
        assertTrue(
            declaration.containsMatchIn(stripComments(source)),
            "顶层静态渐变 AiFeatureGradient 被改写了：Task 3 口径要求品牌渐变保持静态（DrawScope 之外不求值）",
        )
    }
}
