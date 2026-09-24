package com.example.englishlearning.ui.mascot

/**
 * AI 表情能表达的全部状态。
 *
 * **刻意只有两个成员，且都由真实事实决定**：本机是否配置了可用的 AI Profile。
 * 不设「思考中」「工作中」「生成中」「成功」「失败」等成员——F2-03 与 F2-06 尚未接通，
 * 这些状态没有真实驱动，加进来就等于让界面声称一个不存在的能力。
 *
 * 想加成员之前先读 `docs/superpowers/specs/2026-09-23-ai-learning-tab-design.md` §4.5，
 * 那里把这条约束写成了硬性要求，并由 instrumentation 的关键字扫描守住。
 */
enum class AiMascotExpression {
    AI_UNCONFIGURED,
    AI_CONFIGURED,
    ;

    companion object {
        /** 唯一的构造入口：只接受本机真实事实，不接受调用方自由指定状态。 */
        fun of(aiConfigured: Boolean): AiMascotExpression =
            if (aiConfigured) AI_CONFIGURED else AI_UNCONFIGURED
    }
}
