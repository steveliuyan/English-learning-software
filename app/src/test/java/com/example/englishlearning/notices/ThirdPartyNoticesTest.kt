package com.example.englishlearning.notices

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class ThirdPartyNoticesTest {
    @Test
    fun `notice entry missing data flow is rejected`() {
        assertContains(
            verifyThirdPartyNotices(validNotice("")),
            NoticeValidationError.MissingField("androidx-room", "数据流"),
        )
    }

    @Test
    fun `notice entry with blank data flow is rejected`() {
        assertContains(
            verifyThirdPartyNotices(validNotice("   ")),
            NoticeValidationError.MissingField("androidx-room", "数据流"),
        )
    }

    @Test
    fun `notice entry with blank license is rejected`() {
        assertContains(
            verifyThirdPartyNotices(validNotice("本地设备内数据，不传输网络", "   ")),
            NoticeValidationError.MissingField("androidx-room", "许可证"),
        )
    }

    private fun validNotice(dataFlow: String, license: String = "Apache-2.0") = """
        ## androidx-room
        - 名称: Room
        - 版本: 2.x
        - 许可证: $license
        - 用途: 本地关系数据
        - 数据流: $dataFlow
        - NOTICE 位置: 本文件
        - 替代方案: SQLiteDatabase
        - 商业分发结论: 可商业分发
    """.trimIndent()
}
