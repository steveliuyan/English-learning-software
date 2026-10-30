package com.example.englishlearning.core.storage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 迁移链的**登记完整性**门（批 1 Task 12 立，起因是本批一次真实失手）。
 *
 * 为什么 JVM 侧也要管这件事：`AppDatabaseMigrationTest`（connected）确实会走 v1 → 当前的整条链，
 * 但它是 androidTest，**不在本阶段的验收门禁里**（门禁 = `assembleDebug` / `testDebugUnitTest` /
 * `compileDebugAndroidTestKotlin`）。Task 12 往 `MIGRATIONS` 数组尾部追加 `MIGRATION_32_33` 时，
 * 同一行里原有的 `MIGRATION_28_29` 被顺手抹掉了——三台门禁全绿，只有真机整链用例把它炸出来
 * （`A migration from 4 to 33 was required but not found`，Room 报的是端点，缺口在中间）。
 * 「改一行数组就把老用户升级路径打断」是这一层最贵的错误形态，所以把它收到 JVM 门里。
 *
 * 四条判据，两两不重复：
 *  ① **声明集 == 数组集**：写了 `val MIGRATION_x_y` 却没进数组 = 死代码 + 升级路径断裂（本次的事故）；
 *     数组里有而文件里没声明 = 根本编不过，这条是反向兜底；
 *  ② **逐格衔接**：排序后每步的 `start` 恰等于上一步的 `end`，从 1 起、到 `@Database(version = N)` 止，
 *     中间不许跳号（27 → 29 这种「看起来连着其实缺一步」在这里红）；
 *  ③ **数组顺序即声明顺序**：数组里相邻两枚的版本号也必须相邻，防止「数组里插错位置」这种 Room 能容忍、
 *     人读不出来的形状；
 *  ④ **校验器的合成负控**：②③ 的逻辑喂缺步 / 跨格 / 终点短 / 顺序错四种合成序列，都必须当场抛，
 *     连着的必须过——没有这条，②③ 就只是「把解析到的东西念一遍」。
 *
 * 取样口径：只认 `^\s*val MIGRATION_(\d+)_(\d+)\s*:\s*Migration\s*=\s*object` 这一种行首形状，
 * 所以 KDoc 里 `[MIGRATION_21_22] 的实现` 这类引用不会被误读成声明（不需要剥注释）。
 */
class AppDatabaseMigrationChainContractTest {

    private val file: File by lazy {
        val relative = "src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt"
        listOf(File(relative), File("app/$relative")).firstOrNull { it.exists() }
            ?: error("取不到 AppDatabase.kt：判据会空转")
    }

    private val source: String by lazy { file.readText() }

    /**
     * 声明形状：行首 `val MIGRATION_a_b: Migration = … object : Migration(a, b)`，
     * 并回读 `object` 那侧的两个版本号。
     *
     * 连接处一律用 `\s*`（不是 `[ \t]*`）：本文件早期那十几枚写的是
     * `val MIGRATION_1_2: Migration =` **换行**再 `object : Migration(1, 2) {`，
     * 把 pattern 收成单行空白会让判据只看得到后面那几枚——「解析面窄」的门比没有门更危险，
     * 它会把「少登记一枚」读成「那一枚根本不存在」。锚点仍留在行首的 `val`，
     * 所以 KDoc 里的 `[MIGRATION_21_22]` 引用不会误入。
     */
    private val declaration = Regex(
        """(?m)^[ \t]*val MIGRATION_([0-9]+)_([0-9]+)\s*:\s*Migration\s*=\s*"""+
            """object\s*:\s*Migration\s*\(\s*([0-9]+)\s*,\s*([0-9]+)\s*\)""",
    )

    private val declaredSteps: List<Triple<Int, Int, Int>> by lazy {
        declaration.findAll(source).map { m ->
            Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }.toList()
    }

