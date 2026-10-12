package com.example.englishlearning.language.spike

import java.io.File

object KokoroReviewAudioExporter {
    fun write(directory: File, filename: String, wavBytes: ByteArray): File {
        require(filename.matches(Regex("(?:apple|schedule|record|workbuddy)-(?:us|gb)\\.wav|record-(?:noun|verb)-(?:us|gb)\\.wav"))) { "Invalid review audio filename" }
        require(wavBytes.size >= 44 && wavBytes.copyOfRange(0, 4).contentEquals(byteArrayOf(82, 73, 70, 70))) {
            "Review audio must be a WAV file"
        }
        require(directory.exists() || directory.mkdirs()) { "Review audio directory unavailable" }
        val output = File(directory, filename)
        check(output.createNewFile()) { "Review audio file already exists" }
        try {
            output.outputStream().use { it.write(wavBytes) }
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        }
        return output
    }
}
