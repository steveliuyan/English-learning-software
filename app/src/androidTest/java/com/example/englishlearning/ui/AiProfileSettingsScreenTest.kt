package com.example.englishlearning.ui

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.ai.domain.AiVoiceCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiProfileSettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setScreen(
        listState: AiProfileListUiState = AiProfileListUiState.Ready(emptyList()),
        editor: AiProfileEditorUiState? = null,
        onAdd: () -> Unit = {},
        onEdit: (String) -> Unit = {},
        onDraftChange: (AiProfileDraft) -> Unit = {},
        onSave: () -> Unit = {},
        onCloseEditor: () -> Unit = {},
        onRequestDeleteProfile: (String) -> Unit = {},
        onRequestDeleteKey: (String) -> Unit = {},
        onSetDefaultTextProfile: (String) -> Unit = {},
        onSetDefaultImageProfile: (String) -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            AiProfileSettingsScreen(
                listState = listState,
                editor = editor,
                onAdd = onAdd,
                onEdit = onEdit,
                onDraftChange = onDraftChange,
                onSave = onSave,
                onCloseEditor = onCloseEditor,
                onRequestDeleteProfile = onRequestDeleteProfile,
                onRequestDeleteKey = onRequestDeleteKey,
                onSetDefaultTextProfile = onSetDefaultTextProfile,
                onSetDefaultImageProfile = onSetDefaultImageProfile,
                onBack = onBack,
            )
        }
    }

    @Test fun list_renders_every_profile_with_its_key_state() {
        setScreen(
            listState = AiProfileListUiState.Ready(
                listOf(
                    AiProfileListItem(profile("p1", "公司网关"), hasKey = true),
                    AiProfileListItem(profile("p2", "备用服务"), hasKey = false),
                ),
            ),
        )

        composeRule.onNodeWithTag("ai_profiles_screen").assertExists()
        composeRule.onNodeWithText("公司网关").assertExists()
        composeRule.onNodeWithText("备用服务").assertExists()
        // 密钥徽章在可点击的 Card 里，语义会被父节点合并，所以必须查未合并树。
        composeRule.onNodeWithTag("ai_profile_key_state_p1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("已设置密钥").assertExists()
        composeRule.onNodeWithText("还没有设置密钥").assertExists()
    }

    @Test fun list_marks_the_default_profile_and_reports_set_default_callback() {
        var selected: String? = null
        setScreen(
            listState = AiProfileListUiState.Ready(
                listOf(AiProfileListItem(profile("p1", "公司网关"), hasKey = true, isDefaultTextProfile = true)),
            ),
            onSetDefaultTextProfile = { selected = it },
        )

        composeRule.onNodeWithTag("ai_profile_default_p1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("文章默认").assertExists()
        composeRule.onNodeWithTag("ai_profile_set_default_p1").assertDoesNotExist()
    }

    @Test fun list_only_offers_set_default_for_a_qualified_profile() {
        var selected: String? = null
        setScreen(
            listState = AiProfileListUiState.Ready(
                listOf(
                    AiProfileListItem(profile("p1", "有资格"), hasKey = true, canBeDefaultTextProfile = true),
                    AiProfileListItem(profile("p2", "没有密钥"), hasKey = false),
                    AiProfileListItem(profile("p3", "没有文本能力").copy(capabilities = setOf(AiCapability.Speech)), hasKey = true),
                ),
            ),
            onSetDefaultTextProfile = { selected = it },
        )

        composeRule.onNodeWithTag("ai_profile_set_default_p1").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("p1", selected)
        composeRule.onNodeWithTag("ai_profile_set_default_p2").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_profile_set_default_p3").assertDoesNotExist()
    }

    /** 列表把 Endpoint 里最要紧的信息（域名）显示出来，而不是只显示一个模型名。 */
    @Test fun list_shows_the_endpoint_host() {
        setScreen(
            listState = AiProfileListUiState.Ready(
                listOf(AiProfileListItem(profile("p1", "公司网关", "https://gateway.corp.example/v1/chat"), hasKey = true)),
            ),
        )

        composeRule.onNodeWithText("gpt-4o-mini · gateway.corp.example").assertExists()
    }

    @Test fun empty_list_explains_what_is_missing() {
        setScreen(listState = AiProfileListUiState.Ready(emptyList()))

        composeRule.onNodeWithTag("ai_profiles_empty").assertExists()
    }

    @Test fun list_admits_when_the_local_store_cannot_be_read() {
        setScreen(listState = AiProfileListUiState.Unavailable)

        composeRule.onNodeWithTag("ai_profiles_unavailable").assertExists()
    }

    @Test fun add_button_reports_its_callback() {
        var added = 0
        setScreen(onAdd = { added++ })

        composeRule.onNodeWithTag("ai_profiles_add").performScrollTo().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, added)
    }

    @Test fun tapping_a_row_asks_to_edit_that_profile() {
        var edited: String? = null
        setScreen(
            listState = AiProfileListUiState.Ready(listOf(AiProfileListItem(profile("p1", "公司网关"), hasKey = true))),
            onEdit = { edited = it },
        )

        composeRule.onNodeWithTag("ai_profile_item_p1").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("p1", edited)
    }

    @Test fun back_from_the_list_reports_its_callback() {
        var backs = 0
        setScreen(onBack = { backs++ })

        composeRule.onNodeWithContentDescription("返回").assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, backs)
    }

    /**
     * 这一层也能从「AI 学」功能页直达，那时底部选中的是 AI 学栏而不是设置栏，所以返回位不能
     * 写死「返回设置」——那会让用户以为要回设置，实际回到了 AI 学。
     *
     * 批 4 Task 3：旧的「← 返回上一层」文字药片下线，返回位归共享 `AppScreenHeader`（`app_screen_header`
     * 标签 + 可及名「返回」），两枚旧文案必须都不在树上。
     */
    @Test fun back_goes_through_the_shared_header_without_claiming_a_tab() {
        setScreen()

        composeRule.onNodeWithTag("app_screen_header").assertExists()
        composeRule.onNodeWithContentDescription("返回").assertExists()
        composeRule.onNodeWithText("← 返回上一层").assertDoesNotExist()
        composeRule.onNodeWithText("← 返回设置").assertDoesNotExist()
    }

    @Test fun editor_prefills_the_metadata_of_the_profile_being_edited() {
        setScreen(editor = editorState())

        composeRule.onNodeWithTag("ai_profile_editor").assertExists()
        composeRule.onNodeWithText("编辑 AI 配置").assertExists()
        composeRule.onNodeWithText("https://api.example.com/v1").assertExists()
        composeRule.onNodeWithText("gpt-4o-mini").assertExists()
    }

    /**
     * 编辑已有配置时密钥输入框必须是空的：界面拿到的只有「已设置密钥」这个事实，拿不到密钥本身。
     *
     * 断言直接读输入框的**值**（空输入框没有 Text 属性，只能看 EditableText），所以将来若有人给
     * 状态加一个 `storedKey` 字段并回填进输入框，这条会立刻失败。至于 ViewModel 会不会把已保存的
     * 密钥回填到草案里，由
     * `AiProfileSettingsViewModelTest.startEditPrefillsMetadataAndNeverPrefillsTheStoredKey` 负责。
     */
    @Test fun editor_never_puts_a_stored_key_into_the_input() {
        val state = editorState(pendingKey = "", hasStoredKey = true)
        setScreen(editor = state)

        composeRule.onNodeWithTag("ai_profile_key_state").assertExists()
        composeRule.onNodeWithText("已设置密钥").assertExists()
        composeRule.onNodeWithTag("ai_profile_key_input").performScrollTo().assert(isEmptyInput())
        assertEquals("", state.draft.pendingKey)
    }

    @Test fun editor_admits_when_no_key_is_stored_yet() {
        setScreen(editor = editorState(hasStoredKey = false))

        composeRule.onNodeWithText("还没有设置密钥").assertExists()
        composeRule.onNodeWithTag("ai_profile_key_clear").assertDoesNotExist()
    }

    @Test fun editor_typing_a_field_reports_the_updated_draft() {
        var captured: AiProfileDraft? = null
        setScreen(editor = editorState(displayName = ""), onDraftChange = { captured = it })

        composeRule.onNodeWithTag("ai_profile_name").performScrollTo().performTextInput("我的服务")
        composeRule.waitForIdle()

        assertEquals("我的服务", captured?.displayName)
    }

    @Test fun editor_capability_toggle_reports_the_added_and_removed_capability() {
        // 用可变状态驱动：能力是多选，第二次点击必须基于第一次的结果，否则测的就不是真实交互。
        var state by mutableStateOf(editorState())
        var captured: AiProfileDraft? = null
        composeRule.setContent {
            AiProfileSettingsScreen(
                listState = AiProfileListUiState.Ready(emptyList()),
                editor = state,
                onAdd = {},
                onEdit = {},
                onDraftChange = {
                    captured = it
                    state = state.copy(draft = it)
                },
                onSave = {},
                onCloseEditor = {},
                onRequestDeleteProfile = {},
                onRequestDeleteKey = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("ai_profile_capability_vision").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(setOf(AiCapability.Text, AiCapability.Vision), captured?.capabilities)

        composeRule.onNodeWithTag("ai_profile_capability_text").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(setOf(AiCapability.Vision), captured?.capabilities)
    }

    @Test fun editor_surfaces_the_field_error_verbatim() {
        setScreen(editor = editorState(fieldError = AiProfileFieldError.EndpointInvalid))

        composeRule.onNodeWithTag("ai_profile_field_error").performScrollTo().assertExists()
        composeRule.onNodeWithText(AiProfileFieldError.EndpointInvalid.message).assertExists()
    }

    @Test fun editor_save_reports_its_callback() {
        var saves = 0
        setScreen(editor = editorState(), onSave = { saves++ })

        // 批 4 Task 3：保存条吸底，不再在滚动面里，所以不能再 `performScrollTo()`（没有可滚祖先）。
        composeRule.onNodeWithTag("ai_profile_save").assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, saves)
    }

    /** 已有配置才给删除入口。 */
    @Test fun editor_offers_delete_for_an_existing_profile() {
        setScreen(editor = editorState(profileId = "p1"))

        composeRule.onNodeWithTag("ai_profile_delete").performScrollTo().assertExists().assertHasClickAction()
    }

    /**
     * 批 4 Task 3：清除密钥与删除配置都是不可逆动作，**屏内零对话框**——两枚按钮只把请求交给宿主
     * `AppScreen`，由宿主的 `ConfirmDestructiveDialog` 统一确认（`AppScreenOverlayContainerContractTest`
     * 的守卫站点与 `AiProfileLayoutContractTest` 同批钉住）。这里钉住「点了只发请求、自己不执行、
     * 也不自己弹框」。
     */
    @Test fun destructive_actions_only_report_requests_and_never_open_a_dialog_in_screen() {
        var deleted: String? = null
        var cleared: String? = null
        setScreen(
            editor = editorState(profileId = "p1"),
            onRequestDeleteProfile = { deleted = it },
            onRequestDeleteKey = { cleared = it },
        )

        composeRule.onNodeWithTag("ai_profile_key_clear").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("p1", cleared)

        composeRule.onNodeWithTag("ai_profile_delete").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("p1", deleted)

        composeRule.onNodeWithTag("confirm_destructive_confirm").assertDoesNotExist()
        composeRule.onNodeWithTag("confirm_destructive_dismiss").assertDoesNotExist()
    }

    /** 新建时不该出现「删除这套配置」——还没有东西可删。 */
    @Test fun editor_hides_delete_while_creating() {
        setScreen(editor = editorState(profileId = null))

        composeRule.onNodeWithText("新增 AI 配置").assertExists()
        composeRule.onNodeWithTag("ai_profile_delete").assertDoesNotExist()
    }

    @Test fun editor_disables_save_while_a_save_is_in_flight() {
        setScreen(editor = editorState().copy(saving = true))

        composeRule.onNodeWithText("保存中…").assertExists()
        composeRule.onNodeWithTag("ai_profile_save").assertIsNotEnabled()
    }

    /**
     * 音色下拉已改为玻璃浮层（GlassOverlay）：触发行只负责开浮层，选项在浮层面板里，
     * 选中后先回调 `onDraftChange` 再关闭。浮层关闭时选项不组合，所以必须先点开再断言。
     */
    @Test fun voice_picker_opens_the_glass_overlay_and_reports_the_auto_selection() {
        var captured: AiProfileDraft? = null
        setScreen(
            editor = editorState(capabilities = setOf(AiCapability.Text, AiCapability.Speech)),
            onDraftChange = { captured = it },
        )

        composeRule.onNodeWithTag("ai_profile_voice").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_profile_voice_overlay").assertExists()
        composeRule.onNodeWithTag("ai_profile_voice_auto").performClick()
        composeRule.waitForIdle()

        assertEquals("", captured?.voice)
        composeRule.onNodeWithTag("ai_profile_voice_overlay").assertDoesNotExist()
    }

    @Test fun voice_picker_lists_catalog_voices_and_reports_the_selected_voice() {
        var captured: AiProfileDraft? = null
        setScreen(
            editor = editorState(capabilities = setOf(AiCapability.Text, AiCapability.Speech)),
            onDraftChange = { captured = it },
        )

        composeRule.onNodeWithTag("ai_profile_voice").performScrollTo().performClick()
        composeRule.waitForIdle()

        val voice = AiVoiceCatalog.optionsFor(AiProviderKind.OPENAI_COMPATIBLE).first()
        composeRule.onNodeWithTag("ai_profile_voice_${voice}").performClick()
        composeRule.waitForIdle()

        assertEquals(voice, captured?.voice)
    }

    /**
     * 浮层打开时按系统返回键必须只关浮层：编辑器（和未保存的草稿）原地不动。
     * 浮层迁成页内 GlassOverlay 后没有弹窗 Window，返回键直达编辑层 BackHandler 会丢草稿，
     * 所以编辑器里声明了 `BackHandler(enabled = voiceOverlayVisible)` 拦在前面。
     */
    @Test fun back_closes_the_voice_overlay_before_the_editor() {
        var closed = 0
        setScreen(
            editor = editorState(capabilities = setOf(AiCapability.Text, AiCapability.Speech)),
            onCloseEditor = { closed++ },
        )

        composeRule.onNodeWithTag("ai_profile_voice").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_profile_voice_overlay").assertExists()

        pressSystemBack()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("ai_profile_voice_overlay").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("ai_profile_voice").assertExists()
        composeRule.onNodeWithTag("ai_profile_editor").assertExists()
        assertEquals(0, closed)
    }

    private fun pressSystemBack() {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }

    private fun editorState(
        profileId: String? = "p1",
        hasStoredKey: Boolean = true,
        pendingKey: String = "",
        displayName: String = "公司网关",
        fieldError: AiProfileFieldError? = null,
        capabilities: Set<AiCapability> = setOf(AiCapability.Text),
        voice: String = "",
    ) = AiProfileEditorUiState(
        profileId = profileId,
        draft = AiProfileDraft(
            displayName = displayName,
            websiteUrl = "https://example.com",
            endpoint = "https://api.example.com/v1",
            model = "gpt-4o-mini",
            capabilities = capabilities,
            voice = voice,
            pendingKey = pendingKey,
        ),
        hasStoredKey = hasStoredKey,
        fieldError = fieldError,
    )

    /**
     * 输入框的值为空。
     *
     * 不能靠文本断言：空输入框没有 `Text`，加 `assertTextEquals("API Key")` 只会匹配到标签而不是值。
     * 空时不带 `EditableText` 属性，所以「没有属性或属性为空」都算空。
     */
    private fun isEmptyInput() = SemanticsMatcher("输入框的值为空") { node ->
        val value = node.config.getOrNull(SemanticsProperties.EditableText)
        value == null || value.text.isEmpty()
    }

    private fun profile(id: String, name: String, endpoint: String = "https://api.example.com/v1") = AiProfile(
        profileId = id,
        displayName = name,
        websiteUrl = "https://example.com",
        endpoint = endpoint,
        model = "gpt-4o-mini",
        capabilities = setOf(AiCapability.Text),
        secretReference = AiProfileSecretUseCase.referenceFor(id),
    )
}
