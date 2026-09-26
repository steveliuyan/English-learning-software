package com.example.englishlearning.ui.glass

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.components.glass.GlassDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun showsContentAndDismisses() {
        var dismissed = false
        composeRule.setContent {
            GlassDialog(onDismiss = { dismissed = true }) {
                Text("确认导出？", modifier = Modifier.testTag("glass_dialog_text"))
            }
        }
        composeRule.onNodeWithTag("glass_dialog_text").assertExists()
        composeRule.onNodeWithText("确认导出？").performClick()
        composeRule.waitForIdle()
    }
}
