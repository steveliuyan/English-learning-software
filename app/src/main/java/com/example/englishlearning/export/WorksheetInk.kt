package com.example.englishlearning.export

/**
 * 默写纸的墨色契约。
 *
 * 用户要求「单词和音标要有明显的区分」：单词用最重的近黑、音标用明显更淡的灰，
 * 并且音标字号更小。三者数值集中在这里，渲染器与 JVM 测试读的是同一份，
 * 因此「单词必须明显重于音标」这条要求可以被测试锁定，而不是只靠人眼看。
 */
object WorksheetInk {
    /** 单词：近黑，最重，加粗。 */
    const val LEMMA = 0xFF111111.toInt()

    /** 音标：淡灰，明显轻于单词，用于弱化提示信息。 */
    const val IPA = 0xFFA3ABA8.toInt()

    /** 释义与其余正文。 */
    const val BODY = 0xFF333333.toInt()

    const val LEMMA_SIZE = 8.0f
    const val IPA_SIZE = 6.6f
    const val BODY_SIZE = 7.5f
}
