package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiProfileIdFactory
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.language.SpeechPreferenceRepository
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.language.domain.PronunciationProvider
import com.example.englishlearning.language.domain.PronunciationResult
import com.example.englishlearning.language.domain.SpeechPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SpeechProfileStatus { Available, MissingKey }

data class SpeechProfileCandidate(
    val profileId: String,
    val displayName: String,
    val status: SpeechProfileStatus,
    val providerKind: AiProviderKind = AiProviderKind.OPENAI_COMPATIBLE,
)

data class SpeechSettingsUiState(
    val selectedEngine: PronunciationEngine = PronunciationEngine.SystemTts,
    val openAiProfileId: String? = null,
    val miMoProfileId: String? = null,
    val candidates: List<SpeechProfileCandidate> = emptyList(),
    val message: String? = null,
    /** 试听结果反馈；与选择操作的 message 分开，互不覆盖。 */
    val previewMessage: String? = null,
) {
    /** 当前选中引擎可用的候选：协议不匹配的 Profile 不进列表，防止错绑后发出注定 404 的请求。 */
    fun candidatesForSelectedEngine(): List<SpeechProfileCandidate> = when (selectedEngine) {
        PronunciationEngine.SystemTts -> emptyList()
        PronunciationEngine.OpenAi -> candidates.filter { it.providerKind == AiProviderKind.OPENAI_COMPATIBLE }
        PronunciationEngine.MiMo -> candidates.filter { it.providerKind == AiProviderKind.XIAOMI_MIMO }
    }

    /** 当前引擎绑定的候选；`null` = 未绑定或绑定失效。 */
    fun boundCandidateFor(engine: PronunciationEngine): SpeechProfileCandidate? {
        val id = when (engine) {
            PronunciationEngine.OpenAi -> openAiProfileId
            PronunciationEngine.MiMo -> miMoProfileId
            PronunciationEngine.SystemTts -> return null
        } ?: return null
        return candidates.firstOrNull { it.profileId == id && it.providerKind == kindOf(engine) }
    }

    private fun kindOf(engine: PronunciationEngine): AiProviderKind = when (engine) {
        PronunciationEngine.OpenAi -> AiProviderKind.OPENAI_COMPATIBLE
        PronunciationEngine.MiMo -> AiProviderKind.XIAOMI_MIMO
        PronunciationEngine.SystemTts -> AiProviderKind.OPENAI_COMPATIBLE
    }
}

@HiltViewModel
class SpeechSettingsViewModel @Inject constructor(
    private val profiles: AiProfileRepository,
    private val secrets: AiProfileSecretUseCase,
    private val preferences: SpeechPreferenceRepository,
    private val ids: AiProfileIdFactory,
    private val pronunciation: PronunciationProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(SpeechSettingsUiState())
    val state: StateFlow<SpeechSettingsUiState> = _state

    fun load() = viewModelScope.launch { reload() }

    fun select(engine: PronunciationEngine, profileId: String? = null) = viewModelScope.launch {
        val current = _state.value
        val next = SpeechPreference(
            selectedEngine = engine,
            openAiProfileId = if (engine == PronunciationEngine.OpenAi) profileId else current.openAiProfileId,
            miMoProfileId = if (engine == PronunciationEngine.MiMo) profileId else current.miMoProfileId,
        )
        if (preferences.save(next).isSuccess) {
            _state.value = current.copy(
                selectedEngine = next.selectedEngine,
                openAiProfileId = next.openAiProfileId,
                miMoProfileId = next.miMoProfileId,
                message = null,
            )
        } else {
            _state.value = current.copy(message = "本机存储暂时不可用，改动没有保存。")
        }
    }

    /**
     * 一键创建 Talkify 式 MiMo 预设：服务地址与模型名预填官方默认值，用户之后只需在
     * 「AI 服务与密钥」里填密钥。先落元数据再绑定选中偏好（与 AI Profile 保存同序）；
     * 刷新与提示在同一次状态赋值里完成，避免 load 协程把 message 覆盖掉。
     */
    fun addMiMoPreset() = viewModelScope.launch {
        val profileId = ids.newId()
        val profile = AiProfile(
            profileId = profileId,
            displayName = PRESET_DISPLAY_NAME,
            websiteUrl = PRESET_WEBSITE_URL,
            endpoint = PRESET_ENDPOINT,
            model = PRESET_MODEL,
            capabilities = setOf(AiCapability.Speech),
            secretReference = AiProfileSecretUseCase.referenceFor(profileId),
            providerKind = AiProviderKind.XIAOMI_MIMO,
        )
        if (profiles.save(profile).isFailure) {
            _state.value = _state.value.copy(message = STORAGE_FAILED_MESSAGE)
            return@launch
        }
        val next = SpeechPreference(
            selectedEngine = PronunciationEngine.MiMo,
            openAiProfileId = _state.value.openAiProfileId,
            miMoProfileId = profileId,
        )
        if (preferences.save(next).isFailure) {
            _state.value = _state.value.copy(message = STORAGE_FAILED_MESSAGE)
            return@launch
        }
        reload(message = "已创建 MiMo 预设，请在「AI 服务与密钥」中填写密钥。")
    }

    /**
     * 试听：用当前保存的语音偏好（选中引擎 + 绑定配置）真实朗读一段文本。
     * 反馈只含结果种类，不回显请求细节或密钥信息。
     */
    fun preview(text: String) = viewModelScope.launch {
        if (text.isBlank()) {
            _state.value = _state.value.copy(previewMessage = "请先输入要试听的内容。")
            return@launch
        }
        _state.value = _state.value.copy(previewMessage = null)
        val result = try {
            pronunciation.speak(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PronunciationResult.Failed()
        }
        val message = when (result) {
            PronunciationResult.Played -> "试听已播放。"
            is PronunciationResult.Failed -> "播放失败，请检查密钥与网络。"
            is PronunciationResult.Unavailable -> "当前语音配置不可用（未绑定或缺少密钥）。"
        }
        _state.value = _state.value.copy(previewMessage = message)
    }

    private suspend fun reload(message: String? = null) {
        val preference = preferences.get().getOrDefault(SpeechPreference())
        val speechProfiles = profiles.list().getOrDefault(emptyList()).filter { AiCapability.Speech in it.capabilities }
        _state.value = SpeechSettingsUiState(
            // OpenAI 引擎选项已从语音页移除：历史存储选中 OpenAi 的用户在界面上按系统 TTS
            // 呈现。只回落显示层，偏好文件里的原始选择原样保留，不被静默迁移。
            selectedEngine = preference.selectedEngine.takeUnless { it == PronunciationEngine.OpenAi }
                ?: PronunciationEngine.SystemTts,
            openAiProfileId = preference.openAiProfileId,
            miMoProfileId = preference.miMoProfileId,
            candidates = speechProfiles.map { it.toCandidate() },
            message = message,
        )
    }

    private fun AiProfile.toCandidate() = SpeechProfileCandidate(
        profileId = profileId,
        displayName = displayName,
        status = if (secrets.hasKey(this).getOrDefault(false)) SpeechProfileStatus.Available else SpeechProfileStatus.MissingKey,
        providerKind = providerKind,
    )

    companion object {
        const val PRESET_DISPLAY_NAME = "小米 MiMo TTS"
        const val PRESET_WEBSITE_URL = "https://mimo.mi.com"
        const val PRESET_ENDPOINT = "https://api.xiaomimimo.com/v1/chat/completions"
        const val PRESET_MODEL = "mimo-v2.5-tts"
        private const val STORAGE_FAILED_MESSAGE = "本机存储暂时不可用，改动没有保存。"
    }
}
