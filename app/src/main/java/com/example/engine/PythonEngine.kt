package com.example.engine

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.media.PythonMediaRuntime
import com.example.model.DownloadProgress
import com.example.model.DownloadRequest
import com.example.model.DownloadType
import com.example.model.MediaMetadata
import com.example.model.PlatformType
import com.example.model.QualityOption
import com.example.model.PlaylistItem
import com.example.storage.CookieManager
import com.example.storage.StorageDestinationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Android shell around the original Python downloader engines.
 *
 * Python owns extraction, quality selection, cookies, networking, download,
 * merge/conversion decisions and finalization. Kotlin only supplies Android
 * lifecycle/storage and the native FFmpeg primitive required by Chaquopy.
 */
class PythonEngine(
    context: Context,
    private val destinationManager: StorageDestinationManager,
    private val cookieManager: CookieManager,
    override val platform: PlatformType
) : DownloaderEngine {
    private val appContext = context.applicationContext

    private fun startPython() {
        if (!Python.isStarted()) Python.start(AndroidPlatform(appContext))
    }

    fun supportsPlatform(platform: PlatformType, url: String): Boolean = when (platform) {
        PlatformType.REDGIFS -> host(url).let { it == "redgifs.com" || it.endsWith(".redgifs.com") }
        PlatformType.XXXFOLLOW -> host(url).let { it == "xxxfollow.com" || it.endsWith(".xxxfollow.com") }
        PlatformType.FACEBOOK -> host(url).let { it == "facebook.com" || it.endsWith(".facebook.com") || it == "fb.watch" }
        PlatformType.TWITTER -> host(url).let { it == "twitter.com" || it.endsWith(".twitter.com") || it == "x.com" || it.endsWith(".x.com") }
        PlatformType.YOUTUBE -> host(url).let { it == "youtube.com" || it.endsWith(".youtube.com") || it == "youtu.be" || it.endsWith(".youtu.be") }
    }

    override fun supports(url: String): Boolean = supportsPlatform(platform, url)

    override suspend fun probe(url: String): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        val detected = PlatformType.values().firstOrNull { supportsPlatform(it, url) }
            ?: return@withContext Result.failure(Exception("Unsupported URL or platform: $url"))
        probeForPlatform(url, detected)
    }

    suspend fun probeForPlatform(
        url: String,
        detected: PlatformType,
        forcePlaylist: Boolean = false
    ): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        try {
            startPython()
            val bridge = Python.getInstance().getModule("android_bridge")
            val playlist = if (forcePlaylist) {
                true
            } else {
                detected == PlatformType.YOUTUBE && (url.contains("list=", true) || url.contains("/playlist", true))
            }
            val raw = bridge.callAttr(
                "extract_media",
                url,
                detected.pythonId(),
                cookieManager.getCookieFilePath(detected),
                playlist
            ).toString()
            Result.success(parseMetadata(url, detected, raw))
        } catch (t: Throwable) {
            Result.failure(Exception(clean(t), t))
        }
    }


    suspend fun probePlaylistForPlatform(url: String, detected: PlatformType): Result<MediaMetadata> =
        probeForPlatform(url, detected, forcePlaylist = true).fold(
            onSuccess = { metadata ->
                if (!metadata.isPlaylist || metadata.playlistEntries.isEmpty()) {
                    Result.failure(Exception("This link is not a supported playlist/collection URL."))
                } else {
                    Result.success(metadata)
                }
            },
            onFailure = { Result.failure(it) }
        )

    override suspend fun download(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit): Result<String> =
        withContext(Dispatchers.IO) {
            val tempRoot = File(destinationManager.tempDirectory(), "python_${request.id}").apply {
                deleteRecursively()
                mkdirs()
            }
            try {
                startPython()
                val bridge = Python.getInstance().getModule("android_bridge")
                val callback = ProgressCallback(onProgress)
                val selector = request.quality.id.ifBlank { "best" }
                val runner = PythonMediaRuntime()
                val raw = bridge.callAttr(
                    "download_media",
                    request.url,
                    request.platform.pythonId(),
                    if (request.type == DownloadType.AUDIO) "audio" else "video",
                    selector,
                    tempRoot.absolutePath,
                    cookieManager.getCookieFilePath(request.platform),
                    callback,
                    request.quality.isBest,
                    runner
                ).toString()

                val result = JSONObject(raw)
                val source = File(result.optString("path"))
                if (!source.exists() || source.length() <= 0L) {
                    throw Exception("Python engine returned an empty output file")
                }

                val title = StorageDestinationManager.sanitizeFilename(
                    request.customTitle?.takeIf { it.isNotBlank() }
                        ?: result.optString("title").ifBlank { source.nameWithoutExtension }
                )
                val ext = result.optString("extension").ifBlank { source.extension.ifBlank { "mp4" } }
                val mime = when (ext.lowercase()) {
                    "mp3" -> "audio/mpeg"
                    "webm" -> "video/webm"
                    "gif" -> "image/gif"
                    else -> if (request.type == DownloadType.AUDIO) "audio/mpeg" else "video/mp4"
                }

                copyToDestination(request, source, title, ext, mime, onProgress)
            } catch (t: Throwable) {
                Result.failure(Exception(clean(t), t))
            } finally {
                runCatching { tempRoot.deleteRecursively() }
            }
        }

    private fun copyToDestination(
        request: DownloadRequest,
        source: File,
        title: String,
        ext: String,
        mime: String,
        onProgress: (DownloadProgress) -> Unit
    ): Result<String> {
        val fileName = if (request.playlistName != null && request.itemIndex != null) {
            String.format("%03d - %s.%s", request.itemIndex, title, ext)
        } else {
            "$title.$ext"
        }
        val (out, path) = destinationManager.openOutputStream(
            request.platform,
            request.type,
            fileName,
            mime,
            request.playlistName
        )
        out.use { output ->
            source.inputStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    onProgress(
                        DownloadProgress(
                            bytesDownloaded = copied,
                            totalBytes = source.length(),
                            percentage = if (source.length() > 0) 90f + (copied * 10f / source.length()) else 100f,
                            statusText = "Saving to destination..."
                        )
                    )
                }
            }
        }
        if (!File(path).let { path.startsWith("content://") || (it.exists() && it.length() > 0L) }) {
            // SAF paths are content:// URIs and cannot be validated as java.io.File.
            if (!path.startsWith("content://")) return Result.failure(Exception("Final output is empty"))
        }
        onProgress(DownloadProgress(bytesDownloaded = source.length(), totalBytes = source.length(), percentage = 100f, statusText = "Completed"))
        return Result.success(path)
    }

    private fun parseMetadata(originalUrl: String, detected: PlatformType, raw: String): MediaMetadata {
        val root = JSONObject(raw)
        val videos = mutableListOf<QualityOption>()
        val audios = mutableListOf<QualityOption>()

        val videoJson = root.optJSONArray("video_qualities")
        if (videoJson != null) {
            for (i in 0 until videoJson.length()) {
                val q = videoJson.optJSONObject(i) ?: continue
                val id = q.optString("id")
                if (id.isBlank()) continue
                videos += QualityOption(
                    id = id,
                    label = q.optString("label").ifBlank { id },
                    resolutionOrBitrate = q.optString("value").ifBlank { id },
                    format = q.optString("format").ifBlank { "AUTO" },
                    isBest = q.optBoolean("best") || id == "best"
                )
            }
        }

        val audioJson = root.optJSONArray("audio_qualities")
        if (audioJson != null) {
            for (i in 0 until audioJson.length()) {
                val q = audioJson.optJSONObject(i) ?: continue
                val id = q.optString("id")
                if (id.isBlank()) continue
                audios += QualityOption(
                    id = id,
                    label = q.optString("label").ifBlank { id },
                    resolutionOrBitrate = q.optString("value").ifBlank { id },
                    format = q.optString("format").ifBlank { "SOURCE" },
                    isBest = q.optBoolean("best") || id == "best"
                )
            }
        }

        val entriesJson = root.optJSONArray("entries") ?: org.json.JSONArray()
        val entries = buildList {
            for (i in 0 until entriesJson.length()) {
                val e = entriesJson.optJSONObject(i) ?: continue
                val entryUrl = e.optString("url")
                if (entryUrl.isNotBlank()) {
                    add(PlaylistItem(size + 1, e.optString("title").ifBlank { "Unknown title" }, entryUrl))
                }
            }
        }

        return MediaMetadata(
            url = originalUrl,
            platform = detected,
            title = root.optString("title").ifBlank { "Unknown title" },
            channelOrAuthor = root.optString("uploader").ifBlank { null },
            durationSeconds = root.optLong("duration").takeIf { it > 0 },
            thumbnail = root.optString("thumbnail").ifBlank { null },
            availableVideoQualities = videos,
            availableAudioQualities = audios,
            isPlaylist = root.optBoolean("is_playlist"),
            playlistTitle = root.optString("playlist_title").ifBlank { null },
            playlistEntries = entries
        )
    }

    private fun clean(t: Throwable): String =
        t.message?.lineSequence()?.lastOrNull { it.isNotBlank() }?.trim().orEmpty()
            .ifBlank { "Python engine failed" }

    private fun host(url: String): String = runCatching {
        java.net.URI(if (url.startsWith("http", true)) url else "https://$url")
            .host?.lowercase().orEmpty()
    }.getOrDefault("")

    private fun PlatformType.pythonId() = when (this) {
        PlatformType.REDGIFS -> "redgifs"
        PlatformType.XXXFOLLOW -> "xxxfollow"
        PlatformType.FACEBOOK -> "facebook"
        PlatformType.TWITTER -> "twitter"
        PlatformType.YOUTUBE -> "youtube"
    }

    class ProgressCallback(private val callback: (DownloadProgress) -> Unit) {
        @Suppress("unused")
        fun onProgress(downloaded: Long, total: Long, speed: Long, eta: Int?, percentage: Double, status: String?) {
            callback(
                DownloadProgress(
                    downloaded,
                    total,
                    speed,
                    eta?.toLong(),
                    percentage.coerceIn(0.0, 100.0).toFloat(),
                    status.orEmpty()
                )
            )
        }
    }
}
