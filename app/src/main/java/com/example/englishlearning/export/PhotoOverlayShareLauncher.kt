package com.example.englishlearning.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * 把原位译文覆盖图交给系统分享（F3-04）。
 *
 * 产物只落 `cacheDir/photoshare/`：这是**临时分享物**，不进相册、不登记资产记录，
 * 24 小时后的旧文件在下一次分享时顺手清掉，卸载即随缓存消失。
 * - authority 用运行时包名推导（同 [WorksheetShareLauncher]），换 applicationIdSuffix 也不失效。
 * - URI 只给临时只读权限，并在 chooser 上再声明一次（部分 ROM 的 chooser 不透传内层标志）。
 */
class PhotoOverlayShareLauncher(
    private val context: Context,
) {
    fun createChooser(
        bitmap: Bitmap,
        epochMillis: Long,
    ): Intent {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(dir, PhotoOverlayShareNaming.fileName(epochMillis))
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "png encode failed" }
        }
        pruneOldShares(dir, keep = file, nowMillis = epochMillis)

        val uri = FileProvider.getUriForFile(context, "$PACKAGE_NAME_SUFFIX", file)
        val clip = ClipData.newRawUri("photo-overlay", uri)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = PNG_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "分享原位译文覆盖图").apply {
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun pruneOldShares(
        dir: File,
        keep: File,
        nowMillis: Long,
    ) {
        dir.listFiles()?.forEach { candidate ->
            if (candidate != keep && nowMillis - candidate.lastModified() > SHARE_TTL_MILLIS) {
                candidate.delete()
            }
        }
    }

    private val PACKAGE_NAME_SUFFIX get() = "${context.packageName}.worksheetfiles"

    companion object {
        const val SHARE_DIR = "photoshare"
        private const val PNG_MIME_TYPE = "image/png"
        private const val SHARE_TTL_MILLIS = 24L * 60 * 60 * 1000
    }
}
