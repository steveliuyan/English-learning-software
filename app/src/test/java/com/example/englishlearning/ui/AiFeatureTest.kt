package com.example.englishlearning.ui

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * 锁住「AI 学」页的功能目录。
 *
 * 这套断言的作用不是复述代码，而是**防止文案与事实脱节**：用户此前反复遇到「看起来能用、
 * 点开是坏的」问题，所以只要 `implemented` 与 `status` 的措辞不一致，测试就必须失败。
 */
class AiFeatureTest {
    @Test
    fun `the page shows exactly the seven features, in order`() {
        assertEquals(
            listOf(
                AiFeature.WORD_PASSAGE,
                AiFeature.CLOZE,
                AiFeature.LISTENING,
                AiFeature.COACH,
                AiFeature.SENTENCE_ANALYSIS,
                AiFeature.PHOTO_TRANSLATION,
                AiFeature.IMAGE_STUDIO,
            ),
            AiFeature.entries.toList(),
        )
        assertEquals(
            listOf("词文串学", "AI 短文填词", "单词随身听", "单词串讲", "长难句分析", "拍照翻译", "AI 生图"),
            AiFeature.entries.map { it.title },
        )
    }

    @Test
    fun `keys are unique kebab-case so they survive being used as test tags and routes`() {
        val keys = AiFeature.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "key 重复：$keys")
        keys.forEach { key ->
            assertTrue(key.matches(Regex("[a-z]+(-[a-z]+)*")), "$key 必须是 kebab-case")
        }
    }

    @Test
    fun `each feature keeps its own single character mark so the list never reads twinned`() {
        val glyphs = AiFeature.entries.map { it.glyph }
        assertEquals(glyphs.size, glyphs.toSet().size, "标记重复：$glyphs")
        glyphs.forEach { glyph ->
            assertEquals(1, glyph.length, "标记必须是单个字符，实际是「$glyph」")
        }
    }

    @Test
    fun `every feature carries a non blank summary, status and at least one dependency`() {
        AiFeature.entries.forEach { feature ->
            assertTrue(feature.summary.isNotBlank(), "${feature.name} 缺 summary")
            assertTrue(feature.status.isNotBlank(), "${feature.name} 缺 status")
            assertTrue(feature.dependencies.isNotEmpty(), "${feature.name} 缺依赖说明")
            feature.dependencies.forEach { dependency ->
                assertTrue(dependency.isNotBlank(), "${feature.name} 的依赖说明不能为空")
            }
        }
    }

    @Test
    fun `only the features with real screens are marked as implemented`() {
        // 长难句分析、拍照翻译与 AI 生图已接通；其余功能仍未实现。接通新的功能时在这里同步集合。
        assertEquals(
            setOf(AiFeature.SENTENCE_ANALYSIS, AiFeature.PHOTO_TRANSLATION, AiFeature.IMAGE_STUDIO),
            AiFeature.entries.filter { it.implemented }.toSet(),
        )
    }

    /**
     * 双向一致性：只改文案不改代码、或只改代码不改文案，都会在这里被拦住。
     */
    @Test
    fun `the status wording always matches the implemented flag`() {
        AiFeature.entries.forEach { feature ->
            if (feature.implemented) {
                assertFalse(
                    feature.status.startsWith(UNIMPLEMENTED_PREFIX),
                    "${feature.name} 已实现，但状态文案仍在说「未实现」",
                )
            } else {
                assertTrue(
                    feature.status.startsWith(UNIMPLEMENTED_PREFIX),
                    "${feature.name} 未实现，状态文案必须如实以「未实现」开头，当前是：${feature.status}",
                )
            }
        }
    }

    /**
     * RED-2 的第一半（间接路径）：徽章面必须经 `featureStatusBadge(implemented)` 产出「现在能用」/「未上线」。
     *
     * 这里**故意不直接调用**那个函数——它在现盘还不存在，直接引用只会让整个 test 源集编译失败，
     * 拿到的是一枚「编译红」而不是判据红（派工块 §五.3 要求红样走不动被断言字面量的间接路径）。
     * 现盘形状是 `StatusPill` 里的 `if (implemented) "可用" else "未实现"`：徽章文案与目录原文
     * 混用同一批词，正是裁决④ 要分开的两面。
     */
    @Test
    fun `the badge face is produced by one shared renderer speaking the two planned words`() {
        val catalogue = stripComments(File("$UI_MAIN_SOURCE_PATH/AiFeature.kt").readText())
        assertTrue(
            Regex("""fun featureStatusBadge\(\s*implemented:\s*Boolean\s*\)\s*:\s*String""").containsMatchIn(catalogue),
            "徽章文案必须由 ui/AiFeature.kt 里的 featureStatusBadge(implemented) 统一产出（R-23.9：与目录同文件，不新开组件文件）",
        )
        val body = catalogue.substringAfter("fun featureStatusBadge(")
        // 取样前提：函数写在 AiFeature.kt **末尾**，所以 substringAfter 拿到的就是它自己的尾部。
        // 一旦有人把它挪到目录之前，本条先炸，避免「断言读到整份目录」的假绿。
        assertFalse(body.contains("WORD_PASSAGE"), "featureStatusBadge 必须写在 AiFeature.kt 末尾（本契约按尾部取样）")
        listOf(BADGE_AVAILABLE, BADGE_UNRELEASED).forEach { word ->
            assertTrue(body.contains(word), "featureStatusBadge 必须产出「$word」（裁决④ 的徽章面用词）")
        }
        // 徽章面只许这两个词：把目录原文拿当徽章词就是「两面混用」的复发。
        listOf("可用", UNIMPLEMENTED_PREFIX).forEach { word ->
            assertFalse(body.contains(word), "featureStatusBadge 里混进了目录原文用词「$word」：徽章面与目录面必须分开（R-23.4）")
        }

        val screen = uiScreenSource("AiFeatureScreen.kt")
        assertTrue(
            screen.contains("featureStatusBadge("),
            "AiFeatureScreen 的状态徽章必须调用 featureStatusBadge(，而不是自带一套临场文案",
        )
        assertFalse(
            Regex(""""(可用|未实现)"""").containsMatchIn(screen),
            "AiFeatureScreen.kt 里仍留着徽章文案字面量（应改由 featureStatusBadge 产出）",
        )
    }

    /**
     * RED-2 的第二半（现盘即绿的护栏，R-23.4）：徽章面改词**不许**动目录原文。
     *
     * `AiFeature.status` 是产品目录的实话（未实现必须以「未实现」开头，已上线以「已可用」开头），
     * 本席若顺手把目录原文改成「未上线」，这条当场判红。
     */
    @Test
    fun `the catalogue prose keeps its own wording and is untouched by the badge face`() {
        AiFeature.entries.forEach { feature ->
            if (feature.implemented) {
                assertTrue(
                    feature.status.startsWith(IMPLEMENTED_PREFIX),
                    "${feature.name} 已上线，目录原文必须以「$IMPLEMENTED_PREFIX」开头，当前是：${feature.status}",
                )
            } else {
                assertTrue(
                    feature.status.startsWith(UNIMPLEMENTED_PREFIX),
                    "${feature.name} 未上线，目录原文必须以「$UNIMPLEMENTED_PREFIX」开头，当前是：${feature.status}",
                )
            }
        }
    }

    private companion object {
        const val UNIMPLEMENTED_PREFIX = "未实现"
        const val IMPLEMENTED_PREFIX = "已可用"
        const val BADGE_AVAILABLE = "现在能用"
        const val BADGE_UNRELEASED = "未上线"
    }
}
