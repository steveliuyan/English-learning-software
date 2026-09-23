package com.example.englishlearning.ui

import com.example.englishlearning.ai.AiProfileIdFactory
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.storage.AppErrorException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AI 配置界面的行为契约。
 *
 * 这里守的是四条容易做错、错了又很难在真机上发现的事：
 * ① **元数据先落、密钥后落**：顺序反了会在元数据保存失败时留下谁也管不着的孤儿密钥；
 * ② **删除时先清密钥、再删元数据**：顺序反了会留下「配置已删、密钥还在」；
 * ③ **编辑时不回显已保存的密钥**，密钥只在内存里待一瞬间，保存后立刻从界面状态里清除；
 * ④ **密钥槽按 Profile 隔离**，改了一套路配置不会动到另一套。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiProfileSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /** 记录仓储与密钥库的调用顺序，用来断言「谁先谁后」。 */
    private val ops = mutableListOf<String>()
    private val secrets = FakeSecretStore(ops)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadListsSavedProfilesWithTheirKeyState() = runTest(dispatcher) {
        secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias] = "sk-secret-value"
        val viewModel = viewModel(repository(initial = listOf(profile("p1"), profile("p2"))))

        viewModel.load()
        advanceUntilIdle()

        val items = (viewModel.listState.value as AiProfileListUiState.Ready).items
        assertEquals(listOf("p1", "p2"), items.map { it.profile.profileId })
        assertEquals(listOf(true, false), items.map { it.hasKey })
    }

    @Test
    fun loadReportsUnavailableWhenTheLocalStoreFails() = runTest(dispatcher) {
        val viewModel = viewModel(repository(failList = true))

        viewModel.load()
        advanceUntilIdle()

        assertEquals(AiProfileListUiState.Unavailable, viewModel.listState.value)
    }

    @Test
    fun startCreateOpensAnEmptyDraftWithTheDocumentedDefaults() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.startCreate()

        val editor = viewModel.editor.value!!
        assertNull(editor.profileId, "新建时不该有 profileId")
        assertEquals("", editor.draft.displayName)
        assertEquals("", editor.draft.pendingKey)
        assertEquals(setOf(AiCapability.Text), editor.draft.capabilities)
        val defaults = AiAdvancedParameters()
        assertEquals(defaults.temperature.toString(), editor.draft.temperature)
        assertEquals(defaults.topP.toString(), editor.draft.topP)
        assertEquals(defaults.maxTokens.toString(), editor.draft.maxTokens)
        assertEquals(defaults.timeoutSeconds.toString(), editor.draft.timeoutSeconds)
        assertFalse(editor.hasStoredKey)
    }

    @Test
    fun startEditPrefillsMetadataAndNeverPrefillsTheStoredKey() = runTest(dispatcher) {
        secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias] = "sk-secret-value"
        val viewModel = viewModel(repository(initial = listOf(profile("p1"))))
        viewModel.load()
        advanceUntilIdle()

        viewModel.startEdit("p1")
        advanceUntilIdle()

        val editor = viewModel.editor.value!!
        assertEquals("p1", editor.profileId)
        assertEquals("示例服务 p1", editor.draft.displayName)
        assertEquals("https://api.example.com/v1", editor.draft.endpoint)
        assertEquals("gpt-4o-mini", editor.draft.model)
        assertTrue(editor.hasStoredKey)
        assertEquals("", editor.draft.pendingKey, "已保存的密钥绝不能回显到界面上")
        assertFalse(editor.toString().contains("sk-secret-value"), "界面状态里不该出现密钥原文")
    }

    @Test
    fun startEditOnAMissingProfileOpensNothing() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.startEdit("does-not-exist")
        advanceUntilIdle()

        assertNull(viewModel.editor.value)
    }

    @Test
    fun saveRejectsABlankNameWithoutTouchingTheStore() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = viewModel(repository)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft().copy(displayName = "   "))

        viewModel.save()
        advanceUntilIdle()

        assertEquals(AiProfileFieldError.NameRequired, viewModel.editor.value!!.fieldError)
        assertTrue(repository.saved.isEmpty(), "校验没过就不该落库")
        assertTrue(secrets.entries.isEmpty())
    }

    @Test
    fun saveRejectsAnEndpointTheOutboundPolicyWouldRefuse() = runTest(dispatcher) {
        listOf(
            "http://api.example.com/v1",
            "https://127.0.0.1/v1",
            "https://user:pw@api.example.com/v1",
        ).forEach { bad ->
            val repository = repository()
            val viewModel = viewModel(repository)
            viewModel.startCreate()
            viewModel.updateDraft(validDraft().copy(endpoint = bad))

            viewModel.save()
            advanceUntilIdle()

            assertEquals(AiProfileFieldError.EndpointInvalid, viewModel.editor.value!!.fieldError, "「$bad」必须被拒")
            assertTrue(repository.saved.isEmpty())
        }
    }

    @Test
    fun saveRejectsAMissingModelOrCapability() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.startCreate()

        viewModel.updateDraft(validDraft().copy(model = ""))
        viewModel.save()
        advanceUntilIdle()
        assertEquals(AiProfileFieldError.ModelRequired, viewModel.editor.value!!.fieldError)

        viewModel.updateDraft(validDraft().copy(capabilities = emptySet()))
        viewModel.save()
        advanceUntilIdle()
        assertEquals(AiProfileFieldError.CapabilityRequired, viewModel.editor.value!!.fieldError)
    }

    @Test
    fun saveRejectsAdvancedParametersThatAreNotNumbersOrAreOutOfRange() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.startCreate()

        viewModel.updateDraft(validDraft().copy(temperature = "热一点"))
        viewModel.save()
        advanceUntilIdle()
        assertEquals(AiProfileFieldError.ParameterNotNumeric, viewModel.editor.value!!.fieldError)

        viewModel.updateDraft(validDraft().copy(maxTokens = "4097"))
        viewModel.save()
        advanceUntilIdle()
        assertEquals(AiProfileFieldError.ParameterOutOfRange, viewModel.editor.value!!.fieldError)
    }

    @Test
    fun savingANewProfileStoresMetadataFirstAndThenTheKey() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = viewModel(repository)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft())

        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf("ai-test-1"), repository.saved.map { it.profileId })
        assertEquals(
            listOf("profile-save:ai-test-1", "secret-save:ai-profile-ai-test-1"),
            writeOps(),
            "必须先落元数据再落密钥：反过来一旦元数据失败就会留下孤儿密钥",
        )
        val stored = repository.stored.getValue("ai-test-1")
        assertEquals("https://api.example.com/v1", stored.endpoint)
        assertEquals(AiProfileSecretUseCase.referenceFor("ai-test-1"), stored.secretReference)
        assertEquals(
            AiProfileListUiState.Ready(listOf(AiProfileListItem(stored, hasKey = true))),
            viewModel.listState.value,
        )
        assertNull(viewModel.editor.value, "保存成功后编辑页应当关闭")
        assertFalse(viewModel.listState.value.toString().contains("sk-test-key"), "列表状态里不该出现密钥原文")
    }

    @Test
    fun aFailedMetadataSaveLeavesNoKeyBehind() = runTest(dispatcher) {
        val viewModel = viewModel(repository(failSave = true))
        viewModel.startCreate()
        viewModel.updateDraft(validDraft())

        viewModel.save()
        advanceUntilIdle()

        assertTrue(secrets.entries.isEmpty(), "元数据没存成，就不该把密钥写进去")
        assertEquals(listOf("profile-save:ai-test-1"), writeOps())
        val editor = viewModel.editor.value!!
        assertNull(editor.fieldError, "这不是填写错误，不该标成字段错误")
        assertTrue(editor.message!!.isNotBlank(), "保存失败必须给出可读提示")
        assertFalse(editor.saving, "失败后要退出保存中状态，否则按钮会一直是灰的")
    }

    @Test
    fun editingWithoutTypingANewKeyKeepsTheStoredOne() = runTest(dispatcher) {
        secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias] = "sk-old"
        val repository = repository(initial = listOf(profile("p1")))
        val viewModel = viewModel(repository)
        viewModel.load()
        advanceUntilIdle()
        viewModel.startEdit("p1")
        advanceUntilIdle()
        ops.clear()

        viewModel.updateDraft(viewModel.editor.value!!.draft.copy(model = "gpt-4o"))
        viewModel.save()
        advanceUntilIdle()

        assertEquals("sk-old", secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias])
        assertEquals(listOf("profile-save:p1"), writeOps(), "没输入新密钥就不该动密钥槽")
        assertEquals("gpt-4o", repository.stored.getValue("p1").model)
    }

    @Test
    fun replacingAKeyOnlyRewritesThatProfilesSlot() = runTest(dispatcher) {
        secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias] = "sk-old"
        secrets.entries[AiProfileSecretUseCase.referenceFor("p2").alias] = "sk-other"
        val viewModel = viewModel(repository(initial = listOf(profile("p1"), profile("p2"))))
        viewModel.load()
        advanceUntilIdle()
        viewModel.startEdit("p1")
        advanceUntilIdle()

        viewModel.updateDraft(viewModel.editor.value!!.draft.copy(pendingKey = "sk-new"))
        viewModel.save()
        advanceUntilIdle()

        assertEquals("sk-new", secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias])
        assertEquals("sk-other", secrets.entries[AiProfileSecretUseCase.referenceFor("p2").alias])
    }

    @Test
    fun deletingAProfileClearsItsKeyBeforeItsMetadata() = runTest(dispatcher) {
        secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias] = "sk-old"
        val repository = repository(initial = listOf(profile("p1")))
        val viewModel = viewModel(repository)
        viewModel.load()
        advanceUntilIdle()
        ops.clear()

        viewModel.deleteProfile("p1")
        advanceUntilIdle()

        assertEquals(listOf("secret-delete:ai-profile-p1", "profile-delete:p1"), writeOps())
        assertFalse(secrets.entries.containsKey(AiProfileSecretUseCase.referenceFor("p1").alias))
        assertEquals(AiProfileListUiState.Ready(emptyList()), viewModel.listState.value)
    }

    @Test
    fun deletingAKeyKeepsTheProfileAndReportsItAsUnset() = runTest(dispatcher) {
        secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias] = "sk-old"
        val repository = repository(initial = listOf(profile("p1")))
        val viewModel = viewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.deleteKey("p1")
        advanceUntilIdle()

        assertFalse((viewModel.listState.value as AiProfileListUiState.Ready).items.single().hasKey)
        assertTrue(repository.stored.containsKey("p1"), "删密钥不该顺手把配置也删了")
    }

    @Test
    fun closeEditorDropsTheDraftIncludingAnyTypedKey() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.startCreate()
        viewModel.updateDraft(validDraft())

        viewModel.closeEditor()

        assertNull(viewModel.editor.value)
    }

    /**
     * 只保留**会改状态**的调用，并按发生顺序返回。
     *
     * 读操作（`profile-list` / `profile-find` / `secret-has`）会在保存与删除之后顺带发生，
     * 但顺序契约只关于写入；把它们滤掉，断言才能精确表达「谁先谁后」而不是「谁在附近」。
     */
    private fun writeOps(): List<String> = ops.filter {
        it.startsWith("profile-save:") ||
            it.startsWith("profile-delete:") ||
            it.startsWith("secret-save:") ||
            it.startsWith("secret-delete:")
    }

    private fun viewModel(
        repository: AiProfileRepository = repository(),
        ids: AiProfileIdFactory = AiProfileIdFactory { "ai-test-${ops.count { it.startsWith("profile-save:") } + 1}" },
    ) = AiProfileSettingsViewModel(repository, AiProfileSecretUseCase(secrets), ids)

    private fun repository(
        initial: List<AiProfile> = emptyList(),
        failList: Boolean = false,
        failSave: Boolean = false,
    ) = FakeAiProfileRepository(ops, initial, failList, failSave)

    private fun validDraft() = AiProfileDraft(
        displayName = "我的服务",
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "gpt-4o-mini",
        capabilities = setOf(AiCapability.Text),
        pendingKey = "sk-test-key",
    )

    private fun profile(id: String) = AiProfile(
        profileId = id,
        displayName = "示例服务 $id",
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "gpt-4o-mini",
        capabilities = setOf(AiCapability.Text),
        secretReference = AiProfileSecretUseCase.referenceFor(id),
    )
}

