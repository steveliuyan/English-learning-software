package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 查词入口的接线守卫：**主页查词**与**阅读页点未知词**必须都走索引版全量搜索。
 *
 * 为什么需要它（2026-10-05）：阅读页点一个不在本文词表里的词，原本走的是
 * `WordBookSearchViewModel`——它打开时逐册解析词书包（`cards.cardIds` + `cards.cards`），
 * 这正是用户反馈的「查词慢」。索引建好之后，这条入口却仍然留在旧实现上：
 * 主页已经秒出结果，从阅读页点同一个词还要等整包解析。
 *
 * 这里盯两件不依赖设备的事：
 * 1. `AppScreen` 不再引用旧的全量扫描查词 ViewModel（引用回来 = 入口被接回去了）；
 * 2. 两个入口都通过 `openSearch` 进入，且各自传对了初始查询词——
 *    主页传空串（落在「最近搜索」），阅读页传被点中的词（直接出结果）。
 *
 * 断言写法刻意用「精确调用串」而不是「某个变量名出现过」：前者一旦被改错就红，
 * 后者容易被无关改动带过。
 */
class SearchEntryWiringTest {

    @Test
    fun `neither entry is wired back to the full scan word book search`() {
        val appScreen = appScreenSource()

        assertTrue(
            !appScreen.contains("WordBookSearchViewModel"),
            "AppScreen 又接回了旧的全量扫描查词页（WordBookSearchViewModel）：" +
                "阅读页点未知词会再次逐册解析词书包，查词又变慢。",
        )
    }

    @Test
    fun `home entry enters with a blank query so it lands on recent history`() {
        val appScreen = appScreenSource()

        assertTrue(
            appScreen.contains("""globalVocabularySearchViewModel?.openSearch("")"""),
            "主页查词入口没有带着空串进入：它应当清掉上次的查询词，落在「最近搜索」。",
        )
    }

    @Test
    fun `reading entry carries the tapped word into the search`() {
        val appScreen = appScreenSource()

        assertTrue(
            appScreen.contains("globalVocabularySearchViewModel?.openSearch(tapped)"),
            "阅读页查词入口没有把被点中的词带进搜索：用户点了一个词却看不到它的意思。",
        )
    }

    /** 入口只有一个实现，不允许界面层再各自写一份「设置查询词 + 搜索」。 */
    @Test
    fun `both entries go through the same view model entry point`() {
        val appScreen = appScreenSource()
        val calls = Regex("""openSearch\(""").findAll(appScreen).count()

        assertEquals(2, calls, "查词入口的调用点数量变了（应为主页、阅读页各一处）：$calls")
    }

    /** 单元测试的工作目录是模块目录（app/），兼容直接给相对仓库路径的写法。 */
    private fun appScreenSource(): String =
        listOf(File("src/main/java/com/example/englishlearning/ui/AppScreen.kt"))
            .plus(File("app/src/main/java/com/example/englishlearning/ui/AppScreen.kt"))
            .firstOrNull { it.isFile }
            ?.readText()
            ?: error("找不到 AppScreen.kt")
}
