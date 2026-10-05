package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.WordCard

/**
 * 打开一条搜索结果的详情：按 `cardId` 从词书**回源取完整词卡**。
 *
 * 为什么需要它：索引为了控体积只保存结果列表要显示的四列（lemma / 音标 / 中文释义 / 词书名），
 * 详情页要的释义分组、例句、派生词、短语、近义词、配图都不在索引里。
 * `SearchVocabularyResult` 因此只是一条摘要，不是可直接渲染的卡片。
 *
 * 回源是「按册解析词书包」（一册一个 `book.json`），所以这里只对**用户点开的那一条**做，
 * 而不是对整份结果集做——结果集常跨多本词书（`ability` 精确命中 9 本），
 * 批量补全等于让每次搜索都重新解析命中的每一册，正是索引要消除的开销。
 *
 * 读不到返回 `null`（词书已被删除或包损坏）。索引是派生数据，可能短暂残留已删除词书的行，
 * 所以调用方必须把 `null` 当成「这个词卡暂时打不开」，**不得**当成一张空卡片。
 *
 * 本用例不吞异常：读卡失败是存储层的事实，原样抛给调用方；把「失败降级成可见提示」
 * 放在 ViewModel 里做，是为了让降级行为可测，而不是悄悄吃掉错误。
 */
class OpenVocabularySearchResultUseCase(
    private val cards: WordCardSource,
) {
    suspend operator fun invoke(result: SearchVocabularyResult): WordCard? =
        cards.cards(listOf(result.cardId))
            // 校验 id：拿错词比拿不到更糟。`WordCardSource` 契约允许丢弃无内容的 id，
            // 但不允许用别的卡顶替。
            .firstOrNull { it.cardId == result.cardId }
}
