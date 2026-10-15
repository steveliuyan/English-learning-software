package com.example.englishlearning.ui

import com.example.englishlearning.core.storage.DatabaseStartup
import com.example.englishlearning.core.storage.DatabaseStartupProbe
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.LocalProfile
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelRecoveryTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeStartup(private var reason: String?) : DatabaseStartupProbe {
        override val failureReason: String? get() = reason
        override fun retry(): Boolean {
            reason = null
            return true
        }
    }

    private class SeedRepository : com.example.englishlearning.profile.LocalProfileRepository {
        private val rows = mutableListOf<LocalProfile>()
        override suspend fun getDefault(): LocalProfile? = rows.firstOrNull()
        override suspend fun save(profile: LocalProfile) {
            rows.clear()
            rows.add(profile)
        }
    }

    @Test
    fun `database open failure shows NeedsRecovery with retry path back to Ready`() = runTest(dispatcher) {
        val repo = SeedRepository().apply {
            save(LocalProfile(id = "p1", displayName = "刘"))
        }
        val startup = FakeStartup("数据库结构迁移失败")
        val vm = AppViewModel(repo, CreateLocalProfileUseCase(repo, clock), startup)
        advanceUntilIdle()

        assertEquals(AppUiState.NeedsRecovery("数据库结构迁移失败"), vm.uiState.value)

        // 生产「重试」入口：probe.retry() 重开成功后 reload → Ready。
        vm.retryDatabaseStartup()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is AppUiState.Ready, "清掉启动失败后 reload 应回 Ready：${vm.uiState.value}")
    }

    @Test
    fun `healthy startup never enters NeedsRecovery`() = runTest(dispatcher) {
        val repo = SeedRepository()
        val vm = AppViewModel(repo, CreateLocalProfileUseCase(repo, clock), FakeStartup(null))
        advanceUntilIdle()
        assertTrue(vm.uiState.value is AppUiState.NeedsProfile, "无启动失败时状态机保持原语义：${vm.uiState.value}")
    }

    @Test
    fun `reason summary is one of the fixed safe phrases without path or stack`() {
        val messy = IllegalStateException(
            "couldn't migrate /data/user/0/com.example.englishlearning/databases/english-learning.db " +
                "at com.example.englishlearning.core.storage.AppDatabase.migrate(AppDatabase.kt:420)",
        )
        val reason = DatabaseStartup.reasonSummary(messy)
        assertFalse(reason.contains("/data/"), "原因摘要不得含文件路径")
        assertFalse(reason.contains(".kt:") || reason.contains("at com."), "原因摘要不得含堆栈")
        assertTrue(reason.isBlank() || reason.length <= 40, "摘要应是短句：$reason")
    }
}
