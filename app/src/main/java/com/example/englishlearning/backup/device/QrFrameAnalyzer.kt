package com.example.englishlearning.backup.device

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * CameraX analyser that decodes QR frames locally with ZXing and reports every
 * distinct payload at most once. No frame or decoded content is ever stored.
 */
class QrFrameAnalyzer(
    private val onQrPayload: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }
    private var lastPayload: String? = null

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val bytes = plane.buffer.let { buffer ->
                val array = ByteArray(buffer.remaining())
                buffer.get(array)
                array
            }
            val source = PlanarYUVLuminanceSource(
                bytes,
                plane.rowStride,
                image.height,
                0,
                0,
                image.width.coerceAtMost(plane.rowStride),
                image.height,
                false,
            )
            val payload = try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
            } catch (_: Exception) {
                null
            } finally {
                reader.reset()
            }
            if (payload != null && payload != lastPayload) {
                lastPayload = payload
                onQrPayload(payload)
            }
        } finally {
            image.close()
        }
    }
}
