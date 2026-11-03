package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiConnectionProbe
import com.example.englishlearning.ai.AiPreferenceRepository
import com.example.englishlearning.ai.AiProfileIdFactory
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.ConnectionProbeResult
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
import kotlinx.coroutines.Job
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
 * 「测试连接」的结论。每条都是常量文案，不含 Endpoint、模型名、响应体或密钥——
 * 沿用 `ai/AiFailure.kt` 那条泄露防线：这一屏的结果直接显示在界面上，能进截图，
 * 一旦允许自由文本流进来，用户填的私网地址或 Key 片段就可能跟着外流。
 *
 * 三态不许互相顶替（决策 `docs/decisions/2026-11-04-ai-profile-vision-and-connection-test.md` §3）：
 * 「太慢」「连不上」「对方活着但答不上来」对应三个不同的处置动作。
 */
enum class AiConnectionOutcome(val message: String) {
    Reachable("连接成功：这台服务可达，密钥也被接受。"),
    Unauthorized("鉴权失败：这台服务不接受当前密钥，请检查 API Key。"),
    Timeout("超时：这台服务在你设定的秒数内没有回应，可稍后重试或调大超时秒数。"),
    NetworkUnavailable("连不上：域名解析失败或网络不通，请检查网络与 Endpoint 域名。"),
    ServiceError("服务异常：地址可达但这台服务没有返回可用结果，可能不支持模型列表接口。"),
    MissingKey("这套配置还没有可测试的密钥：请先填写 API Key。"),
    ConfigurationInvalid("配置还不完整：请先填好 Endpoint 和必填项，再测试连接。"),
}

/**
 * 编辑页上「测试连接」这一块的状态。
 *
 * [AwaitingConfirmation] 只带确认所需的 host（域名披露是 AGENTS.md 的硬要求，必须显示给用户看）；
 * 其余成员一律无字段，界面渲染时不可能捡到敏感值。
 */
sealed interface AiConnectionUiState {
    data object Idle : AiConnectionUiState

    data object Testing : AiConnectionUiState

    /** 该域名本次会话还没确认过：界面据此弹共享出站确认框，拒绝即零出站。 */
    data class AwaitingConfirmation(val host: String) : AiConnectionUiState

    data class Reported(val outcome: AiConnectionOutcome) : AiConnectionUiState
}

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
    /**
     * 「测试连接」的结果。随草稿作废：用户一改输入，上一轮的「可达」就变成对新配置的假证词，
     * 与 [fieldError] / [message] 走同一条清除路径（见 [updateDraft]）。
     */
    val connection: AiConnectionUiState = AiConnectionUiState.Idle,
)

