package com.example.model

enum class DownloadType {
    VIDEO,
    AUDIO
}

enum class DownloadState {
    QUEUED,
    RESOLVING,
    READY,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class QualityOption(
    val id: String,
    val label: String,
    val resolutionOrBitrate: String,
    val format: String,
    val isBest: Boolean = false,
    val directUrl: String? = null
)

data class MediaMetadata(
    val url: String,
    val platform: PlatformType,
    val title: String,
    val channelOrAuthor: String? = null,
    val durationSeconds: Long? = null,
    val thumbnail: String? = null,
    val availableVideoQualities: List<QualityOption> = emptyList(),
    val availableAudioQualities: List<QualityOption> = emptyList(),
    val isPlaylist: Boolean = false,
    val playlistTitle: String? = null,
    val playlistEntries: List<PlaylistItem> = emptyList()
)

data class PlaylistItem(
    val index: Int,
    val title: String,
    val url: String
)

data class DownloadProgress(
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
    val percentage: Float = 0f,
    val statusText: String = ""
)

data class DownloadRequest(
    val id: String,
    val url: String,
    val platform: PlatformType,
    val type: DownloadType,
    val quality: QualityOption,
    val customTitle: String? = null,
    val playlistName: String? = null,
    val itemIndex: Int? = null,
    val totalItems: Int? = null
)

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val platform: PlatformType,
    val type: DownloadType,
    val qualityLabel: String,
    val progress: DownloadProgress = DownloadProgress(),
    val state: DownloadState = DownloadState.QUEUED,
    val errorMessage: String? = null,
    val filePath: String? = null,
    val fileSize: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

data class CookieProfile(
    val platform: PlatformType,
    val fileName: String,
    val isConfigured: Boolean,
    val entryCount: Int = 0,
    val lastModified: Long = 0L
)
