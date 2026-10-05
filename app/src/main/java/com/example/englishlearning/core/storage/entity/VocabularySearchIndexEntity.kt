package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 本地词条搜索索引的一行：一个词书里的一张词卡。
 *
 * 主键是 `(wordBookId, cardId)`，与 `WordCard` 的身份一致，所以「同一词书内替换」天然幂等。
 *
 * [dataVersion] 记录建立索引时词书的 `dataVersion`。它只用于判断索引是否过期：
 * 词书内容更新后版本号变化，刷新用例据此重建该册索引，不必逐条比对内容。
 *
 * **这张表只保存查询与结果列表展示必需的列，不保存完整词卡。**
 * 2026-10-05 真机实测：早先每行存一份完整 `WordCard` 的 JSON 快照时，45,290 行的
 * JSON 合计 25.7 MB，占索引文本量的 86.5%，用户库从 270 KB 涨到 46.3 MB——
 * 而这些内容本来就在 `assets` 词书包里，等于复制了一份进数据库。
 *
 * 详情页要的释义分组、例句、派生词、短语、近义词、配图因此不在索引里，改由
 * `OpenVocabularySearchResultUseCase` 按 [cardId] 回 `WordCardSource` 取一次
 * （只对用户点开的那一条，不对整份结果集——后者会让每次搜索重新解析命中的每一册）。
 *
 * [normalizedPhrases] 把该卡的短语用 `\u0001` 连接成一个可检索串，保住「短语也能命中」的语义。
 * 单独建表做短语索引会把复杂度抬高一个量级，而短语命中本来就是包含级匹配，一个串足够。
 */
@Entity(
    tableName = "vocabulary_search_index",
    primaryKeys = ["wordBookId", "cardId"],
    indices = [
        Index(value = ["normalizedLemma"]),
        Index(value = ["wordBookId", "normalizedLemma"]),
    ],
)
data class VocabularySearchIndexEntity(
    val wordBookId: String,
    val cardId: String,
    val wordBookName: String,
    val dataVersion: String,
    val lemma: String,
    val normalizedLemma: String,
    val normalizedPhrases: String,
    /** 结果列表要显示的音标（`WordCard.ipa`）。 */
    val ipa: String,
    /** 结果列表要显示的中文释义（`WordCard.meaningZh`，即首条释义）。 */
    val meaningZh: String,
)
