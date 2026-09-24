package com.example.englishlearning.reading

import com.example.englishlearning.ai.net.AiPrompt
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.ArticleType

/** 译文目标语言。V1 只支持中文；加新语言时必须同步检查渲染层的字体与排版假设。 */
enum class TranslationLanguage { ZH }

/** 一次文章生成的全部输入。字段是终态值（不含 profile 配置），提示词只由它决定。 */
data class ArticleGenerationRequest(
    val profileId: String,
    val localDate: String,
    val wordBookId: String,
    val articleType: ArticleType,
    val length: ArticleLengthPolicy.Resolved,
    val language: TranslationLanguage,
    val targetCards: List<WordCard>,
    /** 已用过、要求模型本次避开的词。让「换一篇」真的换，而不是换个标题重讲一遍。 */
    val excludedLemmas: List<String>,
)

/**
 * 生成请求到提示词的纯函数映射。没有模板引擎，没有隐藏状态：
 * 同一 [ArticleGenerationRequest] 永远产出同一 [AiPrompt]。
 *
 * 系统提示**只要求三个 JSON 字段**且不得出现任何要求模型给高亮位置的措辞——
 * 高亮一律由系统从 `coveredLemmas` 派生（spec F2-03），信任模型坐标是被禁止的。
 */
object ArticlePromptPolicy {
    private val articleTypeLabels = mapOf(
        ArticleType.NEWS to "新闻",
        ArticleType.STORY to "故事",
        ArticleType.SCIENCE to "科普",
        ArticleType.WORKPLACE to "职场",
    )
    private val languageLabels = mapOf(
        TranslationLanguage.ZH to "中文",
    )

    fun build(request: ArticleGenerationRequest): AiPrompt = AiPrompt(
        system = SYSTEM_PROMPT,
        user = buildString {
            append("词书：").append(request.wordBookId).append('\n')
            append("文章类型：").append(articleTypeLabels.getValue(request.articleType)).append('\n')
            append("目标长度：").append(request.length.targetWords.first).append(" 到 ")
                .append(request.length.targetWords.last).append(" 个英文单词（可接受范围 ")
                .append(request.length.acceptedWords.first).append(" 到 ")
                .append(request.length.acceptedWords.last).append("）\n")
            append("english 字段使用英文；chinese 字段使用")
                .append(languageLabels.getValue(request.language)).append("。\n")
            append("必须使用的词（按下列顺序，含词形变化）：\n")
            request.targetCards.forEach { card ->
                append("- ").append(card.lemma).append(inflections(card)).append('\n')
            }
            if (request.excludedLemmas.isNotEmpty()) {
                append("以下词已在此前的文章中使用过，本次必须避开：")
                    .append(request.excludedLemmas.joinToString("、")).append('\n')
            }
        },
    )

    private fun inflections(card: WordCard): String =
        if (card.inflections.isEmpty()) "" else "（${card.inflections.joinToString("、")}）"

    // 注意措辞：这里不能出现「高亮位置」对应的英文词，任何要求模型给坐标的表述都是规格禁止的。
    private const val SYSTEM_PROMPT =
        "You write short English articles for language learners. " +
            "Reply with JSON only, containing exactly three fields: title, english, chinese. " +
            "Do not add positions, markers, annotations, or any extra fields. " +
            "The english field must be pure English prose; the chinese field must be pure Chinese prose. " +
            "Never include HTML, script, or markup of any kind."
}
