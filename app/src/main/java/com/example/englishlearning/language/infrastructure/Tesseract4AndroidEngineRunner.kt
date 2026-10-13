package com.example.englishlearning.language.infrastructure

import android.graphics.BitmapFactory
import com.example.englishlearning.language.domain.OcrImage
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native runner over Tesseract4Android (cz.adaptech, Apache-2.0). The engine needs the model under
 * `<datapath>/tessdata/eng.traineddata`, which matches the layout produced by [FileEnglishOcrModelAssets].
 */
class Tesseract4AndroidEngineRunner : TesseractEngineRunner {
    override suspend fun recognize(
        image: OcrImage,
        traineddataFile: File,
    ): TesseractRunResult =
        withContext(Dispatchers.Default) {
            val datapath = traineddataFile.parentFile?.parentFile?.absolutePath ?: return@withContext TesseractRunResult.InitUnavailable
            val api = TessBaseAPI()
            try {
                if (!api.init(datapath, LANGUAGE)) {
                    return@withContext TesseractRunResult.InitUnavailable
                }
                val bitmap =
                    BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                        ?: throw IllegalArgumentException("Image could not be decoded")
                try {
                    api.setImage(bitmap)
                } finally {
                    bitmap.recycle()
                }
                val lines = readLines(api)
                TesseractRunResult.Blocks(lines = lines, meanConfidence = api.meanConfidence().toFloat())
            } finally {
                api.recycle()
            }
        }

    private fun readLines(
        api: TessBaseAPI,
    ): List<TesseractLine> {
        val iterator = api.resultIterator ?: return fallbackLines(api.getUTF8Text())
        return try {
            val lines = mutableListOf<TesseractLine>()
            iterator.begin()
            do {
                val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)?.trim().orEmpty()
                val rect = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                if (text.isNotEmpty() && rect != null) {
                    lines += TesseractLine(text, rect.left, rect.top, rect.right, rect.bottom)
                }
            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
            lines
        } finally {
            iterator.delete()
        }
    }

    /** Last-resort path when the native iterator is unavailable: text without usable coordinates. */
    private fun fallbackLines(
        rawText: String?,
    ): List<TesseractLine> = rawText.orEmpty().lineSequence().filter { it.isNotBlank() }.map { TesseractLine(it.trim(), 0, 0, 0, 0) }.toList()

    private companion object {
        const val LANGUAGE = "eng"
    }
}
