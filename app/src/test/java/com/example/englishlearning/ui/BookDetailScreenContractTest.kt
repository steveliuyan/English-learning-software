package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 1 Task 15：词书详情屏对齐原型 `sBookDetail`（`assets/app.js:608-640`，路由 `'book-detail'`）。
 *
 * **新建屏**（用户 2026-10-29 追问后拍板「按原型新建这一屏」）：原型画的是独立一页
 * （`barTop({title:'词书', action: more})` + `.scr-body`），本仓此前**没有承载点**——词库页
 * 「词书」段挂的是 `WordBookWordsScreen`（筛选 chip + 字母栏 + 词行，是**词单浏览**，不是词书详情），
 * 所以本屏落到词库页词书段，由该段在「详情 / 词单」两种呈现之间切换：
 * 详情是默认面，「查看完整词单」进原来的词单浏览（`WordBookWordsScreen` 自带返回位，退回详情）。
 * 页头归 `WordBankScreen` 壳（「词库」+ 当前词书名 + 返回位），本屏只出正文——与生词 / 错词 / 收藏
 * 三段的面同一口径，不叠第二份页头。
 *
 * 原型骨架：`strong-card.on-strong`（`ring(learnt, words, 86)` + `.strong-label` 正在使用 +
 * `.strong-title` 书名 + `.strong-meta` 已学 x / y 词 + `.strong-foot`「按每天 4 个新词，还剩 N 天」）
 * → `section('这本书的情况')`（`.card > .rows` 四行：已掌握 / 学习中 / 未开始 / 词书版本）
 * → `section('操作')`（查看完整词单 / 导出学习进度 / 更换词书 / 删除这本书的进度）。
 *
 * ## 五处有意的偏离（都写在这里，不藏在实现里）
 *
 *  1. `.strong-foot` 那句「按每天 4 个新词，还剩 N 天；改计划可以更快」**整句不搬**：
 *     本仓的每日新增写在**本日的计划快照**里（`TodayPlan.newTarget`，且改设置默认次日生效），
 *     词书详情页没有「这本书每天学几个」这一读数；把它写死成 4 就是编造指标
 *     （与 Task 18 台账第 2、3 行同一条口径）。
 *  2. 「这本书的情况」不搬原型那四行的**名目**：`已掌握 / 学习中 / 未开始 / 词书版本`。
 *     前三项里本仓只有 `WordBookRecordStatus` 的 `未学 / 学习中 / 待复习` 三态（没有「已掌握」
 *     这一档），所以行目取本仓真有的三态（已学取 `WordBookProgress.learned`）；「词书版本」那一行
 *     （`2026.11` / `book.json · 随 App 更新`）本仓的词书元数据没有传到界面，整行不画。
 *  3. 「操作」只剩**两行**：`查看完整词单`（切到本段的词单浏览）与 `更换词书`（学习设置里的选词书）。
 *     `导出学习进度` 与 `删除这本书的进度` **整行不搬**：本仓只有整包导入/导出词书（`.wbpack`）与
 *     整库备份（设置页「备份与恢复」），没有「按这一本书导出进度」也没有「只删这一本书的进度」这两项能力；
 *     新增采集/写入通路属功能改动、未获授权（AGENTS.md「UI 之外的逻辑保持原样」）。
 *  4. 页头 `more` 溢出菜单（`dlg delete-book`）不搬：它唯一的内容就是第 3 条那枚删进度的危险动作。
 *  5. 进度环百分比取 `AppType.StatNum`（26sp serif）：原型 `.ring-num` 是 `serif 21px`，六档锁里
 *     没有 21sp 的 serif 档，取同族最近的 `StatNum`，登记为 token 近似（与 Task 18 台账第 7 行同一取法）。
 *
 * 读法是源码文本（[uiScreenSource] 已剥注释）；环的像素、行的高与可点闭合集合由 connected
 * `BookDetailScreenTest` 承担。
 */
class BookDetailScreenContractTest {

    private val screen: String by lazy { uiScreenSource("BookDetailScreen.kt") }
    private val host: String by lazy { uiScreenSource("AppScreen.kt") }

