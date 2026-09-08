package com.example.media

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Real FFmpeg processing used for muxing and MP3 encoding. */
object MediaProcessingUtil {

    suspend fun mergeVideoAndAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!videoFile.exists() || videoFile.length() == 0L) {
            return@withContext Result.failure(Exception("Video input is missing or empty"))
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(Exception("Audio input is missing or empty"))
        }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        // First attempt a stream copy, which is equivalent to yt-dlp's normal
        // bestvideo+bestaudio merge when the selected codecs fit MP4.
        val copyCommand = listOf(
            "-y", "-i", quote(videoFile), "-i", quote(audioFile),
            "-map", "0:v:0", "-map", "1:a:0?", "-c", "copy",
            "-movflags", "+faststart", quote(outputFile)
        ).joinToString(" ")

        val copyResult = execute(copyCommand, onProgress)
        if (copyResult.isSuccess && outputFile.isValidOutput()) {
            return@withContext Result.success(outputFile)
        }

        // Some sources provide codecs which cannot be placed in MP4 with a
        // pure remux. Fall back to a real H.264/AAC transcode rather than
        // producing a misleading file or silently declaring success.
        if (outputFile.exists()) outputFile.delete()
        val transcodeCommand = listOf(
            "-y", "-i", quote(videoFile), "-i", quote(audioFile),
            "-map", "0:v:0", "-map", "1:a:0?",
            "-c:v", "libopenh264", "-b:v", "4M",
            "-c:a", "aac", "-b:a", "192k",
            "-movflags", "+faststart", quote(outputFile)
        ).joinToString(" ")
        val transcodeResult = execute(transcodeCommand, onProgress)
        if (transcodeResult.isSuccess && outputFile.isValidOutput()) {
            Result.success(outputFile)
        } else {
            Result.failure(
                Exception(
                    "FFmpeg could not produce a valid MP4. " +
                        (transcodeResult.exceptionOrNull()?.message ?: copyResult.exceptionOrNull()?.message.orEmpty())
                )
            )
        }
    }

    suspend fun extractAudioToMp3(
        inputFile: File,
        outputFile: File,
        bitrateKbps: Int = 192,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!inputFile.exists() || inputFile.length() == 0L) {
            return@withContext Result.failure(Exception("Audio input is missing or empty"))
        }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val bitrate = bitrateKbps.coerceIn(64, 320)
        val command = listOf(
            "-y", "-i", quote(inputFile),
            "-vn", "-c:a", "libmp3lame", "-b:a", "${bitrate}k",
            quote(outputFile)
        ).joinToString(" ")

        val result = execute(command, onProgress)
        if (result.isSuccess && outputFile.isValidOutput()) {
            Result.success(outputFile)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("FFmpeg MP3 conversion failed"))
        }
    }

    private suspend fun execute(
        command: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        var sessionId = -1L
        try {
            val session = FFmpegKit.executeAsync(
                command,
                { completed ->
                    if (continuation.isCompleted) return@executeAsync
                    val returnCode = completed.returnCode
                    if (ReturnCode.isSuccess(returnCode)) {
                        onProgress(1f)
                        continuation.resume(Result.success(Unit))
                    } else {
                        val output = completed.output?.takeLast(1200).orEmpty()
                        continuation.resume(
                            Result.failure(
                                Exception("FFmpeg failed (code=${returnCode?.value ?: -1}): $output")
                            )
                        )
                    }
                },
                { log ->
                    // FFmpegKit statistics callbacks are intentionally not
                    // parsed into fake percentages. Output validation is the
                    // authoritative completion check.
                    if (!continuation.isCompleted && log != null) Unit
                },
                { statistics ->
                    if (!continuation.isCompleted && statistics != null) {
                        // FFmpegKit's time is not enough to derive a reliable
                        // percentage without duration metadata; keep this
                        // callback deliberately non-authoritative.
                    }
                }
            )
            sessionId = session.sessionId
            continuation.invokeOnCancellation {
                if (sessionId >= 0L) FFmpegKit.cancel(sessionId)
            }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    private fun quote(file: File): String = "'" + file.absolutePath.replace("'", "'\\''") + "'"

    private fun File.isValidOutput(): Boolean = exists() && isFile && length() > 0L
}
