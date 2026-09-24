package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.reading.ImportRejection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleImportScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun showsFieldsDisclaimerAndSubmit() {
        composeRule.setContent {
            ArticleImportScreen(
                state = ImportUiState(),
                onTitleChange = {},
                onBodyChange = {},
                onImport = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("import_title").assertExists()
        composeRule.onNodeWithTag("import_body").assertExists()
        // 责任声明必须明确「你有权使用」；不出网的承诺也要说清。
        composeRule.onNodeWithText("你有权使用", substring = true).assertExists()
        composeRule.onNodeWithText("不会发起任何网络请求", substring = true).assertExists()
        composeRule.onNodeWithTag("import_submit").assertExists()
    }

    @Test
    fun rejectionShowsTheSpecificReason() {
        composeRule.setContent {
            ArticleImportScreen(
                state = ImportUiState(rejection = ImportRejection.BodyTooShort),
                onTitleChange = {},
                onBodyChange = {},
                onImport = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("import_rejection_reason").assertExists()
        composeRule.onNodeWithText("至少需要 40 个单词", substring = true).assertExists()
    }

    @Test
    fun fieldsAndSubmitWireThroughTheCallbacks() {
        var imports = 0
        // 受控 TextField 必须用可变状态驱动重组：普通局部 var 不触发重组，输入会被丢弃。
        var title by androidx.compose.runtime.mutableStateOf("")
        var body by androidx.compose.runtime.mutableStateOf("")
        composeRule.setContent {
            ArticleImportScreen(
                state = ImportUiState(title = title, body = body),
                onTitleChange = { title = it },
                onBodyChange = { body = it },
                onImport = { imports++ },
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("import_title").performTextInput("My Notes")
        composeRule.onNodeWithTag("import_body").performTextInput("Some english text goes here.")
        composeRule.waitForIdle()
        // 受控更新真正生效的证据：输入框自己显示的就是回调回写后的文本。
        composeRule.onNodeWithTag("import_title").assertTextContains("My Notes")
        composeRule.onNodeWithTag("import_body").assertTextContains("Some english text goes here.")
        composeRule.onNodeWithTag("import_submit").performClick()
        composeRule.waitForIdle()
        assertEquals(1, imports)
    }

    @Test
    fun storageFailureShowsAnExplicitMessage() {
        composeRule.setContent {
            ArticleImportScreen(
                state = ImportUiState(storageFailed = true),
                onTitleChange = {},
                onBodyChange = {},
                onImport = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("import_storage_failed").assertExists()
        composeRule.onNodeWithText("本地存储暂时不可用", substring = true).assertExists()
    }

    @Test
    fun backUsesTheMultiEntryLabel() {
        composeRule.setContent {
            ArticleImportScreen(
                state = ImportUiState(),
                onTitleChange = {},
                onBodyChange = {},
                onImport = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("import_back").assertExists()
        composeRule.onNodeWithText("← 返回上一层").assertExists()
    }
}
