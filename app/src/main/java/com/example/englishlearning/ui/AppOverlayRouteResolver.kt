package com.example.englishlearning.ui

/**
 * 挂载权威：`AppMountFlags` → 当前该挂的那一支全屏覆盖层（`null` = tab 宿主）。
 *
 * 下面 35 枚条件与 `AppScreen.kt:728`–`2020` 原链**逐字一致、顺序逐字照抄**：
 * 「先声明先赢」在原文件里既是 z 序也是返回键优先级承诺（QA3 A1 的许可/关于顺序就是这条），
 * 所以这里改顺序 = 改产品行为，必须回到决策文档重开，不许顺手「整理」。
 * 由 `ScreenTransitionContractTest`（批 D-2 落闸）双向钉：链上不许残留挂载级判据，本文件不许漏支。
 */
object AppOverlayRouteResolver {
    /** 链末 `} else {`（tab 宿主）的层级序号：比 34 支都浅，比任何 route 都浅。 */
    const val TAB_HOST_INDEX = 35

    fun resolve(flags: AppMountFlags): AppOverlayRoute? = when {
        flags.selectedSearchCard != null -> AppOverlayRoute.VocabularyDetail(flags.selectedSearchCard)
        flags.showGlobalVocabularySearch -> AppOverlayRoute.GlobalSearch
        flags.selectedRelearnCard != null -> AppOverlayRoute.RelearnCard(flags.selectedRelearnCard)
        flags.selectedLearningRecordCard != null -> AppOverlayRoute.LearningRecordDetail(flags.selectedLearningRecordCard)
        flags.selectedFavoriteCard != null -> AppOverlayRoute.FavoriteDetail(flags.selectedFavoriteCard)
        flags.showVocabularyRelearn -> AppOverlayRoute.VocabularyRelearnList
        flags.showLearningRecords -> AppOverlayRoute.LearningRecords
        flags.setupRequired || flags.showSetup -> AppOverlayRoute.LearningSetup
        flags.showLearning -> AppOverlayRoute.LearningFlow
        flags.showWorksheetPreview -> AppOverlayRoute.WorksheetPreview
        flags.showWorksheetSettings -> AppOverlayRoute.WorksheetSettings
        flags.showReviewQueue && flags.todayReady != null -> AppOverlayRoute.ReviewQueue(flags.todayReady)
        flags.showTodayWordList && flags.todayReady != null -> AppOverlayRoute.TodayWordList(flags.todayReady)
        flags.showCheckIn -> AppOverlayRoute.CheckIn
        flags.showWordBank -> AppOverlayRoute.WordBank
        flags.showAiProfiles -> AppOverlayRoute.AiProfiles
        flags.showSpeechSettings -> AppOverlayRoute.SpeechSettings
        flags.showReminderSettings -> AppOverlayRoute.ReminderSettings
        flags.showBackupExport -> AppOverlayRoute.BackupExport
        flags.showBackupRestore -> AppOverlayRoute.BackupRestore
        flags.showBackupPairingExport -> AppOverlayRoute.BackupPairingExport
        flags.showBackupHub -> AppOverlayRoute.BackupHub
        flags.showLicenses -> AppOverlayRoute.Licenses
        flags.showAbout -> AppOverlayRoute.About
        flags.selectedFeature == AiFeature.SENTENCE_ANALYSIS -> AppOverlayRoute.SentenceAnalysis
        flags.selectedFeature == AiFeature.PHOTO_TRANSLATION -> AppOverlayRoute.PhotoTranslation
        flags.selectedFeature == AiFeature.IMAGE_STUDIO -> AppOverlayRoute.ImageStudio
        flags.selectedFeature != null -> AppOverlayRoute.AiFeatureScaffold(flags.selectedFeature)
        flags.selectedFeatureKey != null -> AppOverlayRoute.UnknownAiFeature(flags.selectedFeatureKey)
        flags.showReadingHistory && flags.historyArticle == null -> AppOverlayRoute.ReadingHistory
        flags.showReadingFeed && flags.readingTarget == null && flags.historyArticle == null -> AppOverlayRoute.ReadingFeed
        flags.showArticleImport -> AppOverlayRoute.ArticleImport
        flags.readingTarget != null || flags.historyArticle != null ->
            AppOverlayRoute.ArticleReading(flags.readingTarget, flags.historyArticle)
        flags.showWordBookTransfer -> AppOverlayRoute.WordBookTransfer
        flags.showSettings -> AppOverlayRoute.Settings
        else -> null
    }
}