    /** 数组形状：`val MIGRATIONS … arrayOf(…)` 里的名字，按出现顺序。 */
    private val arrayedNames: List<String> by lazy {
        val start = source.indexOf("val MIGRATIONS")
        assertTrue(start >= 0, "取不到 `val MIGRATIONS`：数组判据会空转")
        val open = source.indexOf("arrayOf(", start)
        assertTrue(open >= 0, "`MIGRATIONS` 后面没有 `arrayOf(`：判据会空转")
        var depth = 0
        var end = -1
        for (i in open until source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) { end = i; break }
                }
            }
        }
        assertTrue(end > open, "`arrayOf(` 的花括号没配上")
        Regex("""MIGRATION_[0-9]+_[0-9]+""")
            .findAll(source.substring(open, end))
            .map { it.value }
            .toList()
    }

    private val databaseVersion: Int by lazy {
        val m = Regex("""@Database\([^)]*version[ \t]*=[ \t]*([0-9]+)""", RegexOption.DOT_MATCHES_ALL)
            .find(source)
        assertTrue(m != null, "取不到 `@Database(version = …)`：终点判据会空转")
        m!!.groupValues[1].toInt()
    }

    @Test
    fun `every declared migration is registered in the MIGRATIONS array and vice versa`() {
        val declaredNames = declaredSteps.map { "MIGRATION_${it.first}_${it.second}" }.toSet()
        assertTrue(declaredNames.isNotEmpty(), "本文件一条迁移声明都没解析到：判据会空转")
        // 名字对不上版本号的那种写法（`val MIGRATION_3_4 = object : Migration(3, 5)`）单独挡一次。
        declaredSteps.forEach { (from, to, objectFrom) ->
            assertEquals(from, objectFrom, "声明名写 $from，`object : Migration(` 的第一个实参却是 $objectFrom")
            assertTrue(to > from, "MIGRATION_${from}_$to 的终点不大于起点")
        }
        assertEquals(
            declaredNames,
            arrayedNames.toSet(),
            "声明集与数组集两向都不许偏：漏登记的那一枚在真机升级时就是「A migration from x to y was " +
                "required but not found」，而 JVM 三台门禁全绿——只有整链 connected 用例才炸得出来，" +
                "所以这里必须当场挡。差集里的名字就是缺的那一步。",
        )
        assertEquals(
            arrayedNames.size,
            arrayedNames.toSet().size,
            "数组里有重复的迁移名：同一版本对登记两次不需要重复执行，别用重复项掩盖断链",
        )
    }

    @Test
    fun `the migration chain walks one version at a time from 1 to the database version`() {
        assertContiguousChain(arrayedSteps(), databaseVersion)
    }

    /**
     * 链式校验器的**合成负控**：本用例不读 `AppDatabase.kt`，只喂合成序列，
     * 证明上面那条不是一句「解析出来多少就念多少」的空转判据——
     * 缺一步、跨两格、终点短于 `@Database` 版本这三种形状都必须当场抛，连着的必须过。
     * 上一棒（Task 13 首版）的教训就是「摘了行但门是空的」：这里没有 seed，但同样要有负控。
     */
    @Test
    fun `the chain walker rejects a gap a double step and a short finish`() {
        val gapped = (1 until 10).map { it to it + 1 } + listOf(11 to 12)
        assertFailsWith<AssertionError> { assertContiguousChain(gapped, endVersion = 12) }
        assertFailsWith<AssertionError> { assertContiguousChain(listOf(1 to 2, 2 to 4), endVersion = 4) }
        assertFailsWith<AssertionError> { assertContiguousChain((1 until 5).map { it to it + 1 }, endVersion = 6) }
        // 插错位置（Room 能容忍、人读不出来）也必须挡。
        assertFailsWith<AssertionError> {
            assertContiguousChain(listOf(1 to 2, 3 to 4, 2 to 3), endVersion = 4)
        }
        assertContiguousChain((1 until 5).map { it to it + 1 }, endVersion = 5)
    }

    private fun arrayedSteps(): List<Pair<Int, Int>> = arrayedNames.map { name ->
        val m = Regex("""MIGRATION_([0-9]+)_([0-9]+)""").matchEntire(name)
            ?: error("数组里的 $name 不是 `MIGRATION_a_b` 形状")
        m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    /** 逐级衔接校验：从 1 起、按书写顺序逐级 +1、终点等于 [endVersion]。 */
    private fun assertContiguousChain(steps: List<Pair<Int, Int>>, endVersion: Int) {
        // 数组的**书写顺序**也必须逐级递增：Room 找路径靠版本号，但人读的是顺序；
        // 插错位置的登记（…27_28, 29_30, 28_29…）在这里红，而不是等真机。
        assertEquals(steps.sortedBy { it.first }, steps, "MIGRATIONS 数组不是按版本递增书写：请保持逐行相邻")
        var expected = 1
        steps.forEach { (from, to) ->
            assertEquals(expected, from, "升级链在 $expected 处断了：下一枚登记的是 $from → $to")
            assertEquals(from + 1, to, "MIGRATION_${from}_$to 一步跨了不止一个版本：链式校验按逐级写")
            expected = to
        }
        assertEquals(
            endVersion,
            expected,
            "链的终点必须等于 `@Database(version = $endVersion)`：加版本却不加迁移，就是让老用户撞不上去",
        )
    }
}
