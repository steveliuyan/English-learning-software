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
import org.junit.Assert.assertTrue
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
        File(cacheRoot, "$id.json").delete()
        File(cacheRoot, "$id.tmp").delete()
        File(cacheRoot, "$id.wav.tmp").delete()
        File(cacheRoot, "$id.json.tmp").delete()
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
        val audio = File(cacheRoot, "${key.cacheId()}.wav")
        val ledger = File(cacheRoot, "${key.cacheId()}.json")
        assertTrue("完整性台账必须与音频同批落盘", ledger.isFile)
        audio.appendBytes(byteArrayOf(9))

        assertNull(FilePronunciationAudioCache(context).get(key))
        assertFalse("拒绝播放必须连带清掉坏音频", audio.exists())
        assertFalse("坏音频的台账也不许留下", ledger.exists())
    }

    @Test
    fun tamperedLedgerIsRejectedAndRemoved() = runTest {
        FilePronunciationAudioCache(context).put(key, byteArrayOf(4, 5, 6))
        val audio = File(cacheRoot, "${key.cacheId()}.wav")
        val ledger = File(cacheRoot, "${key.cacheId()}.json")
        ledger.writeText("""{"schemaVersion":1,"sha256":"not-a-sha256"}""")

        assertNull("台账被改写即无法证明完整性，必须按未命中处理", FilePronunciationAudioCache(context).get(key))
        assertFalse("无有效台账的孤儿 WAV 必须清掉，让它可被重新生成", audio.exists())
        assertFalse(ledger.exists())
    }
}
