package com.example.englishlearning.ui

import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.language.SpeechPreferenceRepository
import com.example.englishlearning.language.domain.PronunciationEngine
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
            profiles = listOf(
                profile("text", setOf(AiCapability.Text)),
                profile("openai", setOf(AiCapability.Speech)),
                profile("mimo", setOf(AiCapability.Speech)),
            ),
            secretStore = secretStore,
        )

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(PronunciationEngine.SystemTts, state.selectedEngine)
        assertEquals(listOf("openai", "mimo"), state.candidates.map { it.profileId })
        assertEquals(listOf(SpeechProfileStatus.Available, SpeechProfileStatus.MissingKey), state.candidates.map { it.status })
        assertEquals(setOf("selectedEngine", "openAiProfileId", "miMoProfileId", "candidates", "message"),
            SpeechSettingsUiState::class.java.declaredFields.filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet())
        assertEquals(setOf("profileId", "displayName", "status"),
            SpeechProfileCandidate::class.java.declaredFields.filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet())
    }

    @Test fun selectOpenAiSavesItsProfileIdAndKeepsMimoBinding() = runTest(dispatcher) {
        val preferences = FakePreferences(SpeechPreference(miMoProfileId = "mimo"))
        val viewModel = viewModel(profiles = listOf(profile("openai", setOf(AiCapability.Speech))), preferences = preferences)
        viewModel.load()
        advanceUntilIdle()

        viewModel.select(PronunciationEngine.OpenAi, "openai")
        advanceUntilIdle()

        assertEquals(SpeechPreference(selectedEngine = PronunciationEngine.OpenAi, openAiProfileId = "openai", miMoProfileId = "mimo"), preferences.saved.single())
        assertEquals(PronunciationEngine.OpenAi, viewModel.state.value.selectedEngine)
    }

    @Test fun failedSaveRetainsThePreviousUiSelection() = runTest(dispatcher) {
        val preferences = FakePreferences(
            SpeechPreference(selectedEngine = PronunciationEngine.OpenAi, openAiProfileId = "bound"),
            failSave = true,
        )
        val viewModel = viewModel(profiles = listOf(profile("bound", setOf(AiCapability.Speech))), preferences = preferences)
        viewModel.load()
        advanceUntilIdle()

        viewModel.select(PronunciationEngine.OpenAi, "new")
        advanceUntilIdle()

        assertEquals(PronunciationEngine.OpenAi, viewModel.state.value.selectedEngine)
        assertEquals("bound", viewModel.state.value.openAiProfileId)
        assertTrue(viewModel.state.value.message != null)
    }

    @Test fun providerSummaryUsesSpeechCandidatesKeysAndTheCurrentBinding() = runTest(dispatcher) {
        val viewModel = viewModel(
            profiles = listOf(profile("openai", setOf(AiCapability.Speech)), profile("mimo", setOf(AiCapability.Speech))),
            preferences = FakePreferences(SpeechPreference(selectedEngine = PronunciationEngine.OpenAi, openAiProfileId = "openai")),
            secretStore = SpeechFakeSecretStore(setOf("openai")),
        )
        viewModel.load()
        advanceUntilIdle()

        assertEquals("OpenAI TTS", viewModel.state.value.engineStatuses().currentProvider)
        assertEquals("当前供应商 · 已配置", viewModel.state.value.engineStatuses().openAi)
        assertEquals("可选配置 · 未绑定", viewModel.state.value.engineStatuses().miMo)
        assertEquals("未下载", viewModel.state.value.engineStatuses().zipVoice)
    }

    @Test fun invalidatedBindingIsNotReportedAsConfigured() = runTest(dispatcher) {
        val viewModel = viewModel(
            profiles = listOf(profile("openai", setOf(AiCapability.Speech))),
            preferences = FakePreferences(SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "removed")),
            secretStore = SpeechFakeSecretStore(setOf("openai")),
        )
        viewModel.load()
        advanceUntilIdle()

        assertEquals("当前供应商 · 绑定失效", viewModel.state.value.engineStatuses().miMo)
        assertEquals("可选配置 · 未绑定", viewModel.state.value.engineStatuses().openAi)
    }

    @Test fun unboundSpeechCandidateIsNotMisreportedAsAnActiveBinding() = runTest(dispatcher) {
        val viewModel = viewModel(
            profiles = listOf(profile("voice", setOf(AiCapability.Speech))),
            secretStore = SpeechFakeSecretStore(setOf("voice")),
        )
        viewModel.load()
        advanceUntilIdle()

        assertEquals("可选配置 · 未绑定", viewModel.state.value.engineStatuses().openAi)
        assertEquals("可选配置 · 未绑定", viewModel.state.value.engineStatuses().miMo)
        assertEquals("未下载", viewModel.state.value.engineStatuses().zipVoice)
        assertEquals("系统 TTS", viewModel.state.value.engineStatuses().currentProvider)
    }

    @Test fun profileWithoutSpeechCapabilityDoesNotMarkProviderConfigured() = runTest(dispatcher) {
        val viewModel = viewModel(
            profiles = listOf(profile("text-only", setOf(AiCapability.Text))),
            preferences = FakePreferences(SpeechPreference(selectedEngine = PronunciationEngine.OpenAi, openAiProfileId = "text-only")),
            secretStore = SpeechFakeSecretStore(setOf("text-only")),
        )
        viewModel.load()
        advanceUntilIdle()

        assertEquals("当前供应商 · 绑定失效", viewModel.state.value.engineStatuses().openAi)
    }

    @Test fun boundProfileWithoutKeyIsExplicitlyReportedMissingKey() = runTest(dispatcher) {
        val viewModel = viewModel(
            profiles = listOf(profile("openai", setOf(AiCapability.Speech))),
            preferences = FakePreferences(SpeechPreference(selectedEngine = PronunciationEngine.OpenAi, openAiProfileId = "openai")),
        )
        viewModel.load()
        advanceUntilIdle()

        assertEquals("当前供应商 · 缺少密钥", viewModel.state.value.engineStatuses().openAi)
    }

    private fun viewModel(
        profiles: List<AiProfile> = emptyList(),
        preferences: FakePreferences = FakePreferences(),
        secretStore: SpeechFakeSecretStore = SpeechFakeSecretStore(),
    ) = SpeechSettingsViewModel(FakeProfiles(profiles), AiProfileSecretUseCase(secretStore), preferences)

    private fun profile(id: String, capabilities: Set<AiCapability>) = AiProfile(
        profileId = id,
        displayName = "Profile $id",
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "tts-1",
        capabilities = capabilities,
        secretReference = AiProfileSecretUseCase.referenceFor(id),
        advancedParameters = AiAdvancedParameters(),
    )
}

private class FakeProfiles(initial: List<AiProfile>) : AiProfileRepository {
    private val values = initial
    override suspend fun list() = Result.success(values)
    override suspend fun find(profileId: String) = Result.success(values.firstOrNull { it.profileId == profileId })
    override suspend fun save(profile: AiProfile) = Result.success(Unit)
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
