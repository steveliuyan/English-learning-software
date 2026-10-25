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
            screen.contains("status = row.feedback.label()"),
            "批 1 Task 8：状态列只写「认识 / 模糊 / 不认识」。" +
                "「反馈：」三枚汉字＋冒号会把右列挤成省略号（右列 widthIn(max = 120.dp)），" +
                "而列的语义已经被行位与右对齐表达——前缀是纯冗余",
        )
        assertTrue(
            "反馈：" !in stripComments(screen),
            "「反馈：」前缀必须从**代码**里删净（注释允许保留这段历史说明——本判据只咬代码，" +
                "否则登记理由本身就会把判据变成永远红、逼下一个人放宽它）",
        )
    }

    /**
     * 批 1 Task 8：学习记录从「每条一张卡」改成**裸行列表**（spec §4.1 裸行族）。
     *
     * 判据的两半：
     *  ① 同日多条记录之间靠 `WordRow` 的 `topDivider` 画发丝线——`topDivider = index > 0`
     *     是「首行不画线、其后每行一条」的唯一写法（计划原文此处写作 `topDivider = true`，
     *     与同页 GREEN 本体的 `topDivider = index > 0` 自相矛盾；以本体形状为准；
     *     若哪天退化成「每行都画线」，首行线会贴在标签页下沿，本条不改口径即可判红——
     *     因为本锚要求的是**逐行按 index 决定**）；
     *  ② 行不再套 `Card(`：卡面是本屏「卡片汤」的来源，行与行之间只留一条线。
     */
    @Test
    fun `the record row is a bare word row with a hairline between rows`() {
        val screen = stripComments(uiScreenSource("LearningRecordsScreen.kt"))
        assertTrue(
            "topDivider = index > 0" in screen,
            "同日/同组多条记录靠 `topDivider = index > 0` 画分隔线（首行不画、其后每行一条，§4.1 裸行族）",
        )
        assertEquals(
            2,
            countMatches(screen, Regex("""itemsIndexed\(""")),
            "今日与历史两个分支都必须用 itemsIndexed 拿到行号（行号是 topDivider 的唯一依据；" +
                "漏一处就有一半记录没有分隔线）",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""\bCard\(""")),
            "学习记录不再套卡：每条记录就是一行（卡面 + 描边是「卡片汤」的原样，§4.1）",
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
