package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D-1 真机走查缺陷契约锚：相机权限被拒后的提示不得承诺不存在的「点下方按钮重新授权」，
 * 必须如实指向系统设置，并且页面上真的存在跳转系统设置的入口（testTag +
 * ACTION_APPLICATION_DETAILS_SETTINGS）。防止文案与入口再次脱节。
 */
class PhotoTranslationPermissionContractTest {
    private fun screenSource(): String =
        File("src/main/java/com/example/englishlearning/ui/PhotoTranslationScreen.kt").readText()

    @Test
    fun `denied camera notice points to system settings without promising a nonexistent button`() {
        val source = screenSource()

        assertFalse(source.contains("点下方按钮"), "permission notice must not promise a nonexistent re-authorize button")
        assertTrue(source.contains("系统设置"), "permission notice must honestly point to system settings")
    }

    @Test
    fun `settings entry exists behind the denied camera notice`() {
        val source = screenSource()

        assertTrue(source.contains("photo_translation_permission_settings"), "settings jump button tag missing")
        assertTrue(source.contains("ACTION_APPLICATION_DETAILS_SETTINGS"), "settings intent action missing")
        assertTrue(
            source.contains("photo_translation_permission_hint"),
            "permission notice must remain visible via its hint tag",
        )
    }
}
