package com.example.englishlearning.ui

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.LearningRecordRepository
import com.example.englishlearning.learning.LearningRecord
import com.example.englishlearning.learning.LearningHistoryGroup
import com.example.englishlearning.learning.LearningProfileRepositoryError
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import com.example.englishlearning.learning.domain.WordCard
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WordBookWordsViewModelTest {
    @Test
    fun `groups words by first letter case-insensitively with a trailing hash bucket`() {
        val groups = groupWordBookWordsByLetter(
            listOf(
                word("book:banana", "Banana"),
                word("book:apple", "apple"),
                word("book:avocado", "Avocado"),
                word("book:zebra", "zebra"),
                word("book:苹果", "苹果"),
            ),
        )
        assertEquals(listOf("A", "B", "Z", "#"), groups.map { it.letter })
        assertEquals(listOf("apple", "Avocado"), groups.first().records.map { it.displayLemma })
        assertEquals(listOf("Banana"), groups[1].records.map { it.displayLemma })
        assertEquals("苹果", groups.last().records.single().displayLemma)
    }

    @Test
    fun `letter buckets fold case and send non-letters to hash`() {
        assertEquals("A", letterBucketOf("apple"))
        assertEquals("A", letterBucketOf("Apple"))
        assertEquals("Z", letterBucketOf("  zebra "))
        assertEquals("#", letterBucketOf("苹果"))
        assertEquals("#", letterBucketOf("3rd"))
        assertEquals("#", letterBucketOf(""))
    }

    @Test
    fun `load fetches all words for the active word book and re-entry refreshes`() = runTest {
        val repository = FakeLearningRecordRepository()
        repository.allWordsResult = listOf(word("book:a", "apple", WordBookRecordStatus.Unlearned))
        val vm = WordBookWordsViewModel(repository, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load("book")
        testScheduler.advanceUntilIdle()
        assertEquals(1, repository.allWordsCalls)
        assertEquals("apple", vm.state.value.words.single().displayLemma)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.error)
        vm.load("book")
        testScheduler.advanceUntilIdle()
        assertEquals(2, repository.allWordsCalls)
    }

    @Test
    fun `filter narrows groups locally without reloading`() = runTest {
        val repository = FakeLearningRecordRepository()
        repository.allWordsResult = listOf(
            word("book:a", "apple", WordBookRecordStatus.Unlearned),
            word("book:b", "banana", WordBookRecordStatus.Due),
        )
        val vm = WordBookWordsViewModel(repository, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load("book")
        testScheduler.advanceUntilIdle()
        vm.selectFilter(WordBookRecordStatus.Due)
        assertEquals(1, repository.allWordsCalls)
        assertEquals(listOf("B"), vm.state.value.groups.map { it.letter })
        assertEquals("banana", vm.state.value.groups.single().records.single().displayLemma)
        vm.selectFilter(null)
        assertEquals(2, vm.state.value.groups.size)
        assertEquals(1, repository.allWordsCalls)
    }

    @Test
    fun `failed load reports an error and retry calls the repository again`() = runTest {
        val repository = FakeLearningRecordRepository()
        repository.allWordsResults += RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
        repository.allWordsResults += RepositoryResult.Success(emptyList())
        val vm = WordBookWordsViewModel(repository, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load("book")
        testScheduler.advanceUntilIdle()
        assertEquals("词书单词暂时无法读取", vm.state.value.error)
        assertTrue(vm.state.value.words.isEmpty())
        vm.retry()
        testScheduler.advanceUntilIdle()
        assertEquals(2, repository.allWordsCalls)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `null word book id yields an empty idle state without touching the repository`() = runTest {
        val repository = FakeLearningRecordRepository()
        val vm = WordBookWordsViewModel(repository, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load(null)
        testScheduler.advanceUntilIdle()
        assertEquals(0, repository.allWordsCalls)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.groups.isEmpty())
    }

    /**
     * Task 20-B 修复轮 1 · F1 ② 前提锚（JVM 源码契约，本仓口径：剥注释后 `SourceRatchet.countMatches`
     * + 行首/精确串锚；该屏无契约类，按派工单挂最贴近的既有类）。
     *
     * 修复轮 1 的本屏写法（`gloss = 释义 ?: 原因串`）依赖「两槽互斥」：禁用 ⇒ `card == null` ⇒
     * 释义侧按构造恒为 `null`，`?:` 才落到原因串。互斥前提的生产真值有两枚：
     * `WordBookRecord.canOpenDetail` 的字段默认值仍是 `card != null`；
     * `RoomLearningRecordRepository.allWords` 唯一生产构造站点仍显式传 `card != null`。
     * 前提哪天放开（例如允许 `card != null` 却 `canOpenDetail == false`），这条当场红并提示改屏侧写法——
     * 那种形态下原因串会被释义吃掉（新的信息丢失）。connected 夹具一次构造好两槽，测不出前提漂移，
     * 只有这条源码锚能挡。
     */
    @Test
    fun `can open detail stays derived from card presence at the default and the production construction site`() {
        val recordSource = learningMainSource("LearningRecordRepository.kt").readText()
        assertEquals(
            1,
            countMatches(recordSource, Regex("""(?m)^\s*val canOpenDetail: Boolean = card != null,$""")),
            "learning/LearningRecordRepository.kt 里 WordBookRecord 的字段默认必须逐字仍是 `= card != null`（行首锚）：" +
                "默认一旦放开，「禁用即无释义」不再按构造成立，WordBookWordsScreen 走 gloss 槽的原因串会被释义吃掉",
        )
        val repositorySource = learningMainSource("RoomLearningRecordRepository.kt").readText()
        assertEquals(
            1,
            countMatches(repositorySource, Regex("""\bWordBookRecord\(""")),
            "learning/RoomLearningRecordRepository.kt 里 WordBookRecord 构造站点应恰一处（allWords 的唯一生产来源）：" +
                "长出第二处构造站点时本锚不再覆盖全部生产数据，先扩锚再谈改屏侧写法",
        )
        assertEquals(
            1,
            countMatches(repositorySource, Regex("""(?m)^\s*WordBookRecord\(.*card != null\)\s*$""")),
            "生产构造站点仍须把 `card != null` 逐字传在 canOpenDetail 位（行首 + 行尾锚，现盘写法 `…, card != null)`）：" +
                "换成常量或改名参数即「两槽互斥」前提失效，屏侧 `gloss = 释义 ?: 原因串` 必须一起改",
        )
        assertEquals(
            0,
            countMatches(repositorySource, Regex("""canOpenDetail""")),
            "构造站点不得改用命名参数 `canOpenDetail = …` 覆盖上面那条位置推导（现盘该串在本文件 0 处）",
        )
        assertEquals(
            1,
            countMatches(uiScreenSource("WordBookWordsScreen.kt"), Regex("""val enabled = row\.canOpenDetail""")),
            "本屏的 enabled 只许读 `row.canOpenDetail`（互斥链第三环：原因串侧与释义侧由它决定）",
        )
    }

    /** 读 `learning/` 包 main 源码：app 模块目录与仓库根两种 JVM 工作目录都试，读不到响亮失败（防锚空转）。 */
    private fun learningMainSource(name: String): File =
        listOf(
            File("src/main/java/com/example/englishlearning/learning/$name"),
            File("app/src/main/java/com/example/englishlearning/learning/$name"),
        ).firstOrNull(File::isFile)
            ?: error("词书记录前提锚读不到 learning/$name（相对 ${File(".").absolutePath}）：工作目录漂移或文件搬家，本锚会假绿，必须修路径")

    private fun word(
        cardId: String,
        lemma: String,
        status: WordBookRecordStatus = WordBookRecordStatus.Unlearned,
    ): WordBookRecord {
        val card = WordCard(cardId, "book", lemma, "", "", "释义")
        return WordBookRecord(card, cardId, lemma, status)
    }

    private class FakeLearningRecordRepository : LearningRecordRepository {
        var todayCalls = 0
        var historyCalls = 0
        var allWordsCalls = 0
        var allWordsResult: List<WordBookRecord> = emptyList()
        val allWordsResults = ArrayDeque<RepositoryResult<List<WordBookRecord>>>()
        override suspend fun today(profileId: String, planId: String): RepositoryResult<List<LearningRecord>> {
            todayCalls++
            return RepositoryResult.Success(emptyList())
        }
        override suspend fun history(profileId: String): RepositoryResult<List<LearningHistoryGroup>> {
            historyCalls++
            return RepositoryResult.Success(emptyList())
        }
        override suspend fun allWords(wordBookId: String, now: Instant): RepositoryResult<List<WordBookRecord>> {
            allWordsCalls++
            allWordsResults.removeFirstOrNull()?.let { return it }
            return RepositoryResult.Success(allWordsResult)
        }
    }
}
