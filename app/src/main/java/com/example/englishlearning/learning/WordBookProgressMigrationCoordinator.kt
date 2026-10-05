package com.example.englishlearning.learning

sealed interface MigrationPreparation {
    /** 不需要迁移：同册，或目标册里没有可匹配的已学词。 */
    data object SaveWithoutMigration : MigrationPreparation

    /** 目标册里存在已学的同词，需要用户先决定是否标记。 */
    data class RequiresConfirmation(val preview: ProgressMigrationPreview) : MigrationPreparation

    /** 读取源/目标词卡或复习状态失败，无法判断；不得当作「没有重复词」放行。 */
    data object StorageUnavailable : MigrationPreparation
}

/**
 * 切书时「是否要迁移已学状态」的决策层。
 *
 * 把预览与执行分开，是为了让 UI 能先问用户、再落库；`prepare` 本身**不写任何数据**。
 */
class WordBookProgressMigrationCoordinator(
    private val service: WordBookProgressMigrationService,
) {
    suspend fun prepare(
        profileId: String,
        sourceBookId: String,
        targetBookId: String,
    ): MigrationPreparation {
        if (sourceBookId == targetBookId) return MigrationPreparation.SaveWithoutMigration
        val preview = service.preview(profileId, sourceBookId, targetBookId)
            .getOrElse { return MigrationPreparation.StorageUnavailable }
        return if (preview.candidates.isEmpty()) {
            MigrationPreparation.SaveWithoutMigration
        } else {
            MigrationPreparation.RequiresConfirmation(preview)
        }
    }

    suspend fun confirm(
        profileId: String,
        sourceBookId: String,
        targetBookId: String,
        preview: ProgressMigrationPreview,
    ): ProgressMigrationResult = service.migrate(profileId, sourceBookId, targetBookId, preview)
}
