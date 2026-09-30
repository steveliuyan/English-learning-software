package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiPreferenceRepository
import com.example.englishlearning.ai.AiProfileIdFactory
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiPreference
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.ai.domain.AiVoiceCatalog
import com.example.englishlearning.ai.domain.validateEndpoint
import com.example.englishlearning.ai.validateRequestParameters
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AiProfileListUiState {
    data object Loading : AiProfileListUiState

    data class Ready(
        val items: List<AiProfileListItem>,
        /** 删除之类「不在编辑页里发生」的操作留下的提示。 */
        val message: String? = null,
    ) : AiProfileListUiState

    data object Unavailable : AiProfileListUiState
}

data class AiProfileListItem(
    val profile: AiProfile,
    val hasKey: Boolean,
    val isDefaultTextProfile: Boolean = false,
    val canBeDefaultTextProfile: Boolean = false,
    val isDefaultImageProfile: Boolean = false,
    val canBeDefaultImageProfile: Boolean = false,
)

/**
 * 表单错误。每条都是常量文案，不含用户填进去的原文——Endpoint 本身就可能是敏感信息片段，
 * 回显进错误提示就会被截图带走。
 */
enum class AiProfileFieldError(val message: String) {
    NameRequired("请填写配置名称。"),
    WebsiteInvalid("官网地址要以 http:// 或 https:// 开头。"),
    EndpointInvalid("Endpoint 必须是 https 公网地址，且不能带账号密码。"),
    ModelRequired("请填写要调用的模型名。"),
    CapabilityRequired("至少选择一种能力。"),
    VoiceInvalid("语音角色不在该服务的可选列表里。"),
    ParameterNotNumeric("高级参数请填写数字。"),
    ParameterOutOfRange("高级参数超出允许范围。"),
}

/**
 * 编辑页上的原始输入。
 *
 * 高级参数存字符串而不是数字：输入框里随时可能是「热一点」这种中间状态，解析失败要能如实
 * 报错，而不是静默退回默认值。
 *
 * [pendingKey] 只在内存里存活，保存成功即随编辑页一起丢弃；它**永远**不回填已保存的密钥。
 */
data class AiProfileDraft(
    val displayName: String = "",
    val websiteUrl: String = "",
    val endpoint: String = "",
    val model: String = "",
    val capabilities: Set<AiCapability> = setOf(AiCapability.Text),
    /** 语音角色；空串 = 自动。仅当能力含「语音」时在编辑器里出现。 */
    val voice: String = "",
    val temperature: String = AiAdvancedParameters().temperature.toString(),
    val topP: String = AiAdvancedParameters().topP.toString(),
    val maxTokens: String = AiAdvancedParameters().maxTokens.toString(),
    val timeoutSeconds: String = AiAdvancedParameters().timeoutSeconds.toString(),
    val pendingKey: String = "",
)

data class AiProfileEditorUiState(
    /** `null` 表示正在新建。 */
    val profileId: String?,
    val draft: AiProfileDraft,
    val hasStoredKey: Boolean,
    /**
     * 协议身份随编辑会话保存：草稿只覆盖用户可见的字段，`providerKind` 不在表单里，
     * 保存时必须从这里原样带回——否则编辑一次 MiMo 预设就会被重置成 OpenAI 兼容，
     * MiMo 引擎按协议过滤候选时它就「消失」了（真机实证过）。
     */
    val providerKind: AiProviderKind = AiProviderKind.OPENAI_COMPATIBLE,
    val saving: Boolean = false,
    val fieldError: AiProfileFieldError? = null,
    val message: String? = null,
)

/**
 * 「设置 · AI」里的多套 AI 服务配置。
 *
 * 写库顺序是这个类最要紧的部分，改之前先看 [save] 与 [deleteProfile] 上的注释：密钥与元数据的
 * 落库/清除顺序错了，会分别留下孤儿密钥和「配置已删、密钥还在」两种很难被发现的状态。
 */
