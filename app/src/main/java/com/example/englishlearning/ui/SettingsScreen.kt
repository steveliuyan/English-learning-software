package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.EntryIconTone
import com.example.englishlearning.ui.components.EntryRow
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppThemePreferences
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.ThemeMode
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.rememberAppThemeMode

data class SpeechEngineStatuses(
    val miMo: String = "未配置",
    val openAi: String = "未配置",
    val zipVoice: String = "未下载",
    val currentProvider: String = "系统 TTS",
)

fun SpeechSettingsUiState.engineStatuses(): SpeechEngineStatuses {
    // 状态按协议匹配的候选派生：OpenAI 槽位只认 OPENAI_COMPATIBLE，MiMo 槽位只认
    // XIAOMI_MIMO。把通用 Profile 算进 MiMo、或把 MiMo 预设算进 OpenAI，都会把
    // 「根本发不出正确请求的绑定」误报成已配置。
    fun status(profileId: String?, kind: AiProviderKind): String {
        val kindCandidates = candidates.filter { it.providerKind == kind }
        return when {
            profileId == null && kindCandidates.any { it.status == SpeechProfileStatus.Available } -> "可选配置 · 未绑定"
            profileId == null -> "未配置"
            kindCandidates.firstOrNull { it.profileId == profileId }?.status == SpeechProfileStatus.Available -> "已配置"
            kindCandidates.any { it.profileId == profileId } -> "缺少密钥"
            else -> "绑定失效"
        }
    }
    val openAiStatus = status(openAiProfileId, AiProviderKind.OPENAI_COMPATIBLE)
    val miMoStatus = status(miMoProfileId, AiProviderKind.XIAOMI_MIMO)
    return SpeechEngineStatuses(
        openAi = openAiStatus,
        miMo = if (selectedEngine == PronunciationEngine.MiMo) "当前供应商 · $miMoStatus" else miMoStatus,
        currentProvider = when (selectedEngine) {
            PronunciationEngine.SystemTts -> "系统 TTS"
            PronunciationEngine.MiMo -> "小米 MiMo"
        },
    )
}

/**
 * 一条设置行的静态描述（AC-R-21 的 IA 数据）。
 *
 * [icon] 可为 `null`：「主题」行是分段选择卡而不是入口行（稿 §7④），没有行首徽标位；
 * 其余八行都必须给 [AppIcon] 白名单内的字形——设置页禁止任何文字徽标（AI / ♪ / ⏰ / ℹ / ⭕ / ⚠ / ›）。
 */
data class SettingsEntry(
    val title: String,
    val tag: String,
    val icon: AppIcon?,
    val domain: AppDomain,
)

/** 设置一级页的一个分组（标题 + 行清单）。 */
data class SettingsGroup(
    val title: String,
    val tag: String,
    val entries: List<SettingsEntry>,
)

/**
 * 设置页信息架构的唯一台账（AC-R-21 / 裁决②）：**恰 4 组、恰 9 行**。
 *
 * 落位：「学习」3 行（词书与每日目标 / 学习提醒 / 生成默写纸）、「AI 与语音」2 行、
 * 「外观与关于」2 行（主题三态 / 开源许可与隐私说明）、「数据与安全」2 行
 * （词书内容包、备份与恢复——两行都是折叠入口，展开后才见 SAF 与危险操作子行）。
 * 「学习提醒」按裁决②归「学习」组，不再错挂 AI 组；「账户」整组与两条占位行已删。
 *
 * JVM 侧 `SettingsNoDeveloperJargonTest` 钉本表形状；connected 侧
 * `SettingsScreenTest` 逐条核对这些 tag 真的渲染出来——两边对不上都会红。
 */
