package com.example.englishlearning.language.infrastructure

import android.graphics.BitmapFactory
import com.example.englishlearning.language.domain.OcrBlockOrientation
import com.example.englishlearning.language.domain.OcrImage
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Device client for the bundled ML Kit Latin text recognizer.
 * The model is statically linked at build time (docs/decisions/2026-10-13-f3-03-ocr-provider-licenses.md);
 * recognition runs on-device, but ML Kit may send aggregate performance telemetry — disclosed in settings.
 */
class MlKitTextRecognizerClient : MlTextRecognitionClient {
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(
        image: OcrImage,
    ): List<MlClientBlock> {
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
            ?: throw IllegalArgumentException("Image could not be decoded")
        return try {
            // Confidence and bounding boxes are exposed per line (TextBlock itself carries neither),
            // and line granularity is what the low-confidence marking in the UI needs anyway.
            // F3-04 additionally needs the line text next to its box, so the domain region keeps both.
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitOrThrow().textBlocks
                .flatMap { block -> block.lines }
                .map { line ->
                    val box = line.boundingBox
                    MlClientBlock(
                        text = line.text,
                        left = box?.left ?: 0,
                        top = box?.top ?: 0,
                        right = box?.right ?: 0,
                        bottom = box?.bottom ?: 0,
                        confidence = line.confidence.takeIf { it > 0f },
                        // 行框高于宽即判竖排（竖排 CJK 最常见的几何特征）；其余一律横排。
                        orientation =
                            if (box != null && box.height() > box.width()) {
                                OcrBlockOrientation.Vertical
                            } else {
                                OcrBlockOrientation.Horizontal
                            },
                    )
                }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun <T> Task<T>.awaitOrThrow(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { continuation.resume(it) }
            addOnFailureListener { continuation.resumeWithException(it) }
            addOnCanceledListener { continuation.resumeWithException(CancellationException("Text recognition task cancelled")) }
        }
}
