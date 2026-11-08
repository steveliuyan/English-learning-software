package com.example.englishlearning.ui

import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article

/**
 * 覆盖全屏的二级层「现在挂的是哪一支」——挂载判据的唯一载体（批 D-1，决策 §3.1）。
 *
 * `mountIndex` 就是它在 `AppScreen` 原链里的声明序号（0..34，见计划批 D 节 route 表），
 * tab 宿主 = [AppOverlayRouteResolver.TAB_HOST_INDEX]。序号即层级：越小越深、越先赢，
 * 所以方向判据（[ScreenTransitionDirectionPolicy]）只比序号，不需要第二张深度表。
 *
 * payload 为什么冻在这里而不是让渲染腿回读实时状态：`AnimatedContent` 的退场腿在标志位翻转之后
 * 还要继续组合 300ms，回读实时状态会让旧屏在半秒里被换掉内容甚至直接 NPE
 * （原链 849 行 `selectedRelearnCard!!` 就是那种写法）。
 */
sealed interface AppOverlayRoute {
    val mountIndex: Int

    data class VocabularyDetail(val card: WordCard) : AppOverlayRoute { override val mountIndex = 0 }
    data object GlobalSearch : AppOverlayRoute { override val mountIndex = 1 }
    data class RelearnCard(val card: WordCard) : AppOverlayRoute { override val mountIndex = 2 }
    data class LearningRecordDetail(val card: WordCard) : AppOverlayRoute { override val mountIndex = 3 }
    data class FavoriteDetail(val card: WordCard) : AppOverlayRoute { override val mountIndex = 4 }
    data object VocabularyRelearnList : AppOverlayRoute { override val mountIndex = 5 }
    data object LearningRecords : AppOverlayRoute { override val mountIndex = 6 }
    data object LearningSetup : AppOverlayRoute { override val mountIndex = 7 }
    data object LearningFlow : AppOverlayRoute { override val mountIndex = 8 }
    data object WorksheetPreview : AppOverlayRoute { override val mountIndex = 9 }
    data object WorksheetSettings : AppOverlayRoute { override val mountIndex = 10 }
    data class ReviewQueue(val plan: TodayPlanUiState.Ready) : AppOverlayRoute { override val mountIndex = 11 }
    data class TodayWordList(val plan: TodayPlanUiState.Ready) : AppOverlayRoute { override val mountIndex = 12 }
    data object CheckIn : AppOverlayRoute { override val mountIndex = 13 }
    data object WordBank : AppOverlayRoute { override val mountIndex = 14 }
    data object AiProfiles : AppOverlayRoute { override val mountIndex = 15 }
    data object SpeechSettings : AppOverlayRoute { override val mountIndex = 16 }
    data object ReminderSettings : AppOverlayRoute { override val mountIndex = 17 }
    data object BackupExport : AppOverlayRoute { override val mountIndex = 18 }
    data object BackupRestore : AppOverlayRoute { override val mountIndex = 19 }
    data object BackupPairingExport : AppOverlayRoute { override val mountIndex = 20 }
    data object BackupHub : AppOverlayRoute { override val mountIndex = 21 }
    data object Licenses : AppOverlayRoute { override val mountIndex = 22 }
    data object About : AppOverlayRoute { override val mountIndex = 23 }
    data object SentenceAnalysis : AppOverlayRoute { override val mountIndex = 24 }
    data object PhotoTranslation : AppOverlayRoute { override val mountIndex = 25 }
    data object ImageStudio : AppOverlayRoute { override val mountIndex = 26 }
    data class AiFeatureScaffold(val feature: AiFeature) : AppOverlayRoute { override val mountIndex = 27 }
    data class UnknownAiFeature(val featureKey: String) : AppOverlayRoute { override val mountIndex = 28 }
    data object ReadingHistory : AppOverlayRoute { override val mountIndex = 29 }
    data object ReadingFeed : AppOverlayRoute { override val mountIndex = 30 }
    data object ArticleImport : AppOverlayRoute { override val mountIndex = 31 }

    /** 两枚都进 route：`AppScreen.kt:1847` 的 `fromHistory = readingTarget == null` 靠它们判来源，不能回读实时状态。 */
    data class ArticleReading(val readingTarget: ReadingTarget?, val historyArticle: Article?) : AppOverlayRoute {
        override val mountIndex = 32
    }

    data object WordBookTransfer : AppOverlayRoute { override val mountIndex = 33 }
    data object Settings : AppOverlayRoute { override val mountIndex = 34 }
}
