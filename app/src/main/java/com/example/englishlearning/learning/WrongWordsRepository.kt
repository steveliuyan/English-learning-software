package com.example.englishlearning.learning

/**
 * F4-01 决策⑤错词条目：某 profile 下「最近一次学习事件反馈为 Again」的一张卡。
 *
 * [nextReviewAtEpochMillis] 取自 `card_review_states` 的调度缓存，可能为 null
 * （无状态行）——排序与显示都必须容忍缺失，事件时间才是权威。
 */
data class WrongWord(
    val cardId: String,
    val wordBookId: String,
    val lastFeedbackAtEpochMillis: Long,
    val nextReviewAtEpochMillis: Long?,
)

interface WrongWordsRepository {
    /** 派生顺序即展示顺序：按最近到期（或事件时间回退）升序。 */
    suspend fun wrongWords(profileId: String): Result<List<WrongWord>>
}
