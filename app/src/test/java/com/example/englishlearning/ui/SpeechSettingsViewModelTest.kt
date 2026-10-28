package com.example.englishlearning.ui

import com.example.englishlearning.ai.AiProfileIdFactory
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.language.SpeechPreferenceRepository
import com.example.englishlearning.language.domain.PronunciationCapability
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.language.domain.PronunciationProvider
import com.example.englishlearning.language.domain.PronunciationResult
import com.example.englishlearning.language.domain.SpeechPreference
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test fun loadDefaultsToSystemAndOnlyListsSpeechProfilesWithLiveKeyState() = runTest(dispatcher) {
        val secretStore = SpeechFakeSecretStore(setOf("openai"))
        val viewModel = viewModel(
            profiles = RecordingProfiles(listOf(
                profile("text", setOf(AiCapability.Text)),
                profile("openai", setOf(AiCapability.Speech)),
                profile("mimo", setOf(AiCapability.Speech), AiProviderKind.XIAOMI_MIMO),
            )),
            secretStore = secretStore,
        )

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(PronunciationEngine.SystemTts, state.selectedEngine)
        assertEquals(listOf("openai", "mimo"), state.candidates.map { it.profileId })
        assertEquals(listOf(SpeechProfileStatus.Available, SpeechProfileStatus.MissingKey), state.candidates.map { it.status })
        assertEquals(setOf("selectedEngine", "openAiProfileId", "miMoProfileId", "autoSpeakOnCardReady", "speechRate", "candidates", "message", "previewMessage"),
            SpeechSettingsUiState::class.java.declaredFields.filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet())
        assertEquals(setOf("profileId", "displayName", "status", "providerKind"),
            SpeechProfileCandidate::class.java.declaredFields.filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet())
    }

    /** 历史 OpenAI 发音值由 Room repository 规范化；设置 ViewModel 只接收 System/MiMo。 */
    @Test fun storedLegacySelectionIsDisplayedAsSystemTts() = runTest(dispatcher) {
        val preferences = FakePreferences(SpeechPreference(selectedEngine = PronunciationEngine.SystemTts, openAiProfileId = "legacy"))
        val viewModel = viewModel(preferences = preferences)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(PronunciationEngine.SystemTts, viewModel.state.value.selectedEngine)
        assertEquals("系统 TTS", viewModel.state.value.engineStatuses().currentProvider)
    }

    /** D1：切换引擎是读改写，自动发音开关不能被抹回默认值。 */
    @Test fun selectKeepsTheAutoSpeakSetting() = runTest(dispatcher) {
        val preferences = FakePreferences(SpeechPreference(autoSpeakOnCardReady = false, miMoProfileId = "mimo"))
        val viewModel = viewModel(
            profiles = RecordingProfiles(listOf(profile("mimo", setOf(AiCapability.Speech), AiProviderKind.XIAOMI_MIMO))),
            preferences = preferences,
        )
        viewModel.load()
        advanceUntilIdle()

        viewModel.select(PronunciationEngine.MiMo, "mimo")
        advanceUntilIdle()

        assertEquals(false, preferences.saved.single().autoSpeakOnCardReady)
        assertEquals(PronunciationEngine.MiMo, viewModel.state.value.selectedEngine)
        assertEquals(false, viewModel.state.value.autoSpeakOnCardReady)
    }

    /** D1：开关切换要落盘并反映到状态。 */
    @Test fun setAutoSpeakOnCardReadyPersistsAndReflectsInState() = runTest(dispatcher) {
        val preferences = FakePreferences()
        val viewModel = viewModel(preferences = preferences)
        viewModel.load()
        advanceUntilIdle()
        assertEquals(true, viewModel.state.value.autoSpeakOnCardReady)

        viewModel.setAutoSpeakOnCardReady(false)
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.autoSpeakOnCardReady)
        assertEquals(false, preferences.saved.single().autoSpeakOnCardReady)
        // 读改写：已保存的引擎选择不被抹掉。
        assertEquals(PronunciationEngine.SystemTts, preferences.saved.single().selectedEngine)

        viewModel.setAutoSpeakOnCardReady(true)
        advanceUntilIdle()
        assertEquals(true, viewModel.state.value.autoSpeakOnCardReady)
        assertEquals(true, preferences.saved.last().autoSpeakOnCardReady)
    }

    @Test fun failedAutoSpeakSaveKeepsStateAndReportsStorageError() = runTest(dispatcher) {
        val preferences = FakePreferences(failSave = true)
        val viewModel = viewModel(preferences = preferences)
        viewModel.load()
        advanceUntilIdle()

        viewModel.setAutoSpeakOnCardReady(false)
        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.autoSpeakOnCardReady)
        assertTrue(viewModel.state.value.message != null)
    }

    /** F3-02（决策 §2）：四档循环 0.75 → 1.0 → 1.25 → 1.5 → 0.75，落盘并反映到状态。 */
    @Test fun cycleSpeechRateAdvancesStepAndPersists() = runTest(dispatcher) {
        val preferences = FakePreferences()
        val viewModel = viewModel(preferences = preferences)
        viewModel.load()
        advanceUntilIdle()
        assertEquals(1.0f, viewModel.state.value.speechRate)

        viewModel.cycleSpeechRate()
        advanceUntilIdle()

        assertEquals(1.25f, viewModel.state.value.speechRate)
        assertEquals(1.25f, preferences.saved.single().speechRate)

        viewModel.cycleSpeechRate()
        advanceUntilIdle()
        assertEquals(1.5f, viewModel.state.value.speechRate)
        assertEquals(1.5f, preferences.saved.last().speechRate)
    }

    /** 最快档再点一次回到最慢档（循环语义，档位表外值按就近档位推进）。 */
    @Test fun cycleSpeechRateWrapsFromFastestToSlowest() = runTest(dispatcher) {
        val preferences = FakePreferences(SpeechPreference(speechRate = 1.5f))
        val viewModel = viewModel(preferences = preferences)
        viewModel.load()
        advanceUntilIdle()

        viewModel.cycleSpeechRate()
        advanceUntilIdle()

        assertEquals(0.75f, viewModel.state.value.speechRate)
        assertEquals(0.75f, preferences.saved.single().speechRate)
    }

    /** 存储失败：状态与档位不变，只报存储错误。 */
    @Test fun failedSpeechRateCycleKeepsStateAndReportsStorageError() = runTest(dispatcher) {
        val preferences = FakePreferences(failSave = true)
        val viewModel = viewModel(preferences = preferences)
        viewModel.load()
        advanceUntilIdle()

        viewModel.cycleSpeechRate()
        advanceUntilIdle()

        assertEquals(1.0f, viewModel.state.value.speechRate)
        assertTrue(viewModel.state.value.message != null)
    }

    /**
     * 批 5 Task 1：语速从「点按循环」改成滑杆后，滑杆给的是**连续**值，落盘前必须吸附到四档表
     * （[SpeechRateSteps]）里最近的一档——否则存储里会出现 1.1x 这种档位表外的值，
     * 播放侧与「四档」的产品口径当场分叉。
     */
    @Test fun setSpeechRateSnapsToTheNearestLegalStepAndPersists() = runTest(dispatcher) {
        val preferences = FakePreferences()
        val viewModel = viewModel(preferences = preferences)
        viewModel.load()
        advanceUntilIdle()

        viewModel.setSpeechRate(1.3f)
        advanceUntilIdle()

        assertEquals(1.25f, viewModel.state.value.speechRate)
        assertEquals(1.25f, preferences.saved.single().speechRate)

        viewModel.setSpeechRate(0.7f)
        advanceUntilIdle()
        assertEquals(0.75f, viewModel.state.value.speechRate)
        assertEquals(0.75f, preferences.saved.last().speechRate)

        viewModel.setSpeechRate(1.9f)
        advanceUntilIdle()
        assertEquals(1.5f, viewModel.state.value.speechRate)
        assertEquals(1.5f, preferences.saved.last().speechRate)
    }

    /** 滑杆拖到当前档位不需要写盘（等值是幂等操作），但状态里的报告必须收干净。 */
    @Test fun setSpeechRateAtTheCurrentStepDoesNotRewriteStorage() = runTest(dispatcher) {
        val preferences = FakePreferences(SpeechPreference(speechRate = 1.25f))
        val viewModel = viewModel(preferences = preferences)
        viewModel.load()
        advanceUntilIdle()

        viewModel.setSpeechRate(1.25f)
        advanceUntilIdle()

        assertEquals(1.25f, viewModel.state.value.speechRate)
        assertTrue(preferences.saved.isEmpty(), "同一档位不写盘（现 ${preferences.saved.size} 次）")
    }

    /** 存储失败：状态与档位不变，只报存储错误——与 [cycleSpeechRate] 同一口径。 */
    @Test fun failedSpeechRateSetKeepsStateAndReportsStorageError() = runTest(dispatcher) {
        val preferences = FakePreferences(failSave = true)
        val viewModel = viewModel(preferences = preferences)
        viewModel.load()
        advanceUntilIdle()

        viewModel.setSpeechRate(0.75f)
        advanceUntilIdle()

        assertEquals(1.0f, viewModel.state.value.speechRate)
        assertTrue(viewModel.state.value.message != null)
    }

    /** 四档表本身就是 UI 刻度表：滑杆的刻度数、吸附目标、循环步进三者必须同一份。 */
    @Test fun speechRateStepsStayTheFourProductSteps() {
        assertEquals(listOf(0.75f, 1.0f, 1.25f, 1.5f), SpeechRateSteps)
    }

    /**
     * 决策 §2 读改写口径（`:78` 历史坑同款防线）：select 用当前 UI 状态手工构造偏好，
     * 必须显式带上已存的 speechRate——存过 1.25x 之后换引擎不得把它复位。
     */
    @Test fun selectKeepsThePersistedSpeechRate() = runTest(dispatcher) {
        val preferences = FakePreferences(SpeechPreference(speechRate = 1.25f, miMoProfileId = "mimo"))
        val viewModel = viewModel(
            profiles = RecordingProfiles(listOf(profile("mimo", setOf(AiCapability.Speech), AiProviderKind.XIAOMI_MIMO))),
            preferences = preferences,
        )
        viewModel.load()
        advanceUntilIdle()
        assertEquals(1.25f, viewModel.state.value.speechRate)

        viewModel.select(PronunciationEngine.MiMo, "mimo")
        advanceUntilIdle()

        assertEquals(1.25f, preferences.saved.last().speechRate)
        assertEquals(1.25f, viewModel.state.value.speechRate)
    }

    /** 决策 §2 读改写口径：一键建 MiMo 预设同样不得把已存语速静默复位。 */
    @Test fun addMiMoPresetKeepsThePersistedSpeechRate() = runTest(dispatcher) {
        val profiles = RecordingProfiles(emptyList())
        val preferences = FakePreferences(SpeechPreference(speechRate = 1.25f))
        val viewModel = viewModel(profiles = profiles, preferences = preferences)
        viewModel.load()
        advanceUntilIdle()

        viewModel.addMiMoPreset()
        advanceUntilIdle()

        assertEquals(1.25f, preferences.saved.single().speechRate)
        assertEquals(1.25f, viewModel.state.value.speechRate)
    }

    @Test fun failedSaveRetainsThePreviousUiSelection() = runTest(dispatcher) {
        val preferences = FakePreferences(
            SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "bound"),
            failSave = true,
        )
        val viewModel = viewModel(
            profiles = RecordingProfiles(listOf(profile("bound", setOf(AiCapability.Speech), AiProviderKind.XIAOMI_MIMO))),
            preferences = preferences,
        )
        viewModel.load()
        advanceUntilIdle()

        viewModel.select(PronunciationEngine.MiMo, "new")
        advanceUntilIdle()

        assertEquals(PronunciationEngine.MiMo, viewModel.state.value.selectedEngine)
        assertEquals("bound", viewModel.state.value.miMoProfileId)
        assertTrue(viewModel.state.value.message != null)
    }

    @Test fun providerSummaryMatchesCandidatesByProtocolKind() = runTest(dispatcher) {
        val viewModel = viewModel(
            profiles = RecordingProfiles(listOf(
                profile("openai", setOf(AiCapability.Speech)),
                profile("mimo", setOf(AiCapability.Speech), AiProviderKind.XIAOMI_MIMO),
            )),
            // OpenAI 引擎行已移除，「当前供应商 ·」前缀改在 MiMo 行上验证协议匹配。
            preferences = FakePreferences(SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "mimo")),
            secretStore = SpeechFakeSecretStore(setOf("openai", "mimo")),
        )
        viewModel.load()
        advanceUntilIdle()

        assertEquals("小米 MiMo", viewModel.state.value.engineStatuses().currentProvider)
        assertEquals("当前供应商 · 已配置", viewModel.state.value.engineStatuses().miMo)
        // openai 兼容候选未绑定且可用 → 可选配置 · 未绑定（协议匹配的候选才算数）。
        assertEquals("可选配置 · 未绑定", viewModel.state.value.engineStatuses().openAi)
        assertEquals("未下载", viewModel.state.value.engineStatuses().zipVoice)
    }

    @Test fun openAiKindProfileDoesNotMakeMiMoLookConfigured() = runTest(dispatcher) {
        val viewModel = viewModel(
            profiles = RecordingProfiles(listOf(profile("voice", setOf(AiCapability.Speech)))),
            secretStore = SpeechFakeSecretStore(setOf("voice")),
        )
        viewModel.load()
        advanceUntilIdle()

        // 只有 OpenAI 兼容 Profile 时，MiMo 槽位没有任何协议匹配的候选 → 未配置，
        // 界面此时应给出「添加 MiMo 预设」入口而不是假装可选。
        assertEquals("可选配置 · 未绑定", viewModel.state.value.engineStatuses().openAi)
        assertEquals("未配置", viewModel.state.value.engineStatuses().miMo)
        assertEquals("未下载", viewModel.state.value.engineStatuses().zipVoice)
        assertEquals("系统 TTS", viewModel.state.value.engineStatuses().currentProvider)
    }

    @Test fun invalidatedBindingIsNotReportedAsConfigured() = runTest(dispatcher) {
        val viewModel = viewModel(
            profiles = RecordingProfiles(listOf(profile("openai", setOf(AiCapability.Speech)))),
            preferences = FakePreferences(SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "removed")),
            secretStore = SpeechFakeSecretStore(setOf("openai")),
        )
        viewModel.load()
        advanceUntilIdle()

        assertEquals("当前供应商 · 绑定失效", viewModel.state.value.engineStatuses().miMo)
        assertEquals("可选配置 · 未绑定", viewModel.state.value.engineStatuses().openAi)
    }

    @Test fun profileWithoutSpeechCapabilityDoesNotMarkProviderConfigured() = runTest(dispatcher) {
        val viewModel = viewModel(
            profiles = RecordingProfiles(listOf(profile("text-only", setOf(AiCapability.Text)))),
            preferences = FakePreferences(SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "text-only")),
            secretStore = SpeechFakeSecretStore(setOf("text-only")),
        )
        viewModel.load()
        advanceUntilIdle()

        assertEquals("当前供应商 · 绑定失效", viewModel.state.value.engineStatuses().miMo)
    }

    @Test fun boundProfileWithoutKeyIsExplicitlyReportedMissingKey() = runTest(dispatcher) {
        val viewModel = viewModel(
            profiles = RecordingProfiles(listOf(profile("mimo", setOf(AiCapability.Speech), AiProviderKind.XIAOMI_MIMO))),
            preferences = FakePreferences(SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "mimo")),
        )
        viewModel.load()
        advanceUntilIdle()

        assertEquals("当前供应商 · 缺少密钥", viewModel.state.value.engineStatuses().miMo)
    }

    @Test fun addMiMoPresetSavesTalkifyDefaultsAndBindsMiMoEngine() = runTest(dispatcher) {
        val profiles = RecordingProfiles(emptyList())
        val preferences = FakePreferences()
        val viewModel = viewModel(profiles = profiles, preferences = preferences)

        viewModel.addMiMoPreset()
        advanceUntilIdle()

        val preset = profiles.saved.single()
        assertEquals(SpeechSettingsViewModel.PRESET_ENDPOINT, preset.endpoint)
        assertEquals(SpeechSettingsViewModel.PRESET_MODEL, preset.model)
        assertEquals(SpeechSettingsViewModel.PRESET_DISPLAY_NAME, preset.displayName)
        assertEquals(AiProviderKind.XIAOMI_MIMO, preset.providerKind)
        assertEquals(setOf(AiCapability.Speech), preset.capabilities)
        // 密钥别名只由 profileId 推导，不携带端点或模型信息。
        assertEquals(AiProfileSecretUseCase.referenceFor(preset.profileId).alias, preset.secretReference.alias)
        // 创建后自动绑定到 MiMo 槽位，用户只差填密钥一步。
        assertEquals(
            SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = preset.profileId),
            preferences.saved.single(),
        )
        assertEquals(PronunciationEngine.MiMo, viewModel.state.value.selectedEngine)
        assertEquals(listOf(preset.profileId), viewModel.state.value.candidates.map { it.profileId })
        assertEquals(AiProviderKind.XIAOMI_MIMO, viewModel.state.value.candidates.single().providerKind)
        assertTrue(viewModel.state.value.message!!.contains("AI 服务与密钥"))
    }

    @Test fun addMiMoPresetKeepsMessageWhenPreferenceSaveFails() = runTest(dispatcher) {
        val profiles = RecordingProfiles(emptyList())
        val preferences = FakePreferences(failSave = true)
        val viewModel = viewModel(profiles = profiles, preferences = preferences)

        viewModel.addMiMoPreset()
        advanceUntilIdle()

        assertEquals(1, profiles.saved.size)
        assertTrue(viewModel.state.value.message != null)
        assertEquals(null, viewModel.state.value.miMoProfileId)
    }

    @Test fun addMiMoPresetReportsWhenStorageFails() = runTest(dispatcher) {
        val viewModel = viewModel(profiles = RecordingProfiles(emptyList(), failSave = true))

        viewModel.addMiMoPreset()
        advanceUntilIdle()

        assertEquals("本机存储暂时不可用，改动没有保存。", viewModel.state.value.message)
        assertEquals(0, viewModel.state.value.candidates.size)
    }

    @Test fun previewUsesTheSavedPreferenceAndReportsTheOutcome() = runTest(dispatcher) {
        val pronunciation = FakePronunciation(PronunciationResult.Played)
        val viewModel = viewModel(pronunciation = pronunciation)

        viewModel.preview("Hello! 你好，世界！")
        advanceUntilIdle()

        assertEquals("Hello! 你好，世界！", pronunciation.lastText)
        assertEquals("试听已播放。", viewModel.state.value.previewMessage)
    }

    @Test fun previewFailureAndUnavailableMapToSafeMessages() = runTest(dispatcher) {
        val failed = viewModel(pronunciation = FakePronunciation(PronunciationResult.Failed()))
        failed.preview("hello")
        advanceUntilIdle()
        assertEquals("播放失败，请检查密钥与网络。", failed.state.value.previewMessage)

        val unavailable = viewModel(pronunciation = FakePronunciation(PronunciationResult.Unavailable("key unavailable")))
        unavailable.preview("hello")
        advanceUntilIdle()
        // 失败原因里可能带内部细节（如 "key unavailable"），界面只允许出现安全的归类文案。
        assertEquals("当前语音配置不可用（未绑定或缺少密钥）。", unavailable.state.value.previewMessage)
    }

    @Test fun previewWithBlankTextAsksForInputWithoutTouchingTheProvider() = runTest(dispatcher) {
        val pronunciation = FakePronunciation(PronunciationResult.Played)
        val viewModel = viewModel(pronunciation = pronunciation)

        viewModel.preview("   ")
        advanceUntilIdle()

        assertEquals(null, pronunciation.lastText)
        assertEquals("请先输入要试听的内容。", viewModel.state.value.previewMessage)
    }

    private fun viewModel(
        profiles: AiProfileRepository = RecordingProfiles(emptyList()),
        preferences: FakePreferences = FakePreferences(),
        secretStore: SpeechFakeSecretStore = SpeechFakeSecretStore(),
        pronunciation: PronunciationProvider = FakePronunciation(PronunciationResult.Played),
    ) = SpeechSettingsViewModel(profiles, AiProfileSecretUseCase(secretStore), preferences, AiProfileIdFactory { "fixed-id" }, pronunciation)

    private fun profile(
        id: String,
        capabilities: Set<AiCapability>,
        kind: AiProviderKind = AiProviderKind.OPENAI_COMPATIBLE,
    ) = AiProfile(
        profileId = id,
        displayName = "Profile $id",
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "tts-1",
        capabilities = capabilities,
        secretReference = AiProfileSecretUseCase.referenceFor(id),
        advancedParameters = AiAdvancedParameters(),
        providerKind = kind,
    )
}

