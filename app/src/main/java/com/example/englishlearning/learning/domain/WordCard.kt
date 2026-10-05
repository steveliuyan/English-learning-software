package com.example.englishlearning.learning.domain

import kotlinx.serialization.Serializable

/** 一条按词性分组的释义（参考竞品词详情页：一词多词性多条）。 */
@Serializable
data class WordSense(
    val partOfSpeech: String,
    val meaningZh: String,
)

/** 派生词（如 surf → surfer / surfing）。 */
@Serializable
data class DerivedWord(
    val lemma: String,
    val partOfSpeech: String,
    val meaningZh: String,
)

/** 常见关联短语（如 surf the Internet）。 */
@Serializable
data class PhraseEntry(
    val text: String,
    val meaningZh: String,
)

/** 近义词（如 surf → breaker）。 */
@Serializable
data class RelatedWord(
    val lemma: String,
    val partOfSpeech: String,
    val meaningZh: String,
)

/**
 * Content shown on a single word card (spec F1-03, 扩展于 2026-09-28)。
 *
 * 基础字段（[lemma]~[inflections]）来自最早的占位词卡；扩展字段来自「词书包」——
 * 以用户提供的竞品词详情页为基线：多词性释义、例句译文、派生词、关联短语、近义词、配图。
 *
 * **全部扩展字段都有默认值**：占位词卡与既有调用方不需要改动，而导入的词书包可以带全量信息。
 * [partOfSpeech] / [meaningZh] 保持「首条释义」的语义，供只读这两处的旧界面继续工作；
 * 新界面应优先渲染 [senses]（为空时回退到 [partOfSpeech] + [meaningZh]）。
 *
 * [imagePath] 是**已落盘图片的绝对路径**（词书包导入后位于应用私有目录），
 * 不是文件名——渲染方直接读文件，不需要知道包结构，也不会碰网络。
 */
@Serializable
data class WordCard(
    val cardId: String,
    val wordBookId: String,
    val lemma: String,
    val ipa: String,
    val partOfSpeech: String,
    val meaningZh: String,
    val example: String? = null,
    val inflections: List<String> = emptyList(),
    val senses: List<WordSense> = emptyList(),
    val exampleZh: String? = null,
    val derived: List<DerivedWord> = emptyList(),
    val phrases: List<PhraseEntry> = emptyList(),
    val synonyms: List<RelatedWord> = emptyList(),
    val imagePath: String? = null,
    val rank: Int? = null,
)
