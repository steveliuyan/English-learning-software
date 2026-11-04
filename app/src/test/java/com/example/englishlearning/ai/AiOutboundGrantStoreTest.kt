package com.example.englishlearning.ai

import com.example.englishlearning.ui.stripComments
import java.io.File
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 会话级文本出站确认台账（决策 2026-11-05 D-2）的契约。
 *
 * 这枚台账要挡住的事故只有一类：**「按域名确认一次」被写成「落盘一次」**。
 * 一旦确认状态进了 Room、SharedPreferences、备份包或日志，用户在设置页换了 Endpoint、
 * 换了密钥、甚至恢复了一次备份之后，出站确认会继续静默豁免真实请求——而确认框里那句
 * 「文本和密钥会发送给该服务」的承诺，只在那一次点击时成立过。
 * 因此本类的断言分两面：台账**该有什么**（累积、共享、幂等），以及台账**不能是什么**
 * （可被调用方改写、可被持久化、可以多实例）。
 */
class AiOutboundGrantStoreTest {
    @Test
    fun aFreshSessionHasConfirmedNothing() {
        val store = AiOutboundGrantStore()

        assertEquals(emptySet(), store.confirmedHosts(), "进程内台账必须空着开始：跨进程、跨重启都不延续")
    }

    @Test
    fun confirmingAHostMakesItTheOnlyEntry() {
        val store = AiOutboundGrantStore()

        store.confirm("api.example.com")

        assertEquals(setOf("api.example.com"), store.confirmedHosts())
    }

    @Test
    fun confirmingTheSameHostTwiceDoesNotGrowTheLedger() {
        val store = AiOutboundGrantStore()

        store.confirm("api.example.com")
        store.confirm("api.example.com")

        assertEquals(setOf("api.example.com"), store.confirmedHosts(), "重复确认要幂等：同一域名只可能是一条记录")
    }

    @Test
    fun hostsConfirmedFromDifferentScreensAccumulateInOneLedger() {
        // 查词问答确认过 A 域，长难句分析在同一个会话里就不该再问 A 域——两屏共用的是**同一份**集合，
        // 不是各存一份再取并集。台账本身必须允许累积，否则第二屏永远拿不到第一屏的那条。
        val store = AiOutboundGrantStore()

        store.confirm("api.example.com")
        store.confirm("other.example.com")

        assertEquals(setOf("api.example.com", "other.example.com"), store.confirmedHosts())
    }

    @Test
    fun clearDropsEveryHost() {
        val store = AiOutboundGrantStore()
        listOf("a.test", "b.test").forEach(store::confirm)

        store.clear()

        assertEquals(emptySet(), store.confirmedHosts(), "清账后必须回到空集：拒绝/退出这条兜底路径不能有残留")
    }

    @Test
    fun theReturnedViewIsASnapshotNotTheLedgerItself() {
        // 关键的一条：如果 confirmedHosts() 把背后的可变集合原样递出去，任何一屏都能
        // `add("没确认过的域名")` 然后拿着它免确认出站——「用户点过确认」这件事就此由代码代填。
        // 判据不依赖具体实现是「只读包装」还是「可变副本」（两者都合格），只看台账本体有没有被握住：
        // 台账变了、先前取出去的那份跟着变，说明递出去的就是本体。
        val store = AiOutboundGrantStore()
        store.confirm("api.example.com")

        val view = store.confirmedHosts()
        store.confirm("other.example.com")

        assertEquals(setOf("api.example.com"), view, "取出去的是快照；跟着台账一起变=调用方握住了台账本体")
    }

    @Test
    fun mutatingTheReturnedViewCannotReachTheLedger() {
        val store = AiOutboundGrantStore()
        store.confirm("api.example.com")
        val view = store.confirmedHosts()

        // 只读视图会当场抛 UnsupportedOperationException，可变副本则改中了副本——两种都合格，
        // 唯一不合格的是改完台账也变了。用 runCatching 把两条合格路径都接住，只断言不变量。
        runCatching {
            @Suppress("UNCHECKED_CAST")
            (view as MutableSet<String>).add("evil.test")
        }

        assertEquals(setOf("api.example.com"), store.confirmedHosts(), "台账只能由 confirm() 写入，读出去的那份不算")
    }