private class RecordingProfiles(
    initial: List<AiProfile>,
    private val failSave: Boolean = false,
) : AiProfileRepository {
    private val values = initial.toMutableList()
    val saved = mutableListOf<AiProfile>()
    override suspend fun list() = Result.success(values.toList())
    override suspend fun find(profileId: String) = Result.success(values.firstOrNull { it.profileId == profileId })
    override suspend fun save(profile: AiProfile): Result<Unit> {
        if (failSave) return Result.failure(IllegalStateException())
        saved += profile
        values.removeAll { it.profileId == profile.profileId }
        values += profile
        return Result.success(Unit)
    }
    override suspend fun delete(profileId: String) = Result.success(Unit)
}

private class FakePreferences(initial: SpeechPreference = SpeechPreference(), private val failSave: Boolean = false) : SpeechPreferenceRepository {
    private var value = initial
    val saved = mutableListOf<SpeechPreference>()
    override suspend fun get() = Result.success(value)
    override suspend fun save(preference: SpeechPreference): Result<Unit> {
        if (failSave) return Result.failure(IllegalStateException())
        value = preference
        saved += preference
        return Result.success(Unit)
    }
}

private class SpeechFakeSecretStore(idsWithKey: Set<String> = emptySet()) : SecretStore {
    private val aliases = idsWithKey.map(AiProfileSecretUseCase::referenceFor).map(SecretReference::alias).toSet()
    override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
    override fun read(reference: SecretReference) = Result.success("secret-value".toCharArray())
    override fun delete(reference: SecretReference) = Result.success(Unit)
    override fun has(reference: SecretReference) = Result.success(reference.alias in aliases)
}

private class FakePronunciation(private val result: PronunciationResult) : PronunciationProvider {
    var lastText: String? = null
    override fun capabilities() = setOf(PronunciationCapability.RemoteAudio)
    override suspend fun speak(text: String): PronunciationResult {
        lastText = text
        return result
    }
}
