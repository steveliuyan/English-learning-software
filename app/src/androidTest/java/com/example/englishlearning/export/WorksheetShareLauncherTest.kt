package com.example.englishlearning.export

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorksheetShareLauncherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun sharingPdfUsesReadOnlyContentUri() {
        val file = File(context.cacheDir, "worksheets/test.pdf").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }

        val chooser = WorksheetShareLauncher(context).createChooser(file)
        val target = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)

        assertNotNull(target)
        requireNotNull(target)
        assertEquals("application/pdf", target.type)
        assertTrue(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("content", target.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.scheme)
    }
}
