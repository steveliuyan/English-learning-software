package com.example.englishlearning.export

import android.content.Context
import android.content.Intent
import android.net.Uri
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
        val chooser = WorksheetShareLauncher(context).createChooser(cachedWorksheet("test"))
        val target = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)

        assertNotNull(target)
        requireNotNull(target)
        assertEquals("application/pdf", target.type)
        assertTrue(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("content", target.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.scheme)
    }

    @Test
    fun chooserAlsoOffersOpeningThePdfWithAViewer() {
        val chooser = WorksheetShareLauncher(context).createChooser(cachedWorksheet("open"))

        @Suppress("DEPRECATION")
        val initialIntents = chooser.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS)
        val view = initialIntents?.filterIsInstance<Intent>()?.firstOrNull()

        assertNotNull(view)
        requireNotNull(view)
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals("application/pdf", view.type)
        assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        // authority 必须与 Manifest 里的 ${applicationId}.worksheetfiles 一致，否则接收方拿不到读权限。
        assertEquals("${context.packageName}.worksheetfiles", view.data?.authority)
        assertEquals("content", view.data?.scheme)
    }

    @Test
    fun chooserItselfCarriesTheReadGrantSoTheSystemSheetCanForwardIt() {
        val chooser = WorksheetShareLauncher(context).createChooser(cachedWorksheet("grant"))

        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("content", chooser.clipData?.getItemAt(0)?.uri?.scheme)
    }

    private fun cachedWorksheet(name: String) = File(context.cacheDir, "worksheets/$name.pdf").apply {
        parentFile?.mkdirs()
        writeBytes(byteArrayOf(1))
    }
}
