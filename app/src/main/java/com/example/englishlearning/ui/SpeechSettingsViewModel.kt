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

/**
 * 语速四档（F3-02 决策 §2）：`0.75x → 1.0x → 1.25x → 1.5x`。
 *
 * 批 5 Task 1 把屏上的「点按循环」换成滑杆后，这张表同时是三件事的唯一源：
 * 滑杆的刻度表（屏侧读它算 index 与读数）、[SpeechSettingsViewModel.setSpeechRate] 的吸附目标、
 * [SpeechSettingsViewModel.cycleSpeechRate] 的步进序。三处若各抄一份，「滑到 1.25x 却存下 1.4x」
 * 这类分叉会立刻出现，因此它放在 ViewModel 文件里而不是散在屏上。
 */
val SpeechRateSteps: List<Float> = listOf(0.75f, 1.0f, 1.25f, 1.5f)

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
    /** 学习流词卡就绪自动朗读一次；关闭后仅用户点击触发（D1）。 */
    val autoSpeakOnCardReady: Boolean = true,
    /** 播放侧变速倍率（F3-02 四控之速，决策 2026-10-15 §2）；UI 只暴露 0.75/1.0/1.25/1.5 四档。 */
    val speechRate: Float = 1.0f,
    val candidates: List<SpeechProfileCandidate> = emptyList(),
    val message: String? = null,
    /** 试听结果反馈；与选择操作的 message 分开，互不覆盖。 */
    val previewMessage: String? = null,
) {
    /** 当前选中引擎可用的候选：协议不匹配的 Profile 不进列表，防止错绑后发出注定 404 的请求。 */
    fun candidatesForSelectedEngine(): List<SpeechProfileCandidate> = when (selectedEngine) {
        PronunciationEngine.SystemTts -> emptyList()
        PronunciationEngine.MiMo -> candidates.filter { it.providerKind == AiProviderKind.XIAOMI_MIMO }
    }

    /** 当前引擎绑定的候选；`null` = 未绑定或绑定失效。 */
    fun boundCandidateFor(engine: PronunciationEngine): SpeechProfileCandidate? {
        val id = when (engine) {
            PronunciationEngine.MiMo -> miMoProfileId
            PronunciationEngine.SystemTts -> return null
        } ?: return null
        return candidates.firstOrNull { it.profileId == id && it.providerKind == AiProviderKind.XIAOMI_MIMO }
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
            openAiProfileId = current.openAiProfileId,
            miMoProfileId = if (engine == PronunciationEngine.MiMo) profileId else current.miMoProfileId,
            // 引擎选择是读改写：不能把自动发音开关抹回默认值。
            autoSpeakOnCardReady = current.autoSpeakOnCardReady,
            // v32 同类防线（决策 §6）：语速同理，缺这一行会把已保存的倍率抹回 1.0。
            speechRate = current.speechRate,
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

    /** 学习时自动发音开关（D1）：读改写保存，失败只报存储错误，不改本地状态。 */
    fun setAutoSpeakOnCardReady(enabled: Boolean) = viewModelScope.launch {
        val stored = preferences.get().getOrDefault(SpeechPreference())
        val next = stored.copy(autoSpeakOnCardReady = enabled)
        if (preferences.save(next).isSuccess) {
            _state.value = _state.value.copy(autoSpeakOnCardReady = enabled, message = null)
        } else {
            _state.value = _state.value.copy(message = STORAGE_FAILED_MESSAGE)
        }
    }

    /**
     * 语速四档循环（决策 §2）：0.75x → 1.0x → 1.25x → 1.5x → 0.75x。
     * 起点以**存储值**为准（不信任 UI 快照），非标准值先就近吸附再进下一档；
     * 保存失败保持原状态并报告存储错误——与 setAutoSpeakOnCardReady 同一口径。
     *
     * 批 5 Task 1 起屏上不再有循环入口（语速是滑杆），本函数保留为**同一张档位表的顺序步进**，
     * 与 [setSpeechRate] 共用 [persistSpeechRate]，两条入口不会各写一套保存/回写逻辑。
     */
    fun cycleSpeechRate() = viewModelScope.launch {
        val stored = preferences.get().getOrDefault(SpeechPreference())
        val nearest = nearestStepIndex(stored.speechRate)
        persistSpeechRate(SpeechRateSteps[(nearest + 1) % SpeechRateSteps.size])
    }

    /**
     * 语速滑杆（批 5 Task 1）：滑杆给的是连续值，落盘前吸附到 [SpeechRateSteps] 里最近的一档——
     * 存储里仍只有四档合法值，播放侧读到的倍率与循环时代逐字相同。
     * 拖到当前档位是等值操作，不写盘（但状态里的报告照样收干净）。
     */
    fun setSpeechRate(rate: Float) = viewModelScope.launch {
        persistSpeechRate(SpeechRateSteps[nearestStepIndex(rate)])
    }

    /** 零基下标：把任意倍率吸到档位表里最近的一档（非标准值按绝对差就近）。 */
    private fun nearestStepIndex(rate: Float): Int =
        SpeechRateSteps.indices.minByOrNull { kotlin.math.abs(SpeechRateSteps[it] - rate) } ?: 1

    /** 保存一档倍率并回写状态；等值不写盘，失败保持原状态并报存储错误。 */
    private suspend fun persistSpeechRate(next: Float) {
        val stored = preferences.get().getOrDefault(SpeechPreference())
        if (stored.speechRate == next) {
            _state.value = _state.value.copy(speechRate = next, message = null)
            return
        }
        if (preferences.save(stored.copy(speechRate = next)).isSuccess) {
            _state.value = _state.value.copy(speechRate = next, message = null)
        } else {
            _state.value = _state.value.copy(message = STORAGE_FAILED_MESSAGE)
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
            autoSpeakOnCardReady = _state.value.autoSpeakOnCardReady,
            // v32 同类防线（决策 §6）：预设绑定同样不得把已保存的倍率抹回 1.0。
            speechRate = _state.value.speechRate,
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
            // 历史 OpenAI 发音值已由 Room repository 规范化为 MiMo/System。
            selectedEngine = preference.selectedEngine,
            openAiProfileId = preference.openAiProfileId,
            miMoProfileId = preference.miMoProfileId,
            autoSpeakOnCardReady = preference.autoSpeakOnCardReady,
            speechRate = preference.speechRate,
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
