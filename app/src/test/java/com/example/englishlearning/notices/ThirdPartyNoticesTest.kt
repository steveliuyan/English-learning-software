package com.example.englishlearning.notices

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class ThirdPartyNoticesTest {
    @Test
    fun `notice entry missing data flow is rejected`() {
        val markdown = """
            ## androidx-room
            - 名称: Room
            - 版本: 2.x
            - 许可证: Apache-2.0
            - 用途: 本地关系数据
        """.trimIndent()

        assertContains(
            verifyThirdPartyNotices(markdown),
            NoticeValidationError.MissingField("androidx-room", "数据流"),
        )
    }
}
