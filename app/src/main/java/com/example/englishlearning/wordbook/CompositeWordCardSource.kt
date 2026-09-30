package com.example.englishlearning.wordbook

import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.WordCard

/**
 * 把「导入册」与「内置册」合成一个 [WordCardSource]。
 *
 * 路由规则（顺序即优先级）：
 * 1. [cardIds]：导入册有该 id 的内容就用它，否则回退内置册。**同 id 时导入册优先**——
 *    用户导入的内容必须能盖住内置旧内容，否则导入等于没导入。
 * 2. [cards]：把请求按 id 拆给两个来源，各自只解析自己拥有的 id，再按**请求顺序**合并。
 *
 * 为什么需要它：`WordCardSource` 是单例端口，DI 里只能绑一个实现。没有这层路由，
 * 导入册只能出现在设置页列表里，学习流程仍然只读代码内的占位词。
 */
class CompositeWordCardSource(
    private val imported: WordCardSource,
    private val bundled: WordCardSource,
) : WordCardSource {

    override suspend fun cardIds(wordBookId: String): List<String> {
        if (wordBookId.isBlank()) return emptyList()
        val fromImported = imported.cardIds(wordBookId)
        return fromImported.ifEmpty { bundled.cardIds(wordBookId) }
    }

    override suspend fun cards(cardIds: List<String>): List<WordCard> {
        if (cardIds.isEmpty()) return emptyList()
        val fromImported = imported.cards(cardIds)
        val resolved = fromImported.mapTo(mutableSetOf(), WordCard::cardId)
        // 只把导入册没解析出来的 id 交给内置册：两册都可能有同 id 的词卡，导入册必须赢。
        val remainder = cardIds.filterNot(resolved::contains)
        val fromBundled = if (remainder.isEmpty()) emptyList() else bundled.cards(remainder)
        val byId = (fromImported + fromBundled).associateBy(WordCard::cardId)
        // 按请求顺序回，且丢掉两个来源都没解析出来的 id（端口契约：无内容即丢弃）。
        return cardIds.mapNotNull(byId::get)
    }
}
