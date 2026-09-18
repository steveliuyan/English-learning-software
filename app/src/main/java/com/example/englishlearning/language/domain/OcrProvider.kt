package com.example.englishlearning.language.domain

/** Local text recognition boundary; translation belongs to a separate future step. */
interface OcrProvider {
    fun recognize(): OcrResult
}

data class OcrResult(
    val text: String,
    val regions: List<OcrRegion>,
)

data class OcrRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float,
)
