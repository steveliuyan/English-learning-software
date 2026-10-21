package com.example.englishlearning.ui

/**
 * 「我的词」入口行的尾部计数串（稿 `mockups.html:916` 的 `34 · 12 · 8`）的唯一规则。
 *
 * 从 `AppScreen` 组合代码里抽成纯函数（评审 I-3 修复轮 1：这段「三段全 `Ready` 才非 null」的规则
 * 原先一条判据都没有）。抽函数让规则可以在 JVM 侧逐分支钉死（`MyWordsSummaryTest`），
 * 宿主只保留一处调用（静态形状由 `MyWordsScreenContractTest` 点名）。
 *
 * 口径一字未变：**三段任一还没读出来就整体 null（宁缺）**——半个数字会让人把「还在读」当成「0 个」；
 * 三段都读好了才给「收藏数 · 生词本数 · 错词数」，空段给 0（0 是读出来的事实，不是没读）。
 */
internal fun myWordsSummaryOf(
    favorites: FavoritesUiState?,
    vocabulary: VocabularyUiState?,
    wrongWords: WrongWordsUiState?,
): String? {
    val favoritesReady = favorites as? FavoritesUiState.Ready ?: return null
    val vocabularyReady = vocabulary as? VocabularyUiState.Ready ?: return null
    val wrongWordsReady = wrongWords as? WrongWordsUiState.Ready ?: return null
    return "${favoritesReady.words.size} · ${vocabularyReady.items.size} · ${wrongWordsReady.cards.size}"
}
