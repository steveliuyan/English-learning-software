package com.example.englishlearning.ui

import com.example.englishlearning.ai.AiConnectionProbe
import com.example.englishlearning.ai.AiProfileIdFactory
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.AiPreferenceRepository
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpResponse
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiPreference
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.storage.AppErrorException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    private val preferences = FakeAiPreferenceRepository()

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
    fun loadDerivesTheDefaultTextProfileAndEligibilityFromStoredPreferenceAndKeyState() = runTest(dispatcher) {
        secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias] = "sk-secret-value"
        preferences.value = AiPreference(defaultTextProfileId = "p1")
        val viewModel = viewModel(repository(initial = listOf(profile("p1"), profile("p2"))))

        viewModel.load()
        advanceUntilIdle()

        val items = (viewModel.listState.value as AiProfileListUiState.Ready).items
        assertEquals(true, items.first { it.profile.profileId == "p1" }.isDefaultTextProfile)
        assertEquals(true, items.first { it.profile.profileId == "p1" }.canBeDefaultTextProfile)
        assertEquals(false, items.first { it.profile.profileId == "p2" }.isDefaultTextProfile)
        assertEquals(false, items.first { it.profile.profileId == "p2" }.canBeDefaultTextProfile)
    }

    @Test
    fun setDefaultTextProfilePersistsOnlyAQualifiedTextProfile() = runTest(dispatcher) {
        secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias] = "sk-secret-value"
        val viewModel = viewModel(repository(initial = listOf(profile("p1"))))

        viewModel.setDefaultTextProfile("p1")
        advanceUntilIdle()

        assertEquals("p1", preferences.value.defaultTextProfileId)
        assertTrue((viewModel.listState.value as AiProfileListUiState.Ready).items.single().isDefaultTextProfile)
    }

    @Test
    fun setDefaultTextProfileRejectsAProfileWithoutTextOrKey() = runTest(dispatcher) {
        val speech = profile("speech").copy(capabilities = setOf(AiCapability.Speech))
        val repository = repository(initial = listOf(profile("p1"), speech))
        val viewModel = viewModel(repository)

        viewModel.setDefaultTextProfile("p1")
        advanceUntilIdle()
        assertNull(preferences.value.defaultTextProfileId)
        assertFalse((viewModel.listState.value as AiProfileListUiState.Ready).items.first { it.profile.profileId == "p1" }.isDefaultTextProfile)

        secrets.entries[AiProfileSecretUseCase.referenceFor("speech").alias] = "sk-speech"
        viewModel.setDefaultTextProfile("speech")
        advanceUntilIdle()
        assertNull(preferences.value.defaultTextProfileId)
    }

    @Test
    fun setDefaultImageProfilePersistsOnlyAQualifiedImageProfile() = runTest(dispatcher) {
        val image = profile("p-img").copy(capabilities = setOf(AiCapability.ImageGeneration))
        secrets.entries[AiProfileSecretUseCase.referenceFor("p-img").alias] = "sk-img"
        val viewModel = viewModel(repository(initial = listOf(image)))

        viewModel.setDefaultImageProfile("p-img")
        advanceUntilIdle()

        assertEquals("p-img", preferences.value.defaultImageProfileId)
        assertTrue((viewModel.listState.value as AiProfileListUiState.Ready).items.single().isDefaultImageProfile)
    }

    @Test
    fun setDefaultImageProfileRejectsAProfileWithoutImageGenerationOrKey() = runTest(dispatcher) {
        val textOnly = profile("p-text")
        val viewModel = viewModel(repository(initial = listOf(textOnly)))

        viewModel.setDefaultImageProfile("p-text")
        advanceUntilIdle()

        assertNull(preferences.value.defaultImageProfileId)
        assertFalse((viewModel.listState.value as AiProfileListUiState.Ready).items.single().isDefaultImageProfile)
    }

    @Test
    fun settingTheImageDefaultPreservesTheTextDefault() = runTest(dispatcher) {
        val text = profile("p-text")
        secrets.entries[AiProfileSecretUseCase.referenceFor("p-text").alias] = "sk-text"
        preferences.value = AiPreference(defaultTextProfileId = "p-text")
        val image = profile("p-img").copy(capabilities = setOf(AiCapability.ImageGeneration))
        secrets.entries[AiProfileSecretUseCase.referenceFor("p-img").alias] = "sk-img"
        val viewModel = viewModel(repository(initial = listOf(text, image)))

        viewModel.setDefaultImageProfile("p-img")
        advanceUntilIdle()

        assertEquals("p-text", preferences.value.defaultTextProfileId)
        assertEquals("p-img", preferences.value.defaultImageProfileId)
    }

    @Test
    fun settingTheTextDefaultPreservesTheImageDefault() = runTest(dispatcher) {
        preferences.value = AiPreference(defaultImageProfileId = "p-img")
        val image = profile("p-img").copy(capabilities = setOf(AiCapability.ImageGeneration))
        secrets.entries[AiProfileSecretUseCase.referenceFor("p-img").alias] = "sk-img"
        val text = profile("p-text")
        secrets.entries[AiProfileSecretUseCase.referenceFor("p-text").alias] = "sk-text"
        val viewModel = viewModel(repository(initial = listOf(image, text)))

        viewModel.setDefaultTextProfile("p-text")
        advanceUntilIdle()

        assertEquals("p-img", preferences.value.defaultImageProfileId)
        assertEquals("p-text", preferences.value.defaultTextProfileId)
    }

    @Test
    fun deletingTheImageDefaultProfileClearsOnlyTheImageField() = runTest(dispatcher) {
        val text = profile("p-text")
        secrets.entries[AiProfileSecretUseCase.referenceFor("p-text").alias] = "sk-text"
        preferences.value = AiPreference(defaultTextProfileId = "p-text", defaultImageProfileId = "p-img")
        val image = profile("p-img").copy(capabilities = setOf(AiCapability.ImageGeneration))
        secrets.entries[AiProfileSecretUseCase.referenceFor("p-img").alias] = "sk-img"
        val viewModel = viewModel(repository(initial = listOf(text, image)))

        viewModel.deleteProfile("p-img")
        advanceUntilIdle()

        assertEquals("p-text", preferences.value.defaultTextProfileId)
        assertNull(preferences.value.defaultImageProfileId)
    }

    @Test
    fun deletingTheDefaultKeyClearsTheDevicePreference() = runTest(dispatcher) {
        secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias] = "sk-old"
        preferences.value = AiPreference(defaultTextProfileId = "p1")
        val viewModel = viewModel(repository(initial = listOf(profile("p1"))))
        viewModel.load()
        advanceUntilIdle()

        viewModel.deleteKey("p1")
        advanceUntilIdle()

        assertNull(preferences.value.defaultTextProfileId)
        assertFalse((viewModel.listState.value as AiProfileListUiState.Ready).items.single().isDefaultTextProfile)
    }

    @Test
    fun deletingTheDefaultProfileClearsTheDevicePreference() = runTest(dispatcher) {
        secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias] = "sk-old"
        preferences.value = AiPreference(defaultTextProfileId = "p1")
        val viewModel = viewModel(repository(initial = listOf(profile("p1"))))

        viewModel.deleteProfile("p1")
        advanceUntilIdle()

        assertNull(preferences.value.defaultTextProfileId)
        assertTrue((viewModel.listState.value as AiProfileListUiState.Ready).items.isEmpty())
    }

    @Test
    fun loadKeepsDefaultBindingWhenItsProfileHasNoKey() = runTest(dispatcher) {
        preferences.value = AiPreference(defaultTextProfileId = "p1")
        val viewModel = viewModel(repository(initial = listOf(profile("p1"))))

        viewModel.load()
        advanceUntilIdle()

        assertEquals("p1", preferences.value.defaultTextProfileId)
        assertFalse((viewModel.listState.value as AiProfileListUiState.Ready).items.single().isDefaultTextProfile)
    }

    @Test
    fun loadKeepsImageDefaultBindingWhenItsProfileHasNoKey() = runTest(dispatcher) {
        val image = profile("p-img").copy(capabilities = setOf(AiCapability.ImageGeneration))
        preferences.value = AiPreference(defaultImageProfileId = "p-img")
        val viewModel = viewModel(repository(initial = listOf(image)))

        viewModel.load()
        advanceUntilIdle()

        assertEquals("p-img", preferences.value.defaultImageProfileId)
        assertFalse((viewModel.listState.value as AiProfileListUiState.Ready).items.single().isDefaultImageProfile)
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
            AiProfileListUiState.Ready(listOf(AiProfileListItem(stored, hasKey = true, canBeDefaultTextProfile = true))),
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
    fun editingAMimoPresetKeepsItsProviderKind() = runTest(dispatcher) {
        // 真机事故复现：给 MiMo 预设填 Key 保存后，providerKind 被编辑器重置成
        // OPENAI_COMPATIBLE，MiMo 引擎按协议过滤候选时它就「消失」了（绑定失效、
        // 反复要求重新添加预设）。编辑器必须原样保留协议身份。
        val repository = repository(
            initial = listOf(
                profile("mimo-1").copy(
                    endpoint = "https://api.xiaomimimo.com/v1/chat/completions",
                    model = "mimo-v2.5-tts",
                    capabilities = setOf(AiCapability.Speech),
                    providerKind = AiProviderKind.XIAOMI_MIMO,
                ),
            ),
        )
        val viewModel = viewModel(repository)
        viewModel.load()
        advanceUntilIdle()
        viewModel.startEdit("mimo-1")
        advanceUntilIdle()

        viewModel.updateDraft(viewModel.editor.value!!.draft.copy(pendingKey = "sk-mimo", voice = "茉莉"))
        viewModel.save()
        advanceUntilIdle()

        val saved = repository.stored.getValue("mimo-1")
        assertEquals(AiProviderKind.XIAOMI_MIMO, saved.providerKind)
        assertEquals("https://api.xiaomimimo.com/v1/chat/completions", saved.endpoint)
        assertEquals("mimo-v2.5-tts", saved.model)
        assertEquals("茉莉", saved.voice)
        assertEquals("sk-mimo", secrets.entries[AiProfileSecretUseCase.referenceFor("mimo-1").alias])
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
    fun savingASpeechProfilePersistsTheSelectedVoice() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = viewModel(repository)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft().copy(capabilities = setOf(AiCapability.Speech), voice = "nova"))

        viewModel.save()
        advanceUntilIdle()

        assertEquals("nova", repository.stored.getValue("ai-test-1").voice)
    }

    @Test
    fun savingAProfileWithAVoiceOutsideTheCatalogIsRejected() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = viewModel(repository)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft().copy(capabilities = setOf(AiCapability.Speech), voice = "不存在的音色"))

        viewModel.save()
        advanceUntilIdle()

        assertEquals(AiProfileFieldError.VoiceInvalid, viewModel.editor.value?.fieldError)
        assertTrue(repository.stored.isEmpty(), "校验失败就不该落库")
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

    // ---------- 连接探活（决策 2026-11-04 §3-§4）----------

    @Test
    fun testConnectionStopsAtDomainConfirmationBeforeAnyByteLeaves() = runTest(dispatcher) {
        val transport = FakeProbeTransport()
        val viewModel = viewModel(transport = transport)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft())

        viewModel.testConnection()
        advanceUntilIdle()

        assertEquals(
            AiConnectionUiState.AwaitingConfirmation("api.example.com"),
            viewModel.editor.value?.connection,
            "编辑页的探活必须和其他 AI 功能走同一条域名披露闸门：状态里只带 host，不带密钥",
        )
        assertEquals(0, transport.requests.size, "确认之前零出站——这是 AGENTS.md 的硬约束，不是提示文案")
    }

    @Test
    fun confirmingTheHostSendsOneMinimalGetAndReportsReachable() = runTest(dispatcher) {
        val transport = FakeProbeTransport()
        val viewModel = viewModel(transport = transport)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft())
        viewModel.testConnection()
        advanceUntilIdle()

        viewModel.confirmOutbound(true)
        advanceUntilIdle()

        assertEquals(AiConnectionUiState.Reported(AiConnectionOutcome.Reachable), viewModel.editor.value?.connection)
        val request = transport.requests.single()
        assertEquals("https://api.example.com/v1/models", request.url)
        assertEquals("GET", request.method)
        assertEquals("", request.body, "探活只问「通不通」，不许带任何内容或模型名——带了就产生计费")
        assertEquals(setOf("Authorization"), request.headers.keys)
    }

    @Test
    fun refusingTheConfirmationLeavesTheEditorIdleAndStillSendsNothing() = runTest(dispatcher) {
        val transport = FakeProbeTransport()
        val viewModel = viewModel(transport = transport)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft())
        viewModel.testConnection()
        advanceUntilIdle()

        viewModel.confirmOutbound(false)

        assertEquals(AiConnectionUiState.Idle, viewModel.editor.value?.connection)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun anIncompleteDraftIsCalledOutAsConfigurationInvalidWithoutGoingOut() = runTest(dispatcher) {
        val transport = FakeProbeTransport()
        val viewModel = viewModel(transport = transport)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft().copy(model = ""))

        viewModel.testConnection()
        advanceUntilIdle()

        assertEquals(AiConnectionUiState.Reported(AiConnectionOutcome.ConfigurationInvalid), viewModel.editor.value?.connection)
        assertEquals(0, transport.requests.size, "草稿过不了既有校验就不许出网：探活不是绕过表单校验的后门")
    }

    @Test
    fun aDraftWithNoKeyAnywhereSaysSoInsteadOfGoingOut() = runTest(dispatcher) {
        val transport = FakeProbeTransport()
        val viewModel = viewModel(transport = transport)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft().copy(pendingKey = ""))

        viewModel.testConnection()
        advanceUntilIdle()

        assertEquals(AiConnectionUiState.Reported(AiConnectionOutcome.MissingKey), viewModel.editor.value?.connection)
        assertEquals(0, transport.requests.size)
        assertFalse(
            ops.any { it.startsWith("secret-read:") },
            "新建配置没有 profileId，连该读哪个密钥槽都不知道，不许去碰安全存储",
        )
    }

    @Test
    fun theProbeUsesTheDraftKeyBeforeTheStoredOne() = runTest(dispatcher) {
        secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias] = "sk-stored"
        val transport = FakeProbeTransport()
        val viewModel = viewModel(repository(initial = listOf(profile("p1"))), transport = transport)
        viewModel.startEdit("p1")
        advanceUntilIdle()
        viewModel.updateDraft(viewModel.editor.value!!.draft.copy(pendingKey = "sk-typed-now"))

        viewModel.testConnection()
        advanceUntilIdle()
        viewModel.confirmOutbound(true)
        advanceUntilIdle()

        assertEquals(
            "Bearer sk-typed-now",
            transport.requests.single().headers["Authorization"],
            "「先测通再保存」是探活的主要用途：刚填进去还没保存的 Key 必须比库里的旧 Key 优先",
        )
    }

    @Test
    fun theProbeFallsBackToTheStoredKeyWhenTheDraftLeavesItEmpty() = runTest(dispatcher) {
        secrets.entries[AiProfileSecretUseCase.referenceFor("p1").alias] = "sk-stored"
        val transport = FakeProbeTransport()
        val viewModel = viewModel(repository(initial = listOf(profile("p1"))), transport = transport)
        viewModel.startEdit("p1")
        advanceUntilIdle()

        viewModel.testConnection()
        advanceUntilIdle()
        viewModel.confirmOutbound(true)
        advanceUntilIdle()

        assertEquals("Bearer sk-stored", transport.requests.single().headers["Authorization"])
        assertTrue(
            ops.any { it == "secret-read:${AiProfileSecretUseCase.referenceFor("p1").alias}" },
            "编辑已存配置时草稿不回显密钥，取不到草稿密钥就要取这份 Profile 自己的密钥槽",
        )
    }

    @Test
    fun theConnectionStateCarriesNoSlotThatCouldHoldTheKey() = runTest(dispatcher) {
        val transport = FakeProbeTransport()
        val viewModel = viewModel(transport = transport)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft())

        viewModel.testConnection()
        advanceUntilIdle()
        viewModel.confirmOutbound(true)
        advanceUntilIdle()

        val reported = viewModel.editor.value!!.connection.toString()
        assertFalse(
            reported.contains("sk-test-key"),
            "探活状态只能是无字段常量（沿用 AiFailure 那条泄露防线）：$reported",
        )
        assertEquals(
            AiConnectionUiState.Reported(AiConnectionOutcome.Reachable),
            viewModel.editor.value!!.connection,
        )
    }

    @Test
    fun timeoutAndNetworkFailureStayApartInWhatTheUserIsTold() = runTest(dispatcher) {
        val timeoutTransport = FakeProbeTransport(result = AiHttpResult.TimedOut)
        val timeoutViewModel = viewModel(transport = timeoutTransport)
        timeoutViewModel.startCreate()
        timeoutViewModel.updateDraft(validDraft())
        timeoutViewModel.testConnection()
        advanceUntilIdle()
        timeoutViewModel.confirmOutbound(true)
        advanceUntilIdle()
        assertEquals(
            AiConnectionUiState.Reported(AiConnectionOutcome.Timeout),
            timeoutViewModel.editor.value!!.connection,
        )

        val offlineTransport = FakeProbeTransport(result = AiHttpResult.NetworkUnavailable)
        val offlineViewModel = viewModel(transport = offlineTransport)
        offlineViewModel.startCreate()
        offlineViewModel.updateDraft(validDraft())
        offlineViewModel.testConnection()
        advanceUntilIdle()
        offlineViewModel.confirmOutbound(true)
        advanceUntilIdle()
        assertEquals(
            AiConnectionUiState.Reported(AiConnectionOutcome.NetworkUnavailable),
            offlineViewModel.editor.value!!.connection,
            "「太慢」和「压根连不上」是两个处置动作；折叠成一条就等于把用户支到错误的排查方向",
        )
    }

    @Test
    fun aRejectedKeyIsReportedAsAuthFailureNotAsBrokenNetwork() = runTest(dispatcher) {
        val transport = FakeProbeTransport(result = AiHttpResult.Responded(AiHttpResponse(401, """{"error":"bad key"}""")))
        val viewModel = viewModel(transport = transport)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft())
        viewModel.testConnection()
        advanceUntilIdle()
        viewModel.confirmOutbound(true)
        advanceUntilIdle()

        assertEquals(AiConnectionUiState.Reported(AiConnectionOutcome.Unauthorized), viewModel.editor.value!!.connection)
        val message = (viewModel.editor.value!!.connection as AiConnectionUiState.Reported).outcome.message
        assertFalse(message.contains("bad key"), "响应体原文不许流到界面：$message")
        assertFalse(message.contains("api.example.com"), "界面文案只能是常量：$message")
    }

    @Test
    fun editingTheDraftClearsAStaleConnectionReport() = runTest(dispatcher) {
        val transport = FakeProbeTransport()
        val viewModel = viewModel(transport = transport)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft())
        viewModel.testConnection()
        advanceUntilIdle()
        viewModel.confirmOutbound(true)
        advanceUntilIdle()
        assertEquals(AiConnectionUiState.Reported(AiConnectionOutcome.Reachable), viewModel.editor.value!!.connection)
        val probesSoFar = transport.requests.size

        viewModel.updateDraft(validDraft().copy(endpoint = "https://other.test/v1"))

        assertEquals(
            AiConnectionUiState.Idle,
            viewModel.editor.value!!.connection,
            "Endpoint 一改，「可达」就是假证词：结果必须随输入一起作废，和 fieldError 同一族处理",
        )
        assertEquals(
            probesSoFar,
            transport.requests.size,
            "作废是清状态，不是补一发新请求：编辑不许自己触发出站",
        )
    }

    @Test
    fun aLateAnswerCannotResurrectAReportForAStaleDraft() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val transport = FakeProbeTransport(release = gate)
        val viewModel = viewModel(transport = transport)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft())
        viewModel.testConnection()
        advanceUntilIdle()
        viewModel.confirmOutbound(true)
        runCurrent()
        assertEquals(AiConnectionUiState.Testing, viewModel.editor.value!!.connection)

        // 等待期间用户改了 Endpoint：这一轮的答案说的是旧地址。
        viewModel.updateDraft(validDraft().copy(endpoint = "https://other.test/v1"))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            AiConnectionUiState.Idle,
            viewModel.editor.value!!.connection,
            "迟到的答案不许落到新草稿上：那等于拿旧 Endpoint 的「可达」给新配置背书",
        )
    }

    @Test
    fun closingTheEditorCancelsAnInFlightProbe() = runTest(dispatcher) {
        val transport = FakeProbeTransport(ops = ops, hang = true)
        val viewModel = viewModel(transport = transport)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft())
        viewModel.testConnection()
        advanceUntilIdle()
        viewModel.confirmOutbound(true)
        runCurrent()

        viewModel.closeEditor()
        advanceUntilIdle()

        assertTrue(
            ops.contains("transport-cancelled"),
            "退出编辑页要真的掐掉在飞的请求（不得阻塞 UI 的反面就是别让它挂在后台）",
        )
    }

    @Test
    fun startingAnotherProbeCancelsTheOneInFlight() = runTest(dispatcher) {
        val transport = FakeProbeTransport(ops = ops, hang = true)
        val viewModel = viewModel(transport = transport)
        viewModel.startCreate()
        viewModel.updateDraft(validDraft())
        viewModel.testConnection()
        advanceUntilIdle()
        viewModel.confirmOutbound(true)
        runCurrent()

        viewModel.testConnection()
        advanceUntilIdle()

        assertEquals(
            1,
            ops.count { it == "transport-cancelled" },
            "连点两次不许攒出两条并发探活：同一份草稿同时只允许一个在飞的诊断请求",
        )
        assertEquals(
            AiConnectionUiState.Testing,
            viewModel.editor.value?.connection,
            "同一域名本轮已确认过，第二次点下去直接重探（Text 档按域名记一次，与其余 AI 功能同口径），" +
                "但状态必须回到「测试中」，不许挂着上一轮被掐掉的中间态",
        )
    }

    @Test
    fun testConnectionWithoutAnOpenEditorDoesNothing() = runTest(dispatcher) {
        val transport = FakeProbeTransport()
        val viewModel = viewModel(transport = transport)

        viewModel.testConnection()
        advanceUntilIdle()

        assertNull(viewModel.editor.value)
        assertEquals(0, transport.requests.size)
    }

    private fun viewModel(
        repository: AiProfileRepository = repository(),
        ids: AiProfileIdFactory = AiProfileIdFactory { "ai-test-${ops.count { it.startsWith("profile-save:") } + 1}" },
        transport: AiHttpTransport = FakeProbeTransport(),
    ) = AiProfileSettingsViewModel(
        repository,
        AiProfileSecretUseCase(secrets),
        ids,
        preferences,
        AiConnectionProbe(transport),
    )

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