/**
 * 「我的 → AI 服务」里的多套 AI 服务配置。
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
    private val connectionProbe: AiConnectionProbe,
) : ViewModel() {
    private val _listState = MutableStateFlow<AiProfileListUiState>(AiProfileListUiState.Loading)
    val listState: StateFlow<AiProfileListUiState> = _listState

    private val _editor = MutableStateFlow<AiProfileEditorUiState?>(null)
    val editor: StateFlow<AiProfileEditorUiState?> = _editor

    /** 本次编辑会话里已确认过出站的域名（Text 档，与其余 AI 功能同一口径：换域名一定重新问）。 */
    private var confirmedConnectionHost: String? = null
    private var connectionJob: Job? = null

    fun load() {
        _listState.value = AiProfileListUiState.Loading
        viewModelScope.launch { refresh() }
    }

    fun startCreate() {
        connectionJob?.cancel()
        confirmedConnectionHost = null
        _editor.value = AiProfileEditorUiState(
            profileId = null,
            draft = AiProfileDraft(),
            hasStoredKey = false,
        )
    }

    fun startEdit(profileId: String) {
        // 每次进入编辑都是一次新的出站会话：上一轮对某个域名的确认不许顺延到这一轮。
        connectionJob?.cancel()
        confirmedConnectionHost = null
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
        // 连接探活的结果同理，而且更危险：改了 Endpoint 还挂着上一轮的「可达」就是假证词，
        // 所以连同在飞的那次请求一起作废。
        connectionJob?.cancel()
        _editor.value = current.copy(draft = draft, fieldError = null, message = null, connection = AiConnectionUiState.Idle)
    }

    fun closeEditor() {
        // 退出编辑页必须掐掉在飞的探活：它带着密钥，不该在后台继续跑。
        connectionJob?.cancel()
        connectionJob = null
        confirmedConnectionHost = null
        _editor.value = null
    }

    /**
     * 「测试连接」：按 OpenAI 兼容惯例向 `{endpoint}/models` 发一次最小 `GET`。
     *
     * 测的是**屏幕上当前的草稿**，不是库里那份旧配置——用户填完 Key 的第一件事就是想知道
     * 这套新组合通不通，此时读库里的旧值等于答非所问。
     *
     * 顺序是安全要求，不是风格：先用既有 [check] 过一遍表单（不合法就零出站，探活不是绕过校验的后门），
     * 再解析密钥，最后才轮到 [AiConnectionProbe]——而探活自己还会在字节出网吧子上停一次域名确认。
     */
    fun testConnection() = startConnectionProbe()

    /** 出站确认框的答复；拒绝即回到 [AiConnectionUiState.Idle]，一字节都没出去。 */
    fun confirmOutbound(confirmed: Boolean) {
        val current = _editor.value ?: return
        val pending = current.connection as? AiConnectionUiState.AwaitingConfirmation ?: return
        if (!confirmed) {
            _editor.value = current.copy(connection = AiConnectionUiState.Idle)
            return
        }
        // 只记本次会话、且按域名记：换 Endpoint 一定重新问（AiOutboundConfirmation 的既有口径）。
        confirmedConnectionHost = pending.host
        startConnectionProbe()
    }

    private fun startConnectionProbe() {
        val current = _editor.value ?: return
        val check = check(current.draft, current.providerKind)
        if (check is DraftCheck.Invalid) {
            setConnection(AiConnectionUiState.Reported(AiConnectionOutcome.ConfigurationInvalid))
            return
        }
        val parameters = (check as DraftCheck.Valid).parameters
        val profile = current.draft.toProfile(
            current.profileId ?: PROBE_DRAFT_ID,
            parameters,
            current.providerKind,
        )
        val apiKey = resolveProbeKey(current.draft, profile, current.profileId) ?: run {
            setConnection(AiConnectionUiState.Reported(AiConnectionOutcome.MissingKey))
            return
        }
        // 先给界面一个「测试中」，再把网络放进协程：探活最坏等满 timeout_seconds，
        // 期间界面必须照常可编辑（AGENTS.md：核心路径不得阻塞 UI）。
        setConnection(AiConnectionUiState.Testing)
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            val result = connectionProbe.probe(profile, apiKey, confirmedConnectionHost)
            // 一枚 `when` 打尽：探活的每种诚实分类都要对应一句能指导下一步的界面文案，
            // 「超时」「连不上」「服务答不上来」不许折叠成同一个。
            when (result) {
                is ConnectionProbeResult.NeedsConfirmation ->
                    setConnection(AiConnectionUiState.AwaitingConfirmation(result.host))
                ConnectionProbeResult.Reachable -> report(AiConnectionOutcome.Reachable)
                ConnectionProbeResult.Unauthorized -> report(AiConnectionOutcome.Unauthorized)
                ConnectionProbeResult.Timeout -> report(AiConnectionOutcome.Timeout)
                ConnectionProbeResult.NetworkUnavailable -> report(AiConnectionOutcome.NetworkUnavailable)
                ConnectionProbeResult.ServiceError -> report(AiConnectionOutcome.ServiceError)
                ConnectionProbeResult.ConfigurationInvalid -> report(AiConnectionOutcome.ConfigurationInvalid)
            }
        }
    }

    private fun report(outcome: AiConnectionOutcome) = setConnection(AiConnectionUiState.Reported(outcome))

    /**
     * 探活用的密钥：草稿里刚填的优先，其次才是这份配置已存的——新建时库里没有，也不该去猜隔壁的槽。
     * 两条都拿不到就返回 `null`，由调用方报 [AiConnectionOutcome.MissingKey]，绝不发匿名请求。
     */
    private fun resolveProbeKey(
        draft: AiProfileDraft,
        profile: AiProfile,
        profileId: String?,
    ): CharArray? {
        if (draft.pendingKey.isNotEmpty()) return draft.pendingKey.toCharArray()
        // 没有 profileId = 还没落库的新配置，它的密钥槽不存在；读安全存储只会读到别人的。
        if (profileId == null) return null
        return secrets.loadKey(profile).getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun setConnection(state: AiConnectionUiState) {
        val current = _editor.value ?: return
        _editor.value = current.copy(connection = state)
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
        val textCapabilityValid = defaultTextProfile?.let { AiCapability.Text in it.capabilities } == true
        val textHasKey = defaultTextProfile?.let { secrets.hasKey(it).getOrNull() } == true
        val textValid = textCapabilityValid && textHasKey

        val defaultImageId = stored.defaultImageProfileId
        val defaultImageProfile = profilesList.firstOrNull { it.profileId == defaultImageId }
        val imageCapabilityValid = defaultImageProfile?.let { AiCapability.ImageGeneration in it.capabilities } == true
        val imageHasKey = defaultImageProfile?.let { secrets.hasKey(it).getOrNull() } == true
        val imageValid = imageCapabilityValid && imageHasKey

        // 只有 Profile 已不存在或能力不匹配时才清除绑定。缺少 Key / Keystore 暂不可读时保留
        // Profile ID，用户补录 Key 后可以自动恢复；两个默认字段始终独立处理。
        val repaired = stored.copy(
            defaultTextProfileId = defaultTextId.takeUnless { it != null && !textCapabilityValid },
            defaultImageProfileId = defaultImageId.takeUnless { it != null && !imageCapabilityValid },
        )
        if (repaired != stored) {
            preferences.save(repaired)
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

        /**
         * 探活用的是**草稿**，可能还没落库、没有 profileId；[AiProfile] 又要求非空 id。
         * 这里给一个不可能与真实槽撞名的占位：探活只读 endpoint 与 timeout_seconds，
         * 永远不会拿它去碰安全存储（密钥由 [resolveProbeKey] 单独解析后直接传入）。
         */
        const val PROBE_DRAFT_ID = "unsaved-draft"
    }
}
