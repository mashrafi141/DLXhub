package com.example.media

import kotlinx.coroutines.runBlocking

/**
 * Very small native primitive exposed to the Python backend.
 *
 * Python remains the owner of the downloader workflow: it chooses formats,
 * downloads streams, decides when a mux/extract is required, and asks this
 * object only to execute the Android-native FFmpeg runtime that Chaquopy
 * cannot provide as a standalone executable.
 */
class PythonMediaRuntime {
    fun mergeVideoAudio(videoPath: String, audioPath: String, outputPath: String) {
        val result = runBlocking {
            MediaProcessingUtil.mergeVideoAndAudio(
                java.io.File(videoPath),
                java.io.File(audioPath),
                java.io.File(outputPath)
            )
        }
        result.getOrElse { throw it }
    }

    fun extractAudio(inputPath: String, outputPath: String, bitrateKbps: Int) {
        val result = runBlocking {
            MediaProcessingUtil.extractAudioToMp3(
                java.io.File(inputPath),
                java.io.File(outputPath),
                bitrateKbps
            )
        }
        result.getOrElse { throw it }
    }
}
