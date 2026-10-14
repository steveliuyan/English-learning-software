package com.example.englishlearning.learning

/**
 * F4-01 决策 §9「删除全部学习数据」的级联影响预览。
 * 前五个数是被删面（学习进度事实与派生态），后两个数是保留面——
 * 确认框把两组一起列出，用户在按下「确认删除」前就能看见自己保住了什么。
 */
data class LearningDataDeletionImpact(
    val eventCount: Int,
    val reviewStateCount: Int,
    val planCount: Int,
    val completionCount: Int,
    val migrationAuditCount: Int,
    val keptArticleCount: Int,
    val keptFavoriteCount: Int,
)

/**
 * 删除全部学习数据端口（决策 §9）。删除集合 = 学习事件、复习调度态、
 * 每日计划快照（含任务行）、阅读完成记录、进度迁移审计；
 * 文章、收藏、搜索历史、词书本体与设置零触碰。
 * [deleteAllLearningData] 在单一事务里先数后删，返回**实删条数**——
 * 回执不许拿预览凑数；任一步失败整体回滚、正式数据零触碰。
 */
interface LearningDataDeletionRepository {
    suspend fun deletionImpact(profileId: String): Result<LearningDataDeletionImpact>

    suspend fun deleteAllLearningData(profileId: String): Result<LearningDataDeletionImpact>
}
