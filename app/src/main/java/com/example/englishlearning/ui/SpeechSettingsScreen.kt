package com.example.englishlearning.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.components.glass.pressableScale
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppMotion
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.LocalIsDark
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 语音合成（设置二级页）。批 5 Task 1 按 spec §5 的「语音合成」行重排版式，信息架构一字不动：
 *
 *  - **页头**换共享 [AppScreenHeader]（标题 + 说明归页头副标题），旧的自写 `TextButton("返回设置")` 下线；
 *  - **试听卡降为一行**：玻璃卡 [PreviewCard] 本体（含签名与几何）一字未动，只把它挂到一枚 [BareRow]
 *    的折叠体里——默认收起，屏上第一眼只占一行；展开后仍是原来那张卡；
 *  - **开关统一 Switch**：自动发音从「整行 clickable + 末尾一枚「开/关」文字」变成 Material `Switch`，
 *    可及名与锚 `speech_auto_speak_toggle` 逐字保留，`speech_auto_speak_state` 的文字锚随文字一起退役
 *    （开关状态由 `Switch` 自身语义表达，`assertIsOn` / `assertIsOff` 比读一枚 Text 更贴近真值）；
 *  - **语速改 slider + 数值**：点按循环（`onCycleSpeechRate`）退役，换 [Slider] 吸附四档
 *    （[SpeechRateSteps]，与 `SpeechSettingsViewModel.setSpeechRate` 的吸附表同一份）+ 行尾读数；
 *    滑杆的 `contentDescription` / `stateDescription` 仍动态带当前倍率——决策 §4 给 slider 立的口径
 *    （「描述动态带上当前值」）在这里继续成立，读屏用户滑完知道当前是几倍速；
 *  - 分组用 [SoftCard] 三档容器，底色走 `appColors().background`（`MintBackground` 静态桥清零）。
 *
 * 判据见 `SpeechSettingsLayoutContractTest`（版式契约）；玻璃卡的墨色与几何仍由
 * `GlassPanelInkPairingContractTest` / `DarkTokenConsumptionRatchetTest` 冻结。
 */