    @Test
    fun `the book detail is its own screen mounted once from the word bank book pane`() {
        assertEquals(1, countMatches(screen, Regex("""(?<![A-Za-z0-9_])fun BookDetailScreen\(""")), "本屏只在 `ui/BookDetailScreen.kt` 定义一次")
        assertEquals(1, countMatches(host, Regex("""(?<![A-Za-z0-9_])BookDetailScreen\(""")), "宿主（词库页词书段）挂载恰一处：这是本轮获授权新建的第六屏（用户 2026-10-29 拍板）")
        // 页头归 `WordBankScreen` 壳：本屏不出 `AppScreenHeader`，也不自带返回位（否则同屏两个返回键）。
        assertEquals(0, countMatches(screen, Regex("""AppScreenHeader\(""")), "本屏不自带页头：词库页壳已经给了「词库 + 词书名 + 返回」")
        assertEquals(0, countMatches(screen, Regex("""(?<![A-Za-z0-9_])onBack\b""")), "本屏不接返回位：退出词库页归壳那一枚返回键")
    }

    @Test
    fun `the summary card mirrors the prototype ring label title and meta`() {
        assertEquals(1, countMatches(screen, Regex("""\bStrongCard\(""")), "强卡走共享 `StrongCard`（原型 `.strong-card.on-strong`）恰一处")
        assertEquals(1, countMatches(screen, Regex("""\bbook_detail_ring\b""")), "进度环有自己的锚点（connected 侧按它量尺寸）")
        assertEquals(1, countMatches(screen, Regex("""AppType\.Eyebrow\b""")), "「正在使用」取 `.strong-label` 那档（`AppType.Eyebrow`）")
        assertEquals(1, countMatches(screen, Regex("""AppType\.StrongTitle\b""")), "书名取 `.strong-title` 27sp serif（`AppType.StrongTitle` 逐字同度量）")
        assertTrue("正在使用" in screen, "眉标逐字（`app.js:617`）")
        assertTrue("已学 " in screen, "`.strong-meta` 那句「已学 x / y 词」取真进度（`WordBookProgress`），不写 mock 数")
        assertEquals(1, countMatches(screen, Regex("""AppType\.StatNum\b""")), "环心百分比取 `AppType.StatNum`（原型 serif 21px 的近似档，见文件头第 5 条）")
    }

    @Test
    fun `the facts section carries only counts and rows this app can actually read`() {
        assertTrue("SectionHeading(text = \"这本书的情况\")" in screen, "节标题逐字（`app.js:625`）")
        assertTrue("已学" in screen && "未学" in screen, "两枚由 `WordBookProgress` 直接给出的数（learned / unlearned）")
        assertTrue("if (learningCount != null)" in screen, "「学习中」只在这一段真读到词行时出现（`null` 表示还没读出来，整行不画）")
        assertTrue("if (dueCount != null)" in screen, "「待复习」同理")
        listOf("已掌握", "词书版本", "book.json", "2026.11").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "原型 mock 的「$token」本仓读不到：不搬名目也不写死值（文件头第 2 条）",
            )
        }
        listOf("按每天", "还剩 ", "改计划可以更快").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "`.strong-foot` 的「$token」依赖「这本书每天学几个」这个本仓没有的读数（文件头第 1 条）",
            )
        }
    }

    @Test
    fun `the action group keeps only the two rows this app can perform`() {
        assertTrue("SectionHeading(text = \"操作\")" in screen, "节标题逐字（`app.js:631`）")
        assertEquals(2, countMatches(screen, Regex("""(?<![A-Za-z0-9_])BareRow\(""")), "操作组恰两行：查看完整词单 / 更换词书")
        assertTrue("title = \"查看完整词单\"" in screen && "onClick = onOpenWordList" in screen, "第一行开本段的词单浏览（原型 `to:'learn-queue'`）")
        assertTrue("title = \"更换词书\"" in screen && "onClick = onOpenPicker" in screen, "第二行开选词书（原型 `to:'book-picker'`）")
        listOf("导出学习进度", "删除这本书的进度", "book_detail_delete", "more").forEach { token ->
            assertEquals(
                0,
                countMatches(screen, Regex(Regex.escape(token))),
                "「$token」没有承载能力：本仓无按书导出、无按书删进度（文件头第 3、4 条），不画这枚入口",
            )
        }
    }

    @Test
    fun `the book detail reads only dynamic tokens`() {
        assertEquals(0, countMatches(screen, Regex("""Color\(0x|#[0-9A-Fa-f]{6}""")), "本屏不留裸 hex")
        assertEquals(0, countMatches(screen, Regex("""MaterialTheme\.typography\.|\bMint[A-Z]\w*""")), "排印与中性色一律走 AppType / appColors 令牌")
        assertEquals(0, countMatches(screen, Regex("""\b(?:fontSize|lineHeight)\s*=""")), "不覆写字号/行高（六档锁）")
        assertEquals(0, countMatches(screen, Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")), "不留静态深色读点")
    }
}