object SettingsGroups {
    val all: List<SettingsGroup> = listOf(
        SettingsGroup(
            title = "学习",
            tag = "settings_group_learning",
            entries = listOf(
                SettingsEntry("词书与每日目标", "settings_open_setup", AppIcon.BookOpen, AppDomain.Learn),
                SettingsEntry("学习提醒", "settings_open_reminder", AppIcon.Clock, AppDomain.Review),
                SettingsEntry("生成默写纸", "settings_open_worksheet", AppIcon.Document, AppDomain.Learn),
            ),
        ),
        SettingsGroup(
            title = "AI 与语音",
            tag = "settings_group_ai_speech",
            entries = listOf(
                SettingsEntry("AI 服务与密钥", "settings_open_ai_profiles", AppIcon.Spark, AppDomain.AiSpeech),
                SettingsEntry("语音朗读", "settings_open_speech", AppIcon.Speaker, AppDomain.AiSpeech),
            ),
        ),
        SettingsGroup(
            title = "外观与关于",
            tag = "settings_group_appearance",
            entries = listOf(
                SettingsEntry("主题", "settings_theme_mode", null, AppDomain.Settings),
                SettingsEntry("开源许可与隐私说明", "settings_open_licenses", AppIcon.Info, AppDomain.Settings),
            ),
        ),
        SettingsGroup(
            title = "数据与安全",
            tag = "settings_group_data_security",
            entries = listOf(
                SettingsEntry("词书内容包", "settings_word_books", AppIcon.Upload, AppDomain.Library),
                SettingsEntry("备份与恢复", "settings_backup", AppIcon.Shield, AppDomain.Settings),
            ),
        ),
    )
}

/**
 * 按 tag 取台账里的一行（Task 19 修复轮 1 / I3：**渲染侧不得再抄一份行标题、图标或域色**）。
 *
 * tag 是这一页的身份证：`SettingsEntry` 的 title/icon/domain 都只写在 [SettingsGroups] 一处，
 * 调用处只报 tag + 动态部分（副标题、可点性、展开态、回调）。找不到就是编程错误，当场点名下标，
 * 不做静默兜底（兜底会把它变成第二套真值）。
 */
private fun settingsEntry(tag: String): SettingsEntry =
    SettingsGroups.all.flatMap { it.entries }.firstOrNull { it.tag == tag }
        ?: error("设置页台账里没有 tag「$tag」的一级行（AC-R-21 的 9 行形状与渲染侧对不上）")

/** 按 tag 取台账里的一个分组（同上：不用 `all[0..3]` 下标，版面重排不漂）。 */
private fun settingsGroup(tag: String): SettingsGroup =
    SettingsGroups.all.firstOrNull { it.tag == tag }
        ?: error("设置页台账里没有 tag「$tag」的分组")

/**
 * 台账行 → [EntryRow] 的唯一装配点：标题、图标、域色、testTag 全部取自 [SettingsEntry]，
 * 调用处只提供动态部分。折叠入口另给 [expanded]（chevron 与展开态语义由它驱动），
 * SAF 在途或未导入时给 [enabled] = false（禁用态真禁用，见 `EntryRow` KDoc）。
 */
@Composable
private fun LedgerEntryRow(
    tag: String,
    subtitle: String?,
    onClick: () -> Unit,
    iconTone: EntryIconTone = EntryIconTone.Tint,
    topDivider: Boolean = false,
    enabled: Boolean = true,
    expanded: Boolean? = null,
) {
    val entry = settingsEntry(tag)
    EntryRow(
        icon = requireNotNull(entry.icon) { "台账行「${entry.title}」没有图标：分段卡不走本装配点" },
        domain = entry.domain,
        title = entry.title,
        subtitle = subtitle,
        iconTone = iconTone,
        topDivider = topDivider,
        enabled = enabled,
        expanded = expanded,
        modifier = Modifier.testTag(entry.tag),
        onClick = onClick,
    )
}

/** 三态在界面上的用户语言标签（存储键仍走 `ThemeMode.storageKey`，这里只是文案）。 */
private val ThemeMode.userLabel: String
    get() = when (this) {
        ThemeMode.System -> "跟随系统"
        ThemeMode.Light -> "浅色"
        ThemeMode.Dark -> "深色"
    }