@Composable
fun SpeechSettingsScreen(
    state: SpeechSettingsUiState,
    onSelect: (PronunciationEngine, String?) -> Unit,
    onOpenAiProfiles: () -> Unit,
    onAddMiMoPreset: () -> Unit,
    onPreview: (String) -> Unit,
    onToggleAutoSpeakOnCardReady: (Boolean) -> Unit = {},
    onSetSpeechRate: (Float) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val domain = appDomain(AppDomain.AiSpeech)
    var previewExpanded by rememberSaveable { mutableStateOf(false) }
    var candidatesExpanded by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("speech_settings_screen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AppScreenHeader(
                    title = "语音合成",
                    subtitle = "选择朗读服务。密钥仅保存在系统安全区。",
                    onBack = onBack,
                )

                // 试听：默认收起成一行。配置对不对最该当场听见，所以它排在最前；但它不该常驻半屏。
                BareRow(
                    title = "试听",
                    subtitle = if (previewExpanded) null else "播放一段文字，配置对不对当场听见。",
                    trailing = if (previewExpanded) "收起" else "展开",
                    modifier = Modifier.testTag("speech_preview_toggle"),
                    onClick = { previewExpanded = !previewExpanded },
                )
                if (previewExpanded) {
                    PreviewCard(state, onPreview)
                }

                Text(text = "朗读服务", style = AppType.Label, color = colors.text3)
                Text(
                    text = "系统朗读用手机自带的声音，离线可用；小米 MiMo 走已保存的 AI 服务，要先在「AI 服务与密钥」里填密钥。",
                    style = AppType.Footnote,
                    color = colors.text2,
                )
                SoftCard {
                    val statuses = state.engineStatuses()
                    EngineRow(
                        name = "系统 TTS",
                        engine = PronunciationEngine.SystemTts,
                        selected = state.selectedEngine,
                        boundProfileId = null,
                        detail = null,
                        onSelect = onSelect,
                    )
                    // OpenAI 引擎行已移除；engineStatuses() 仍计算 openAi 字段供设置页摘要兼容，
                    // 但本页 UI 不再消费。
                    EngineRow(
                        name = "小米 MiMo",
                        engine = PronunciationEngine.MiMo,
                        selected = state.selectedEngine,
                        boundProfileId = state.miMoProfileId,
                        detail = detailFor(state, PronunciationEngine.MiMo, statuses.miMo),
                        onSelect = onSelect,
                    )

                    // 候选配置收进折叠二级菜单：引擎行已经显示绑定的配置名，列表平时不占屏，
                    // 选中即收起——不再出现「上下两处都显示配置」的重复。
                    if (state.selectedEngine != PronunciationEngine.SystemTts) {
                        val candidatesOfEngine = state.candidatesForSelectedEngine()
                        PillButton(
                            text = if (candidatesExpanded) "收起语音配置" else "选择语音配置 ▾",
                            onClick = { candidatesExpanded = !candidatesExpanded },
                            modifier = Modifier.fillMaxWidth(),
                            style = PillStyle.Secondary,
                            testTag = "speech_toggle_candidates",
                        )
                        Column(modifier = Modifier.animateContentSize(tween(AppMotion.Normal, easing = AppMotion.Easing))) {
                            if (candidatesExpanded) {
                                val boundProfileId = when (state.selectedEngine) {
                                    PronunciationEngine.MiMo -> state.miMoProfileId
                                    PronunciationEngine.SystemTts -> null
                                }
                                candidatesOfEngine.forEach { candidate ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSelect(state.selectedEngine, candidate.profileId)
                                                candidatesExpanded = false
                                            }
                                            .padding(vertical = 10.dp)
                                            .testTag("speech_profile_${candidate.profileId}")
                                            .semantics { contentDescription = "选择 ${candidate.displayName}" },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = candidate.displayName,
                                            modifier = Modifier.weight(1f),
                                            style = AppType.Title,
                                            color = colors.text1,
                                        )
                                        if (candidate.profileId == boundProfileId) {
                                            Text(
                                                text = "当前绑定",
                                                modifier = Modifier.testTag("speech_bound_profile_${candidate.profileId}"),
                                                style = AppType.Label,
                                                color = domain.deep,
                                            )
                                        }
                                        Text(
                                            text = if (candidate.status == SpeechProfileStatus.Available) "可用" else "缺少密钥",
                                            style = AppType.Label,
                                            color = colors.text2,
                                        )
                                    }
                                }
                                if (state.selectedEngine == PronunciationEngine.MiMo && candidatesOfEngine.isEmpty()) {
                                    Text(
                                        text = "还没有小米 MiMo 配置。可以一键创建预设：服务地址与模型名已按官方默认值填好，只需再填密钥。",
                                        style = AppType.Footnote,
                                        color = colors.text2,
                                    )
                                    PillButton(
                                        text = "添加小米 MiMo 预设",
                                        onClick = onAddMiMoPreset,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = PillStyle.Primary,
                                        testTag = "speech_add_mimo_preset",
                                    )
                                }
                                PillButton(
                                    text = "AI 服务与密钥",
                                    onClick = onOpenAiProfiles,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = PillStyle.Text,
                                    testTag = "speech_open_ai_profiles",
                                )
                            }
                        }
                    }
                }

                Text(text = "朗读方式", style = AppType.Label, color = colors.text3)
                SoftCard {
                    // 学习时自动发音（D1）：新词卡出现自动读一次；关闭后仅点击「播放发音」触发。
                    //
                    // 版式：标题 + 开关同一行，说明另起一行吃满整宽。说明若留在开关左侧的
                    // `weight(1f)` 列里，会被开关压窄到 20 字上下，「播放发音」这类词组当场被
                    // 折行劈开（真机目视 2026-11-02 修，语速行同一处理）。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "学习时自动发音",
                            style = AppType.Title,
                            color = colors.text1,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.autoSpeakOnCardReady,
                            onCheckedChange = onToggleAutoSpeakOnCardReady,
                            modifier = Modifier
                                .testTag("speech_auto_speak_toggle")
                                .semantics { contentDescription = "学习时自动发音" },
                            colors = SwitchDefaults.colors(
                                // 深套里 `strongSurface`（#22332C）压在 `surface`（#17201C）上，轨道与卡底几乎同色，
                                // 拇指又随默认前景变暗 ⇒ 真机上「开」读不出来。深套改用域强调色（AiSpeech 薄荷），
                                // 浅套保持 `strongSurface` 深绿轨道不动（浅套目视与截图基线零变化）。
                                checkedTrackColor = if (LocalIsDark.current) domain.base else colors.strongSurface,
                                uncheckedTrackColor = colors.tint3,
                            ),
                        )
                    }
                    Text(
                        "新词卡出现时自动朗读一次；关闭后仅点击「播放发音」时朗读。",
                        style = AppType.Footnote,
                        color = colors.text2,
                    )

                    // 语速（F3-02 四控之速）：播放侧变速，不重新生成音频（决策 §2）。
                    //
                    // 滑杆只暴露四档（`SpeechRateSteps`），落盘前由 ViewModel 吸附到最近一档——存储里仍只有
                    // 0.75/1.0/1.25/1.5 四个合法值，播放侧读到的值与循环时代逐字相同。
                    // 描述与 stateDescription 都携带当前档位（I-A1）：滑杆的默认播报是百分比，
                    // 用户滑完不知道这是「倍速」而不是「音量」。
                    val rateIndex = SpeechRateSteps.indices
                        .minByOrNull { abs(SpeechRateSteps[it] - state.speechRate) } ?: 1
                    val rateLabel = speechRateLabel(SpeechRateSteps[rateIndex])
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "发音语速",
                                style = AppType.Title,
                                color = colors.text1,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = rateLabel,
                                modifier = Modifier.testTag("speech_rate_value"),
                                style = AppType.Label,
                                color = domain.deep,
                            )
                        }
                        Text(
                            "只调整本机播放速度，不会重新生成音频；对系统朗读与已缓存发音均生效。",
                            style = AppType.Footnote,
                            color = colors.text2,
                        )
                        Slider(
                            value = rateIndex.toFloat(),
                            onValueChange = { position ->
                                onSetSpeechRate(
                                    SpeechRateSteps[position.roundToInt().coerceIn(0, SpeechRateSteps.lastIndex)],
                                )
                            },
                            valueRange = 0f..SpeechRateSteps.lastIndex.toFloat(),
                            steps = SpeechRateSteps.size - 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("speech_rate_toggle")
                                .semantics {
                                    contentDescription = "发音语速，当前 $rateLabel"
                                    stateDescription = rateLabel
                                },
                            colors = SliderDefaults.colors(
                                // 同开关行的深套取值：`strongSurface` 在深套里与卡底同色，拇指会「消失」。
                                thumbColor = if (LocalIsDark.current) domain.base else colors.strongSurface,
                                activeTrackColor = if (LocalIsDark.current) domain.base else colors.strongSurface,
                                inactiveTrackColor = colors.tint3,
                            ),
                        )
                    }
                }

                state.message?.let {
                    // Task 26 Step 3：错误行收口到共享 `InlineNotice`；旧锚留在外层（组件的标签后设，会覆盖同一 modifier）。
                    Box(Modifier.testTag("speech_message")) {
                        InlineNotice(it, InlineNoticeKind.Error)
                    }
                }
            }
        }
    }
}

