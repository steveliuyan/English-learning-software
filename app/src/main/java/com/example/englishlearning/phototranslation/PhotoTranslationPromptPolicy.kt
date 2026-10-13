package com.example.englishlearning.phototranslation

import com.example.englishlearning.ai.net.AiPrompt

/**
 * 拍照翻译的受控提示词。OCR（或用户编辑后的）文本是**数据位**，不是提示词：
 * 指令部分固定，文本长度受限，模型只被允许输出译文本身。
 */
object PhotoTranslationPromptPolicy {
    const val maxTextLength = 4000

    fun build(text: String): AiPrompt {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "text" }
        require(trimmed.length <= maxTextLength) { "text too long" }
        return AiPrompt(
            system = "你是翻译引擎。只输出把用户提供的英语文本翻译成简体中文的译文本身，" +
                "不解释、不逐词罗列、不加任何前后缀，也不输出与译文无关的内容。",
            user = buildString {
                append("把下面这段文本翻译成简体中文，只输出译文：")
                append('\n')
                append(trimmed)
            },
        )
    }
}
