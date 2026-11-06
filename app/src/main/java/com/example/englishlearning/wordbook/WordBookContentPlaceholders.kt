package com.example.englishlearning.wordbook

/**
 * 词书包内容里**已知占位语**的判据，全站唯一一份。
 *
 * 为什么需要它：随包十册（45,290 张卡）的 `example` 字段实测只有一个取值——字符串「暂无例句」，
 * 真实例句 0 条；这串占位语来自外部词典源本身（全仓检索只命中 `assets/wordbooks` 下十份
 * `book.json` 与它们的 `mergeDebugAssets` 副本，代码里没有任何一处生产它），打包时原样落盘，
 * 于是词卡卡面把一行中文占位当例句印了出来。AC1-12 要求「无数据的模块隐藏区域、不出现『暂无』占位」，
 * 这里就是把它在**进入 domain 之前**兑现掉。
 *
 * 三条硬边界，防止归一退化成模糊匹配：
 *  - **只认整串相等**（去首尾空白后）。含「暂无」二字的正常句子（「这份材料暂无例句可循」）
 *    带标点的变体（「暂无例句。」）都是真实内容，一律保留；串里含破折号的真短语（`mother-to-be`、
 *    「the — not so simple — part」）同理。
 *  - [EXAMPLES] 与 [NO_DATA_MARKS] 都是**封闭清单**：新增一项要有数据出处（哪一册、哪个字段、多少条），
 *    不许凭感觉加。
 *
 * 决策与实测读数：`docs/decisions/2026-11-06-placeholder-text-is-not-learning-content.md`。
 */
internal object WordBookContentPlaceholders {

    /** 整串等于其中任何一项 ⇒ 这条内容不存在。 */
    val EXAMPLES: Set<String> = setOf("暂无例句")

    /**
     * 数据源用来表示「这一项没有内容」的记号：全角破折号「—」，且**整串就是它**。
     *
     * 出处（2026-11-06 对随包十册现算，脚本读数见本批报告）：78,392 条搭配里 6,517 条的
     * `text` 与 `meaningZh` 同为「—」；91,971 条近义里 3,224 条的 `lemma` 同为「—」。
     * 这些条目过去各自渲染成一行「常用搭配 —」与一枚点得动的「—」近义 chip——比自镜像更接近
     * AC1-12 禁止的「空标题 + 占位」。同一条数据在 `senses` / `derived` 两字段实测 0 命中，
     * 所以判据只开在这两处用到的字段上。
     */
    val NO_DATA_MARKS: Set<String> = setOf("—")

    /** 例句：空白或整串是占位语 ⇒ 没有例句。 */
    fun exampleOrNull(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() && it !in EXAMPLES }

    /**
     * 条目身份字段（搭配短语、近义词 lemma）：空白或整串是「无内容」记号 ⇒ 这一条整体不存在，
     * 调用处应把条目丢掉，而不是留下一个只有记号的行。
     */
    fun entryOrNull(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() && it !in NO_DATA_MARKS }

    /**
     * 搭配的中文释义：与英文短语本身逐字相同 ⇒ 只讲得出一个字段。
     *
     * 随包十册 78,392 条搭配里 77,358 条（98.7%）是这种自镜像（`a lot 许多 / a lot 许多`、`非常 / 非常`），
     * 详情页因此把同一串字念两遍。这条判据是**结构性的**（比较两个字段），不依赖任何词表。
     */
    fun phraseMeaningOrNull(text: String, meaning: String?): String? =
        meaning?.trim()?.takeIf { it.isNotEmpty() && it != text.trim() }
}