    @Test
    fun concurrentConfirmsAllLandWithoutCorruptingTheLedger() {
        // 两屏各自跑在自己的 viewModelScope 上；主线程之外还可能有回调线程。
        // 非线程安全的 HashSet 在这里会丢更新，甚至抛异常——丢更新的表现正是「刚确认过又问一遍」。
        val store = AiOutboundGrantStore()
        val threads = (0 until 4).map { shard ->
            Thread {
                (0 until 50).forEach { index -> store.confirm("host-$shard-$index.test") }
            }.apply { start() }
        }
        threads.forEach { it.join() }

        assertEquals(200, store.confirmedHosts().size, "并发确认必须全部落账：丢一条就等于白确认一次")
        assertTrue("host-0-0.test" in store.confirmedHosts())
    }

    @Test
    fun blankHostIsRejectedAsProgrammingContract() {
        val store = AiOutboundGrantStore()

        assertFailsWith<IllegalArgumentException> { store.confirm("  ") }
        assertEquals(emptySet(), store.confirmedHosts(), "空域名绝不入账：入账即免确认出站")
    }

    @Test
    fun theLedgerCarriesNoPersistenceHookOfAnyKind() {
        // 不落盘是**结构**上成立，不是靠注释承诺：这枚类拿不到 Context，也就拿不到
        // SharedPreferences / 文件 / Room / Keystore。将来有人要「让确认跨重启保留」，
        // 必须先往构造函数加 Context——本条当场判红，逼他回到决策文档改口径。
        val source = stripComments(mainSource("ai/AiOutboundGrantStore.kt"))

        listOf(
            "Context",
            "SharedPreferences",
            "DataStore",
            "AppDatabase",
            "Room",
            "SecretStore",
            "File(",
            "java.io",
            "androidx",
        ).forEach { forbidden ->
            assertFalse(
                source.contains(forbidden),
                "台账里出现「$forbidden」= 确认状态有落盘通道，违反决策 2026-11-05 D-2：$source",
            )
        }
        assertTrue(
            Regex("""fun\s+confirmedHosts\s*\(\s*\)\s*:\s*Set<String>""").containsMatchIn(source),
            "读接口必须只暴露只读的 Set<String>；交出可变集合会让上一条防御形同虚设。",
        )
    }

    @Test
    fun theStoreIsProvidedAsASingletonSoTheTwoScreensReallyShareIt() {
        // 「跨屏共享」是用户拍板的一半（决策 D-1：查词问答 + 长难句分析共用一份台账）。
        // 只要 provider 漏了 @Singleton，Hilt 就按注入点各造一份，两屏各自忘记对方的确认，
        // 而 JVM 侧的构造注入测试照样绿——所以这条只能钉在 DI 源码上。
        val module = stripComments(mainSource("di/AppModule.kt"))
        val lines = module.lines()
        val providerIndexes = lines.indices.filter { lines[it].contains("fun provideAiOutboundGrantStore") }

        assertEquals(1, providerIndexes.size, "AppModule 里必须恰好一个 AiOutboundGrantStore 的 provider")
        // 本仓 provider 有两种写法：注解各占一行（多行 fun），或注解与 fun 同行（单行写法）。
        // 因此取「声明行 + 其上方连续的注解行」整段，而不是只看相邻一行。
        val declarationLine = lines[providerIndexes.single()]
        val annotationBlock = lines.take(providerIndexes.single()).takeLastWhile { it.trimStart().startsWith("@") }
            .joinToString("\n")
        val sameLineAnnotations = declarationLine.substringBefore("fun ")
        val annotations = "$annotationBlock\n$sameLineAnnotations"

        assertTrue(
            annotations.contains("@Singleton"),
            "provideAiOutboundGrantStore 必须是 @Singleton，否则两屏不共享同一份确认台账：$annotations",
        )
        assertTrue(
            annotations.contains("@Provides"),
            "provideAiOutboundGrantStore 必须走 @Provides（本仓 DI 口径）：$annotations",
        )
    }

    private fun mainSource(relative: String): String {
        val file = File("src/main/java/com/example/englishlearning").resolve(relative)
        assertTrue(file.isFile, "源码契约扫描缺失：${file.path}（JVM 工作目录 ${File(".").absolutePath}）")
        return file.readText()
    }
}