/**
 * 「设置」一级页（AC-R-21）：**4 组 9 行**，静态台账见 [SettingsGroups]。
 *
 * - 资料卡刻意**不显示任何今日计划数字**：当日统计的职责归打卡页（裁决③），
 *   而 `newTarget` 在词书新词学完时会小于用户配置的每日目标（PRD FR-01 第 8 条），
 *   把它当「每日新增」印在设置页更是错的。资料卡只报昵称与词书名。
 * - 「词书内容包」仍收成**一行折叠入口**：展开后才是原来的 SAF 子行（导入/导出词书），
 *   chevron 随展开态取 `ChevronDown`/`ChevronRight`，展开态经 `EntryRow(expanded = …)` 注册成
 *   `SemanticsActions.Expand`/`Collapse`（AC-R-12）。**结果提示住在折叠行外侧**（修复轮 1 / I4）：
 *   回执是「上一次操作的结果」，收起态也必须读得到，不该被折叠藏起来。
 *   SAF 拉起链路一行未动，本屏只改呈现层级。
 * - 「备份与恢复」是**导航行**（裁决⑥）：点击进入 [BackupHubScreen] 三节子页（导出 / 恢复 /
 *   删除全部学习数据），不再就地折叠。删除行只在子页末节出现，级联预览 + 二次确认仍由
 *   AppScreen 宿主承载（F4-01 Spec 04 L18），语义不变。
 * - 「主题」为三态分段选择：选中即 `AppThemePreferences.write`，回读经
 *   [rememberAppThemeMode] 的监听让整棵树即时换套，无需重启。
 * - 九行标题、图标、域色与 testTag 只在 [SettingsGroups] 定义一处，渲染侧按 tag 经
 *   [settingsEntry] 取（修复轮 1 / I3：台账不得是第二套真值）。
 *
 * @param importedBookCount 已导入的词书册数：0 时导出子行**真禁用**（`enabled = false`）并说明前提。
 * @param transferBusy 导入/导出进行中：两枚 SAF 子行一律**真禁用**（不挂 click、语义「不可用」、
 *   副标题显示处理中），避免重复拉起文件选择器；一级 9 行不受牵连，始终可点。
 * @param transferMessage 导入/导出的一句结果提示，`null` 时不占位；在「词书内容包」折叠行**外侧**呈现
 *   （收起态也读得到）。档位仍取 `InlineNoticeKind.Info`：宿主传来的是裸 `String`，不做文本嗅探。
 * @param onOpenBackupHub 「备份与恢复」导航入口：打开三节子页 [BackupHubScreen]（裁决⑥）。
 *   删除回执与删除回调都在子页，本屏不再持有它们。
 */