/** 语速读数：整档写「1x」，带小数的档写「1.25x」。 */
private fun speechRateLabel(rate: Float): String =
    if (rate == rate.toInt().toFloat()) "${rate.toInt()}x" else "${rate}x"

/** 引擎行右侧的详情：绑定了具体配置就显示「配置名 · 密钥状态」，否则回落到摘要状态。 */
private fun detailFor(state: SpeechSettingsUiState, engine: PronunciationEngine, fallback: String): String? {
    val bound = state.boundCandidateFor(engine) ?: return fallback.removePrefix("当前供应商 · ")
    val keyState = if (bound.status == SpeechProfileStatus.Available) "可用" else "缺少密钥"
    return "${bound.displayName} · $keyState"
}

/**
 * 试听卡（玻璃面板）。批 2 终审修复席 B（task-18fix-b）容器与卡内前景同批迁：
 *  - 容器底 `appColors().surfaceGlass` 随主题换套（浅套取值与旧 `AppPalette.GlassFill` 逐字相同 ⇒ 浅色侧零变化），
 *    描边 `AppPalette.GlassHighlight` 是主题无关蒙版，按台账口径不动；
 *  - 卡内每一枚排印点显式接当前套的墨，输入框容器与指示线一起按套取值——
 *    原来写死的 `Color.White` 与浅套 `surface` 逐字相同，深色档不再出现「深玻璃里的白底输入框」。
 * 判据见 `GlassPanelInkPairingContractTest`（容器—前景同源契约）与本批深色档 connected 取样。
 *
 * 批 5 Task 1 只改了它在屏上的**挂载方式**（收进一枚裸行的折叠体），函数本体与签名一字未动：
 * `GlassPanelInkPairingContractTest` 用 `private fun PreviewCard(` 定位这个窗口，
 * `GlassPanelThemeInkTest` 直接调用它取样。
 */
