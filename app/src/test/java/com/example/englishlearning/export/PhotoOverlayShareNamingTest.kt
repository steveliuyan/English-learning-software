package com.example.englishlearning.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** F3-04 产物文件名：确定性、可排序、不带路径分隔符（临时区清理按前缀匹配）。 */
class PhotoOverlayShareNamingTest {
    @Test
    fun fileNameIsDeterministicAndTimestamped() {
        assertEquals("photo-translation-1731110400000.png", PhotoOverlayShareNaming.fileName(1731110400000L))
    }

    @Test
    fun fileNameHasNoPathSeparators() {
        val name = PhotoOverlayShareNaming.fileName(1L)

        assertTrue(name.none { it == '/' || it == '\\' || it == ':' })
        assertTrue(name.endsWith(".png"))
    }
}