@Composable
fun SettingsScreen(
    profileName: String,
    wordBookName: String?,
    onOpenSetup: () -> Unit,
    onOpenWorksheet: () -> Unit,
    onOpenAiProfiles: () -> Unit,
    onOpenSpeechSettings: () -> Unit = {},
    onOpenReminderSettings: () -> Unit = {},
    /** 备份与恢复导航入口：打开三节子页（留档导出 / 认证导出 / 认证恢复 / 删除全部学习数据）。 */
    onOpenBackupHub: () -> Unit = {},
    /** 已配置的 AI 服务摘要；`null` 表示还没读出本地配置。 */
    aiProfileSubtitle: String? = null,
    speechEngineStatuses: SpeechEngineStatuses = SpeechEngineStatuses(),
    /** 词书导入入口：拉起系统文件选择器（SAF），文件由用户挑。 */
    onImportWordBook: () -> Unit = {},
    /** 词书导出入口：拉起系统「另存为」（SAF）。 */
    onExportWordBook: () -> Unit = {},
    importedBookCount: Int = 0,
    transferBusy: Boolean = false,
    transferMessage: String? = null,
    /** F5-04「关于与开源许可」入口（AC5-04）：必须是真实可达页面，不许做假入口。 */
    onOpenLicenses: () -> Unit = {},
) {
    val colors = appColors()
    val context = LocalContext.current
    val themeMode = rememberAppThemeMode()
    var wordBooksExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            // 底部导航会吃掉一截高度，小屏或大字号下整页必须可滚动，否则最后一组被裁掉。
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = "设置",
            subtitle = "学习、AI、语音、数据与外观。API Key 只保存在本机加密区。",
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.outlineSoft, RoundedCornerShape(18.dp))
                .testTag("settings_profile_card"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(profileName, style = AppType.Title, color = colors.text1)
                Text(
                    text = wordBookName ?: "尚未选择词书",
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.testTag("settings_profile_word_book"),
                )
            }
        }

        // 「学习」组三条入口行按稿 §7④：词书与每日目标走强档渐变底，其余弱档 tint；
        // 「学习提醒」保持导航行（开关状态在二级页），副标题沿用既有文案。
        // 标题/图标/域色一律从台账取（修复轮 1 / I3），这里只写动态的副标题与回调。
        SettingsGroupSection(settingsGroup("settings_group_learning")) {
            LedgerEntryRow(
                tag = "settings_open_setup",
                subtitle = "换词书、改每日新增量、改反馈后的详情开关",
                iconTone = EntryIconTone.Gradient,
                onClick = onOpenSetup,
            )
            LedgerEntryRow(
                tag = "settings_open_reminder",
                subtitle = "每日提醒，默认关闭（非精确、每天最多一次）",
                topDivider = true,
                onClick = onOpenReminderSettings,
            )
            LedgerEntryRow(
                tag = "settings_open_worksheet",
                subtitle = "选中译英或英译中，预览后导出可打印 PDF",
                topDivider = true,
                onClick = onOpenWorksheet,
            )
        }

        // 「AI 与语音」是这一页唯一的云服务入口组：一级页只放两个可点入口，引擎状态与
        // 「待接入」厂商属于二级页的信息量——它们在一级页占过近一半屏，用户学习成本过高。
        SettingsGroupSection(settingsGroup("settings_group_ai_speech")) {
            LedgerEntryRow(
                tag = "settings_open_ai_profiles",
                subtitle = aiProfileSubtitle ?: "添加多套 OpenAI 兼容服务，管理 Endpoint、模型与密钥",
                iconTone = EntryIconTone.Gradient,
                onClick = onOpenAiProfiles,
            )
            LedgerEntryRow(
                tag = "settings_open_speech",
                subtitle = "当前供应商：${speechEngineStatuses.currentProvider}",
                topDivider = true,
                onClick = onOpenSpeechSettings,
            )
        }

        // 「外观与关于」：主题三态分段卡（稿 §7④「主题」卡）+ 许可入口行，两张卡共用一个组标题。
        // 分段选中即写偏好；[rememberAppThemeMode] 的监听让装配点即时换套。
        SettingsGroupSectionHeader(settingsGroup("settings_group_appearance"))
        ThemeModeRow(mode = themeMode, onSelect = { AppThemePreferences.write(context, it) })
        SettingsGroupCard {
            LedgerEntryRow(
                tag = "settings_open_licenses",
                subtitle = "第三方组件、许可证与隐私说明",
                onClick = onOpenLicenses,
            )
        }

        // 词书内容包与备份与恢复各收成一行折叠入口（R-B）：SAF 子行只在展开区出现，
        // busy / 未导入的子行是真禁用（`enabled`，不是「点了没反应」），一级 9 行始终可点。
        // 结果提示住在折叠行**外侧**（修复轮 1 / I4）：收起态也读得到回执，不必先展开。
        SettingsGroupSection(settingsGroup("settings_group_data_security")) {
            LedgerEntryRow(
                tag = "settings_word_books",
                subtitle = "导入 / 导出 .wbpack · 全离线",
                expanded = wordBooksExpanded,
                onClick = { wordBooksExpanded = !wordBooksExpanded },
            )
            if (wordBooksExpanded) {
                EntryRow(
                    icon = AppIcon.Upload,
                    domain = AppDomain.Library,
                    title = "导入词书",
                    subtitle = if (transferBusy) {
                        "正在处理，请稍候…"
                    } else {
                        "选择 `.wbpack` 文件，校验通过后加入本机词书"
                    },
                    iconTone = EntryIconTone.Tint,
                    topDivider = true,
                    enabled = !transferBusy,
                    modifier = Modifier.testTag("settings_import_word_book"),
                    onClick = onImportWordBook,
                )
                EntryRow(
                    icon = AppIcon.Download,
                    domain = AppDomain.Library,
                    title = "导出词书",
                    subtitle = when {
                        transferBusy -> "正在处理，请稍候…"
                        importedBookCount > 0 -> "已导入 $importedBookCount 册，导出为 `.wbpack` 分享给别的设备"
                        else -> "先导入一册词书后才能导出"
                    },
                    iconTone = EntryIconTone.Tint,
                    topDivider = true,
                    enabled = !transferBusy && importedBookCount > 0,
                    modifier = Modifier.testTag("settings_export_word_book"),
                    onClick = onExportWordBook,
                )
            }
            transferMessage?.let { message ->
                InlineNotice(
                    text = message,
                    kind = InlineNoticeKind.Info,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // 裁决⑥：「备份与恢复」是导航行——四枚备份入口与删除行都搬进子页三节，
            // 本屏不再持有折叠态、删除回调与删除回执（回执在子页内呈现）。
            LedgerEntryRow(
                tag = "settings_backup",
                subtitle = "导出本机文件、换机恢复与清空学习数据",
                topDivider = true,
                onClick = onOpenBackupHub,
            )
        }
    }
}

