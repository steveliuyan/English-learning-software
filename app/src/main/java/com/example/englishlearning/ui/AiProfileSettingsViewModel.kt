package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiProfileIdFactory
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
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
        val check = check(current.draft)
        if (check is DraftCheck.Invalid) {
            _editor.value = current.copy(fieldError = check.error, message = null)
            return
        }
        val parameters = (check as DraftCheck.Valid).parameters
        _editor.value = current.copy(saving = true, fieldError = null, message = null)
        viewModelScope.launch {
            val profileId = current.profileId ?: ids.newId()
            val profile = current.draft.toProfile(profileId, parameters)
            // 顺序不可换：先落元数据。反过来的话，元数据保存失败就会留下一个谁也认领不了的密钥槽。
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
            profiles.delete(profileId)
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
            refresh()
        }
    }

    private suspend fun refresh() {
        _listState.value = refreshItems() ?: AiProfileListUiState.Unavailable
    }

    private suspend fun refreshItems(): AiProfileListUiState.Ready? {
        val loaded = profiles.list()
        if (loaded.isFailure) return null
        return AiProfileListUiState.Ready(loaded.getOrThrow().map { it.toItem() })
    }

    private fun AiProfileListUiState.Ready.withMessage(text: String) = copy(message = text)

    private fun AiProfile.toItem(): AiProfileListItem =
        AiProfileListItem(profile = this, hasKey = secrets.hasKey(this).getOrDefault(false))

    private fun AiProfile.toDraft() = AiProfileDraft(
        displayName = displayName,
        websiteUrl = websiteUrl,
        endpoint = endpoint,
        model = model,
        capabilities = capabilities,
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
    )

    private sealed interface DraftCheck {
        data class Valid(val parameters: AiAdvancedParameters) : DraftCheck
        data class Invalid(val error: AiProfileFieldError) : DraftCheck
    }

    private fun check(draft: AiProfileDraft): DraftCheck {
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
    }
}
