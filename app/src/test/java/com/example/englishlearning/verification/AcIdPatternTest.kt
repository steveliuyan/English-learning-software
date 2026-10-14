package com.example.englishlearning.verification

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * AC5-01（阶段 5 F5-01）：AC 追踪矩阵的编号口径钉桩测试。
 * 矩阵与 root 任务 `verifyAcTraceabilityMatrix` 必须使用与本测试一致的正则：
 * 仅识别阶段 0–4 的 `AC<0-4>-<两位数字>`，阶段 5 自身 AC5-xx 不在矩阵范围内。
 */
class AcIdPatternTest {
    fun ids(text: String): Set<String> = Regex("""AC[0-4]-\d{2}""").findAll(text).map { it.value }.toSet()

    @Test
    fun `pattern captures stage 0 to 4 ids only`() {
        val sample = "AC0-01 AC4-08 AC5-03 ac1-22 AC1-3"
        assertEquals(setOf("AC0-01", "AC4-08"), ids(sample))
    }

    @Test
    fun `stage numbering covers exactly five stages with two-digit suffixes`() {
        val generated = (0..4).flatMap { stage -> (1..99).map { n -> "AC%d-%02d".format(stage, n) } }
        assertEquals(generated.toSet(), ids(generated.joinToString(" ") + " AC5-01 ACX-01"))
    }
}
