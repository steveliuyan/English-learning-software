package com.example.englishlearning.phototranslation

import com.example.englishlearning.ai.net.AiPrompt

/**
 * 一屏多块的受控提示词（F3-04）。
 *
 * 块文本是**数据位**，不是提示词：指令固定、条数与长度受限、块内换行压成空格，模型只被允许
 * 输出一个 JSON 数组，每项 `{"id":编号,"translation":"..."}`。上行不传图，只传编号后的文字块。
 */
object BlockTranslationPromptPolicy {
    /** 一屏上限。超过它的屏幕先分屏再翻，避免单次请求的 token 成本失控。 */
    const val maxBlocks = 60

    const val maxBlockTextLength = 500
    const val maxTotalLength = 6000

    private val lineBreaks = Regex("[\\r\\n]+")

    private const val SYSTEM_PROMPT =
        "你是翻译引擎。用户会给出若干条编号的文本块，每条一行、以“编号. ”开头。" +
            "把每一块翻译成简体中文，只输出一个 JSON 数组，每项形如 " +
            "{\"id\":编号,\"translation\":\"译文\"}；id 必须与输入编号一致且不重复，" +
            "不要输出 JSON 之外的任何内容，不解释、不逐词罗列、不加前后缀。"

    /**
     * 校验并规整块文本：去首尾空白、块内换行压成空格（否则会破坏「一行一块」的编号结构）。
     * 不合格直接 [IllegalArgumentException]——这些错误在发请求之前就该被拦住。
     */
    fun blocksOf(texts: List<String>): List<String> {
        require(texts.isNotEmpty()) { "blocks" }
        require(texts.size <= maxBlocks) { "too many blocks: ${texts.size}" }
        val blocks = texts.map { it.trim().replace(lineBreaks, " ").trim() }
        require(blocks.all { it.isNotEmpty() }) { "blank block" }
        require(blocks.all { it.length <= maxBlockTextLength }) { "block too long" }
        require(blocks.sumOf { it.length } <= maxTotalLength) { "total too long" }
        return blocks
    }

    fun build(texts: List<String>): AiPrompt {
        val blocks = blocksOf(texts)
        return AiPrompt(
            system = SYSTEM_PROMPT,
            user = buildString {
                append("把下面每一块文本翻译成简体中文，只输出 JSON 数组：")
                blocks.forEachIndexed { index, block ->
                    append('\n')
                    append(index + 1)
                    append(". ")
                    append(block)
                }
            },
        )
    }
}