private class FakeAiProfileRepository(
    private val ops: MutableList<String>,
    initial: List<AiProfile> = emptyList(),
    private val failList: Boolean = false,
    private val failSave: Boolean = false,
) : AiProfileRepository {
    val stored: MutableMap<String, AiProfile> = initial.associateBy { it.profileId }.toMutableMap()
    val saved: MutableList<AiProfile> = mutableListOf()

    override suspend fun list(): Result<List<AiProfile>> {
        ops += "profile-list"
        return if (failList) {
            Result.failure(AppErrorException(AppError.StorageUnavailable))
        } else {
            Result.success(stored.values.sortedBy { it.profileId })
        }
    }

    override suspend fun find(profileId: String): Result<AiProfile?> {
        ops += "profile-find:$profileId"
        return Result.success(stored[profileId])
    }

    override suspend fun save(profile: AiProfile): Result<Unit> {
        ops += "profile-save:${profile.profileId}"
        if (failSave) return Result.failure(AppErrorException(AppError.StorageUnavailable))
        stored[profile.profileId] = profile
        saved += profile
        return Result.success(Unit)
    }

    override suspend fun delete(profileId: String): Result<Unit> {
        ops += "profile-delete:$profileId"
        stored.remove(profileId)
        return Result.success(Unit)
    }
}

private class FakeSecretStore(private val ops: MutableList<String>) : SecretStore {
    val entries: MutableMap<String, String> = mutableMapOf()

    override fun save(reference: SecretReference, secret: CharArray): Result<Unit> {
        ops += "secret-save:${reference.alias}"
        entries[reference.alias] = String(secret)
        return Result.success(Unit)
    }

    override fun delete(reference: SecretReference): Result<Unit> {
        ops += "secret-delete:${reference.alias}"
        entries.remove(reference.alias)
        return Result.success(Unit)
    }

    override fun has(reference: SecretReference): Result<Boolean> {
        ops += "secret-has:${reference.alias}"
        return Result.success(entries.containsKey(reference.alias))
    }
}
