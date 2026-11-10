package com.example.englishlearning.ui

import com.example.englishlearning.learning.domain.WordCard

/**
 * 覆盖全屏的二级层「现在挂的是哪一支」——挂载判据的唯一载体（批 D-1，决策 §3.1；
 * 2026-11-09 修正 1：优先级与层深分列于本表两列，不另建第二张手工表）。
 *
 * `mountIndex` 就是它在 `AppScreen` 原链里的声明序号（0..33，见计划批 D 节 route 表），
 * tab 宿主 = [AppOverlayRouteResolver.TAB_HOST_INDEX]。序号只承载**优先级**：z 序与返回键先后
 * （「先声明先赢」，QA3 A1 的许可/关于顺序就靠这条）；它**不是**方向判据的层深——挂载判据带
 * fall-through 守卫，更深的一屏常声明在更后（阅读历史→文章、学习设置→学习流程即此类）。
 * 方向层深由同表 [transitionDepth] 列承担：默认等于序号，只在有点名 from→to 边证据的支上覆写，
 * 覆写清单 == 命名边用例清单由 `ScreenTransitionContractTest` 钉住。
 *
 * payload 为什么冻在这里而不是让渲染腿回读实时状态：`AnimatedContent` 的退场腿在标志位翻转之后
 * 仍要继续组合（时长/位移见批 D-3 的 `AppTransition`），回读实时状态会让旧屏在退场期间被换掉内容
 * 甚至直接 NPE（原链 849 行 `selectedRelearnCard!!` 就是那种写法）。
 * 口径边界（决策册「2026-11-09 修正」修正 3）：这条理由只覆盖**挂载判据 + 该腿确实渲染读取的字段**——
 * 站 1 腿体内仍有 20 处 `collectAsState()` 读实时 VM（2026-11-09 现算），「冻了却没人读」的字段按裁定从 payload 摘掉，
 * 零读者登记项只许变短；把 35 支渲染输入全数搬进 payload 不在批 D 范围。
 */
sealed interface AppOverlayRoute {
    val mountIndex: Int

    /**
     * 方向层深列：越小越深（[ScreenTransitionDirectionPolicy.overlay] 比这一列定前进/返回）。
     * 默认访问器就是「未覆写支 = 序号」这条默认律的唯一承载体；覆写只允许出现在
     * `ScreenTransitionContractTest.TRANSITION_DEPTH_OVERRIDES` 点名的支上（取值理由与发起点证据见该清单与方向用例）。
     */
    val transitionDepth: Int get() = mountIndex

    /**
     * 导航身份列（终评补口 第 2 条，决策册「2026-11-09 修正」修正 2）：「用户换了一屏」的稳定判据。
     * 站 1 的 `AnimatedContent(targetState = overlayRoute, contentKey = { it?.navigationIdentity })`
     * 比的就是这一列——payload 里的实时派生字段（`newDone` / `dueDone` / 卡面 / 词面列表）因此不再把
     * **一次数据刷新**读成**一次导航**（旧形状下每提交一次三档反馈都会造出一枚不等的 route，起一轮新转场、
     * 该腿重新组合、屏内 `remember` 态清零）。
     *
     * 默认访问器承载「26 枚 `data object` 是单例，`this` 天然稳定」这条默认律，与 [transitionDepth]
     * 同一套写法；9 枚 `data class` **必须**各自 `override val navigationIdentity` 给出稳定业务主键
     * （由 `ScreenTransitionContractTest.routeIdentityColumnIsOverriddenOnEveryDataClassBranch` 钉，
     * 漏一枚即判红；取值理由逐枚记在该闸的身份台账注释里）。
     *
     * 口径边界：这一列只管**要不要起转场**，不管方向（方向仍比 [transitionDepth]），也不是渲染输入。
     */
    val navigationIdentity: Any get() = this

