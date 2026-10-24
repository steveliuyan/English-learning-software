package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 学习记录行迁移（Task 26 Step 3）的**互斥前提锚** + 站点形状钉。
 *
 * `LearningRecordsScreen.LearningRecordRow` 把不可用原因放进 `WordRow` 的 gloss 槽
 * （`gloss = 释义 ?: 原因串`），理由与 WordBookWords 同一条：`meta` 在右列 `widthIn(max = 120.dp)`
 * 下会把 11 枚汉字的原因卡成省略号。这条写法依赖「两槽互斥」——禁用 ⇒ `wordCard == null` ⇒
 * 释义侧按构造恒为 `null`，`?:` 才落到原因串。互斥前提的生产真值有三枚：
 *  ① `LearningRecord.canOpenDetail` 的字段默认值仍是 `= wordCard != null`；
 *  ② `LearningRecord.missing(...)` 显式传 `null` 卡 + `false`；
 *  ③ `RoomLearningRecordRepository` 查不到卡时走 ②、查到卡时走默认值（构造站点不覆盖 `canOpenDetail`）。
 *
 * 前提哪天放开（例如允许 `wordCard != null` 却 `canOpenDetail == false`），这条当场红并提示改屏侧写法——
 * 那种形态下原因串会被释义吃掉（新的信息丢失）。connected 夹具一次构造好两槽，测不出前提漂移，
 * 只有这条源码锚能挡。对照：`WordBookWordsViewModelTest` 的同款锚（Task 20-B 修复轮 1 · F1 ②）。
 */
class LearningRecordsRowContractTest {

    @Test
    fun `can open detail stays derived from card presence at the default and both production construction sites`() {
        val models = learningMainSource("LearningRecordRepository.kt").readText()
        assertEquals(
            1,
            countMatches(models, Regex("""(?m)^\s*val canOpenDetail: Boolean = wordCard != null,$""")),
            "LearningRecord.canOpenDetail 的字段默认必须逐字仍是 `= wordCard != null`（行首锚）：" +
                "默认一旦放开，「禁用即无释义」不再按构造成立，屏侧 gloss 槽的原因串会被释义吃掉",
        )
        assertEquals(
            1,
            countMatches(models, Regex("""LearningRecord\(cardId, cardId\.substringAfterLast\(':'\), null, feedback, occurredAt, localDate, false, planId\)""")),
            "LearningRecord.missing 必须仍把 `null`（卡）与 `false`（canOpenDetail）显式传在同一位置：" +
                "这是「禁用 ⇒ 无卡」的第二枚生产真值",
        )

        val repository = learningMainSource("RoomLearningRecordRepository.kt").readText()
        assertEquals(
            1,
            countMatches(repository, Regex("""\bLearningRecord\(""")),
            "RoomLearningRecordRepository 里 `LearningRecord(` 构造站点应恰一处（查到卡的分支）：" +
                "长出第二处构造站点时本锚不再覆盖全部生产数据，先扩锚再谈改屏侧写法",
        )
        assertEquals(
            0,
            countMatches(repository, Regex("""canOpenDetail""")),
            "生产构造站点不得用命名参数 `canOpenDetail = …` 覆盖默认值（现盘该串在本文件 0 处）",
        )

        val screen = uiScreenSource("LearningRecordsScreen.kt")
        assertEquals(
            1,
            countMatches(screen, Regex("""val enabled = row\.canOpenDetail""")),
            "本屏的 enabled 只许读 `row.canOpenDetail`（互斥链第三环：原因串侧与释义侧由它决定）",
        )
        assertEquals(
            1,
            countMatches(screen, Regex("""\bWordRow\(""")),
            "LearningRecordRow 必须真调共享 WordRow（Task 26 Step 3 的接线动作）",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""private fun RecordRow\(""")),
            "私有 RecordRow 必须随迁移删净（族 4 的合规出口会把它遮蔽至隐形，这里单独钉）",
        )
        assertTrue(
            screen.contains("?: unavailableNotice"),
            "不可用原因必须仍走 gloss 槽的 `?: unavailableNotice` 形态（改走 meta 会被右列宽度卡成省略号）",
        )
        assertTrue(
            screen.contains("status = \"反馈：\${row.feedback.label()}\""),
            "反馈状态文案必须保持原样（connected 用例按可见文本/行标签取证）",
        )
    }

    /** 读 `learning/` 包 main 源码：两种 JVM 工作目录都试，读不到响亮失败（防锚空转）。 */
    private fun learningMainSource(name: String): File =
        listOf(
            File("src/main/java/com/example/englishlearning/learning/$name"),
            File("app/src/main/java/com/example/englishlearning/learning/$name"),
        ).firstOrNull(File::isFile)
            ?: error(
                "学习记录行前提锚读不到 learning/$name（相对 ${File(".").absolutePath}）：" +
                    "工作目录漂移或文件搬家，本锚会假绿，必须修路径"
            )
}
