package com.example.coordinator

import android.content.Context
import com.example.database.AppDatabase
import com.example.database.DownloadEntity
import com.example.engine.EngineRegistry
import com.example.model.DownloadItem
import com.example.model.DownloadProgress
import com.example.model.DownloadRequest
import com.example.model.DownloadState
import com.example.model.DownloadType
import com.example.model.MediaMetadata
import com.example.model.PlatformType
import com.example.model.QualityOption
import com.example.service.DownloadForegroundService
import com.example.storage.CookieManager
import com.example.storage.StorageDestinationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class DownloadCoordinator(
    private val context: Context,
    val destinationManager: StorageDestinationManager = StorageDestinationManager(context),
    val cookieManager: CookieManager = CookieManager(context)
) {
    val engineRegistry = EngineRegistry(context, destinationManager, cookieManager)
    private val database = AppDatabase.getInstance(context)
    private val dao = database.downloadDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueLock = Any()

    private var queueJob: Job? = null
    private var activeDownloadJob: Deferred<Result<String>>? = null

    private val _queue = MutableStateFlow<List<DownloadItem>>(emptyList())
    val queue: StateFlow<List<DownloadItem>> = _queue.asStateFlow()
    private val _activeDownload = MutableStateFlow<DownloadItem?>(null)
    val activeDownload: StateFlow<DownloadItem?> = _activeDownload.asStateFlow()

    val history = dao.getAllDownloads().map { list -> list.map { it.toDownloadItem() } }
    private val requestMap = mutableMapOf<String, DownloadRequest>()

    suspend fun probeUrl(url: String): Result<MediaMetadata> {
        val engine = engineRegistry.resolveEngine(url)
            ?: return Result.failure(Exception("Unsupported URL or platform: $url"))
        return engine.probe(url)
    }

    suspend fun probePlaylistUrl(url: String): Result<MediaMetadata> {
        val detected = engineRegistry.detectPlatform(url)
            ?: return Result.failure(Exception("Unsupported URL or platform: $url"))
        val engine = engineRegistry.engines.firstOrNull { it.platform == detected } as? com.example.engine.PythonEngine
            ?: return Result.failure(Exception("Playlist engine unavailable for ${detected.displayName}"))
        return engine.probePlaylistForPlatform(url.trim(), detected)
    }

    fun enqueueDownload(
        url: String,
        platform: PlatformType,
        type: DownloadType,
        quality: QualityOption,
        title: String,
        playlistName: String? = null,
        itemIndex: Int? = null,
        totalItems: Int? = null
    ) {
        val item = DownloadItem(
            id = UUID.randomUUID().toString(), url = url, title = title,
            platform = platform, type = type, qualityLabel = quality.label,
            state = DownloadState.QUEUED
        )
        val request = DownloadRequest(
            id = item.id, url = url, platform = platform, type = type,
            quality = quality, customTitle = title, playlistName = playlistName,
            itemIndex = itemIndex, totalItems = totalItems
        )
        _queue.value = _queue.value + item
        synchronized(queueLock) { requestMap[item.id] = request }
        ensureQueueProcessor()
    }

    fun enqueueBatch(urls: List<String>, type: DownloadType, quality: QualityOption) {
        val cleanUrls = urls.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanUrls.isEmpty()) return

        // The CLI batch flow always lets each engine resolve the real media
        // title before downloading. Do the same here so the queue/history never
        // shows synthetic "Batch Media #N" names.
        scope.launch {
            cleanUrls.forEachIndexed { index, url ->
                val detected = engineRegistry.detectPlatform(url)
                if (detected == null) {
                    val item = DownloadItem(
                        id = UUID.randomUUID().toString(), url = url,
                        title = "Unsupported URL #${index + 1}",
                        platform = PlatformType.YOUTUBE, type = type,
                        qualityLabel = quality.label, state = DownloadState.FAILED,
                        errorMessage = "Unsupported URL or platform: $url"
                    )
                    _queue.value = _queue.value + item
                    dao.insertOrUpdate(DownloadEntity.fromDownloadItem(item))
                    return@forEachIndexed
                }

                val engine = engineRegistry.resolveEngine(url)
                val metadata = runCatching { engine?.probe(url)?.getOrThrow() }.getOrNull()
                val title = metadata?.title?.takeIf { it.isNotBlank() } ?: "Media #${index + 1}"

                enqueueDownload(
                    url = url, platform = detected, type = type, quality = quality,
                    title = title,
                    itemIndex = null, totalItems = cleanUrls.size
                )
            }
        }
    }

    private fun ensureQueueProcessor() {
        synchronized(queueLock) {
            if (queueJob?.isActive == true) return
            queueJob = scope.launch { processQueue() }
        }
    }

    /** Single authoritative queue worker. It never recursively calls itself. */
    private suspend fun processQueue() {
        try {
            while (scope.isActive) {
                val next = _queue.value.firstOrNull { it.state == DownloadState.QUEUED }
                if (next == null) break
                executeDownload(next)
            }
        } finally {
            activeDownloadJob = null
            _activeDownload.value = null
            DownloadForegroundService.stop(context)
            synchronized(queueLock) {
                queueJob = null
                if (_queue.value.any { it.state == DownloadState.QUEUED }) {
                    queueJob = scope.launch { processQueue() }
                }
            }
        }
    }

    private suspend fun executeDownload(item: DownloadItem) {
        val request = synchronized(queueLock) { requestMap[item.id] } ?: run {
            updateItemState(item.id, DownloadState.FAILED, "Download request was lost")
            persistCurrent(item.id)
            return
        }
        val engine = engineRegistry.resolveEngine(item.url) ?: run {
            updateItemState(item.id, DownloadState.FAILED, "No supported engine found for ${item.url}")
            persistCurrent(item.id)
            return
        }

        updateItemState(item.id, DownloadState.DOWNLOADING)
        _activeDownload.value = item.copy(state = DownloadState.DOWNLOADING)
        DownloadForegroundService.start(context, item.title)

        var terminalSuccess = false
        try {
            activeDownloadJob = scope.async {
                engine.download(request) { progress ->
                    updateItemProgress(item.id, progress)
                    DownloadForegroundService.updateProgress(
                        context = context,
                        title = item.title,
                        progress = progress.percentage.coerceIn(0f, 100f).toInt(),
                        speedText = formatBytes(progress.speedBytesPerSec) + "/s",
                        etaText = progress.etaSeconds?.let(::formatTime).orEmpty()
                    )
                }
            }
            val result = activeDownloadJob!!.await()
            if (result.isSuccess) {
                val path = result.getOrNull()
                if (path.isNullOrBlank()) {
                    updateItemState(item.id, DownloadState.FAILED, "Engine returned success without an output path")
                    persistCurrent(item.id)
                } else {
                    updateItemCompleted(item.id, path)
                    terminalSuccess = true
                }
            } else {
                updateItemState(item.id, DownloadState.FAILED, result.exceptionOrNull()?.message ?: "Download failed")
                persistCurrent(item.id)
            }
        } catch (_: CancellationException) {
            updateItemState(item.id, DownloadState.CANCELLED, "Cancelled by user")
            persistCurrent(item.id)
        } catch (e: Exception) {
            updateItemState(item.id, DownloadState.FAILED, e.message ?: "Download failed")
            persistCurrent(item.id)
        } finally {
            activeDownloadJob = null
            // Keep failed/cancelled requests in memory so Retry can execute the
            // exact same DownloadRequest again. Completed requests are removed.
            if (terminalSuccess) {
                synchronized(queueLock) { requestMap.remove(item.id) }
            }
        }
    }

    private fun updateItemState(id: String, state: DownloadState, error: String? = null) {
        _queue.value = _queue.value.map { if (it.id == id) it.copy(state = state, errorMessage = error) else it }
        val current = _activeDownload.value
        if (current?.id == id) _activeDownload.value = current.copy(state = state, errorMessage = error)
    }

    private fun updateItemProgress(id: String, progress: DownloadProgress) {
        _queue.value = _queue.value.map { if (it.id == id) it.copy(progress = progress) else it }
        val current = _activeDownload.value
        if (current?.id == id) _activeDownload.value = current.copy(progress = progress)
    }

    private suspend fun updateItemCompleted(id: String, filePath: String) {
        var completed: DownloadItem? = null
        _queue.value = _queue.value.map {
            if (it.id == id) {
                val size = it.progress.bytesDownloaded.coerceAtLeast(it.progress.totalBytes)
                val updated = it.copy(state = DownloadState.COMPLETED, filePath = filePath, fileSize = size)
                completed = updated
                updated
            } else it
        }
        completed?.let { dao.insertOrUpdate(DownloadEntity.fromDownloadItem(it)) }
    }

    private fun persistCurrent(id: String) {
        val item = _queue.value.find { it.id == id } ?: return
        scope.launch { dao.insertOrUpdate(DownloadEntity.fromDownloadItem(item)) }
    }

    fun cancel(id: String) {
        val active = _activeDownload.value?.id == id
        if (active) {
            activeDownloadJob?.cancel()
            DownloadForegroundService.updateProgress(context, "Cancelling…", 0, "", "")
        } else {
            updateItemState(id, DownloadState.CANCELLED, "Cancelled by user")
            persistCurrent(id)
        }
        ensureQueueProcessor()
    }

    fun retry(id: String) {
        if (_queue.value.none { it.id == id }) return
        _queue.value = _queue.value.map {
            if (it.id == id) it.copy(state = DownloadState.QUEUED, errorMessage = null, progress = DownloadProgress()) else it
        }
        _activeDownload.value = null
        ensureQueueProcessor()
    }

    fun remove(id: String) {
        if (_activeDownload.value?.id == id) activeDownloadJob?.cancel()
        _queue.value = _queue.value.filter { it.id != id }
        synchronized(queueLock) { requestMap.remove(id) }
        ensureQueueProcessor()
    }

    fun clearHistory() = scope.launch { dao.clearAll() }
    fun deleteHistoryItem(id: String) = scope.launch { dao.deleteById(id) }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var value = bytes.toDouble(); var index = 0
            while (value >= 1024.0 && index < units.lastIndex) { value /= 1024.0; index++ }
            return String.format("%.1f %s", value, units[index])
        }
        fun formatTime(seconds: Long): String {
            if (seconds <= 0) return "--"
            val m = seconds / 60; val s = seconds % 60; val h = m / 60; val rem = m % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, rem, s) else String.format("%02d:%02d", rem, s)
        }
    }
}
