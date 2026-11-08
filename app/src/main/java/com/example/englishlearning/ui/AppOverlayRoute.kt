package com.example.englishlearning.ui

import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article

/**
 * 覆盖全屏的二级层「现在挂的是哪一支」——挂载判据的唯一载体（批 D-1，决策 §3.1；
 * 2026-11-09 修正 1：优先级与层深分列于本表两列，不另建第二张手工表）。
 *
 * `mountIndex` 就是它在 `AppScreen` 原链里的声明序号（0..34，见计划批 D 节 route 表），
 * tab 宿主 = [AppOverlayRouteResolver.TAB_HOST_INDEX]。序号只承载**优先级**：z 序与返回键先后
 * （「先声明先赢」，QA3 A1 的许可/关于顺序就靠这条）；它**不是**方向判据的层深——挂载判据带
 * fall-through 守卫，更深的一屏常声明在更后（阅读历史→文章、学习设置→学习流程即此类）。
 * 方向层深由同表 [transitionDepth] 列承担：默认等于序号，只在有点名 from→to 边证据的支上覆写，
 * 覆写清单 == 命名边用例清单由 `ScreenTransitionContractTest` 钉住。
 *
 * payload 为什么冻在这里而不是让渲染腿回读实时状态：`AnimatedContent` 的退场腿在标志位翻转之后
 * 仍要继续组合（时长/位移见批 D-3 的 `AppTransition`），回读实时状态会让旧屏在退场期间被换掉内容
 * 甚至直接 NPE（原链 849 行 `selectedRelearnCard!!` 就是那种写法）。
 */
sealed interface AppOverlayRoute {
    val mountIndex: Int

    /**
     * 方向层深列：越小越深（[ScreenTransitionDirectionPolicy.overlay] 比这一列定前进/返回）。
     * 默认访问器就是「未覆写支 = 序号」这条默认律的唯一承载体；覆写只允许出现在
     * `ScreenTransitionContractTest.TRANSITION_DEPTH_OVERRIDES` 点名的支上（取值理由与发起点证据见该清单与方向用例）。
     */
    val transitionDepth: Int get() = mountIndex

    data class VocabularyDetail(val card: WordCard) : AppOverlayRoute { override val mountIndex = 0 }
    data object GlobalSearch : AppOverlayRoute { override val mountIndex = 1 }
    data class RelearnCard(val card: WordCard) : AppOverlayRoute { override val mountIndex = 2 }
    data class LearningRecordDetail(val card: WordCard) : AppOverlayRoute { override val mountIndex = 3 }
    data class FavoriteDetail(val card: WordCard) : AppOverlayRoute { override val mountIndex = 4 }
    data object VocabularyRelearnList : AppOverlayRoute { override val mountIndex = 5 }
    data object LearningRecords : AppOverlayRoute { override val mountIndex = 6 }
    data object LearningSetup : AppOverlayRoute { override val mountIndex = 7 }
    // 覆写（终评补口 第 1 条，发起点证据 AppScreen.kt:1075-1077 onSaved 落位后 setup 臂守卫 `setupRequired || showSetup`
    // 翻假、学习臂 resolver :24 接住，7→8 是前进）：层深压在 LearningSetup(7) 之下一档；
    // 取 <7 的最大整数 6，改判区只有序号 {6,7} 两臂（命名边用例 namedEdgeLearningSetupToLearningFlowIsForward）。
    data object LearningFlow : AppOverlayRoute { override val mountIndex = 8; override val transitionDepth = 6 }
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

    /** 两枚都进 route：`AppScreen.kt:1914` 的 `fromHistory = route.readingTarget == null` 靠它们判来源，不能回读实时状态。 */
    data class ArticleReading(val readingTarget: ReadingTarget?, val historyArticle: Article?) : AppOverlayRoute {
        override val mountIndex = 32
        // 覆写（终评补口 第 1 条）：三枚列表臂的挂载守卫带 fall-through（resolver :45-48），文章层比它们都深，
        // 序号 32 却把「更深」读成「更浅」；发起点证据 AppScreen.kt:1865（历史）、:1896 +
        // ReadingAccessViewModel.kt:243（选读）、ReadingAccessViewModel.kt:201 + AppScreen.kt:529（导入）。
        // 取 <29 的最大整数 28：与 ≤27 各臂的先后关系逐字不变，改判区恰为发起臂 29/30/31
        //（命名边用例 namedEdgeReadingHistory/ReadingFeed/ArticleImportToArticleReadingIsForward 三对 + 反向两对）。
        override val transitionDepth = 28
    }

    data object WordBookTransfer : AppOverlayRoute { override val mountIndex = 33 }
    data object Settings : AppOverlayRoute { override val mountIndex = 34 }
}