/**
 * 主题三态选择卡（稿 §7④「主题」分段控件）：纯展示组件——[mode] 进、[onSelect] 出，
 * 写偏好与回读由调用方接线（`AppThemePreferences.write` + `rememberAppThemeMode()`）。
 *
 * 三档标签固定为「跟随系统 / 浅色 / 深色」；每档可点面 `heightIn(min = 48.dp)`（F5-03 触控下限）。
 *
 * 卡标题与卡片 testTag 都取自台账里「主题」那一行（修复轮 1 / I3：这一页的行标题只许定义一处）；
 * 本件不消费 [EntryRow]，所以不走 [LedgerEntryRow]，但同样只报 tag、不抄标题。
 */
@Composable
fun ThemeModeRow(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = appColors()
    val themeEntry = settingsEntry("settings_theme_mode")
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineSoft, RoundedCornerShape(18.dp))
            .testTag(themeEntry.tag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = themeEntry.title,
                style = AppType.Body,
                fontWeight = FontWeight.Bold,
                color = colors.text1,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.outlineSoft, AppShape.Button)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ThemeMode.entries.forEach { option ->
                    val selected = option == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) colors.surface else Color.Transparent)
                            .clickable { onSelect(option) }
                            .heightIn(min = 48.dp)
                            .testTag("settings_theme_${option.storageKey}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.userLabel,
                            style = AppType.Footnote,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) colors.text1 else colors.text2,
                        )
                    }
                }
            }
            Text(
                text = "切换后立即生效，仅影响本机显示，不属于学习数据。",
                style = AppType.Footnote,
                color = colors.text2,
            )
        }
    }
}

/** 分组标题（`.sec-title`）：标题文字与 tag 都取自 [SettingsGroups] 台账，别在调用处另写一份。 */
@Composable
private fun SettingsGroupSectionHeader(group: SettingsGroup) {
    Text(
        text = group.title,
        style = AppType.Body,
        fontWeight = FontWeight.Bold,
        color = appColors().text1,
        modifier = Modifier.testTag(group.tag),
    )
}

/** 分组卡壳：标题 + `surface` 容器卡（18dp 圆角 + `outlineSoft` 描边），行内容交给 [content]。 */
@Composable
private fun SettingsGroupSection(group: SettingsGroup, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsGroupSectionHeader(group)
        SettingsGroupCard(content = content)
    }
}

/** 只有卡壳、不带标题的分组容器（供「外观与关于」这类「标题下两张卡」的组用）。 */
@Composable
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = appColors()
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineSoft, RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            content = content,
        )
    }
}
