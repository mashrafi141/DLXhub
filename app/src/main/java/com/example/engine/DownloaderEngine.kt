package com.example.engine

import com.example.model.DownloadProgress
import com.example.model.DownloadRequest
import com.example.model.MediaMetadata
import com.example.model.PlatformType

interface DownloaderEngine {
    val platform: PlatformType

    fun supports(url: String): Boolean

    suspend fun probe(url: String): Result<MediaMetadata>

    suspend fun download(
        request: DownloadRequest,
        onProgress: (DownloadProgress) -> Unit
    ): Result<String>
}
