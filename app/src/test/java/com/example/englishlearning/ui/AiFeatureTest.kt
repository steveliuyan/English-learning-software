package com.example.englishlearning.ui

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
    fun `the page shows exactly the four features the user asked for, in order`() {
        assertEquals(
            listOf(AiFeature.WORD_PASSAGE, AiFeature.CLOZE, AiFeature.LISTENING, AiFeature.COACH),
            AiFeature.entries.toList(),
        )
        assertEquals(
            listOf("词文串学", "AI 短文填词", "单词随身听", "单词串讲"),
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
    fun `no feature is advertised as implemented while the AI gateway is still missing`() {
        AiFeature.entries.forEach { feature ->
            assertFalse(
                feature.implemented,
                "${feature.name} 被标记为已实现，但本轮并未接通 AI 网关；接通前必须保持 false",
            )
        }
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

    private companion object {
        const val UNIMPLEMENTED_PREFIX = "未实现"
    }
}
