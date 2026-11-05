package com.example.englishlearning.language.domain

/**
 * 发音缓存台账里的「关联实体」（决策 2026-11-05 §5 D-7）。
 *
 * 记的是**这条音频从哪个屏出来**（[kind]）与**哪一条**（[id]，本地主键）。
 * 两者刻意分开：长难句分析、语音设置试听这类场景本来就没有主键，
 * 硬凑一个 id 只会让台账把「键」当成「关联」（决策 D-9），所以 [id] 允许为 null，
 * 而 [kind] 恒有值——调用方不给时缓存层记 [PronunciationEntityKind.Unattached]。
 *
 * 它**不进缓存键**（[PronunciationCacheKey] 保持内容级）：同一个词的音频按卡号裂成多份
 * 会直接推翻「同一输入命中同一缓存」，也白花一次推理或一次付费出网（决策 D-8）。
 */
data class PronunciationEntityRef(
    val kind: PronunciationEntityKind,
    val id: String? = null,
) {
    /** 落台账的字符串：id 缺失就写 null，不写空串——空串在 JSON 里看起来像"有值但被截断"。 */
    val idOrNull: String? get() = id?.takeIf { it.isNotBlank() }

    companion object {
        fun wordCard(cardId: String): PronunciationEntityRef = PronunciationEntityRef(PronunciationEntityKind.WordCard, cardId)

        fun article(articleId: String): PronunciationEntityRef = PronunciationEntityRef(PronunciationEntityKind.Article, articleId)

        /** 拍照翻译：保存之后才有主键，未保存时 kind 照记、id 为空。 */
        fun photoTranslation(recordId: String?): PronunciationEntityRef =
            PronunciationEntityRef(PronunciationEntityKind.PhotoTranslation, recordId)

        val SentenceAnalysis: PronunciationEntityRef = PronunciationEntityRef(PronunciationEntityKind.SentenceAnalysis)
        val Preview: PronunciationEntityRef = PronunciationEntityRef(PronunciationEntityKind.Preview)
    }
}

enum class PronunciationEntityKind {
    /** 词卡（今日计划、复习、收藏、学习记录、查词详情、文章里点中的词）。 */
    WordCard,

    /** 每日文章 / 历史文章的一句。 */
    Article,

    /** 拍照翻译结果里的一句。 */
    PhotoTranslation,

    /** 长难句分析里的一句——屏上没有主键。 */
    SentenceAnalysis,

    /** 语音设置页的试听。 */
    Preview,

    /** 调用方没交代实体（含本批之前的旧台账，决策 D-12）。 */
    Unattached,
}
