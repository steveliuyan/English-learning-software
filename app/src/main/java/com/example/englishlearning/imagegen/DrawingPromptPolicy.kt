package com.example.englishlearning.imagegen

import com.example.englishlearning.ai.net.AiPrompt

/**
 * 生图提示词的受控模板：用户只提供「画什么」这一个主题数据位，
 * 输出约束（英文、≤600 字符）固定写在指令里——主题是数据不是指令，
 * 提示词注入面被压缩到单个经过校验的字段。
 *
 * 系统提示固定不随主题变化；同一主题永远产出同一提示（纯函数）。
 */
object DrawingPromptPolicy {

    /** 主题长度上限：一句话能说清的画题足够，防止把长文塞进生图载荷。 */
    const val maxSubjectLength = 100

    /** 提示词长度上限（输出约束同样是对不可信模型输出的验收上限）。 */
    const val maxPromptLength = 600

    fun build(subject: String): AiPrompt {
        val trimmed = subject.trim()
        require(trimmed.isNotEmpty()) { "subject" }
        require(trimmed.length <= maxSubjectLength) { "subject" }
        return AiPrompt(
            system = systemPrompt,
            user = "请为主题「$trimmed」生成一段用于文生图模型的英文提示词，" +
                "要求：只用英文输出；不超过 600 字符；只输出提示词本身，" +
                "不要任何解释、引号或前后缀。",
        )
    }

    private const val systemPrompt =
        "你是文生图提示词工程师。根据用户给出的绘画主题，产出一段高质量的英文" +
            "生图提示词：包含主体、场景、风格与光线要素，语言自然、无歧义，" +
            "不要输出与提示词无关的任何内容。"
}
