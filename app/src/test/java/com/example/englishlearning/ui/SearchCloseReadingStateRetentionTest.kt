package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * F3-01 验收锚点：搜索关闭后回到原文章必须保留阅读位置与选词状态。
 *
 * 这条链路分散在三处，任何一处被重构掉，真机上都会表现为
 * 「查个词回来文章跳回顶部」——JVM 层无法用 Compose 状态复现，
 * 所以沿用 [AppScreenProductionWiringTest] 的源码契约测试风格，
 * 把三个必要环节钉在测试里：
 *
 * 1. AppScreen 用 `rememberSaveable` 持有 `articleScrollPosition`（进程/配置变化后也在）；
 * 2. AppScreen 把该位置与回传回调同时传给 `ArticleReadingScreen`；
 * 3. `ArticleReadingScreen` 用该位置初始化 `rememberScrollState`，
 *    并把滚动变化持续回传。
 *
 * 搜索 overlay 打开时文章离开组合（if/else if 链），
 * 返回时靠 saveable 状态重建——这正是「保留」的实现机制本身。
 */
class SearchCloseReadingStateRetentionTest {

    private fun projectFile(relativePath: String): File =
        listOf(File(relativePath), File("app/$relativePath")).firstOrNull { it.isFile }
            ?: error("找不到源文件：$relativePath")

    @Test
    fun `article scroll position survives via rememberSaveable in AppScreen`() {
        val source = projectFile("src/main/java/com/example/englishlearning/ui/AppScreen.kt").readText()

        assertTrue(
            source.contains(Regex("""rememberSaveable\(state\.profile\.id\) \{ mutableStateOf\(0\) \}"""))
                && source.contains("articleScrollPosition by rememberSaveable"),
            "articleScrollPosition 必须用 rememberSaveable 持有，否则搜索关闭后文章会跳回顶部",
        )
    }

    @Test
    fun `AppScreen passes position and change callback together to ArticleReadingScreen`() {
        val source = projectFile("src/main/java/com/example/englishlearning/ui/AppScreen.kt").readText()
        val articleCall = source.substringAfter("ArticleReadingScreen(").substringBefore("onSetLearnedMarks") +
            source.substringAfter("onSetLearnedMarks = { articleReadingViewModel?.setLearnedMarks(it) },").substringBefore("\n                    )")

        assertTrue(
            "articleScrollPosition = articleScrollPosition" in articleCall,
            "ArticleReadingScreen 必须收到当前滚动位置，搜索关闭后才能恢复",
        )
        assertTrue(
            "onArticleScrollPositionChange = { articleScrollPosition = it }" in articleCall,
            "ArticleReadingScreen 必须把滚动变化回传给 AppScreen 持久化",
        )
    }

    @Test
    fun `ArticleReadingScreen restores the scroll state from the passed position`() {
        val source = projectFile("src/main/java/com/example/englishlearning/ui/ArticleReadingScreen.kt").readText()

        assertTrue(
            source.contains("rememberScrollState(articleScrollPosition)"),
            "滚动状态必须用传入的 articleScrollPosition 初始化，搜索关闭后位置才不会丢失",
        )
        assertTrue(
            source.contains(Regex("""snapshotFlow \{ scrollState\.value \}\.collect\(onArticleScrollPositionChange\)""")),
            "滚动变化必须持续回传，否则查词期间的滚动在关闭搜索后无法恢复到最新位置",
        )
    }

    @Test
    fun `search overlay takes priority over the article branch in the layering chain`() {
        val source = projectFile("src/main/java/com/example/englishlearning/ui/AppScreen.kt").readText()

        // 搜索 overlay（~476 行）在 if/else if 链中先于文章分支（~888 行）：
        // 搜索打开时文章离开组合，关闭时靠 rememberSaveable 恢复——
        // 这是「保留阅读位置」的机制前提。若层级被重排（例如文章先命中），
        // 搜索页将永远无法盖在文章上，F3-01 阅读位置保留需重新验收。
        val searchAt = source.indexOf("} else if (showGlobalVocabularySearch)")
        val articleAt = source.indexOf("} else if (readingTarget != null || historyArticle != null)")
        assertTrue(searchAt in 1 until articleAt, "搜索分支必须先于文章分支命中（overlay 优先展示）")
    }
}
