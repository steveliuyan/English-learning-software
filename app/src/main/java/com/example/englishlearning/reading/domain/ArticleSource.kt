package com.example.englishlearning.reading.domain

/**
 * 文章的来源。**刻意做成封闭联合类型**。
 *
 * 设计意图：**用类型而不是校验来保证溯源完整性**。「抓取来源缺少署名或许可说明」这类错误
 * 不靠运行时校验拦截——那个分支的字段是必填构造参数，不完整的状态在编译期就写不出来。
 * 新增来源只能扩展本接口，而所有消费点（存储映射、阅读页署名展示）都会因 `when` 的穷尽性
 * 检查被强制同步处理新分支。
 *
 * 与存储层的约定：持久化用 [ArticleSourceType] 判别列加扁平来源列，映射集中在
 * `RoomArticleRepository` 一处；[AiGenerated] 复用既有 `modelName`/`parameterSummary`
 * 两列承载审计信息，不产生死列。
 */
sealed interface ArticleSource {
    /**
     * AI 生成。[modelName] 与 [parameterSummary] 供审计，必须保持不敏感：
     * 不得包含 Endpoint 或 Key 的任何部分。
     */
    data class AiGenerated(
        val modelName: String,
        val parameterSummary: String,
    ) : ArticleSource

    /**
     * 已核验许可的外刊抓取。[licenseNote] 与 [attributionText] 是**许可条件**而不是装饰：
     * 阅读页必须把它们连同 [articleUrl] 一起展示，且链接不自动打开，须用户主动点击。
     */
    data class WebFetched(
        val sourceId: String,
        val displayName: String,
        val articleUrl: String,
        val licenseNote: String,
        val attributionText: String,
    ) : ArticleSource

    /** 用户自行粘贴导入。无额外溯源字段；界面须说明内容由用户自行提供、请确保有权使用。 */
    data object UserImported : ArticleSource
}

/** 存储判别列的取值。仅用于持久化，领域代码应匹配 [ArticleSource] 本身。 */
enum class ArticleSourceType {
    AI_GENERATED,
    WEB_FETCHED,
    USER_IMPORTED,
}
