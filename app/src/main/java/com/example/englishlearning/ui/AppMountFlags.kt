package com.example.englishlearning.ui

import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article

/**
 * [AppOverlayRouteResolver] 的唯一入参：把散在 `AppScreen` 里 170–440 行的标志位与派生值收成一份值对象。
 *
 * **全部带默认值**——生产侧逐一点亮（不新增状态、不改任何写入点），测试侧只写相关位。
 * `setupRequired` / `todayReady` / `selectedFeature` 三枚本来就是 `AppScreen` 里的派生值
 * （395 / 394 / 369 行），照原样传进来；resolver 不重新推导，避免长出第二份派生口径。
 */
data class AppMountFlags(
    val selectedSearchCard: WordCard? = null,
    val showGlobalVocabularySearch: Boolean = false,
    val selectedRelearnCard: WordCard? = null,
    val selectedLearningRecordCard: WordCard? = null,
    val selectedFavoriteCard: WordCard? = null,
    val showVocabularyRelearn: Boolean = false,
    val showLearningRecords: Boolean = false,
    val setupRequired: Boolean = false,
    val showSetup: Boolean = false,
    val showLearning: Boolean = false,
    val showWorksheetPreview: Boolean = false,
    val showWorksheetSettings: Boolean = false,
    val showReviewQueue: Boolean = false,
    val todayReady: TodayPlanUiState.Ready? = null,
    val showTodayWordList: Boolean = false,
    val showCheckIn: Boolean = false,
    val showWordBank: Boolean = false,
    val showAiProfiles: Boolean = false,
    val showSpeechSettings: Boolean = false,
    val showReminderSettings: Boolean = false,
    val showBackupExport: Boolean = false,
    val showBackupRestore: Boolean = false,
    val showBackupPairingExport: Boolean = false,
    val showBackupHub: Boolean = false,
    val showLicenses: Boolean = false,
    val showAbout: Boolean = false,
    val selectedFeature: AiFeature? = null,
    val selectedFeatureKey: String? = null,
    val showReadingHistory: Boolean = false,
    val historyArticle: Article? = null,
    val showReadingFeed: Boolean = false,
    val readingTarget: ReadingTarget? = null,
    val showArticleImport: Boolean = false,
    val showWordBookTransfer: Boolean = false,
    val showSettings: Boolean = false,
)
