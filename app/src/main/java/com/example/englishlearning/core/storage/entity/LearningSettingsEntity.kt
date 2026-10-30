package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learning_settings")
data class LearningSettingsEntity(
    @PrimaryKey val profileId: String,
    val openDetailOnKnown: Boolean,
    val openDetailOnFuzzy: Boolean,
    val openDetailOnForgotten: Boolean,
    val showVocabularySearchCount: Boolean = true,
    /**
     * v33 新增：每日复习上限（原型 `sPlanEdit` 的 `.range`，10–80，步长 5，缺省 30）。
     *
     * 这里的 30 与领域默认 `LearningSettings.DEFAULT_DAILY_REVIEW_LIMIT` 同值，两侧各自写死而**不互相 import**：
     * `core/storage/entity` 不依赖业务包是这一层的既有约束（本目录零条 `import com.example.englishlearning.*`），
     * 而 Room 的 Kotlin 初始值还要参与 schema `defaultValue` 的校验。同值这一条由
     * `AppDatabaseMigrationTest.migrateV32ToV33_*`（回填必须是 30、不许是 0）与
     * `LearningSetupViewModelTest`（未存过偏好时读到 30）两头钉住，不在注释里空口保证。
     */
    val dailyReviewLimit: Int = 30,
)