private class FakeAiPreferenceRepository : AiPreferenceRepository {
    var value = AiPreference()
    var failGet = false
    var failSave = false

    override suspend fun get(): Result<AiPreference> =
        if (failGet) Result.failure(AppErrorException(AppError.StorageUnavailable)) else Result.success(value)

    override suspend fun save(preference: AiPreference): Result<Unit> {
        if (failSave) return Result.failure(AppErrorException(AppError.StorageUnavailable))
        value = preference
        return Result.success(Unit)
    }
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

private class FakeProbeTransport(
    private val ops: MutableList<String>? = null,
    var result: AiHttpResult = AiHttpResult.Responded(AiHttpResponse(200, """{"data":[]}""")),
    var hang: Boolean = false,
    var release: CompletableDeferred<Unit>? = null,
) : AiHttpTransport {
    val requests = mutableListOf<AiHttpRequest>()

    override suspend fun send(request: AiHttpRequest): AiHttpResult {
        requests += request
        if (hang) {
            // 「在飞」的可观察证据只有取消：把 CancellationException 记进 ops 再原样抛回去，
            // 这样「退出编辑页/重连必须掐掉旧请求」这类断言不用去偷看协程内部。
            try {
                awaitCancellation()
            } catch (cancellation: CancellationException) {
                ops?.add("transport-cancelled")
                throw cancellation
            }
        }
        // 可控闸门：让「答案什么时候回来」由用例决定，才能演出「等待期间用户改了输入」这一场。
        release?.let { gate ->
            try {
                gate.await()
            } catch (cancellation: CancellationException) {
                ops?.add("transport-cancelled")
                throw cancellation
            }
        }
        return result
    }
}

private class FakeSecretStore(private val ops: MutableList<String>) : SecretStore {
    val entries: MutableMap<String, String> = mutableMapOf()

    override fun save(reference: SecretReference, secret: CharArray): Result<Unit> {
        ops += "secret-save:${reference.alias}"
        entries[reference.alias] = String(secret)
        return Result.success(Unit)
    }

    override fun read(reference: SecretReference): Result<CharArray> {
        ops += "secret-read:${reference.alias}"
        val value = entries[reference.alias]
            ?: return Result.failure(AppErrorException(AppError.KeyStoreUnavailable))
        // 返回新数组：真实实现保证调用方清零上一次结果不影响下一次读取。
        return Result.success(value.toCharArray())
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