    // 身份取 cardId（WordCard.kt:57 的业务主键；卡面 lemma/ipa/释义/例句是易变字段，不进身份）。
    // 四枚词卡支共用主键 ⇒ 靠 mountIndex 支别前缀区分（词卡详情→重学页 AppScreen.kt:834-836 即此类边；行号为 2026-11-09 现算）。
    data class VocabularyDetail(val card: WordCard) : AppOverlayRoute {
        override val mountIndex = 0
        override val navigationIdentity: Any get() = mountIndex to card.cardId
    }
    data object GlobalSearch : AppOverlayRoute { override val mountIndex = 1 }
    data class RelearnCard(val card: WordCard) : AppOverlayRoute {
        override val mountIndex = 2
        override val navigationIdentity: Any get() = mountIndex to card.cardId
    }
    data class LearningRecordDetail(val card: WordCard) : AppOverlayRoute {
        override val mountIndex = 3
        override val navigationIdentity: Any get() = mountIndex to card.cardId
    }
    data class FavoriteDetail(val card: WordCard) : AppOverlayRoute {
        override val mountIndex = 4
        override val navigationIdentity: Any get() = mountIndex to card.cardId
    }
    data object VocabularyRelearnList : AppOverlayRoute { override val mountIndex = 5 }
    data object LearningRecords : AppOverlayRoute { override val mountIndex = 6 }
    data object LearningSetup : AppOverlayRoute { override val mountIndex = 7 }
    // 覆写（终评补口 第 1 条，发起点证据 AppScreen.kt:1082-1085 onSaved 落位后 setup 臂守卫 `setupRequired || showSetup`
    //（resolver :25）翻假、学习臂 resolver :26 接住，7→8 是前进）：层深压在 LearningSetup(7) 之下一档；
    // 取 <7 的最大整数 6，改判区只有序号 {6,7} 两臂（命名边用例 namedEdgeLearningSetupToLearningFlowIsForward）。
    data object LearningFlow : AppOverlayRoute { override val mountIndex = 8; override val transitionDepth = 6 }
    data object WorksheetPreview : AppOverlayRoute { override val mountIndex = 9 }
    data object WorksheetSettings : AppOverlayRoute { override val mountIndex = 10 }
    // 身份取 planId（TodayPlanViewModel.kt:72，**可空**）；缺席时兜底「活动词书(:73) + 日期标签(:64)」，
    // 免得 `null` 主键把两份不同册/不同天的计划并成同一身份。newDone/dueDone/队列卡面/bookProgress 全是派生态，不进身份。
    data class ReviewQueue(val plan: TodayPlanUiState.Ready) : AppOverlayRoute {
        override val mountIndex = 11
        override val navigationIdentity: Any get() = mountIndex to (plan.planId ?: "${plan.activeWordBookId}@${plan.localDateLabel}")
    }
    data class TodayWordList(val plan: TodayPlanUiState.Ready) : AppOverlayRoute {
        override val mountIndex = 12
        override val navigationIdentity: Any get() = mountIndex to (plan.planId ?: "${plan.activeWordBookId}@${plan.localDateLabel}")
    }
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
    // AiFeature 是 enum（AiFeature.kt:32），枚举实例本身就是稳定身份。
    data class AiFeatureScaffold(val feature: AiFeature) : AppOverlayRoute {
        override val mountIndex = 27
        override val navigationIdentity: Any get() = mountIndex to feature
    }
    // 未注册 key 的字符串就是兜底页那一屏的业务主键。
    data class UnknownAiFeature(val featureKey: String) : AppOverlayRoute {
        override val mountIndex = 28
        override val navigationIdentity: Any get() = mountIndex to featureKey
    }
    data object ReadingHistory : AppOverlayRoute { override val mountIndex = 29 }
    data object ReadingFeed : AppOverlayRoute { override val mountIndex = 30 }
    data object ArticleImport : AppOverlayRoute { override val mountIndex = 31 }

    /**
     * `readingTarget` 单参：来源判定只认它——`AppScreen.kt:1921` 的 `fromHistory = route.readingTarget == null`（行号 2026-11-09 现算）。
     * 终评补口 第 3 条把旧的 `historyArticle` 参数从 payload 摘了：全仓对它零读者（正文由 `articleReadingViewModel`
     * 的 uiState 供给），「冻了却没人读」不接受，所以按裁定取「摘字段」而不是「给腿长出第二个真相源」，
     * `KNOWN_UNREAD_PAYLOAD_FIELDS` 的登记项同步缩短（决策册「2026-11-09 修正」修正 3）。
     * 挂载判据那半边不受影响：`AppMountFlags.historyArticle` 仍在，resolver 的 :47/:48/:50 三条守卫照旧读它（行号 2026-11-09 现算）。
     */
    data class ArticleReading(val readingTarget: ReadingTarget?) : AppOverlayRoute {
        override val mountIndex = 32
        // 身份取 article.articleId（Article.kt:4）；cards 列表是易变字段，不进身份。
        // 历史腿（readingTarget == null）没有可携带的主键（historyArticle 已按第 3 条摘除），落到一枚支内哨兵常量：
        // 全仓历史腿唯一发起点 AppScreen.kt:1872（2026-11-09 现算）进出一律经过 ReadingHistory 列表，故不存在「两篇历史文章同身份」的真实边。
        override val navigationIdentity: Any get() = mountIndex to (readingTarget?.article?.articleId ?: "reading-history-leg")
        // 覆写（终评补口 第 1 条）：三枚列表臂的挂载守卫带 fall-through（resolver :47–:50，2026-11-09 现算），文章层比它们都深，
        // 序号 32 却把「更深」读成「更浅」；发起点证据 AppScreen.kt:1872（历史）、:1903 +
        // ReadingAccessViewModel.kt:243（选读）、ReadingAccessViewModel.kt:201 + AppScreen.kt:529（导入）。
        // 取 <29 的最大整数 28：与 ≤27 各臂的先后关系逐字不变，改判区恰为发起臂 29/30/31
        //（命名边用例 namedEdgeReadingHistory/ReadingFeed/ArticleImportToArticleReadingIsForward 三对 + 反向两对）。
        override val transitionDepth = 28
    }

    data object WordBookTransfer : AppOverlayRoute { override val mountIndex = 33 }
}
