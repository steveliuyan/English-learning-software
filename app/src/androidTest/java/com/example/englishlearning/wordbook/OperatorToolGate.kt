package com.example.englishlearning.wordbook

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * 「批量打包 / 质量探针」工具类的显式 opt-in 闸。
 *
 * `wordbook/` 下这三个类（[WordBookPackTest]、[WordCardFieldsProbeTest]、[WordMeaningProbeTest]）
 * 名字带 `Test`、住在 androidTest 里，但它们是**打包阶段的批处理工具**：逐词发真实 AI 请求
 * （几百次、按量计费）、把 TSV/JSONL 摊给人核质量，本身不对产品行为做任何断言。
 * 于是它们在 connected 门禁里长期提供的是**与被测改动无关的红**——2026-11-03 与 2026-11-05 两次
 * 全量走查里，它们的失败形状完全一样（缺已落盘词条 / 设备未配默认文本或生图服务），
 * 而每一任都要重新花一遍「这是不是我改坏的」的判断成本。
 *
 * 所以这里把它们从「默认执行、缺前置条件就红」改成「默认跳过、操作者显式点名才执行」：
 *  - 跳过理由把工具性质、`-e` 开关名和「跳过 ≠ 通过」一次说清，看得见原人才不会误读成绿灯；
 *  - 类内原有的两条前置断言**一句不删**：显式 opt-in 之后仍然会红，那是给操作者的真信号
 *    （没配服务就跑打包，等于让几百次请求空打）。
 *
 * 运行方式（真机，已配好默认文本与生图服务，且愿意承担这批费用）：
 * ```
 * adb shell am instrument -w -e operatorTool true \
 *   -e class com.example.englishlearning.wordbook.WordBookPackTest \
 *   com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
internal const val OPERATOR_TOOL_FLAG = "operatorTool"

/** 每个工具用例的第一行调用本函数：未被显式点名即整条跳过。 */
internal fun assumeOperatorToolRequested(toolName: String) {
    val requested =
        InstrumentationRegistry.getArguments().getString(OPERATOR_TOOL_FLAG, "false") == "true"
    assumeTrue(
        "$toolName 是打包阶段的批量出网工具（逐词发真实 AI 请求、产数据供人工核质量），不做运行时断言：" +
            "connected 门禁默认跳过它，避免长出与本次改动无关的红。" +
            "**跳过 ≠ 通过**——本轮该类零执行，必须在报告里写清。" +
            "确要运行请显式加 `-e $OPERATOR_TOOL_FLAG true`（并先在设备上配好默认文本与生图服务）。",
        requested,
    )
}
