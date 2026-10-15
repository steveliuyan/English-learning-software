package com.example.englishlearning.notices

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SP-D 资产接线契约：设备上渲染的许可台账**就是**门禁校验过的同一份 markdown。
 * 构建期 Copy 任务（syncThirdPartyNoticesAsset）缺失或未生效时，本测试以
 * FileNotFoundException 失败——这是接线的 RED 证据，不许用兜底空文件掩盖。
 */
@RunWith(AndroidJUnit4::class)
class LedgerAssetSyncTest {
    @Test
    fun shippedAssetParsesAndPassesTheSameValidationAsTheGate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val markdown = context.assets.open("third-party-notices.md").bufferedReader().use { it.readText() }
        val notices = parseThirdPartyNotices(markdown)
        assertTrue("资产台账解析条目异常: ${notices.size}", notices.size >= 50)
        assertEquals("AndroidX Core KTX", notices.first().field("名称"))
        assertEquals(emptyList<String>(), verifyThirdPartyNotices(markdown).map { it.toString() })
    }
}