@Composable
private fun PreviewCard(state: SpeechSettingsUiState, onPreview: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("Hello! 你好，世界！") }
    Surface(
        shape = AppShape.Card,
        color = appColors().surfaceGlass,
        border = BorderStroke(1.dp, AppPalette.GlassHighlight),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("试听", style = AppType.Title, color = appColors().text1)
            Text("用当前选中的朗读服务播放这段文字。", style = AppType.Footnote, color = appColors().text2)
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("speech_preview_input"),
                minLines = 2,
                placeholder = { Text("输入要试听的内容") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = appColors().surface,
                    unfocusedContainerColor = appColors().surface,
                    focusedIndicatorColor = appDomain(AppDomain.AiSpeech).base,
                    unfocusedIndicatorColor = appDomain(AppDomain.AiSpeech).base.copy(alpha = 0.3f),
                ),
            )
            PillButton(
                text = "试听",
                onClick = { onPreview(text) },
                modifier = Modifier.fillMaxWidth(),
                style = PillStyle.Primary,
                enabled = text.isNotBlank(),
                testTag = "speech_preview_button",
                contentDescription = "试听",
            )
            state.previewMessage?.let {
                Text(it, style = AppType.Footnote, color = appColors().text2, modifier = Modifier.testTag("speech_preview_message"))
            }
        }
    }
}

@Composable
private fun EngineRow(
    name: String,
    engine: PronunciationEngine,
    selected: PronunciationEngine,
    boundProfileId: String?,
    detail: String?,
    onSelect: (PronunciationEngine, String?) -> Unit,
) {
    val colors = appColors()
    val domain = appDomain(AppDomain.AiSpeech)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(AppMotion.Normal, easing = AppMotion.Easing))
            .pressableScale(interaction)
            .clickable(interactionSource = interaction, indication = null) { onSelect(engine, boundProfileId) }
            .padding(vertical = 10.dp)
            .testTag("speech_engine_${engine.name.lowercase()}")
            .semantics { contentDescription = "选择 $name" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f), style = AppType.Title, color = colors.text1)
        if (engine == selected) {
            Text(
                "当前供应商",
                modifier = Modifier.testTag("speech_current_${engine.name.lowercase()}"),
                style = AppType.Label,
                color = domain.deep,
            )
        }
        if (detail != null) {
            Text(
                detail,
                modifier = Modifier.padding(start = 8.dp).testTag("speech_engine_status_${engine.name.lowercase()}"),
                style = AppType.Footnote,
                color = colors.text2,
            )
        }
    }
}
