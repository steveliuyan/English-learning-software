package com.example.englishlearning.language

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.language.domain.PronunciationAccent
import com.example.englishlearning.language.domain.PronunciationCacheKey
import com.example.englishlearning.language.infrastructure.FilePronunciationAudioCache
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FilePronunciationAudioCacheTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val key = PronunciationCacheKey(
        text = "cache-integrity-${System.nanoTime()}",
        accent = PronunciationAccent.American,
        providerId = "test-provider",
        model = "test-model",
        voice = "test-voice",
    )
    private lateinit var cacheRoot: File

    @Before
    fun setUp() {
        cacheRoot = File(context.filesDir, "pronunciation-cache")
    }

    @After
    fun tearDown() {
        val id = key.cacheId()
        File(cacheRoot, "$id.wav").delete()
        File(cacheRoot, "$id.sha256").delete()
        File(cacheRoot, "$id.tmp").delete()
    }

    @Test
    fun successfulWriteCanBeReadBackWithExactBytes() = runTest {
        val bytes = byteArrayOf(0, 1, 2, 127, -1)

        FilePronunciationAudioCache(context).put(key, bytes)

        assertArrayEquals(bytes, FilePronunciationAudioCache(context).get(key))
    }

    @Test
    fun modifiedAudioIsRejectedAndRemoved() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        FilePronunciationAudioCache(context).put(key, bytes)
        File(cacheRoot, "${key.cacheId()}.wav").appendBytes(byteArrayOf(9))

        assertNull(FilePronunciationAudioCache(context).get(key))
        assertFalse(File(cacheRoot, "${key.cacheId()}.wav").exists())
        assertFalse(File(cacheRoot, "${key.cacheId()}.sha256").exists())
    }

    @Test
    fun modifiedChecksumIsRejectedAndRemoved() = runTest {
        FilePronunciationAudioCache(context).put(key, byteArrayOf(4, 5, 6))
        File(cacheRoot, "${key.cacheId()}.sha256").writeText("not-a-sha256")

        assertNull(FilePronunciationAudioCache(context).get(key))
        assertFalse(File(cacheRoot, "${key.cacheId()}.wav").exists())
        assertFalse(File(cacheRoot, "${key.cacheId()}.sha256").exists())
    }
}
