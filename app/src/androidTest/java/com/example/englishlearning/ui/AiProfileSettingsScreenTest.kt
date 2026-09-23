package com.example.englishlearning.ui

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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
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
        onDeleteProfile: (String) -> Unit = {},
        onDeleteKey: (String) -> Unit = {},
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
                onDeleteProfile = onDeleteProfile,
                onDeleteKey = onDeleteKey,
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

        composeRule.onNodeWithText("← 返回上一层").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, backs)
    }

    /**
     * 这一层也能从「AI 学」功能页直达，那时底部选中的是 AI 学栏而不是设置栏，所以返回按钮不能
     * 写死「返回设置」——那会让用户以为要回设置，实际回到了 AI 学。
     */
    @Test fun back_label_does_not_claim_a_specific_tab() {
        setScreen()

        composeRule.onNodeWithText("← 返回上一层").assertExists()
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
                onDeleteProfile = {},
                onDeleteKey = {},
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

        composeRule.onNodeWithTag("ai_profile_save").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, saves)
    }

    /** 已有配置才给删除入口。 */
    @Test fun editor_offers_delete_for_an_existing_profile() {
        setScreen(editor = editorState(profileId = "p1"))

        composeRule.onNodeWithTag("ai_profile_delete").performScrollTo().assertExists().assertHasClickAction()
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
        composeRule.onNodeWithTag("ai_profile_save").performScrollTo().assertIsNotEnabled()
    }

    private fun editorState(
        profileId: String? = "p1",
        hasStoredKey: Boolean = true,
        pendingKey: String = "",
        displayName: String = "公司网关",
        fieldError: AiProfileFieldError? = null,
    ) = AiProfileEditorUiState(
        profileId = profileId,
        draft = AiProfileDraft(
            displayName = displayName,
            websiteUrl = "https://example.com",
            endpoint = "https://api.example.com/v1",
            model = "gpt-4o-mini",
            capabilities = setOf(AiCapability.Text),
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
