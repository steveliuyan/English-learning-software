package com.example.englishlearning.ui

import com.example.englishlearning.learning.domain.WordCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * m-5（评审席 `task-20A-review.md` Minor 段，控制者裁定「取抽一处那条」）：
 * 词汇类列表的四枚格式化 helper 的**唯一所在地**。
 *
 * 修掉的债：`7a30a47` 之前 `MyWordsScreen.kt:72-85`（`myWordsDateFormatter` / `myWordsAddedLabel` /
 * `myWordsSourceLabel` / `myWordsMeaning`）与 `VocabularyScreen.kt:124-130`（`vocabularyDateFormatter` /
 * `vocabularySourceLabel`）**逐字同文各一份**，而这族重复没有任何门管——`SharedComponentUniquenessRatchetTest`
 * 族 4 的取样窗口只认 `^private fun …Row…\(`，非 `Row` 命名的格式化 helper 整体隐形。
 * 「默默留两份」与「只登记不改」都被明令禁止，所以这里收成一处 internal，两屏同调；
 * 重复门与行为锚在 `WordListFormatTest`（判据先于实现存在，红样见 fix2 报告）。
 *
 * 本文件只做**文本格式化**：不取色、不组合、不读时区之外的任何状态，所以不碰三张棘轮，
 * 也不新增用户可见文案（文案逐字继承旧页，见下方各函数注释）。
 */

/** 日期档（`yyyy-MM-dd`）：收藏时间戳用它——旧收藏页给的就是这一档，合并页收藏段逐字继承（旧收藏页屏已于 Task 22-B 下线）。 */
private val wordListDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 时分档（`yyyy-MM-dd HH:mm`）：生词本条目的加入时间用它——旧生词页 `VocabularyScreen.kt:124` 原样。 */
private val wordListDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * 「收藏于」那一档：只到日。
 *
 * m-6（同一 Minor 段）：同一个时间戳在旧收藏页是 `yyyy-MM-dd`、在合并页是 `yyyy-MM-dd HH:mm`，
 * 同一条收藏在两处读到两种时间；裁定统一到**日期档**（对齐旧页，列表里时分的额外信息量不值两页分叉）。
 * 摘除前两页并存，必须同文，所以两页都调这一枚，谁也不再自己拼日期。
 * 时区由调用方传入（屏的 `zoneId` 形参），本函数不偷读系统时区，免得两页各自漂到不同默认值。
 */
internal fun wordListDateLabel(instant: Instant, zoneId: ZoneId): String =
    instant.atZone(zoneId).format(wordListDateFormatter)

/** 到分的那一档：生词本条目的「加入于 / 加入时间」用它（旧生词页口径逐字保留，m-6 不动它）。 */
internal fun wordListDateTimeLabel(instant: Instant, zoneId: ZoneId): String =
    instant.atZone(zoneId).format(wordListDateTimeFormatter)

/**
 * 加入来源档：两枚已知反馈翻成中文，未知的一律原样保留（旧生词页 `VocabularyScreen.kt:126-130` 口径逐字）。
 *
 * 已知遗留（登记，不在本席改）：`else -> "来源：$feedback"` 会把存储里的原始枚举名直接念给用户，
 * 这是旧页的既有毛病，合并页继承它；改它要连数据口径一起审，属另一笔账。
 */
internal fun wordListSourceLabel(feedback: String): String = when (feedback) {
    "Again" -> "来源：不认识"
    "Manual" -> "来源：手动加入"
    else -> "来源：$feedback"
}

/**
 * 释义档：主释义为空退第一枚义项，两者都没有给 `null`（调用方据此不渲染释义槽，也不给死行）。
 * 形状逐字继承 `MyWordsScreen.kt:83-85`（该处又继承旧生词页的义项优先口径）。
 */
internal fun wordListMeaning(card: WordCard): String? =
    card.meaningZh.ifBlank { card.senses.firstOrNull()?.meaningZh.orEmpty() }
        .ifBlank { null }

/**
 * 「词性 + 释义」那一档（批 1 Task 16，今日词单屏的行副文案）。
 *
 * 原型 `wordRow` 的 `row-sub` 逐字是 `w.pos + ' ' + w.meaning`（`assets/app.js:228`）——一个空格把两截
 * 拼成一行。这里保住那一档形状，同时补原型没有的**空值口径**（原型是写死的数据，两截都有值）：
 *  - 词性取主档，主档为空退第一枚义项的词性——**跟着释义那一档走**：释义取自第一枚义项时词性也取自
 *    同一枚，不许「主档词性 + 义项释义」这种拼法（会读出「v. 波浪」一类错配）。
 *  - 两截里哪一截空就不留前导/尾随空格；两截都空给 `null`，调用方据此不渲染副文案槽。
 *
 * 与 [wordListMeaning] 同住本文件（m-5 立的规矩：词汇列表的格式化 helper 只有这一处），
 * 屏侧再拼一份「词性 + 释义」就是第二份口径。
 */
internal fun wordListGloss(card: WordCard): String? {
    val meaning = wordListMeaning(card)
    val partOfSpeech = card.partOfSpeech.ifBlank { card.senses.firstOrNull()?.partOfSpeech.orEmpty() }
    return listOfNotNull(partOfSpeech.takeIf { it.isNotBlank() }, meaning)
        .joinToString(separator = " ")
        .ifBlank { null }
}