@HiltViewModel
class AiProfileSettingsViewModel @Inject constructor(
    private val profiles: AiProfileRepository,
    private val secrets: AiProfileSecretUseCase,
    private val ids: AiProfileIdFactory,
    private val preferences: AiPreferenceRepository,
) : ViewModel() {
    private val _listState = MutableStateFlow<AiProfileListUiState>(AiProfileListUiState.Loading)
    val listState: StateFlow<AiProfileListUiState> = _listState

    private val _editor = MutableStateFlow<AiProfileEditorUiState?>(null)
    val editor: StateFlow<AiProfileEditorUiState?> = _editor

    fun load() {
        _listState.value = AiProfileListUiState.Loading
        viewModelScope.launch { refresh() }
    }

    fun startCreate() {
        _editor.value = AiProfileEditorUiState(
            profileId = null,
            draft = AiProfileDraft(),
            hasStoredKey = false,
        )
    }

    fun startEdit(profileId: String) {
        viewModelScope.launch {
            val found = profiles.find(profileId).getOrNull() ?: return@launch
            _editor.value = AiProfileEditorUiState(
                profileId = found.profileId,
                draft = found.toDraft(),
                hasStoredKey = secrets.hasKey(found).getOrDefault(false),
                providerKind = found.providerKind,
            )
        }
    }

    fun updateDraft(draft: AiProfileDraft) {
        val current = _editor.value ?: return
        // 用户一动输入，上一次的报错就该消失，否则会一直挂着一个已经修好的错误。
        _editor.value = current.copy(draft = draft, fieldError = null, message = null)
    }

    fun closeEditor() {
        _editor.value = null
    }

    fun save() {
        val current = _editor.value ?: return
        val check = check(current.draft, current.providerKind)
        if (check is DraftCheck.Invalid) {
            _editor.value = current.copy(fieldError = check.error, message = null)
            return
        }
        val parameters = (check as DraftCheck.Valid).parameters
        _editor.value = current.copy(saving = true, fieldError = null, message = null)
        viewModelScope.launch {
            val profileId = current.profileId ?: ids.newId()
            val profile = current.draft.toProfile(profileId, parameters, current.providerKind)            // 顺序不可换：先落元数据。反过来的话，元数据保存失败就会留下一个谁也认领不了的密钥槽。
            if (profiles.save(profile).isFailure) {
                _editor.value = current.copy(saving = false, message = STORAGE_FAILED_MESSAGE)
                return@launch
            }
            if (current.draft.pendingKey.isNotEmpty()) {
                val keyed = secrets.saveKey(profile, current.draft.pendingKey.toCharArray())
                if (keyed.isFailure) {
                    // 配置本身已经存下了，如实说清只有密钥没写成功，并让编辑页开着以便重试。
                    _editor.value = current.copy(saving = false, message = KEY_FAILED_MESSAGE)
                    refresh()
                    return@launch
                }
            }
            // 编辑页连同 pendingKey 一起丢掉，密钥不在界面状态里多留一秒。
            _editor.value = null
            refresh()
        }
    }

    fun setDefaultTextProfile(profileId: String) {
        viewModelScope.launch {
            val found = profiles.find(profileId).getOrNull() ?: return@launch
            if (AiCapability.Text !in found.capabilities || !secrets.hasKey(found).getOrDefault(false)) {
                _listState.value = refreshItems()?.withMessage(DEFAULT_PROFILE_INVALID_MESSAGE)
                    ?: AiProfileListUiState.Unavailable
                return@launch
            }
            // 读改写而不是整表覆盖：生图默认是另一个独立字段，不能被文本默认保存抹掉。
            val current = preferences.get().getOrNull() ?: AiPreference()
            if (preferences.save(current.copy(defaultTextProfileId = profileId)).isFailure) {
                _listState.value = refreshItems()?.withMessage(STORAGE_FAILED_MESSAGE)
                    ?: AiProfileListUiState.Unavailable
                return@launch
            }
            refresh()
        }
    }

    fun setDefaultImageProfile(profileId: String) {
        viewModelScope.launch {
            val found = profiles.find(profileId).getOrNull() ?: return@launch
            if (AiCapability.ImageGeneration !in found.capabilities || !secrets.hasKey(found).getOrDefault(false)) {
                _listState.value = refreshItems()?.withMessage(DEFAULT_IMAGE_PROFILE_INVALID_MESSAGE)
                    ?: AiProfileListUiState.Unavailable
                return@launch
            }
            // 读改写：文本默认是另一个独立字段，不能被生图默认保存抹掉。
            val current = preferences.get().getOrNull() ?: AiPreference()
            if (preferences.save(current.copy(defaultImageProfileId = profileId)).isFailure) {
                _listState.value = refreshItems()?.withMessage(STORAGE_FAILED_MESSAGE)
                    ?: AiProfileListUiState.Unavailable
                return@launch
            }
            refresh()
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            val found = profiles.find(profileId).getOrNull() ?: return@launch
            // 顺序不可换：先清密钥。反过来的话元数据已经删了、密钥还在读得出来，等于留下一个
            // 没人管得了的可用凭据。
            if (secrets.deleteKey(found).isFailure) {
                _listState.value = refreshItems()?.withMessage(STORAGE_FAILED_MESSAGE)
                    ?: AiProfileListUiState.Unavailable
                return@launch
            }
            if (profiles.delete(profileId).isFailure) {
                _listState.value = refreshItems()?.withMessage(STORAGE_FAILED_MESSAGE)
                    ?: AiProfileListUiState.Unavailable
                return@launch
            }
            clearDefaultBindingFor(profileId)
            _editor.value = null
            refresh()
        }
    }

    fun deleteKey(profileId: String) {
        viewModelScope.launch {
            val found = profiles.find(profileId).getOrNull() ?: return@launch
            if (secrets.deleteKey(found).isFailure) {
                _listState.value = refreshItems()?.withMessage(STORAGE_FAILED_MESSAGE)
                    ?: AiProfileListUiState.Unavailable
                return@launch
            }
            clearDefaultBindingFor(profileId)
            refresh()
        }
    }

    /** 删除配置/密钥后把两个默认里指向它的字段清掉，另一个字段原样保留。 */
    private suspend fun clearDefaultBindingFor(profileId: String) {
        val current = preferences.get().getOrNull() ?: return
        if (current.defaultTextProfileId != profileId && current.defaultImageProfileId != profileId) return
        preferences.save(
            current.copy(
                defaultTextProfileId = current.defaultTextProfileId.takeIf { it != profileId },
                defaultImageProfileId = current.defaultImageProfileId.takeIf { it != profileId },
            ),
        )
    }

    private suspend fun refresh() {
        _listState.value = refreshItems() ?: AiProfileListUiState.Unavailable
    }

    private suspend fun refreshItems(): AiProfileListUiState.Ready? {
        val loaded = profiles.list()
        val preference = preferences.get()
        if (loaded.isFailure || preference.isFailure) return null
        val profilesList = loaded.getOrThrow()
        val stored = preference.getOrThrow()

        val defaultTextId = stored.defaultTextProfileId
        val defaultTextProfile = profilesList.firstOrNull { it.profileId == defaultTextId }
        val textValid = defaultTextProfile != null &&
            AiCapability.Text in defaultTextProfile.capabilities &&
            secrets.hasKey(defaultTextProfile).getOrDefault(false)

        val defaultImageId = stored.defaultImageProfileId
        val defaultImageProfile = profilesList.firstOrNull { it.profileId == defaultImageId }
        val imageValid = defaultImageProfile != null &&
            AiCapability.ImageGeneration in defaultImageProfile.capabilities &&
            secrets.hasKey(defaultImageProfile).getOrDefault(false)

        // 失效清除只清各自的字段：文本默认失效不能连带清掉生图默认，反之亦然。
        if (defaultTextId != null && !textValid) {
            preferences.save(stored.copy(defaultTextProfileId = null, defaultImageProfileId = defaultImageId.takeIf { imageValid }))
        }
        if (defaultImageId != null && !imageValid) {
            preferences.save(stored.copy(defaultImageProfileId = null, defaultTextProfileId = defaultTextId.takeIf { textValid }))
        }

        val effectiveTextId = defaultTextId.takeIf { textValid }
        val effectiveImageId = defaultImageId.takeIf { imageValid }
        return AiProfileListUiState.Ready(profilesList.map { it.toItem(effectiveTextId, effectiveImageId) })
    }

    private fun AiProfileListUiState.Ready.withMessage(text: String) = copy(message = text)

    private fun AiProfile.toItem(defaultTextId: String?, defaultImageId: String?): AiProfileListItem {
        val hasKey = secrets.hasKey(this).getOrDefault(false)
        return AiProfileListItem(
            profile = this,
            hasKey = hasKey,
            isDefaultTextProfile = profileId == defaultTextId && AiCapability.Text in capabilities && hasKey,
            canBeDefaultTextProfile = AiCapability.Text in capabilities && hasKey,
            isDefaultImageProfile = profileId == defaultImageId && AiCapability.ImageGeneration in capabilities && hasKey,
            canBeDefaultImageProfile = AiCapability.ImageGeneration in capabilities && hasKey,
        )
    }

    private fun AiProfile.toDraft() = AiProfileDraft(
        displayName = displayName,
        websiteUrl = websiteUrl,
        endpoint = endpoint,
        model = model,
        capabilities = capabilities,
        voice = voice,
        temperature = advancedParameters.temperature.toString(),
        topP = advancedParameters.topP.toString(),
        maxTokens = advancedParameters.maxTokens.toString(),
        timeoutSeconds = advancedParameters.timeoutSeconds.toString(),
        // 已保存的密钥不回显：界面上只出现「已设置 / 未设置」，不出现密钥本身。
        pendingKey = "",
    )

    private fun AiProfileDraft.toProfile(
        profileId: String,
        parameters: AiAdvancedParameters,
        providerKind: AiProviderKind,
    ) = AiProfile(
        profileId = profileId,
        displayName = displayName.trim(),
        websiteUrl = websiteUrl.trim(),
        endpoint = endpoint.trim(),
        model = model.trim(),
        capabilities = capabilities,
        // 别名只由 profileId 推导，与 Endpoint / 模型 / 名称无关：换 Endpoint 也就绝不会读到
        // 别的 Profile 的密钥槽。
        secretReference = AiProfileSecretUseCase.referenceFor(profileId),
        advancedParameters = parameters,
        providerKind = providerKind,
        voice = voice.trim(),
    )

    private sealed interface DraftCheck {
        data class Valid(val parameters: AiAdvancedParameters) : DraftCheck
        data class Invalid(val error: AiProfileFieldError) : DraftCheck
    }

    private fun check(draft: AiProfileDraft, providerKind: AiProviderKind): DraftCheck {
        if (draft.displayName.isBlank()) return DraftCheck.Invalid(AiProfileFieldError.NameRequired)
        if (draft.websiteUrl.isNotBlank() &&
            !draft.websiteUrl.startsWith("http://") &&
            !draft.websiteUrl.startsWith("https://")
        ) {
            return DraftCheck.Invalid(AiProfileFieldError.WebsiteInvalid)
        }
        // 复用出站策略本身的校验，而不是在界面里再写一遍 https/私网判断，避免两套规则漂移。
        if (validateEndpoint(draft.endpoint.trim()).isFailure) {
            return DraftCheck.Invalid(AiProfileFieldError.EndpointInvalid)
        }
        if (draft.model.isBlank()) return DraftCheck.Invalid(AiProfileFieldError.ModelRequired)
        if (draft.capabilities.isEmpty()) return DraftCheck.Invalid(AiProfileFieldError.CapabilityRequired)
        if (!AiVoiceCatalog.isValid(providerKind, draft.voice.trim())) {
            return DraftCheck.Invalid(AiProfileFieldError.VoiceInvalid)
        }
        val raw = draft.toParameterMap() ?: return DraftCheck.Invalid(AiProfileFieldError.ParameterNotNumeric)
        val parameters = validateRequestParameters(raw)
            .getOrElse { return DraftCheck.Invalid(AiProfileFieldError.ParameterOutOfRange) }
        return DraftCheck.Valid(parameters)
    }

    private fun AiProfileDraft.toParameterMap(): Map<String, Any?>? {
        val temperature = temperature.trim().toDoubleOrNull() ?: return null
        val topP = topP.trim().toDoubleOrNull() ?: return null
        val maxTokens = maxTokens.trim().toIntOrNull() ?: return null
        val timeoutSeconds = timeoutSeconds.trim().toIntOrNull() ?: return null
        return mapOf(
            "temperature" to temperature,
            "top_p" to topP,
            "max_tokens" to maxTokens,
            "timeout_seconds" to timeoutSeconds,
            "system_prompt_template" to AiAdvancedParameters().systemPromptTemplateId,
        )
    }

    private companion object {
        const val STORAGE_FAILED_MESSAGE = "本机存储暂时不可用，改动没有保存。"
        const val KEY_FAILED_MESSAGE = "配置已保存，但密钥没能写入安全存储。"
        const val DEFAULT_PROFILE_INVALID_MESSAGE = "这套配置需要文本能力和已保存的密钥，才能设为文章默认。"
        const val DEFAULT_IMAGE_PROFILE_INVALID_MESSAGE = "这套配置需要生图能力和已保存的密钥，才能设为生图默认。"
    }
}
